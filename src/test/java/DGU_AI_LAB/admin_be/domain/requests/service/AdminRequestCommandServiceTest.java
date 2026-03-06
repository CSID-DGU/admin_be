package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.CreatePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.ChangeRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.domain.usedIds.entity.UsedId;
import DGU_AI_LAB.admin_be.domain.usedIds.service.IdAllocationService;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminRequestCommandServiceTest {

    @Mock private AlarmService alarmService;
    @Mock private RequestRepository requestRepository;
    @Mock private UserRepository userRepository;
    @Mock private ContainerImageRepository containerImageRepository;
    @Mock private ResourceGroupRepository resourceGroupRepository;
    @Mock private IdAllocationService idAllocationService;
    @Mock private ChangeRequestRepository changeRequestRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private PodExternalPortRepository podExternalPortRepository;
    @Mock private PodService podService;
    @Mock private WebClient mockWebClient;

    // WebClient 체이닝 mock
    // bodyValue()의 반환 타입이 RequestHeadersSpec<?>이므로 별도 mock 경유
    @Mock private WebClient.RequestBodyUriSpec putUriSpec;
    @Mock private WebClient.RequestBodySpec putBodySpec;
    @Mock private WebClient.RequestHeadersSpec putHeadersSpec;
    @Mock private WebClient.ResponseSpec putResponseSpec;
    @Mock private WebClient.RequestBodyUriSpec postUriSpec;
    @Mock private WebClient.RequestBodySpec postBodySpec;
    @Mock private WebClient.RequestHeadersSpec postHeadersSpec;
    @Mock private WebClient.ResponseSpec postResponseSpec;

    // 공유 엔티티 mock - when() 내부에서 다른 mock 호출로 인한 UnfinishedStubbingException 방지
    @Mock private ContainerImage mockImage;
    @Mock private ResourceGroup mockRg;
    @Mock private User mockUser;

    private AdminRequestCommandService service;

    @BeforeEach
    void setUp() {
        // @RequiredArgsConstructor 생성자 필드 선언 순서대로 주입
        service = new AdminRequestCommandService(
                alarmService, requestRepository, userRepository, containerImageRepository,
                resourceGroupRepository, idAllocationService, changeRequestRepository,
                groupRepository, podExternalPortRepository, podService, new ObjectMapper(),
                mockWebClient, mockWebClient
        );
        // 공유 엔티티 기본 설정
        when(mockUser.getName()).thenReturn("테스트유저");
        when(mockImage.getImageId()).thenReturn(1L);
        when(mockRg.getRsgroupId()).thenReturn(1);
    }

    /** 사용자 생성 PUT 요청 WebClient 모킹
     *  체이닝: put() → putUriSpec → (uri) → putBodySpec → (bodyValue) → putHeadersSpec → (retrieve) → putResponseSpec */
    @SuppressWarnings("unchecked")
    private void stubWebClientPut() {
        when(mockWebClient.put()).thenReturn(putUriSpec);
        when(putUriSpec.uri(anyString())).thenReturn(putBodySpec);
        doReturn(putHeadersSpec).when(putBodySpec).bodyValue(any());
        when(putHeadersSpec.retrieve()).thenReturn(putResponseSpec);
        when(putResponseSpec.onStatus(any(), any())).thenReturn(putResponseSpec);
        when(putResponseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(Map.of("result", "ok")));
    }

    /** PVC 생성 POST 요청 WebClient 모킹
     *  체이닝: post() → postUriSpec → (uri) → postBodySpec → (bodyValue) → postHeadersSpec → (retrieve) → postResponseSpec */
    @SuppressWarnings("unchecked")
    private void stubWebClientPost() {
        when(mockWebClient.post()).thenReturn(postUriSpec);
        when(postUriSpec.uri(anyString())).thenReturn(postBodySpec);
        doReturn(postHeadersSpec).when(postBodySpec).bodyValue(any());
        when(postHeadersSpec.retrieve()).thenReturn(postResponseSpec);
        when(postResponseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(Map.of()));
    }

    /** 공통 Request + IdAllocation mock 설정 */
    private Request buildMockedRequest(Long requestId) {
        Request request = mock(Request.class);
        when(request.getStatus()).thenReturn(Status.PENDING);
        when(request.getUbuntuUsername()).thenReturn("testuser");
        when(request.getUbuntuPassword()).thenReturn("encoded_pw");
        when(request.getRequestGroups()).thenReturn(new LinkedHashSet<>());
        when(request.getUser()).thenReturn(mockUser);
        when(request.getResourceGroup()).thenReturn(mockRg);
        when(request.getContainerImage()).thenReturn(mockImage);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        return request;
    }

    /** 공통 IdAllocation mock 설정 */
    private void stubIdAllocation(Request request, long uidValue) {
        UsedId uid = mock(UsedId.class);
        when(uid.getIdValue()).thenReturn(uidValue);
        Group primaryGroup = mock(Group.class);
        when(primaryGroup.getUbuntuGid()).thenReturn(uidValue);
        when(primaryGroup.getGroupName()).thenReturn("testuser");
        when(idAllocationService.allocateFor(request))
                .thenReturn(new IdAllocationService.AllocationResult(uid, primaryGroup));
    }

    @Test
    @DisplayName("승인 시 Pod 응답의 external ports가 PodExternalPortRepository에 올바르게 저장된다")
    void approveRequest_savesPodExternalPortsToNewTable() {
        // Given
        Long requestId = 1L;
        Request request = buildMockedRequest(requestId);
        stubIdAllocation(request, 10000L);
        stubWebClientPut();
        stubWebClientPost();

        // ssh(22→30022), jupyter(8888→30888) 두 개의 external port를 반환하는 pod 응답
        CreatePodResponseDTO podResponse = new CreatePodResponseDTO(
                "running", "farm1", "pod-testuser-xxxx",
                List.of(
                        new CreatePodResponseDTO.PortInfo("ssh", 22, 30022),
                        new CreatePodResponseDTO.PortInfo("jupyter", 8888, 30888)
                )
        );
        when(podService.createPod("testuser")).thenReturn(podResponse);
        when(containerImageRepository.findById(1L)).thenReturn(Optional.of(mockImage));
        when(resourceGroupRepository.findById(1)).thenReturn(Optional.of(mockRg));
        when(podExternalPortRepository.save(any(PodExternalPort.class))).thenAnswer(inv -> inv.getArgument(0));

        ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, 10L, "승인합니다");

        // When
        service.approveRequest(dto);

        // Then - PodExternalPortRepository에 2개의 포트가 저장되어야 함
        ArgumentCaptor<PodExternalPort> captor = ArgumentCaptor.forClass(PodExternalPort.class);
        verify(podExternalPortRepository, times(2)).save(captor.capture());

        List<PodExternalPort> saved = captor.getAllValues();
        assertThat(saved).hasSize(2);

        PodExternalPort sshPort = saved.get(0);
        assertThat(sshPort.getUsagePurpose()).isEqualTo("ssh");
        assertThat(sshPort.getInternalPort()).isEqualTo(22);
        assertThat(sshPort.getExternalPort()).isEqualTo(30022);

        PodExternalPort jupyterPort = saved.get(1);
        assertThat(jupyterPort.getUsagePurpose()).isEqualTo("jupyter");
        assertThat(jupyterPort.getInternalPort()).isEqualTo(8888);
        assertThat(jupyterPort.getExternalPort()).isEqualTo(30888);
    }

    @Test
    @DisplayName("Pod가 포트 없이 응답할 때 PodExternalPort는 저장되지 않는다")
    void approveRequest_noPodPorts_savesNoPodExternalPorts() {
        // Given
        Long requestId = 2L;
        Request request = buildMockedRequest(requestId);
        stubIdAllocation(request, 10001L);
        stubWebClientPut();
        stubWebClientPost();

        // 포트가 없는 pod 응답
        CreatePodResponseDTO podResponse = new CreatePodResponseDTO(
                "running", "farm1", "pod-testuser-yyyy", List.of()
        );
        when(podService.createPod("testuser")).thenReturn(podResponse);
        when(containerImageRepository.findById(1L)).thenReturn(Optional.of(mockImage));
        when(resourceGroupRepository.findById(1)).thenReturn(Optional.of(mockRg));

        ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, 10L, "승인합니다");

        // When
        service.approveRequest(dto);

        // Then - 포트가 없으므로 save 호출 없음
        verify(podExternalPortRepository, never()).save(any());
    }

    @Test
    @DisplayName("승인 시 PodService.createPod()가 정확히 1회 호출된다")
    void approveRequest_callsPodServiceCreatePodOnce() {
        // Given
        Long requestId = 3L;
        Request request = buildMockedRequest(requestId);
        stubIdAllocation(request, 10002L);
        stubWebClientPut();
        stubWebClientPost();

        CreatePodResponseDTO podResponse = new CreatePodResponseDTO(
                "running", "farm1", "pod-testuser-zzzz", List.of()
        );
        when(podService.createPod("testuser")).thenReturn(podResponse);
        when(containerImageRepository.findById(1L)).thenReturn(Optional.of(mockImage));
        when(resourceGroupRepository.findById(1)).thenReturn(Optional.of(mockRg));

        ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, 10L, null);

        // When
        service.approveRequest(dto);

        // Then - PodService.createPod()가 username으로 정확히 1회 호출
        verify(podService, times(1)).createPod("testuser");
    }
}
