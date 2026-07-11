package DGU_AI_LAB.admin_be.domain.dashboard.service;

import DGU_AI_LAB.admin_be.domain.nodes.repository.NodeRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.UserServerResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.entity.StatusFilter;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
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

            List<UserServerResponseDTO> result = dashboardService.getUserServers(1L, StatusFilter.ALL);

            assertThat(result).isEmpty();
            verify(requestRepository).findAllByUser_UserId(1L);
            verify(requestRepository, never()).findByUserUserIdAndStatus(anyLong(), any());
        }

        @Test
        @DisplayName("StatusFilter.FULFILLED이면 FULFILLED 요청만 반환한다")
        void getUserServers_returnsFulfilledRequests_whenStatusFulfilled() {
            when(requestRepository.findByUserUserIdAndStatus(1L, Status.FULFILLED)).thenReturn(List.of());

            List<UserServerResponseDTO> result = dashboardService.getUserServers(1L, StatusFilter.FULFILLED);

            assertThat(result).isEmpty();
            verify(requestRepository).findByUserUserIdAndStatus(1L, Status.FULFILLED);
        }

        @Test
        @DisplayName("StatusFilter.PENDING이면 PENDING 요청만 반환한다")
        void getUserServers_returnsPendingRequests_whenStatusPending() {
            when(requestRepository.findByUserUserIdAndStatus(1L, Status.PENDING)).thenReturn(List.of());

            List<UserServerResponseDTO> result = dashboardService.getUserServers(1L, StatusFilter.PENDING);

            assertThat(result).isEmpty();
            verify(requestRepository).findByUserUserIdAndStatus(1L, Status.PENDING);
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

            assertThat(names).containsExactlyInAnyOrder("PENDING", "DENIED", "FULFILLED", "DELETED");
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
