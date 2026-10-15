package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.pod.dto.response.PodResponseDTO;
import DGU_AI_LAB.admin_be.domain.pod.service.PodStatusCache;
import DGU_AI_LAB.admin_be.domain.pod.service.PodStatusSnapshot;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
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

    @Mock
    private PodStatusCache podStatusCache;

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
    @DisplayName("getRequests")
    class GetRequests {

        @Test
        @DisplayName("상태와 시작 시각을 주지 않으면 모든 상태·전체 기간을 읽는다")
        void withoutFilters_readsEveryStatusFromEarliest() {
            when(requestRepository.findAllForAdminList(any(), any())).thenReturn(List.of());

            List<SaveRequestResponseDTO> result = adminRequestQueryService.getRequests(null, null);

            assertThat(result).isEmpty();
            verify(requestRepository).findAllForAdminList(
                    EnumSet.allOf(Status.class), LocalDateTime.of(1970, 1, 1, 0, 0));
        }

        @Test
        @DisplayName("빈 상태 목록도 모든 상태로 본다")
        void emptyStatuses_readsEveryStatus() {
            when(requestRepository.findAllForAdminList(any(), any())).thenReturn(List.of());

            adminRequestQueryService.getRequests(List.of(), null);

            verify(requestRepository).findAllForAdminList(eq(EnumSet.allOf(Status.class)), any());
        }

        @Test
        @DisplayName("준 상태와 시작 시각으로 좁혀 읽고 응답 변환을 맡긴다")
        void withFilters_narrowsAndDelegates() {
            Request request = mock(Request.class);
            SaveRequestResponseDTO dto = mock(SaveRequestResponseDTO.class);
            LocalDateTime from = LocalDateTime.of(2026, 7, 13, 0, 0);
            when(requestRepository.findAllForAdminList(List.of(Status.PENDING), from)).thenReturn(List.of(request));
            when(requestQueryService.toResponseDTOs(List.of(request))).thenReturn(List.of(dto));

            List<SaveRequestResponseDTO> result = adminRequestQueryService.getRequests(List.of(Status.PENDING), from);

            assertThat(result).containsExactly(dto);
        }
    }

    @Nested
    @DisplayName("getRequest")
    class GetRequest {

        @Test
        @DisplayName("신청 하나를 응답으로 돌려준다")
        void returnsOne() {
            Request request = mock(Request.class);
            SaveRequestResponseDTO dto = mock(SaveRequestResponseDTO.class);
            when(requestRepository.findByIdForAdminView(7L)).thenReturn(Optional.of(request));
            when(requestQueryService.toResponseDTOs(List.of(request))).thenReturn(List.of(dto));

            assertThat(adminRequestQueryService.getRequest(7L)).isSameAs(dto);
        }

        @Test
        @DisplayName("없는 신청이면 리소스 없음으로 실패한다")
        void failsWhenMissing() {
            when(requestRepository.findByIdForAdminView(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminRequestQueryService.getRequest(99L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getRequestStatusCounts")
    class GetRequestStatusCounts {

        @Test
        @DisplayName("건수가 없는 상태도 0으로 채워 돌려준다")
        void fillsMissingStatusesWithZero() {
            RequestRepository.StatusCount pending = mock(RequestRepository.StatusCount.class);
            when(pending.getStatus()).thenReturn(Status.PENDING);
            when(pending.getCount()).thenReturn(3L);
            when(requestRepository.countByStatusCreatedFrom(any())).thenReturn(List.of(pending));

            Map<Status, Long> counts = adminRequestQueryService.getRequestStatusCounts(null);

            assertThat(counts).hasSize(Status.values().length)
                    .containsEntry(Status.PENDING, 3L)
                    .containsEntry(Status.DELETED, 0L);
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
            when(podStatusCache.snapshot()).thenReturn(PodStatusSnapshot.EMPTY);
            when(requestRepository.findAllByStatusInWithAssociations(Status.activeStatuses())).thenReturn(List.of());

            List<ContainerInfoDTO> result = adminRequestQueryService.getAllActiveContainers();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("리소스 그룹 번호와 함께 GPU 이름과 서버를 담는다")
        void getAllActiveContainers_includesResourceGroupName() {
            Request request = mock(Request.class);
            var user = mock(DGU_AI_LAB.admin_be.domain.users.entity.User.class);
            var resourceGroup = mock(DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup.class);
            var containerImage = mock(DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage.class);
            when(request.getUser()).thenReturn(user);
            when(user.getUserGroups()).thenReturn(java.util.Set.of());
            when(request.getResourceGroup()).thenReturn(resourceGroup);
            when(resourceGroup.getRsgroupId()).thenReturn(3);
            when(resourceGroup.getResourceGroupName()).thenReturn("RTX A6000 Cluster");
            when(resourceGroup.getServerName()).thenReturn("FARM");
            when(request.getContainerImage()).thenReturn(containerImage);
            when(podStatusCache.snapshot()).thenReturn(PodStatusSnapshot.EMPTY);
            when(requestRepository.findAllByStatusInWithAssociations(Status.activeStatuses())).thenReturn(List.of(request));

            ContainerInfoDTO result = adminRequestQueryService.getAllActiveContainers().get(0);

            assertThat(result.resourceGroupId()).isEqualTo(3);
            assertThat(result.resourceGroupName()).isEqualTo("RTX A6000 Cluster");
            assertThat(result.serverName()).isEqualTo("FARM");
        }

        @Test
        @DisplayName("리소스 그룹이 없으면 GPU 이름과 서버도 비운다")
        void getAllActiveContainers_toleratesNullResourceGroup() {
            Request request = mock(Request.class);
            var user = mock(DGU_AI_LAB.admin_be.domain.users.entity.User.class);
            var containerImage = mock(DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage.class);
            when(request.getUser()).thenReturn(user);
            when(user.getUserGroups()).thenReturn(java.util.Set.of());
            when(request.getResourceGroup()).thenReturn(null);
            when(request.getContainerImage()).thenReturn(containerImage);
            when(podStatusCache.snapshot()).thenReturn(PodStatusSnapshot.EMPTY);
            when(requestRepository.findAllByStatusInWithAssociations(Status.activeStatuses())).thenReturn(List.of(request));

            ContainerInfoDTO result = adminRequestQueryService.getAllActiveContainers().get(0);

            assertThat(result.resourceGroupName()).isNull();
            assertThat(result.serverName()).isNull();
        }

        @Test
        @DisplayName("주기 작업이 읽어 둔 Pod 상태와 읽은 시각을 붙이고, k8s는 부르지 않는다")
        void getAllActiveContainers_attachesCachedPodStatus() {
            LocalDateTime checkedAt = LocalDateTime.of(2026, 10, 15, 21, 25);
            PodResponseDTO running = PodResponseDTO.builder().name("ailab-a-1").status("Running").effectiveStatus("Running").build();
            Request withPod = mock(Request.class);
            Request podGone = mock(Request.class);
            for (Request request : List.of(withPod, podGone)) {
                var user = mock(DGU_AI_LAB.admin_be.domain.users.entity.User.class);
                when(request.getUser()).thenReturn(user);
                when(user.getUserGroups()).thenReturn(java.util.Set.of());
                when(request.getContainerImage()).thenReturn(mock(DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage.class));
                when(request.getStatus()).thenReturn(Status.FULFILLED);
            }
            when(withPod.getPodName()).thenReturn("ailab-a-1");
            when(podGone.getPodName()).thenReturn("ailab-b-1");
            when(podStatusCache.snapshot()).thenReturn(new PodStatusSnapshot(Map.of("ailab-a-1", running), List.of(), checkedAt));
            when(requestRepository.findAllByStatusInWithAssociations(Status.activeStatuses())).thenReturn(List.of(withPod, podGone));

            List<ContainerInfoDTO> result = adminRequestQueryService.getAllActiveContainers();

            assertThat(result.get(0).pod()).isEqualTo(running);
            assertThat(result.get(1).pod()).isNull();
            assertThat(result).allSatisfy(dto -> {
                assertThat(dto.statusCheckedAt()).isEqualTo(checkedAt);
                assertThat(dto.status()).isEqualTo(Status.FULFILLED);
            });
            verify(podStatusCache, never()).refresh();
        }
    }

    @Nested
    @DisplayName("getAllChangeRequests")
    class GetAllChangeRequests {

        @Test
        @DisplayName("모든 변경 요청 목록을 반환한다")
        void getAllChangeRequests_returnsList() {
            when(changeRequestRepository.findAllByStatusNot(Status.DELETED)).thenReturn(List.of());

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
