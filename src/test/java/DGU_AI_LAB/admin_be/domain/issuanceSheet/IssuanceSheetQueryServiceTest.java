package DGU_AI_LAB.admin_be.domain.issuanceSheet;

import DGU_AI_LAB.admin_be.domain.home.service.HomeRetentionPolicy;
import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import DGU_AI_LAB.admin_be.domain.portRequests.repository.PortRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.global.server.ServerProfileProperties;
import DGU_AI_LAB.admin_be.global.server.ServerProfileProperties.Server;
import org.assertj.core.data.Index;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    private final PortRequestRepository portRequestRepository = mock(PortRequestRepository.class);

    private IssuanceSheetQueryService service() {
        Map<String, Server> servers = new LinkedHashMap<>();
        servers.put("LAB", new Server("lab.example.test", "lab-admin", null, null));
        servers.put("FARM", new Server("farm.example.test", "farm-admin", null, null));
        return new IssuanceSheetQueryService(requestRepository, portRequestRepository,
                new ServerProfileProperties(servers), new HomeRetentionPolicy());
    }

    private void activeRequests(Request... requests) {
        when(requestRepository.findAllByStatusInWithAssociations(
                List.of(Status.FULFILLED, Status.MIGRATING, Status.EXPIRING))).thenReturn(List.of(requests));
    }

    private static PortRequests port(Request request, int internalPort, boolean active) {
        PortRequests port = mock(PortRequests.class);
        when(port.getRequest()).thenReturn(request);
        when(port.getInternalPort()).thenReturn(internalPort);
        when(port.getIsActive()).thenReturn(active);
        return port;
    }

    @Test
    @DisplayName("컨테이너가 살아 있는 신청만 읽어 서버마다 표를 나누고, 닫힌 포트는 뺀다")
    void splitsActiveRequestsByServer() {
        Request issued = request(1, Status.FULFILLED, "가", "farm1");
        activeRequests(issued);
        List<PortRequests> ports = List.of(port(issued, 8888, true), port(issued, 9999, false));
        when(portRequestRepository.findByRequestRequestIdIn(any())).thenReturn(ports);

        Map<String, List<List<String>>> tables = service().rowsByServer();

        assertThat(tables.keySet()).containsExactly("LAB", "FARM");
        assertThat(tables.get("LAB")).containsExactly(IssuanceSheetRows.HEADER);
        assertThat(tables.get("FARM")).hasSize(2);
        assertThat(tables.get("FARM").get(1)).contains("8888", Index.atIndex(7));
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
        when(portRequestRepository.findByRequestRequestIdIn(any())).thenReturn(List.of());

        Map<String, List<List<String>>> tables = service().rowsByServer();

        assertThat(tables.get("FARM").get(1)).contains("2026-12-31", Index.atIndex(STORAGE_DELETION_COLUMN));
        assertThat(tables.get("LAB").get(1)).contains("2026-12-31", Index.atIndex(STORAGE_DELETION_COLUMN));
        assertThat(tables.get("FARM").get(2)).contains("2026-11-14", Index.atIndex(STORAGE_DELETION_COLUMN));
    }
}
