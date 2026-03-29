package DGU_AI_LAB.admin_be.domain.dashboard.service;

import DGU_AI_LAB.admin_be.domain.nodes.repository.NodeRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.UserServerResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

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
        @DisplayName("Status.ALL이면 userId의 모든 요청을 반환한다")
        void getUserServers_returnsAllRequests_whenStatusAll() {
            when(requestRepository.findAllByUser_UserId(1L)).thenReturn(List.of());

            List<UserServerResponseDTO> result = dashboardService.getUserServers(1L, Status.ALL);

            assertThat(result).isEmpty();
            verify(requestRepository).findAllByUser_UserId(1L);
            verify(requestRepository, never()).findByUserUserIdAndStatus(anyLong(), any());
        }

        @Test
        @DisplayName("Status.FULFILLED이면 FULFILLED 요청만 반환한다")
        void getUserServers_returnsFulfilledRequests_whenStatusFulfilled() {
            when(requestRepository.findByUserUserIdAndStatus(1L, Status.FULFILLED)).thenReturn(List.of());

            List<UserServerResponseDTO> result = dashboardService.getUserServers(1L, Status.FULFILLED);

            assertThat(result).isEmpty();
            verify(requestRepository).findByUserUserIdAndStatus(1L, Status.FULFILLED);
        }

        @Test
        @DisplayName("Status.PENDING이면 PENDING 요청만 반환한다")
        void getUserServers_returnsPendingRequests_whenStatusPending() {
            when(requestRepository.findByUserUserIdAndStatus(1L, Status.PENDING)).thenReturn(List.of());

            List<UserServerResponseDTO> result = dashboardService.getUserServers(1L, Status.PENDING);

            assertThat(result).isEmpty();
            verify(requestRepository).findByUserUserIdAndStatus(1L, Status.PENDING);
        }
    }
}
