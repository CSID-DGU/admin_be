package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.portRequests.service.PortRequestService;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ContainerInfoDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ResourceUsageDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.ChangeRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestQueryServiceTest {

    @InjectMocks
    private RequestQueryService requestQueryService;

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private ChangeRequestRepository changeRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PortRequestService portRequestService;

    @Nested
    @DisplayName("getRequestsByUserId")
    class GetRequestsByUserId {

        @Test
        @DisplayName("존재하는 userId로 조회하면 요청 목록을 반환한다")
        void getRequestsByUserId_returnsList() {
            when(userRepository.existsById(1L)).thenReturn(true);
            when(requestRepository.findAllByUser_UserId(1L)).thenReturn(List.of());

            List<SaveRequestResponseDTO> result = requestQueryService.getRequestsByUserId(1L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 userId로 조회하면 BusinessException을 던진다")
        void getRequestsByUserId_throwsException_whenUserNotFound() {
            when(userRepository.existsById(99L)).thenReturn(false);

            assertThatThrownBy(() -> requestQueryService.getRequestsByUserId(99L))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("getApprovedRequestsByUserId")
    class GetApprovedRequestsByUserId {

        @Test
        @DisplayName("존재하는 userId로 조회하면 FULFILLED 상태 요청만 반환한다")
        void getApprovedRequestsByUserId_returnsFulfilledList() {
            when(userRepository.existsById(1L)).thenReturn(true);
            when(requestRepository.findAllByUser_UserIdAndStatus(1L, Status.FULFILLED)).thenReturn(List.of());

            List<SaveRequestResponseDTO> result = requestQueryService.getApprovedRequestsByUserId(1L);

            assertThat(result).isEmpty();
            verify(requestRepository).findAllByUser_UserIdAndStatus(1L, Status.FULFILLED);
        }

        @Test
        @DisplayName("존재하지 않는 userId로 조회하면 BusinessException을 던진다")
        void getApprovedRequestsByUserId_throwsException_whenUserNotFound() {
            when(userRepository.existsById(99L)).thenReturn(false);

            assertThatThrownBy(() -> requestQueryService.getApprovedRequestsByUserId(99L))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("getAllFulfilledResourceUsage")
    class GetAllFulfilledResourceUsage {

        @Test
        @DisplayName("FULFILLED 상태 요청들의 리소스 사용량을 반환한다")
        void getAllFulfilledResourceUsage_returnsList() {
            when(requestRepository.findAllByStatus(Status.FULFILLED)).thenReturn(List.of());

            List<ResourceUsageDTO> result = requestQueryService.getAllFulfilledResourceUsage();

            assertThat(result).isEmpty();
            verify(requestRepository).findAllByStatus(Status.FULFILLED);
        }
    }

    @Nested
    @DisplayName("getActiveContainers")
    class GetActiveContainers {

        @Test
        @DisplayName("FULFILLED 상태 요청들의 컨테이너 정보를 반환한다")
        void getActiveContainers_returnsList() {
            when(requestRepository.findAllByStatus(Status.FULFILLED)).thenReturn(List.of());

            List<ContainerInfoDTO> result = requestQueryService.getActiveContainers();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getAllFulfilledUsernames")
    class GetAllFulfilledUsernames {

        @Test
        @DisplayName("FULFILLED 상태 요청들의 ubuntu username 목록을 반환한다")
        void getAllFulfilledUsernames_returnsList() {
            when(requestRepository.findUbuntuUsernamesByStatus(Status.FULFILLED))
                    .thenReturn(List.of("user1", "user2"));

            List<String> result = requestQueryService.getAllFulfilledUsernames();

            assertThat(result).containsExactlyInAnyOrder("user1", "user2");
        }
    }

    @Nested
    @DisplayName("getMyChangeRequests")
    class GetMyChangeRequests {

        @Test
        @DisplayName("존재하는 userId로 내 변경 요청 목록을 조회한다")
        void getMyChangeRequests_returnsList() {
            when(userRepository.existsById(1L)).thenReturn(true);
            when(changeRequestRepository.findAllByRequestedBy_UserId(1L)).thenReturn(List.of());

            var result = requestQueryService.getMyChangeRequests(1L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 userId로 조회하면 BusinessException을 던진다")
        void getMyChangeRequests_throwsException_whenUserNotFound() {
            when(userRepository.existsById(99L)).thenReturn(false);

            assertThatThrownBy(() -> requestQueryService.getMyChangeRequests(99L))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
