package DGU_AI_LAB.admin_be.global.cluster;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import DGU_AI_LAB.admin_be.global.cluster.ClusterClientProperties.Cluster;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.netty.channel.ChannelOption;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 클러스터별 Config Server·Kubernetes 연결을 생성하고 소유한다.
 *
 * <p>각 클러스터는 별도 WebClient connection pool, 내부 API 토큰, kubeconfig와
 * KubernetesClient를 가진다. 호출자가 명시한 clusterId만 사용하며 기본 클러스터나 로컬
 * kubeconfig로 대체하지 않는다.
 */
public class ClusterClientRegistry implements AutoCloseable {

    public static final String API_TOKEN_HEADER = "X-Internal-Token";
    public static final String CLUSTER_ID_HEADER = "X-Cluster-Id";
    static final Path SERVICE_ACCOUNT_DIRECTORY =
            Path.of("/var/run/secrets/kubernetes.io/serviceaccount");
    private static final ObjectMapper KUBECONFIG_MAPPER = new ObjectMapper(new YAMLFactory());

    private final Map<String, Clients> clients;

    public ClusterClientRegistry(ClusterClientProperties properties) {
        if (properties == null || properties.clusters() == null || properties.clusters().isEmpty()) {
            throw new IllegalStateException("provi.clusters 설정이 비어 있습니다.");
        }
        Map<String, Cluster> enabledClusters = properties.enabledClusters();
        if (enabledClusters == null || enabledClusters.isEmpty()) {
            throw new IllegalStateException("활성화된 provi.clusters 설정이 비어 있습니다.");
        }
        long inClusterProfiles = enabledClusters.values().stream()
                .filter(settings -> settings != null && settings.inCluster())
                .count();
        if (inClusterProfiles > 1) {
            throw new IllegalStateException(
                    "in-cluster 프로필은 하나만 설정할 수 있습니다. 여러 프로필은 같은 로컬 클러스터를 가리킵니다.");
        }

        Map<String, PreparedCluster> prepared = prepareClusters(enabledClusters);
        Map<String, Clients> built = new LinkedHashMap<>();
        try {
            prepared.forEach((clusterId, cluster) -> built.put(
                    clusterId, createClients(clusterId, cluster.settings(), cluster.kubernetesConfig())));
            this.clients = Map.copyOf(built);
        } catch (RuntimeException e) {
            built.values().forEach(Clients::close);
            throw e;
        }
    }

    public WebClient configWebClient(String clusterId) {
        return clients(clusterId).configWebClient();
    }

    public WebClient podWebClient(String clusterId) {
        return clients(clusterId).podWebClient();
    }

    public KubernetesClient kubernetesClient(String clusterId) {
        return clients(clusterId).kubernetesClient();
    }

    public String podNamespace(String clusterId) {
        return clients(clusterId).podNamespace();
    }

    public Set<String> clusterIds() {
        return clients.keySet();
    }

    private Clients clients(String requestedId) {
        String clusterId = ClusterId.canonical(requestedId);
        Clients selected = clients.get(clusterId);
        if (selected == null) {
            throw new IllegalArgumentException("설정되지 않은 clusterId입니다: " + clusterId);
        }
        return selected;
    }

    private static Clients createClients(String clusterId, Cluster settings, Config kubernetesConfig) {
        ConnectionProvider configProvider = ConnectionProvider.builder(poolName("config", clusterId))
                .maxConnections(50)
                .maxIdleTime(Duration.ofSeconds(20))
                .build();
        ConnectionProvider podProvider = ConnectionProvider.builder(poolName("pod", clusterId))
                .maxConnections(10)
                .maxIdleTime(Duration.ofSeconds(60))
                .build();

        try {
            WebClient configWebClient = webClient(
                    clusterId, settings.baseUrl(), settings.apiToken(), configProvider,
                    10_000, settings.timeoutSeconds());
            WebClient podWebClient = webClient(
                    clusterId, settings.baseUrl(), settings.apiToken(), podProvider,
                    30_000, settings.podTimeoutSeconds());
            KubernetesClient kubernetesClient = new KubernetesClientBuilder()
                    .withConfig(kubernetesConfig)
                    .build();
            return new Clients(configWebClient, podWebClient, kubernetesClient,
                    settings.podNamespace().trim(), configProvider, podProvider);
        } catch (RuntimeException e) {
            configProvider.dispose();
            podProvider.dispose();
            throw e;
        }
    }

