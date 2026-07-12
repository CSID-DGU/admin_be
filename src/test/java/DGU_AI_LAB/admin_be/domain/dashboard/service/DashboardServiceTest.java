package DGU_AI_LAB.admin_be.domain.dashboard.service;

import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import DGU_AI_LAB.admin_be.domain.nodes.repository.NodeRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.UserServerResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.entity.StatusFilter;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @InjectMocks
    private DashboardService dashboardService;

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private NodeRepository nodeRepository;

    @Nested
    @DisplayName("getUserServers")
    class GetUserServers {

        @Test
        @DisplayName("StatusFilter.ALL이면 userId의 모든 요청을 반환한다")
        void getUserServers_returnsAllRequests_whenStatusAll() {
            when(requestRepository.findAllByUser_UserId(1L)).thenReturn(List.of());
            when(nodeRepository.findAllByResourceGroupIn(anySet())).thenReturn(List.of());

            List<UserServerResponseDTO> result = dashboardService.getUserServers(1L, StatusFilter.ALL);

            assertThat(result).isEmpty();
            verify(requestRepository).findAllByUser_UserId(1L);
            verify(requestRepository, never()).findByUserUserIdAndStatus(anyLong(), any());
        }

        @Test
        @DisplayName("StatusFilter.FULFILLED이면 FULFILLED 요청만 반환한다")
        void getUserServers_returnsFulfilledRequests_whenStatusFulfilled() {
            when(requestRepository.findByUserUserIdAndStatus(1L, Status.FULFILLED)).thenReturn(List.of());
            when(nodeRepository.findAllByResourceGroupIn(anySet())).thenReturn(List.of());

            List<UserServerResponseDTO> result = dashboardService.getUserServers(1L, StatusFilter.FULFILLED);

            assertThat(result).isEmpty();
            verify(requestRepository).findByUserUserIdAndStatus(1L, Status.FULFILLED);
        }

        @Test
        @DisplayName("StatusFilter.PENDING이면 PENDING 요청만 반환한다")
        void getUserServers_returnsPendingRequests_whenStatusPending() {
            when(requestRepository.findByUserUserIdAndStatus(1L, Status.PENDING)).thenReturn(List.of());
            when(nodeRepository.findAllByResourceGroupIn(anySet())).thenReturn(List.of());

            List<UserServerResponseDTO> result = dashboardService.getUserServers(1L, StatusFilter.PENDING);

            assertThat(result).isEmpty();
            verify(requestRepository).findByUserUserIdAndStatus(1L, Status.PENDING);
        }

        @Test
        @DisplayName("요청이 여러 개여도 노드 조회는 findAllByResourceGroupIn 한 번만 호출한다 (N+1 방지)")
        void getUserServers_callsBatchNodeQuery_onceForAllResourceGroups() {
            ResourceGroup rg = new ResourceGroup(1, "rg-name", "desc", "server1");
            Request req1 = Request.builder().resourceGroup(rg).ubuntuUsername("user1").build();
            Request req2 = Request.builder().resourceGroup(rg).ubuntuUsername("user2").build();
            Node node = Node.builder().nodeId("node-1").resourceGroup(rg).cpuCoreCount(8).memorySizeGB(32).build();

            when(requestRepository.findAllByUser_UserId(1L)).thenReturn(List.of(req1, req2));
            when(nodeRepository.findAllByResourceGroupIn(anySet())).thenReturn(List.of(node));

            List<UserServerResponseDTO> result = dashboardService.getUserServers(1L, StatusFilter.ALL);

            assertThat(result).hasSize(2);
            verify(nodeRepository, times(1)).findAllByResourceGroupIn(anySet());
            verify(nodeRepository, never()).findAllByResourceGroup(any());
        }

        @Test
        @DisplayName("노드 정보를 DTO에 올바르게 매핑한다")
        void getUserServers_mapsNodeInfoToDTO() {
            ResourceGroup rg = new ResourceGroup(1, "rg-name", "desc", "server1");
            Request req = Request.builder().resourceGroup(rg).ubuntuUsername("user1").build();
            Node node = Node.builder().nodeId("node-1").resourceGroup(rg).cpuCoreCount(16).memorySizeGB(64).build();

            when(requestRepository.findAllByUser_UserId(1L)).thenReturn(List.of(req));
            when(nodeRepository.findAllByResourceGroupIn(anySet())).thenReturn(List.of(node));

            List<UserServerResponseDTO> result = dashboardService.getUserServers(1L, StatusFilter.ALL);

            assertThat(result).hasSize(1);
            UserServerResponseDTO dto = result.get(0);
            assertThat(dto.cpuCoreCount()).isEqualTo(16);
            assertThat(dto.memoryGB()).isEqualTo(64);
            assertThat(dto.resourceGroupName()).isEqualTo("desc");
        }

        @Test
        @DisplayName("ResourceGroup이 없는 요청도 정상 처리된다")
        void getUserServers_handlesNullResourceGroup() {
            Request req = Request.builder().ubuntuUsername("user1").resourceGroup(null).build();

            when(requestRepository.findAllByUser_UserId(1L)).thenReturn(List.of(req));
            when(nodeRepository.findAllByResourceGroupIn(anySet())).thenReturn(List.of());

            List<UserServerResponseDTO> result = dashboardService.getUserServers(1L, StatusFilter.ALL);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).cpuCoreCount()).isNull();
            assertThat(result.get(0).memoryGB()).isNull();
        }
    }

    @Nested
    @DisplayName("Status enum")
    class StatusEnumTest {

        @Test
        @DisplayName("Status enum은 DB 저장 가능한 값만 포함하고 ALL을 포함하지 않는다")
        void status_containsOnlyValidDbValues() {
            Set<String> names = Arrays.stream(Status.values())
                    .map(Enum::name)
                    .collect(Collectors.toSet());

            assertThat(names).containsExactlyInAnyOrder("PENDING", "PROCESSING", "DENIED", "FULFILLED", "DELETED");
            assertThat(names).doesNotContain("ALL");
        }

        @Test
        @DisplayName("StatusFilter enum은 ALL을 포함한다")
        void statusFilter_containsAll() {
            Set<String> names = Arrays.stream(StatusFilter.values())
                    .map(Enum::name)
                    .collect(Collectors.toSet());

            assertThat(names).contains("ALL");
            assertThat(names).containsExactlyInAnyOrder("PENDING", "DENIED", "FULFILLED", "DELETED", "ALL");
        }
    }
}
