package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.portRequests.service.PortRequestService;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
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

    @Mock
    private PodExternalPortRepository podExternalPortRepository;

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

        @Test
        @DisplayName("관리자 요청 조회 시 pod_external_ports를 함께 반환한다")
        void getAllRequests_returnsPodExternalPorts() {
            Request request = mockRequest(1L);
            PodExternalPort podExternalPort = PodExternalPort.builder()
                    .request(request)
                    .internalPort(22)
                    .externalPort(30022)
                    .usagePurpose("ssh")
                    .build();

            when(requestRepository.findAll()).thenReturn(List.of(request));
            when(portRequestService.getPortRequestsByRequestId(1L)).thenReturn(List.of());
            when(podExternalPortRepository.findByRequestRequestId(1L)).thenReturn(List.of(podExternalPort));

            List<SaveRequestResponseDTO> result = adminRequestQueryService.getAllRequests();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).podExternalPorts()).hasSize(1);
            assertThat(result.get(0).podExternalPorts().get(0).internalPort()).isEqualTo(22);
            assertThat(result.get(0).podExternalPorts().get(0).externalPort()).isEqualTo(30022);
            assertThat(result.get(0).podExternalPorts().get(0).usagePurpose()).isEqualTo("ssh");
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
        when(request.getVolumeSizeGiB()).thenReturn(20L);
        when(request.getUsagePurpose()).thenReturn("학습");
        when(request.getFormAnswers()).thenReturn("{}");
        when(request.getStatus()).thenReturn(Status.FULFILLED);
        return request;
    }
}
