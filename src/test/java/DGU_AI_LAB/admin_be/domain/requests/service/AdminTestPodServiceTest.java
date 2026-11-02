package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import DGU_AI_LAB.admin_be.domain.nodes.repository.NodeRepository;
import DGU_AI_LAB.admin_be.domain.portRequests.service.PortRequestService;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.TestPodCreateRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AdminTestPodServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final NodeRepository nodes = mock(NodeRepository.class);
    private final ContainerImageRepository images = mock(ContainerImageRepository.class);
    private final RequestRepository requests = mock(RequestRepository.class);
    private final PortRequestService ports = mock(PortRequestService.class);
    private final AdminTestPodService service = new AdminTestPodService(users, nodes, images, requests, ports);

    private final User admin = mock(User.class);
    private final ResourceGroup rg1 = mock(ResourceGroup.class);
    private final ResourceGroup rg2 = mock(ResourceGroup.class);

    private Node node(String id, ResourceGroup rg) {
        Node n = mock(Node.class);
        when(n.getNodeId()).thenReturn(id);
        when(n.getResourceGroup()).thenReturn(rg);
        return n;
    }

    private void givenBase(List<Node> all) {
        when(admin.getUbuntuUsername()).thenReturn("admin1");
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(admin));
        when(images.findById(9L)).thenReturn(Optional.of(mock(ContainerImage.class)));
        when(nodes.findAll()).thenReturn(all);
        when(requests.saveAndFlush(any(Request.class))).thenAnswer(i -> i.getArgument(0));
        when(rg1.getRsgroupId()).thenReturn(1);
        when(rg2.getRsgroupId()).thenReturn(2);
    }

    @Test
    void 기본은_모든_노드마다_PENDING_테스트_신청을_만든다() {
        givenBase(List.of(node("farm1", rg1), node("farm2", rg2)));

        service.create(1L, new TestPodCreateRequestDTO(9L, null, null, List.of(3000, 3000)));

        ArgumentCaptor<Request> cap = ArgumentCaptor.forClass(Request.class);
        verify(requests, times(2)).saveAndFlush(cap.capture());
        assertThat(cap.getAllValues()).allSatisfy(r -> {
            assertThat(r.isTest()).isTrue();
            assertThat(r.getStatus()).isEqualTo(Status.PENDING);
            assertThat(r.getExpiresAt()).isBefore(java.time.LocalDateTime.now().plusDays(2));
        });
        assertThat(cap.getAllValues()).extracting(Request::getPinnedNode).containsExactly("farm1", "farm2");
        verify(ports, times(2)).createPortRequest(any(), any(), eq(3000), any()); // 중복 포트는 하나로
    }

    @Test
    void 리소스_그룹으로_좁힌다() {
        givenBase(List.of(node("farm1", rg1), node("farm2", rg2)));

        service.create(1L, new TestPodCreateRequestDTO(9L, List.of(2), null, null));

        verify(requests, times(1)).saveAndFlush(any(Request.class));
        verify(ports, never()).createPortRequest(any(), any(), any(), any());
    }

    @Test
    void 대상_노드가_없으면_거절한다() {
        givenBase(List.of());

        assertThatThrownBy(() -> service.create(1L, new TestPodCreateRequestDTO(9L, null, null, null)))
                .isInstanceOf(BusinessException.class);
        verify(requests, never()).saveAndFlush(any(Request.class));
    }
}
