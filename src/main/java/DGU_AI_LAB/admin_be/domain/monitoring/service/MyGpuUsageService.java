package DGU_AI_LAB.admin_be.domain.monitoring.service;

import DGU_AI_LAB.admin_be.domain.monitoring.client.PrometheusClient;
import DGU_AI_LAB.admin_be.domain.monitoring.client.PrometheusClient.Sample;
import DGU_AI_LAB.admin_be.domain.monitoring.dto.response.MyGpuUsageResponseDTO;
import DGU_AI_LAB.admin_be.domain.monitoring.dto.response.MyGpuUsageResponseDTO.Gpu;
import DGU_AI_LAB.admin_be.domain.monitoring.dto.response.MyGpuUsageResponseDTO.Process;
import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import DGU_AI_LAB.admin_be.domain.nodes.repository.NodeRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 내 서버(Pod가 올라간 노드)의 GPU별 사용률·메모리와 프로세스. 다른 사용자의 정보는 여기서 집계해
 * 사람 수·메모리 합계만 내보낸다 — 응답 DTO에 식별 정보를 담을 자리를 두지 않는다.
 *
 * <p>트랜잭션을 길게 잡지 않는다: DB 읽기는 저장소 호출마다 끝나고, Prometheus 호출은 DB 밖에서 한다.
 * 노드별 Prometheus 결과는 짧게 캐시해 사용자가 화면을 열 때마다 질의가 나가지 않게 한다.
 *
 * <p>보여 주는 GPU: config-server가 Pod에 GPU를 줄 때 노드에 등록된 GPU 종류와 같은 모델만 UUID로 넘기므로
 * (NVIDIA_VISIBLE_DEVICES), 같은 규칙으로 그 모델의 GPU만 내 GPU로 본다. 한계: 노드가 DB에 없거나 모델
 * 목록이 비어 있으면 거를 근거가 없어 노드의 모든 GPU를 보인다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MyGpuUsageService {

    private static final java.util.regex.Pattern POD_NAME = java.util.regex.Pattern.compile("[A-Za-z0-9.-]+");
    static final double MIB = 1024.0 * 1024.0;
    static final long CACHE_TTL_MS = 10_000;
    private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "my-gpu-prom");
        t.setDaemon(true);
        return t;
    });

    private final PrometheusClient prometheusClient;
    private final RequestRepository requestRepository;
    private final NodeRepository nodeRepository;

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    /** 노드 하나의 Prometheus 결과와 소유자 해석. podRamBytesByContainer: 컨테이너 ID → 그 Pod 전체 RAM(바이트), 모르면 키 없음. processesAvailable=false면 프로세스 쪽 조회가 실패한 것이다. */
    record Snapshot(List<Sample> util, Map<String, Sample> used, Map<String, Sample> free,
                    List<Sample> procMem, Map<String, Double> elapsed,
                    Map<String, Owner> podOwnerByContainer, Map<String, Owner> dockerOwnerByContainerGpu,
                    Map<String, Double> podRamBytesByContainer, boolean processesAvailable) {
    }

    /**
     * 프로세스 소유자. key는 같은 사람을 Pod·옛 Docker에서 같게 세는 값(우분투 계정)이다.
     * 옛 Docker 컨테이너는 userId·requestId를 알 수 없어 null이다.
     */
    record Owner(String key, Long userId, String name, String ubuntuUsername, Long requestId) {
    }

    private record Cached(Snapshot snapshot, long at) {
    }

    public MyGpuUsageResponseDTO getMyGpuUsage(Long requestId, Long userId) {
        Request request = requestRepository.findByIdWithUser(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!userId.equals(request.getUser().getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        // 삭제·회수 중인 신청은 podName/nodeName이 남아 있어도 그 노드의 사용량을 보여 주지 않는다.
        if (request.getStatus() != Status.FULFILLED) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        String node = request.getNodeName();
        if (request.getPodName() == null || node == null) {
            return response(node, List.of(), true, null);
        }

        Snapshot snap;
        try {
            snap = snapshot(node);
        } catch (Exception e) {
            // 'GPU 없음'과 구분되도록 빈 목록으로 숨기지 않고 오류로 알린다.
            log.warn("[MY-GPU] GPU 지표 조회 실패: node={} {}", node, e.toString());
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }
        if (snap.util().isEmpty()) {
            log.warn("[MY-GPU] 노드 이름과 맞는 GPU 지표가 없음: node={} (DCGM Hostname 라벨과 노드 이름이 다를 수 있다)", node);
        }

        Set<String> modelsWanted = nodeModels(node);
        String me = ownerKey(request);
        Map<String, List<Process>> mine = new HashMap<>();
        Map<String, Double> otherMem = new HashMap<>();
        Map<String, Set<String>> otherUsers = new HashMap<>();
        for (Sample s : snap.procMem()) {
            String uuid = s.labels().get("gpu_uuid");
            String containerId = s.labels().get("container_id");
            Owner ownerInfo = ownerOf(snap, containerId, uuid);
            String owner = ownerInfo == null ? null : ownerInfo.key();
            double mib = s.value() / MIB;
            if (me.equals(owner)) {
                mine.computeIfAbsent(uuid, k -> new ArrayList<>()).add(new Process(
                        baseName(s.labels().get("process_name")),
                        snap.elapsed().getOrDefault(procKey(s), 0.0).longValue(),
                        mib));
                continue;
            }
            otherMem.merge(uuid, mib, Double::sum);
            // 소유자를 못 찾은 컨테이너도 다른 사용자 한 명으로 센다. 컨테이너 ID가 없으면 PID로,
            // 둘 다 없으면 사람 수에는 넣지 않고 메모리에만 더한다.
            String who = owner != null ? owner
                    : isBlank(containerId) ? (isBlank(s.labels().get("pid")) ? null : "p:" + s.labels().get("pid"))
                    : "c:" + containerId;
            if (who != null) otherUsers.computeIfAbsent(uuid, k -> new HashSet<>()).add(who);
        }

        List<Gpu> gpus = snap.util().stream()
                .filter(s -> s.labels().get("UUID") != null)
                .filter(s -> modelsWanted.isEmpty() || modelsWanted.contains(normalizeModel(s.labels().get("modelName"))))
                .map(s -> {
                    String uuid = s.labels().get("UUID");
                    double usedMiB = snap.used().containsKey(uuid) ? snap.used().get(uuid).value() : 0;
                    double freeMiB = snap.free().containsKey(uuid) ? snap.free().get(uuid).value() : 0;
                    return Gpu.builder()
                            .index(parseIndex(s.labels().get("gpu")))
                            .modelName(s.labels().getOrDefault("modelName", ""))
                            .utilizationPercent(s.value())
                            .memoryUsedMiB(usedMiB)
                            .memoryTotalMiB(usedMiB + freeMiB)
                            .myProcesses(mine.getOrDefault(uuid, List.of()))
                            .otherUserCount(otherUsers.getOrDefault(uuid, Set.of()).size())
                            .otherMemoryMiB(otherMem.getOrDefault(uuid, 0.0))
                            .build();
                })
                .sorted(Comparator.comparingInt(Gpu::index))
                .toList();
        return response(node, gpus, snap.processesAvailable(), podRamMiB(request.getPodName()));
    }

    private static MyGpuUsageResponseDTO response(String node, List<Gpu> gpus, boolean processesAvailable, Double myRamMiB) {
        return MyGpuUsageResponseDTO.builder().nodeName(node).gpus(gpus).processesAvailable(processesAvailable)
                .myRamMiB(myRamMiB).build();
    }

    /** 내 Pod 전체 RAM(MiB). GPU를 안 써도 보이도록 별도로 조회하며, 실패·없음은 GPU 정보를 막지 않고 null(모름)이다. */
    private Double podRamMiB(String pod) {
        if (!POD_NAME.matcher(pod).matches()) return null;
        try {
            List<Sample> r = prometheusClient.querySeries(
                    "sum(container_memory_working_set_bytes{pod=\"" + pod + "\",container!=\"\",container!=\"POD\"})");
            return r.isEmpty() ? null : r.get(0).value() / MIB;
        } catch (Exception e) {
            log.warn("[MY-GPU] Pod RAM 조회 실패: pod={} {}", pod, e.toString());
            return null;
        }
    }

    Set<String> nodeModels(String node) {
        try {
            return nodeRepository.findByIdWithGpus(node).map(Node::getGpuModels).orElse(List.of()).stream()
                    .map(MyGpuUsageService::normalizeModel).collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("[MY-GPU] 노드 GPU 모델 조회 실패, 모든 GPU를 보임: node={} {}", node, e.toString());
            return Set.of();
        }
    }

    /** config-server의 normalize_gpu_model과 같은 규칙: 제조사·제품군 낱말을 빼고 남은 낱말이 같으면 같은 모델. */
    static String normalizeModel(String name) {
        return Arrays.stream((name == null ? "" : name).toUpperCase(Locale.ROOT).split("[^0-9A-Z]+"))
                .filter(t -> !t.isEmpty() && !t.equals("NVIDIA") && !t.equals("GEFORCE") && !t.equals("GENERATION"))
                .collect(Collectors.joining(" "));
    }

    static Owner ownerOf(Snapshot snap, String containerId, String gpuUuid) {
        if (isBlank(containerId)) return null;
        Owner pod = snap.podOwnerByContainer().get(containerId);
        if (pod != null) return pod;
        // 옛 Docker 컨테이너: 지표에 12자 단축 ID만 있어 같은 GPU에서 보이는 시계열일 때만 같은 컨테이너로 본다.
        return snap.dockerOwnerByContainerGpu().get(shortId(containerId) + "/" + gpuUuid);
    }

    static String shortId(String id) {
        return id.length() > 12 ? id.substring(0, 12) : id;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** 노드별 짧은 캐시. 같은 노드를 동시에 부르면 한 번만 조회하고, 실패는 캐시하지 않는다. */
    Snapshot snapshot(String node) {
        Cached c = cache.get(node);
        if (c != null && System.currentTimeMillis() - c.at() < CACHE_TTL_MS) return c.snapshot();
        synchronized (locks.computeIfAbsent(node, k -> new Object())) {
            c = cache.get(node);
            if (c != null && System.currentTimeMillis() - c.at() < CACHE_TTL_MS) return c.snapshot();
            Snapshot fresh = load(node);
            cache.put(node, new Cached(fresh, System.currentTimeMillis()));
            return fresh;
        }
    }

    private Snapshot load(String node) {
        String sel = hostSelector("Hostname", node);
        CompletableFuture<List<Sample>> utilF = CompletableFuture.supplyAsync(
                () -> prometheusClient.querySeries("DCGM_FI_DEV_GPU_UTIL" + sel), POOL);
        CompletableFuture<List<Sample>> usedF = CompletableFuture.supplyAsync(
                () -> prometheusClient.querySeries("DCGM_FI_DEV_FB_USED" + sel), POOL);
        CompletableFuture<List<Sample>> freeF = CompletableFuture.supplyAsync(
                () -> prometheusClient.querySeries("DCGM_FI_DEV_FB_FREE" + sel), POOL);
        List<Sample> util = utilF.join();
        Map<String, Sample> used = byUuid(usedF.join());
        Map<String, Sample> free = byUuid(freeF.join());

        List<Sample> procMem = List.of();
        Map<String, Double> elapsed = new HashMap<>();
        Map<String, Owner> podOwners = new HashMap<>();
        Map<String, Owner> dockerOwners = new HashMap<>();
        Map<String, Double> ramByContainer = new HashMap<>();
        boolean available = true;
        try {
            // exporter가 이 노드에서 프로세스 목록을 읽고 있는지: 없으면(옛 exporter·미배포) 또는 0이면 '프로세스 없음'이 아니라 '모름'이다.
            boolean up = prometheusClient.querySeries("gpu_process_metrics_up" + hostSelector("hostname", node))
                    .stream().anyMatch(s -> s.value() == 1.0);
            if (!up) {
                log.warn("[MY-GPU] 프로세스 지표 없음(gpu_process_metrics_up): node={}", node);
                return new Snapshot(util, used, free, List.of(), elapsed, podOwners, dockerOwners, ramByContainer, false);
            }
            String uuids = util.stream().map(s -> s.labels().get("UUID")).filter(u -> u != null && u.matches("[A-Za-z0-9-]+"))
                    .collect(Collectors.joining("|"));
            if (!uuids.isEmpty()) {
                String psel = "{gpu_uuid=~\"" + uuids + "\"}";
                CompletableFuture<List<Sample>> memF = CompletableFuture.supplyAsync(
                        () -> prometheusClient.querySeries("gpu_process_memory_used_bytes" + psel), POOL);
                CompletableFuture<List<Sample>> elF = CompletableFuture.supplyAsync(
                        () -> prometheusClient.querySeries("gpu_process_elapsed_seconds" + psel), POOL);
                procMem = memF.join();
                for (Sample s : elF.join()) elapsed.put(procKey(s), s.value());
                resolveOwners(procMem, podOwners, dockerOwners, ramByContainer);
            }
        } catch (Exception e) {
            // 프로세스 정보 없이도 GPU별 사용률은 보여 준다. 대신 '모름'임을 응답에 남긴다.
            log.warn("[MY-GPU] 프로세스 지표 조회 실패: node={} {}", node, e.toString());
            procMem = List.of();
            available = false;
        }
        return new Snapshot(util, used, free, procMem, elapsed, podOwners, dockerOwners, ramByContainer, available);
    }

    /**
     * DCGM Hostname 라벨은 노드 이름(예: farm6)이다. 대소문자·FQDN 차이는 허용한다.
     * 노드 이름에 쓸 수 없는 문자가 있으면 정확히 같은 값만 찾는다.
     */
    static String hostSelector(String label, String node) {
        if (node.matches("[A-Za-z0-9.-]+")) {
            return "{" + label + "=~\"(?i)" + node.replace(".", "[.]") + "([.].*)?\"}";
        }
        return "{" + label + "=\"" + node.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    /**
     * Pod는 kube_pod_container_info의 container_id로 Pod 이름을 찾아 신청의 사용자로,
     * 옛 Docker 컨테이너는 docker_gpu_user_container_info의 ubuntu_username으로 찾는다.
     */
    private void resolveOwners(List<Sample> procMem, Map<String, Owner> podOwners, Map<String, Owner> dockerOwners,
                               Map<String, Double> ramByContainer) {
        Set<String> ids = procMem.stream().map(s -> s.labels().get("container_id"))
                .filter(id -> id != null && id.matches("[0-9a-f]{64}")).collect(Collectors.toSet());
        if (ids.isEmpty()) return;

        Map<String, String> podByContainer = new HashMap<>();
        for (Sample s : prometheusClient.querySeries(
                "kube_pod_container_info{container_id=~\".+://(" + String.join("|", ids) + ")\"}")) {
            String cid = s.labels().get("container_id");
            String pod = s.labels().get("pod");
            if (cid != null && pod != null) podByContainer.put(cid.substring(cid.lastIndexOf('/') + 1), pod);
        }
        if (!podByContainer.isEmpty()) {
            fillPodRam(podByContainer, ramByContainer);
            Map<String, Owner> ownerByPod = new HashMap<>();
            for (Request r : requestRepository.findAllByPodNameIn(new HashSet<>(podByContainer.values()))) {
                ownerByPod.put(r.getPodName(), new Owner(ownerKey(r), r.getUser().getUserId(), r.getUser().getName(),
                        r.getUser().getUbuntuUsername(), r.getRequestId()));
            }
            podByContainer.forEach((cid, pod) -> {
                if (ownerByPod.containsKey(pod)) podOwners.put(cid, ownerByPod.get(pod));
            });
        }

        Set<String> unresolved = ids.stream().filter(id -> !podOwners.containsKey(id)).collect(Collectors.toSet());
        if (unresolved.isEmpty()) return;
        for (Sample s : prometheusClient.querySeries("docker_gpu_user_container_info{container_id=~\""
                + unresolved.stream().map(MyGpuUsageService::shortId).collect(Collectors.joining("|")) + "\"}")) {
            String cid = s.labels().get("container_id");
            String uuid = s.labels().get("gpu_uuid");
            String user = s.labels().get("ubuntu_username");
            if (cid != null && uuid != null && !isBlank(user)) {
                dockerOwners.put(cid + "/" + uuid, new Owner(user, null, s.labels().get("real_name"), user, null));
            }
        }
    }

    /** Pod 전체 RAM(cAdvisor working set 합계)을 컨테이너 ID에 붙인다. 실패해도 소유자 해석은 계속한다. */
    private void fillPodRam(Map<String, String> podByContainer, Map<String, Double> ramByContainer) {
        Set<String> pods = podByContainer.values().stream().filter(p -> POD_NAME.matcher(p).matches()).collect(Collectors.toSet());
        if (pods.isEmpty()) return;
        try {
            Map<String, Double> ramByPod = new HashMap<>();
            for (Sample s : prometheusClient.querySeries("sum by (pod) (container_memory_working_set_bytes{pod=~\""
                    + String.join("|", pods) + "\",container!=\"\",container!=\"POD\"})")) {
                if (s.labels().get("pod") != null) ramByPod.put(s.labels().get("pod"), s.value());
            }
            podByContainer.forEach((cid, pod) -> {
                if (ramByPod.containsKey(pod)) ramByContainer.put(cid, ramByPod.get(pod));
            });
        } catch (Exception e) {
            log.warn("[MY-GPU] Pod RAM 조회 실패: {}", e.toString());
        }
    }

    /** 같은 사용자는 Pod든 옛 Docker 컨테이너든 같은 키가 되도록 우분투 계정을 쓴다. */
    static String ownerKey(Request r) {
        String name = r.getUser().getUbuntuUsername();
        return name != null && !name.isBlank() ? name : "id:" + r.getUser().getUserId();
    }

    static Map<String, Sample> byUuid(List<Sample> samples) {
        Map<String, Sample> m = new HashMap<>();
        for (Sample s : samples) {
            String uuid = s.labels().get("UUID");
            if (uuid != null) m.put(uuid, s);
        }
        return m;
    }

    static String procKey(Sample s) {
        return s.labels().get("gpu_uuid") + "/" + s.labels().get("container_id") + "/" + s.labels().get("pid");
    }

    static String baseName(String path) {
        if (path == null || path.isBlank()) return "";
        return path.substring(path.lastIndexOf('/') + 1);
    }

    static int parseIndex(String gpu) {
        try {
            return Integer.parseInt(gpu);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
