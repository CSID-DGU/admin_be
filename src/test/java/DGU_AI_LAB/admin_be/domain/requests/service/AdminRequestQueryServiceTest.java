package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.portRequests.service.PortRequestService;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ChangeRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ContainerInfoDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ResourceUsageDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.ChangeRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
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
class AdminRequestQueryServiceTest {

    @InjectMocks
    private AdminRequestQueryService adminRequestQueryService;

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private ChangeRequestRepository changeRequestRepository;

    @Mock
    private PortRequestService portRequestService;

    @Nested
    @DisplayName("getAllRequests")
    class GetAllRequests {

        @Test
        @DisplayName("모든 요청 목록을 반환한다")
        void getAllRequests_returnsList() {
            when(requestRepository.findAll()).thenReturn(List.of());

            List<SaveRequestResponseDTO> result = adminRequestQueryService.getAllRequests();

            assertThat(result).isEmpty();
            verify(requestRepository).findAll();
        }
    }

    @Nested
    @DisplayName("getNewRequests")
    class GetNewRequests {

        @Test
        @DisplayName("PENDING 상태 요청 목록을 반환한다")
        void getNewRequests_returnsPendingList() {
            when(requestRepository.findAllByStatus(Status.PENDING)).thenReturn(List.of());

            List<SaveRequestResponseDTO> result = adminRequestQueryService.getNewRequests();

            assertThat(result).isEmpty();
            verify(requestRepository).findAllByStatus(Status.PENDING);
        }
    }

    @Nested
    @DisplayName("getAllFulfilledResourceUsage")
    class GetAllFulfilledResourceUsage {

        @Test
        @DisplayName("FULFILLED 상태 요청들의 리소스 사용량을 반환한다")
        void getAllFulfilledResourceUsage_returnsList() {
            when(requestRepository.findAllByStatus(Status.FULFILLED)).thenReturn(List.of());

            List<ResourceUsageDTO> result = adminRequestQueryService.getAllFulfilledResourceUsage();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getAllActiveContainers")
    class GetAllActiveContainers {

        @Test
        @DisplayName("FULFILLED 상태 요청들의 컨테이너 정보를 반환한다")
        void getAllActiveContainers_returnsList() {
            when(requestRepository.findAllByStatus(Status.FULFILLED)).thenReturn(List.of());

            List<ContainerInfoDTO> result = adminRequestQueryService.getAllActiveContainers();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getChangeRequests")
    class GetChangeRequests {

        @Test
        @DisplayName("PENDING 상태 변경 요청 목록을 반환한다")
        void getChangeRequests_returnsPendingChangeRequests() {
            when(changeRequestRepository.findAllByStatus(Status.PENDING)).thenReturn(List.of());

            List<ChangeRequestResponseDTO> result = adminRequestQueryService.getChangeRequests();

            assertThat(result).isEmpty();
            verify(changeRequestRepository).findAllByStatus(Status.PENDING);
        }
    }

    @Nested
    @DisplayName("getAllChangeRequests")
    class GetAllChangeRequests {

        @Test
        @DisplayName("모든 변경 요청 목록을 반환한다")
        void getAllChangeRequests_returnsList() {
            when(changeRequestRepository.findAll()).thenReturn(List.of());

            List<ChangeRequestResponseDTO> result = adminRequestQueryService.getAllChangeRequests();

            assertThat(result).isEmpty();
        }
    }
}
