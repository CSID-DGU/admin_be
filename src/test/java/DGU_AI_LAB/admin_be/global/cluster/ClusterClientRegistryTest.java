package DGU_AI_LAB.admin_be.global.cluster;

import DGU_AI_LAB.admin_be.global.cluster.ClusterClientProperties.Cluster;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ClusterClientRegistry")
class ClusterClientRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("FARM과 LAB은 주소·토큰·namespace·KubernetesClient를 서로 공유하지 않는다")
    void ownsIndependentClientsPerCluster() throws Exception {
        Path farmKubeconfig = kubeconfig("farm", "https://farm-api.test.invalid:6443", "farm-kube-token");
        Path labKubeconfig = kubeconfig("lab", "https://lab-api.test.invalid:6443", "lab-kube-token");
        ClusterClientProperties properties = new ClusterClientProperties(Map.of(
                "FARM", cluster("http://farm-config:8080", "farm-api-token", farmKubeconfig, "farm-pods"),
                "LAB", cluster("http://lab-config:8080", "lab-api-token", labKubeconfig, "lab-pods")), true);

        try (ClusterClientRegistry registry = new ClusterClientRegistry(properties)) {
            ClientRequest farm = sentRequest(registry.configWebClient(" farm "));
            ClientRequest lab = sentRequest(registry.configWebClient("lab"));

            assertThat(farm.url().toString()).isEqualTo("http://farm-config:8080/health");
            assertThat(farm.headers().getFirst(ClusterClientRegistry.API_TOKEN_HEADER))
                    .isEqualTo("farm-api-token");
            assertThat(farm.headers().getFirst(ClusterClientRegistry.CLUSTER_ID_HEADER))
                    .isEqualTo("FARM");
            assertThat(lab.url().toString()).isEqualTo("http://lab-config:8080/health");
            assertThat(lab.headers().getFirst(ClusterClientRegistry.API_TOKEN_HEADER))
                    .isEqualTo("lab-api-token");
            assertThat(lab.headers().getFirst(ClusterClientRegistry.CLUSTER_ID_HEADER))
                    .isEqualTo("LAB");

            assertThat(registry.podNamespace("FARM")).isEqualTo("farm-pods");
            assertThat(registry.podNamespace("LAB")).isEqualTo("lab-pods");
            assertThat(registry.configWebClient("FARM")).isNotSameAs(registry.configWebClient("LAB"));
            assertThat(registry.podWebClient("FARM")).isNotSameAs(registry.podWebClient("LAB"));
            assertThat(registry.kubernetesClient("FARM")).isNotSameAs(registry.kubernetesClient("LAB"));
            assertThat(registry.clusterIds()).containsExactlyInAnyOrder("FARM", "LAB");
        }
    }

    @Test
    @DisplayName("LAB profile을 미리 설정해도 feature flag 전에는 LAB client를 생성하지 않는다")
    void labProfileStaysDisabledUntilFeatureFlagIsEnabled() throws Exception {
        Path farmKubeconfig = kubeconfig(
                "farm-disabled-lab", "https://farm-api.test.invalid:6443", "farm-token");
        Cluster lab = cluster(
                "http://lab-config", "lab-secret", tempDir.resolve("not-read-while-disabled.yaml"), "lab");
        ClusterClientProperties properties = new ClusterClientProperties(Map.of(
                "FARM", cluster("http://farm-config", "farm-secret", farmKubeconfig, "farm"),
                "LAB", lab
        ), false);

        try (ClusterClientRegistry registry = new ClusterClientRegistry(properties)) {
            assertThat(registry.clusterIds()).containsExactly("FARM");
            assertThatThrownBy(() -> registry.configWebClient("LAB"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("LAB");
        }
    }

    @Test
    @DisplayName("등록되지 않았거나 비어 있는 clusterId는 기존 클러스터로 대체하지 않는다")
    void unknownClusterFailsClosed() throws Exception {
        Path kubeconfig = kubeconfig("farm", "https://farm-api.test.invalid:6443", "token");
        try (ClusterClientRegistry registry = new ClusterClientRegistry(new ClusterClientProperties(
                Map.of("FARM", cluster("http://farm-config", "secret", kubeconfig, "default")), false))) {
            assertThatThrownBy(() -> registry.configWebClient("LAB"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("LAB");
            assertThatThrownBy(() -> registry.configWebClient(null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> registry.kubernetesClient(" "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("대소문자만 다른 설정 키가 중복되면 시작하지 않는다")
    void duplicateCanonicalIdsAreRejected() throws Exception {
        Path kubeconfig = kubeconfig("farm", "https://farm-api.test.invalid:6443", "token");
        Map<String, Cluster> clusters = new LinkedHashMap<>();
        clusters.put("FARM", cluster("http://farm-a", "secret-a", kubeconfig, "default"));
        clusters.put("farm", cluster("http://farm-b", "secret-b", kubeconfig, "default"));

        assertThatThrownBy(() -> new ClusterClientRegistry(new ClusterClientProperties(clusters, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("겹칩니다");
    }

    @Test
    @DisplayName("FARM과 LAB이 같은 canonical kubeconfig 파일을 사용하면 클라이언트를 만들기 전에 거절한다")
    void duplicateCanonicalKubeconfigPathsAreRejected() throws Exception {
        Path kubeconfig = kubeconfig("shared", "https://farm-api.test.invalid:6443", "token");
        Path sameFileWithDot = kubeconfig.getParent().resolve(".").resolve(kubeconfig.getFileName());

        assertThatThrownBy(() -> new ClusterClientRegistry(new ClusterClientProperties(Map.of(
                "FARM", cluster("http://farm-config", "farm-secret", kubeconfig, "farm"),
                "LAB", cluster("http://lab-config", "lab-secret", sameFileWithDot, "lab")), true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("같은 kubeconfig 경로");
    }

    @Test
    @DisplayName("서로 다른 kubeconfig 파일도 같은 Kubernetes API endpoint를 가리키면 거절한다")
    void duplicateResolvedKubernetesApiEndpointsAreRejected() throws Exception {
        Path farm = kubeconfig("farm-same-api", "https://farm-api.test.invalid:6443", "farm-token");
        Path lab = kubeconfig("lab-same-api", "https://farm-api.test.invalid:6443/", "lab-token");

        assertThatThrownBy(() -> new ClusterClientRegistry(new ClusterClientProperties(Map.of(
                "FARM", cluster("http://farm-config", "farm-secret", farm, "farm"),
                "LAB", cluster("http://lab-config", "lab-secret", lab, "lab")), true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("같은 Kubernetes API endpoint");
    }

    @Test
    @DisplayName("FARM과 LAB이 같은 Config Server base-url을 사용하면 잘못된 서버 연결을 숨기지 않는다")
    void duplicateConfigServerEndpointsAreRejected() throws Exception {
        Path farm = kubeconfig("farm-unique", "https://farm-api.test.invalid:6443", "farm-token");
        Path lab = kubeconfig("lab-unique", "https://lab-api.test.invalid:6443", "lab-token");

        assertThatThrownBy(() -> new ClusterClientRegistry(new ClusterClientProperties(Map.of(
                "FARM", cluster("http://shared-config", "farm-secret", farm, "farm"),
                "LAB", cluster("http://shared-config/", "lab-secret", lab, "lab")), true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("같은 Config Server base-url");
    }

    @Test
    @DisplayName("kubeconfig를 읽을 수 없으면 로컬 기본 kubeconfig로 돌아가지 않는다")
    void missingKubeconfigIsRejected() {
        Cluster settings = cluster(
                "http://farm-config", "secret", tempDir.resolve("missing.yaml"), "default");

        assertThatThrownBy(() -> new ClusterClientRegistry(
                new ClusterClientProperties(Map.of("FARM", settings), false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kubeconfig를 읽을 수 없습니다");
    }

    @Test
    @DisplayName("내용이 YAML이어도 kubeconfig 구조가 아니면 Fabric8 기본 API 주소로 대체하지 않는다")
    void malformedKubeconfigDoesNotFallBackToFabric8Defaults() throws Exception {
        Path malformed = tempDir.resolve("malformed.yaml");
        Files.writeString(malformed, "not-a-kubeconfig: true\n");

        assertThatThrownBy(() -> new ClusterClientRegistry(new ClusterClientProperties(Map.of(
                "FARM", cluster("http://farm-config", "secret", malformed, "default")), false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kubeconfig를 불러오지 못했습니다");
    }

    @Test
    @DisplayName("current-context가 없는 kubeconfig는 임의의 첫 cluster를 선택하지 않는다")
    void missingCurrentContextIsRejected() throws Exception {
        Path malformed = tempDir.resolve("missing-current-context.yaml");
        Files.writeString(malformed, """
                apiVersion: v1
                kind: Config
                clusters:
                  - name: cluster
                    cluster:
                      server: https://farm-api.test.invalid:6443
                contexts: []
                """);

        assertThatThrownBy(() -> new ClusterClientRegistry(new ClusterClientProperties(Map.of(
                "FARM", cluster("http://farm-config", "secret", malformed, "default")), false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kubeconfig를 불러오지 못했습니다");
    }

    @Test
    @DisplayName("클러스터 필수값이 비어 있으면 일부 클라이언트만 생성하지 않고 전체 시작을 중단한다")
    void blankRequiredSettingIsRejected() throws Exception {
        Path kubeconfig = kubeconfig("farm", "https://farm-api.test.invalid:6443", "token");
        Cluster settings = new Cluster("http://farm-config", " ", 1, 2, false,
                kubeconfig.toString(), "default");

        assertThatThrownBy(() -> new ClusterClientRegistry(
                new ClusterClientProperties(Map.of("FARM", settings), false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("api-token");
    }

    @Test
    @DisplayName("in-cluster는 주입된 ServiceAccount 주소·토큰·CA·namespace만 사용한다")
    void explicitInClusterModeUsesServiceAccountOnly() throws Exception {
        Path serviceAccount = Files.createDirectory(tempDir.resolve("serviceaccount"));
        Files.writeString(serviceAccount.resolve("token"), "pod-token\n");
        Files.writeString(serviceAccount.resolve("ca.crt"), "test-ca\n");
        Files.writeString(serviceAccount.resolve("namespace"), "admin\n");

        var config = ClusterClientRegistry.inClusterConfig("FARM", Map.of(
                "KUBERNETES_SERVICE_HOST", "10.96.0.1",
                "KUBERNETES_SERVICE_PORT", "443"), serviceAccount);

        assertThat(config.getMasterUrl()).isEqualTo("https://10.96.0.1:443/");
        assertThat(config.getOauthToken()).isEqualTo("pod-token");
        assertThat(config.getCaCertFile()).isEqualTo(
                serviceAccount.resolve("ca.crt").toAbsolutePath().normalize().toString());
        assertThat(config.getNamespace()).isEqualTo("admin");
    }

    @Test
    @DisplayName("in-cluster 환경 변수나 ServiceAccount 파일이 없으면 호스트 기본 설정으로 돌아가지 않는다")
    void explicitInClusterModeFailsClosed() {
        assertThatThrownBy(() -> ClusterClientRegistry.inClusterConfig(
                "FARM", Map.of(), tempDir.resolve("missing-serviceaccount")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KUBERNETES_SERVICE_HOST");
    }

    @Test
    @DisplayName("in-cluster 주소가 있어도 ServiceAccount 파일이 없으면 시작하지 않는다")
    void explicitInClusterModeRequiresServiceAccountFiles() {
        assertThatThrownBy(() -> ClusterClientRegistry.inClusterConfig("FARM", Map.of(
                "KUBERNETES_SERVICE_HOST", "10.96.0.1",
                "KUBERNETES_SERVICE_PORT", "443"), tempDir.resolve("missing-serviceaccount")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ServiceAccount token");
    }

    @Test
    @DisplayName("in-cluster와 kubeconfig를 동시에 지정하면 모호한 자격증명 선택을 거절한다")
    void inClusterAndKubeconfigAreMutuallyExclusive() throws Exception {
        Path kubeconfig = kubeconfig("farm", "https://farm-api.test.invalid:6443", "token");
        Cluster settings = new Cluster(
                "http://config-server", "secret", 1, 2, true, kubeconfig.toString(), "default");

        assertThatThrownBy(() -> new ClusterClientRegistry(
                new ClusterClientProperties(Map.of("FARM", settings), false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("정확히 하나");
    }

    @Test
    @DisplayName("in-cluster와 kubeconfig가 모두 없으면 로컬 기본 설정을 추측하지 않는다")
    void kubernetesAccessModeIsRequired() {
        Cluster settings = new Cluster(
                "http://config-server", "secret", 1, 2, false, null, "default");

        assertThatThrownBy(() -> new ClusterClientRegistry(
                new ClusterClientProperties(Map.of("FARM", settings), false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("정확히 하나");
    }

    @Test
    @DisplayName("두 클러스터를 모두 in-cluster로 지정하면 같은 로컬 클러스터를 중복 등록하지 않는다")
    void onlyOneInClusterProfileIsAllowed() {
        Cluster farm = new Cluster(
                "http://farm-config", "farm-secret", 1, 2, true, null, "default");
        Cluster lab = new Cluster(
                "http://lab-config", "lab-secret", 1, 2, true, null, "default");

        assertThatThrownBy(() -> new ClusterClientRegistry(
                new ClusterClientProperties(Map.of("FARM", farm, "LAB", lab), true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("하나만");
    }

    private Cluster cluster(String baseUrl, String apiToken, Path kubeconfig, String namespace) {
        return new Cluster(baseUrl, apiToken, 1, 2, false, kubeconfig.toString(), namespace);
    }

    private Path kubeconfig(String name, String server, String token) throws Exception {
        Path file = tempDir.resolve(name + "-kubeconfig.yaml");
        Files.writeString(file, """
                apiVersion: v1
                kind: Config
                clusters:
                  - name: cluster
                    cluster:
                      server: %s
                      insecure-skip-tls-verify: true
                users:
                  - name: admin
                    user:
                      token: %s
                contexts:
                  - name: context
                    context:
                      cluster: cluster
                      user: admin
                current-context: context
                """.formatted(server, token));
        return file;
    }

    /** 네트워크로 보내지 않고 mutate()가 보존한 base URL과 기본 헤더만 확인한다. */
    private ClientRequest sentRequest(WebClient client) {
        AtomicReference<ClientRequest> seen = new AtomicReference<>();
        client.mutate()
                .exchangeFunction(request -> {
                    seen.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build());
                })
                .build()
                .get()
                .uri("/health")
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return seen.get();
    }
}
