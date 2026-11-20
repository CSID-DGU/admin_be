package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.groups.service.GroupMembershipPolicy;
import DGU_AI_LAB.admin_be.error.ErrorCode;
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
import org.springframework.test.util.ReflectionTestUtils;

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
    private final GroupRepository groups = mock(GroupRepository.class);
    private final GroupMembershipPolicy groupPolicy = mock(GroupMembershipPolicy.class);
    private final AdminTestPodService service =
            new AdminTestPodService(users, nodes, images, requests, ports, groups, groupPolicy);

    private final User admin = mock(User.class);
    private final ResourceGroup rg1 = mock(ResourceGroup.class);
    private final ResourceGroup rg2 = mock(ResourceGroup.class);
    private final ResourceGroup lab = mock(ResourceGroup.class);

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
        // DB가 매기는 신청 번호를 흉내 낸다 — 그룹은 번호가 있는 신청에만 붙는다(Request#addGroup).
        java.util.concurrent.atomic.AtomicLong nextId = new java.util.concurrent.atomic.AtomicLong(100);
        when(requests.saveAndFlush(any(Request.class))).thenAnswer(i -> {
            Request r = i.getArgument(0);
            ReflectionTestUtils.setField(r, "requestId", nextId.incrementAndGet());
            return r;
        });
        when(rg1.getRsgroupId()).thenReturn(1);
        when(rg2.getRsgroupId()).thenReturn(2);
        when(rg1.getServerName()).thenReturn("FARM");
        when(rg2.getServerName()).thenReturn("FARM");
        when(lab.getServerName()).thenReturn("LAB");
    }

    @Test
    void 기본은_모든_노드마다_PENDING_테스트_신청을_만든다() {
        givenBase(List.of(node("farm1", rg1), node("farm2", rg2)));

        service.create(1L, new TestPodCreateRequestDTO(9L, null, null, List.of(3000, 3000), null));

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

        service.create(1L, new TestPodCreateRequestDTO(9L, List.of(2), null, null, null));

        verify(requests, times(1)).saveAndFlush(any(Request.class));
        verify(ports, never()).createPortRequest(any(), any(), any(), any());
    }

    @Test
    void 대상_노드가_없으면_거절한다() {
        givenBase(List.of());

        assertThatThrownBy(() -> service.create(1L, new TestPodCreateRequestDTO(9L, null, null, null, null)))
                .isInstanceOf(BusinessException.class);
        verify(requests, never()).saveAndFlush(any(Request.class));
    }

    @Test
    void LAB_노드는_전체_선택에서도_직접_골라도_제외한다() {
        Node farm1 = node("farm1", rg1);
        Node lab1 = node("lab1", lab);
        givenBase(List.of(farm1, lab1));
        when(nodes.findAllById(List.of("lab1"))).thenReturn(List.of(lab1));

        service.create(1L, new TestPodCreateRequestDTO(9L, null, null, null, null));
        ArgumentCaptor<Request> cap = ArgumentCaptor.forClass(Request.class);
        verify(requests, times(1)).saveAndFlush(cap.capture());
        assertThat(cap.getValue().getPinnedNode()).isEqualTo("farm1");

        assertThatThrownBy(() -> service.create(1L, new TestPodCreateRequestDTO(9L, null, List.of("lab1"), null, null)))
                .isInstanceOf(BusinessException.class);
    }

    private Group group(long id) {
        Group g = mock(Group.class);
        when(g.getGroupId()).thenReturn(id);
        return g;
    }

    @Test
    void 고른_공유_그룹을_노드마다_만든_신청에_붙인다() {
        givenBase(List.of(node("farm1", rg1), node("farm2", rg2)));
        Group g3 = group(3L);
        Group g4 = group(4L);
        when(groups.findAllByIdForUpdate(any())).thenReturn(List.of(g3, g4));

        service.create(1L, new TestPodCreateRequestDTO(9L, null, null, null, List.of(3L, 4L, 3L)));

        ArgumentCaptor<Request> cap = ArgumentCaptor.forClass(Request.class);
        verify(requests, times(2)).saveAndFlush(cap.capture());
        assertThat(cap.getAllValues()).allSatisfy(r ->
                assertThat(r.getRequestGroups()).extracting(rg -> rg.getGroup()).containsExactlyInAnyOrder(g3, g4));
        verify(groupPolicy).requireRoomFor(admin, List.of(g3, g4));
    }

    @Test
    void 그룹을_고르지_않으면_그룹을_읽지_않고_신청에도_붙이지_않는다() {
        givenBase(List.of(node("farm1", rg1)));

        service.create(1L, new TestPodCreateRequestDTO(9L, null, null, null, List.of()));

        ArgumentCaptor<Request> cap = ArgumentCaptor.forClass(Request.class);
        verify(requests).saveAndFlush(cap.capture());
        assertThat(cap.getValue().getRequestGroups()).isEmpty();
        verify(groups, never()).findAllByIdForUpdate(any());
    }

    @Test
    void 없는_그룹을_고르면_신청을_만들지_않고_거절한다() {
        givenBase(List.of(node("farm1", rg1)));
        Group g3 = group(3L); // when(...) 안에서 다른 mock을 만들면 스텁이 끊긴다
        when(groups.findAllByIdForUpdate(any())).thenReturn(List.of(g3));

        assertThatThrownBy(() -> service.create(1L, new TestPodCreateRequestDTO(9L, null, null, null, List.of(3L, 99L))))
                .isInstanceOf(BusinessException.class);
        verify(requests, never()).saveAndFlush(any(Request.class));
    }

    @Test
    void 그룹_한도를_넘으면_신청을_만들지_않고_거절한다() {
        givenBase(List.of(node("farm1", rg1)));
        Group g3 = group(3L); // when(...) 안에서 다른 mock을 만들면 스텁이 끊긴다
        when(groups.findAllByIdForUpdate(any())).thenReturn(List.of(g3));
        doThrow(new BusinessException(ErrorCode.GROUP_LIMIT_EXCEEDED)).when(groupPolicy).requireRoomFor(any(), any());

        assertThatThrownBy(() -> service.create(1L, new TestPodCreateRequestDTO(9L, null, null, null, List.of(3L))))
                .isInstanceOf(BusinessException.class);
        verify(requests, never()).saveAndFlush(any(Request.class));
    }
}