    private static WebClient webClient(String clusterId, String baseUrl, String apiToken,
                                       ConnectionProvider provider, int connectTimeoutMillis,
                                       int responseTimeoutSeconds) {
        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
                .responseTimeout(Duration.ofSeconds(responseTimeoutSeconds));
        return WebClient.builder()
                .baseUrl(baseUrl.trim())
                .defaultHeader(API_TOKEN_HEADER, apiToken)
                .defaultHeader(CLUSTER_ID_HEADER, clusterId)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * 클라이언트를 하나라도 만들기 전에 모든 논리 클러스터의 자격증명·대상을 확정하고 서로 다른지 검증한다.
     * 같은 파일을 다른 이름으로 적거나 kubeconfig를 복사해 같은 API endpoint를 가리키면 FARM/LAB 요청이
     * 한 클러스터로 합쳐질 수 있으므로 시작 자체를 거절한다.
     */
    private static Map<String, PreparedCluster> prepareClusters(Map<String, Cluster> configured) {
        Map<String, PreparedCluster> prepared = new LinkedHashMap<>();
        Map<Path, String> kubeconfigOwners = new LinkedHashMap<>();
        Map<String, String> apiOwners = new LinkedHashMap<>();
        Map<String, String> configServerOwners = new LinkedHashMap<>();

        configured.forEach((configuredId, settings) -> {
            String clusterId = ClusterId.canonical(configuredId);
            if (prepared.containsKey(clusterId)) {
                throw new IllegalStateException(
                        "provi.clusters에 대소문자만 다른 clusterId가 겹칩니다: " + configuredId);
            }
            requireSettings(clusterId, settings);

            rejectDuplicateEndpoint(configServerOwners,
                    canonicalEndpoint(clusterId, "Config Server base-url", settings.baseUrl()),
                    clusterId, "Config Server base-url");

            Config kubernetesConfig;
            if (settings.inCluster()) {
                try {
                    kubernetesConfig = inClusterConfig(clusterId, System.getenv(), SERVICE_ACCOUNT_DIRECTORY);
                } catch (RuntimeException e) {
                    throw new IllegalStateException(
                            "클러스터 " + clusterId + "의 in-cluster Kubernetes 설정을 불러오지 못했습니다.", e);
                }
            } else {
                Path kubeconfigPath = canonicalKubeconfigPath(clusterId, settings.kubeconfigPath());
                String previous = kubeconfigOwners.putIfAbsent(kubeconfigPath, clusterId);
                if (previous != null) {
                    throw new IllegalStateException("클러스터 " + previous + "와 " + clusterId
                            + "가 같은 kubeconfig 경로를 사용합니다: " + kubeconfigPath);
                }
                kubernetesConfig = readKubeconfig(clusterId, kubeconfigPath);
            }

            rejectDuplicateEndpoint(apiOwners,
                    canonicalEndpoint(clusterId, "Kubernetes API", kubernetesConfig.getMasterUrl()),
                    clusterId, "Kubernetes API endpoint");
            prepared.put(clusterId, new PreparedCluster(settings, kubernetesConfig));
        });
        return prepared;
    }

    private static Path canonicalKubeconfigPath(String clusterId, String kubeconfigPath) {
        Path normalized;
        try {
            normalized = Path.of(kubeconfigPath.trim()).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "클러스터 " + clusterId + "의 kubeconfig 경로가 올바르지 않습니다.", e);
        }
        if (!Files.isRegularFile(normalized) || !Files.isReadable(normalized)) {
            throw new IllegalStateException(
                    "클러스터 " + clusterId + "의 kubeconfig를 읽을 수 없습니다: " + normalized);
        }
        try {
            return normalized.toRealPath();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "클러스터 " + clusterId + "의 kubeconfig 실제 경로를 확인할 수 없습니다: " + normalized, e);
        }
    }

    private static Config readKubeconfig(String clusterId, Path path) {
        try {
            String contents = Files.readString(path, StandardCharsets.UTF_8);
            if (contents.isBlank()) {
                throw new IllegalStateException(
                        "클러스터 " + clusterId + "의 kubeconfig가 비어 있습니다: " + path);
            }
            String declaredServer = validateKubeconfigStructure(clusterId, path, contents);
            Config parsed = Config.fromKubeconfig(contents);
            String declaredEndpoint = canonicalEndpoint(clusterId, "kubeconfig server", declaredServer);
            String parsedEndpoint = canonicalEndpoint(clusterId, "Fabric8 Kubernetes API", parsed.getMasterUrl());
            if (!declaredEndpoint.equals(parsedEndpoint)) {
                throw new IllegalStateException(
                        "클러스터 " + clusterId + "의 kubeconfig 해석 결과가 current-context의 server와 다릅니다: "
                                + path);
            }
            return parsed;
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(
                    "클러스터 " + clusterId + "의 kubeconfig를 불러오지 못했습니다: " + path, e);
        }
    }

    /**
     * Fabric8이 불완전한 문서에서 로컬/기본 주소를 추측하기 전에 current-context가 실제 context와 cluster를
     * 가리키고 그 cluster에 명시적인 server가 있는지 검증한다.
     */
    private static String validateKubeconfigStructure(String clusterId, Path path, String contents)
            throws IOException {
        JsonNode root = KUBECONFIG_MAPPER.readTree(contents);
        if (root == null || !root.isObject()
                || !"v1".equals(requiredText(root, "apiVersion"))
                || !"Config".equals(requiredText(root, "kind"))) {
            throw new IllegalStateException(
                    "클러스터 " + clusterId + "의 kubeconfig apiVersion/kind가 올바르지 않습니다: " + path);
        }

        String currentContext = requiredText(root, "current-context");
        JsonNode contextEntry = namedEntry(root.get("contexts"), currentContext);
        String referencedCluster = contextEntry == null
                ? null : requiredText(contextEntry.get("context"), "cluster");
        if (referencedCluster == null) {
            throw new IllegalStateException(
                    "클러스터 " + clusterId + "의 kubeconfig current-context를 찾을 수 없습니다: " + path);
        }

        JsonNode clusterEntry = namedEntry(root.get("clusters"), referencedCluster);
        String server = clusterEntry == null
                ? null : requiredText(clusterEntry.get("cluster"), "server");
        if (server == null) {
            throw new IllegalStateException(
                    "클러스터 " + clusterId + "의 kubeconfig current-context에 server가 없습니다: " + path);
        }
        return server;
    }

    private static JsonNode namedEntry(JsonNode entries, String name) {
        if (entries == null || !entries.isArray() || name == null) {
            return null;
        }
        for (JsonNode entry : entries) {
            if (name.equals(requiredText(entry, "name"))) {
                return entry;
            }
        }
        return null;
    }

    private static String requiredText(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            return null;
        }
        return value.textValue().trim();
    }

    private static String canonicalEndpoint(String clusterId, String label, String value) {
        try {
            URI uri = new URI(value.trim()).normalize();
            String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
            if (scheme == null || host == null) {
                throw new URISyntaxException(value, "scheme 또는 host가 없습니다");
            }
            int port = uri.getPort();
            if (("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80)) {
                port = -1;
            }
            String path = uri.getPath() == null ? "" : uri.getPath();
            while (path.endsWith("/") && path.length() > 1) {
                path = path.substring(0, path.length() - 1);
            }
            if ("/".equals(path)) {
                path = "";
            }
            return new URI(scheme, null, host, port, path, null, null).toString();
        } catch (RuntimeException | URISyntaxException e) {
            throw new IllegalStateException(
                    "클러스터 " + clusterId + "의 " + label + " 주소가 올바르지 않습니다: " + value, e);
        }
    }

    private static void rejectDuplicateEndpoint(Map<String, String> owners, String endpoint,
                                                String clusterId, String label) {
        String previous = owners.putIfAbsent(endpoint, clusterId);
        if (previous != null) {
            throw new IllegalStateException("클러스터 " + previous + "와 " + clusterId
                    + "가 같은 " + label + "를 사용합니다: " + endpoint);
        }
    }

    /**
     * 현재 Pod에 주입된 ServiceAccount 자격증명만 사용한다. 로컬 kubeconfig나 Fabric8 기본값으로
     * 돌아가지 않도록 환경 변수와 세 파일을 직접 확인한 뒤 Config를 만든다.
     */
    static Config inClusterConfig(String clusterId, Map<String, String> environment, Path serviceAccountDirectory) {
        String host = requiredEnvironment(clusterId, environment, "KUBERNETES_SERVICE_HOST");
        String portText = requiredEnvironment(clusterId, environment, "KUBERNETES_SERVICE_PORT");
        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "클러스터 " + clusterId + "의 KUBERNETES_SERVICE_PORT가 올바르지 않습니다.", e);
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalStateException(
                    "클러스터 " + clusterId + "의 KUBERNETES_SERVICE_PORT가 올바르지 않습니다.");
        }

        Path tokenPath = serviceAccountDirectory.resolve("token");
        Path caPath = serviceAccountDirectory.resolve("ca.crt");
        Path namespacePath = serviceAccountDirectory.resolve("namespace");
        String token = readRequiredFile(clusterId, "ServiceAccount token", tokenPath);
        readRequiredFile(clusterId, "ServiceAccount CA", caPath);
        String namespace = readRequiredFile(clusterId, "ServiceAccount namespace", namespacePath);
        String urlHost = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;

        return new ConfigBuilder()
                .withMasterUrl("https://" + urlHost + ":" + port)
                .withOauthToken(token)
                .withCaCertFile(caPath.toAbsolutePath().normalize().toString())
                .withNamespace(namespace)
                .withTrustCerts(false)
                .build();
    }

    private static String requiredEnvironment(
            String clusterId, Map<String, String> environment, String name) {
        String value = environment == null ? null : environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "클러스터 " + clusterId + "의 " + name + " 환경 변수가 비어 있습니다.");
        }
        return value.trim();
    }

    private static String readRequiredFile(String clusterId, String label, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized) || !Files.isReadable(normalized)) {
            throw new IllegalStateException(
                    "클러스터 " + clusterId + "의 " + label + " 파일을 읽을 수 없습니다: " + normalized);
        }
        try {
            String value = Files.readString(normalized, StandardCharsets.UTF_8).trim();
            if (value.isEmpty()) {
                throw new IllegalStateException(
                        "클러스터 " + clusterId + "의 " + label + " 파일이 비어 있습니다: " + normalized);
            }
            return value;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "클러스터 " + clusterId + "의 " + label + " 파일을 읽지 못했습니다: " + normalized, e);
        }
    }

    private static void requireSettings(String clusterId, Cluster settings) {
        if (settings == null) {
            throw new IllegalStateException("클러스터 " + clusterId + " 설정이 비어 있습니다.");
        }
        requireText(clusterId, "base-url", settings.baseUrl());
        requireText(clusterId, "api-token", settings.apiToken());
        requireText(clusterId, "pod-namespace", settings.podNamespace());
        boolean hasKubeconfig = settings.kubeconfigPath() != null && !settings.kubeconfigPath().isBlank();
        if (settings.inCluster() == hasKubeconfig) {
            throw new IllegalStateException(
                    "provi.clusters." + clusterId
                            + "에는 in-cluster와 kubeconfig-path 중 정확히 하나를 설정해야 합니다.");
        }
        if (settings.timeoutSeconds() <= 0 || settings.podTimeoutSeconds() <= 0) {
            throw new IllegalStateException(
                    "클러스터 " + clusterId + "의 timeout은 1초 이상이어야 합니다.");
        }
    }

    private static void requireText(String clusterId, String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "provi.clusters." + clusterId + "." + field + " 설정이 비어 있습니다.");
        }
    }

    private static String poolName(String kind, String clusterId) {
        return "provi-" + kind + "-" + clusterId.toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public void close() {
        clients.values().forEach(Clients::close);
    }

    private record Clients(
            WebClient configWebClient,
            WebClient podWebClient,
            KubernetesClient kubernetesClient,
            String podNamespace,
            ConnectionProvider configProvider,
            ConnectionProvider podProvider
    ) {
        private void close() {
            try {
                kubernetesClient.close();
            } finally {
                configProvider.dispose();
                podProvider.dispose();
            }
        }
    }

    private record PreparedCluster(Cluster settings, Config kubernetesConfig) {}
}
