package DGU_AI_LAB.admin_be.domain.issuanceSheet;

import DGU_AI_LAB.admin_be.domain.home.service.HomeRetentionPolicy;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.global.server.ServerProfileProperties;
import DGU_AI_LAB.admin_be.global.server.ServerProfileProperties.PortForwarding;
import DGU_AI_LAB.admin_be.global.server.ServerProfileProperties.Server;
import DGU_AI_LAB.admin_be.global.server.ServerProfileRegistry;
import org.assertj.core.data.Index;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static DGU_AI_LAB.admin_be.domain.issuanceSheet.IssuanceSheetRowsTest.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("IssuanceSheetQueryService")
class IssuanceSheetQueryServiceTest {

    private static final int STORAGE_DELETION_COLUMN = 9;

    private final RequestRepository requestRepository = mock(RequestRepository.class);
    private final PodExternalPortRepository podExternalPortRepository = mock(PodExternalPortRepository.class);

    private IssuanceSheetQueryService service() {
        Map<String, Server> servers = new LinkedHashMap<>();
        servers.put("LAB", new Server("lab.example.test", "lab-admin", null, null));
        servers.put("FARM", new Server("farm.example.test", "farm-admin", null, new PortForwarding(30000, 9300, 98)));
        ServerProfileProperties properties = new ServerProfileProperties(servers);
        return new IssuanceSheetQueryService(requestRepository, podExternalPortRepository, properties,
                new ServerProfileRegistry(properties, new MockEnvironment()
                        .withProperty("slack-webhook-url.lab-admin", "https://hooks.example.test/lab")
                        .withProperty("slack-webhook-url.farm-admin", "https://hooks.example.test/farm")),
                new HomeRetentionPolicy());
    }

    private void activeRequests(Request... requests) {
        when(requestRepository.findAllByStatusInWithAssociations(
                List.of(Status.FULFILLED, Status.MIGRATING, Status.EXPIRING))).thenReturn(List.of(requests));
    }

    private static PodExternalPort port(Request request, String purpose, int internalPort, int externalPort) {
        PodExternalPort port = mock(PodExternalPort.class);
        when(port.getRequest()).thenReturn(request);
        when(port.getUsagePurpose()).thenReturn(purpose);
        when(port.getInternalPort()).thenReturn(internalPort);
        when(port.getExternalPort()).thenReturn(externalPort);
        return port;
    }

    @Test
    @DisplayName("컨테이너가 살아 있는 신청만 읽어 서버마다 표를 나누고, 열린 포트를 안내 메일과 같은 번호로 적는다")
    void splitsActiveRequestsByServer() {
        Request issued = request(1, Status.FULFILLED, "가", "farm1");
        activeRequests(issued);
        List<PodExternalPort> ports = List.of(port(issued, "웹 서버", 3000, 30028),
                port(issued, "jupyter", 8888, 30007), port(issued, "ssh", 22, 30006));
        when(podExternalPortRepository.findByRequestRequestIdIn(any())).thenReturn(ports);

        Map<String, List<List<String>>> tables = service().rowsByServer();

        assertThat(tables.keySet()).containsExactly("LAB", "FARM");
        assertThat(tables.get("LAB")).containsExactly(IssuanceSheetRows.HEADER);
        assertThat(tables.get("FARM")).hasSize(2);
        assertThat(tables.get("FARM").get(1)).contains("ssh(9306), jupyter(9307), 웹 서버(9328)", Index.atIndex(7));
    }

    @Test
    @DisplayName("스토리지 삭제 예정일은 그 사람의 가장 늦게 끝나는 컨테이너(다른 서버 포함)부터 30일 뒤다")
    void storageDeletionCountsFromUsersLastContainerAcrossServers() {
        Request farm = request(1, 100, "FARM", Status.FULFILLED, "가", "farm1");
        when(farm.getExpiresAt()).thenReturn(LocalDateTime.of(2026, 11, 1, 23, 59));
        Request lab = request(2, 100, "LAB", Status.FULFILLED, "가", "lab3");
        when(lab.getExpiresAt()).thenReturn(LocalDateTime.of(2026, 12, 1, 23, 59));
        Request other = request(3, 200, "FARM", Status.FULFILLED, "나", "farm1");
        when(other.getExpiresAt()).thenReturn(LocalDateTime.of(2026, 10, 15, 23, 59));
        activeRequests(farm, lab, other);
        when(podExternalPortRepository.findByRequestRequestIdIn(any())).thenReturn(List.of());

        Map<String, List<List<String>>> tables = service().rowsByServer();

        assertThat(tables.get("FARM").get(1)).contains("2026-12-31", Index.atIndex(STORAGE_DELETION_COLUMN));
        assertThat(tables.get("LAB").get(1)).contains("2026-12-31", Index.atIndex(STORAGE_DELETION_COLUMN));
        assertThat(tables.get("FARM").get(2)).contains("2026-11-14", Index.atIndex(STORAGE_DELETION_COLUMN));
    }
}
