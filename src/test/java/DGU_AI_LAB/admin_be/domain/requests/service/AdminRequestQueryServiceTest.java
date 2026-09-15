package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.requests.dto.response.ChangeRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ContainerInfoDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ResourceUsageDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
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

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private RequestQueryService requestQueryService;

    @Nested
    @DisplayName("getRequestCreatedAt")
    class GetRequestCreatedAt {

        @Test
        @DisplayName("신청 생성 시각을 돌려준다")
        void returnsCreatedAt() {
            Request request = mock(Request.class);
            LocalDateTime createdAt = LocalDateTime.of(2026, 9, 15, 10, 20, 41);
            when(request.getCreatedAt()).thenReturn(createdAt);
            when(requestRepository.findById(2L)).thenReturn(Optional.of(request));

            assertThat(adminRequestQueryService.getRequestCreatedAt(2L)).isEqualTo(createdAt);
        }

        @Test
        @DisplayName("없는 신청이면 리소스 없음으로 실패한다")
        void failsWhenMissing() {
            when(requestRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminRequestQueryService.getRequestCreatedAt(99L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getAllRequests")
    class GetAllRequests {

        @Test
        @DisplayName("모든 요청 목록을 반환한다")
        void getAllRequests_returnsList() {
            when(requestRepository.findAllWithAssociations()).thenReturn(List.of());

            List<SaveRequestResponseDTO> result = adminRequestQueryService.getAllRequests();

            assertThat(result).isEmpty();
            verify(requestRepository).findAllWithAssociations();
        }

        @Test
        @DisplayName("요청 목록을 RequestQueryService에 위임하여 반환한다")
        void getAllRequests_delegatesToRequestQueryService() {
            Request request = mock(Request.class);
            SaveRequestResponseDTO dto = mock(SaveRequestResponseDTO.class);

            when(requestRepository.findAllWithAssociations()).thenReturn(List.of(request));
            when(requestQueryService.toResponseDTOs(List.of(request))).thenReturn(List.of(dto));

            List<SaveRequestResponseDTO> result = adminRequestQueryService.getAllRequests();

            assertThat(result).hasSize(1);
            verify(requestQueryService, times(1)).toResponseDTOs(List.of(request));
        }
    }

    @Nested
    @DisplayName("getAllFulfilledResourceUsage")
    class GetAllFulfilledResourceUsage {

        @Test
        @DisplayName("FULFILLED 상태 요청들의 리소스 사용량을 반환한다")
        void getAllFulfilledResourceUsage_returnsList() {
            when(requestRepository.findAllByStatusInWithAssociations(Status.activeStatuses())).thenReturn(List.of());

            List<ResourceUsageDTO> result = adminRequestQueryService.getAllFulfilledResourceUsage();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("리소스 그룹이 없는 신청도 NPE 없이 매핑된다")
        void getAllFulfilledResourceUsage_toleratesNullResourceGroup() {
            Request request = mock(Request.class);
            var user = mock(DGU_AI_LAB.admin_be.domain.users.entity.User.class);

            when(request.getUser()).thenReturn(user);
            when(user.getUserId()).thenReturn(7L);
            when(user.getName()).thenReturn("테스트유저");
            when(request.getResourceGroup()).thenReturn(null);
            when(requestRepository.findAllByStatusInWithAssociations(Status.activeStatuses())).thenReturn(List.of(request));

            List<ResourceUsageDTO> result = adminRequestQueryService.getAllFulfilledResourceUsage();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).resourceGroupId()).isNull();
        }
    }

    @Nested
    @DisplayName("getAllActiveContainers")
    class GetAllActiveContainers {

        @Test
        @DisplayName("FULFILLED 상태 요청들의 컨테이너 정보를 반환한다")
        void getAllActiveContainers_returnsList() {
            when(requestRepository.findAllByStatusInWithAssociations(Status.activeStatuses())).thenReturn(List.of());

            List<ContainerInfoDTO> result = adminRequestQueryService.getAllActiveContainers();

            assertThat(result).isEmpty();
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

    private Request mockRequest(Long requestId) {
        Request request = mock(Request.class);
        var resourceGroup = mock(DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup.class);
        var user = mock(DGU_AI_LAB.admin_be.domain.users.entity.User.class);
        var containerImage = mock(DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage.class);

        when(request.getRequestId()).thenReturn(requestId);
        when(request.getResourceGroup()).thenReturn(resourceGroup);
        when(resourceGroup.getRsgroupId()).thenReturn(1);
        when(resourceGroup.getResourceGroupName()).thenReturn("RTX 4090 Cluster");
        when(resourceGroup.getDescription()).thenReturn("GPU cluster");
        when(resourceGroup.getServerName()).thenReturn("LAB");
        when(request.getUser()).thenReturn(user);
        when(user.getUserId()).thenReturn(1L);
        when(user.getEmail()).thenReturn("user@example.com");
        when(user.getName()).thenReturn("테스트유저");
        when(user.getStudentId()).thenReturn("2023000001");
        when(user.getDepartment()).thenReturn("컴퓨터공학과");
        when(user.getPhone()).thenReturn("010-0000-0000");
        when(user.getIsActive()).thenReturn(true);
        when(request.getContainerImage()).thenReturn(containerImage);
        when(containerImage.getImageId()).thenReturn(1L);
        when(containerImage.getImageName()).thenReturn("cuda");
        when(containerImage.getImageVersion()).thenReturn("11.8");
        when(request.getRequestGroups()).thenReturn(java.util.Set.of());
        when(request.getUsagePurpose()).thenReturn("학습");
        when(request.getFormAnswers()).thenReturn("{}");
        when(request.getStatus()).thenReturn(Status.FULFILLED);
        return request;
    }
}
