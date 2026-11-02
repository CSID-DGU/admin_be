package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TestPodApprovalQueueTest {

    private final RequestRepository requests = mock(RequestRepository.class);
    private final AdminRequestCommandService commands = mock(AdminRequestCommandService.class);
    private final TestPodApprovalQueue queue = new TestPodApprovalQueue(requests, commands);

    private Request req(long id, long userId) {
        Request r = mock(Request.class);
        User u = mock(User.class);
        ContainerImage img = mock(ContainerImage.class);
        ResourceGroup rg = mock(ResourceGroup.class);
        when(u.getUserId()).thenReturn(userId);
        when(img.getImageId()).thenReturn(5L);
        when(rg.getRsgroupId()).thenReturn(7);
        when(r.getRequestId()).thenReturn(id);
        when(r.getExpiresAt()).thenReturn(java.time.LocalDateTime.now().plusDays(1));
        when(r.getUser()).thenReturn(u);
        when(r.getContainerImage()).thenReturn(img);
        when(r.getResourceGroup()).thenReturn(rg);
        return r;
    }

    @Test
    void 같은_계정은_한_주기에_한_건만_승인한다() {
        List<Request> waiting = List.of(req(1, 10), req(2, 10));
        when(requests.findAllByTestTrueAndStatusOrderByRequestIdAsc(Status.PENDING)).thenReturn(waiting);

        queue.drain();

        verify(commands).approveRequest(new ApproveRequestDTO(1L, 5L, 7, null));
        verify(commands, times(1)).approveRequest(any());
    }

    @Test
    void 처리_중인_신청이_있으면_기다린다() {
        List<Request> waiting = List.of(req(1, 10));
        when(requests.findAllByTestTrueAndStatusOrderByRequestIdAsc(Status.PENDING)).thenReturn(waiting);
        when(requests.existsByUser_UserIdAndStatus(10L, Status.PROCESSING)).thenReturn(true);

        queue.drain();

        verify(commands, never()).approveRequest(any());
    }

    @Test
    void 승인이_업무_오류로_실패하면_거절로_끝낸다() {
        List<Request> waiting = List.of(req(1, 10));
        when(requests.findAllByTestTrueAndStatusOrderByRequestIdAsc(Status.PENDING)).thenReturn(waiting);
        when(commands.approveRequest(any())).thenThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        queue.drain();

        verify(commands).rejectRequest(any(RejectRequestDTO.class));
    }

    @Test
    void 재시도_한도를_넘으면_거절로_끝낸다() {
        List<Request> waiting = List.of(req(1, 10));
        when(requests.findAllByTestTrueAndStatusOrderByRequestIdAsc(Status.PENDING)).thenReturn(waiting);

        for (int i = 0; i <= TestPodApprovalQueue.MAX_ATTEMPTS; i++) {
            queue.drain();
        }

        verify(commands, times(TestPodApprovalQueue.MAX_ATTEMPTS)).approveRequest(any());
        verify(commands).rejectRequest(any(RejectRequestDTO.class));
    }

    @Test
    void 만료된_신청은_승인하지_않고_거절한다() {
        Request r = req(1, 10);
        when(r.getExpiresAt()).thenReturn(java.time.LocalDateTime.now().minusMinutes(1));
        when(requests.findAllByTestTrueAndStatusOrderByRequestIdAsc(Status.PENDING)).thenReturn(List.of(r));

        queue.drain();

        verify(commands, never()).approveRequest(any());
        verify(commands).rejectRequest(any(RejectRequestDTO.class));
    }

    @Test
    void 일시_오류는_거절하지_않고_다음_주기에_다시_시도한다() {
        List<Request> waiting = List.of(req(1, 10));
        when(requests.findAllByTestTrueAndStatusOrderByRequestIdAsc(Status.PENDING)).thenReturn(waiting);
        when(commands.approveRequest(any())).thenThrow(new BusinessException(ErrorCode.POD_CREATION_FAILED));

        queue.drain();

        verify(commands, never()).rejectRequest(any(RejectRequestDTO.class));
    }

    @Test
    void 거절이_실패해도_남은_신청을_계속_처리한다() {
        List<Request> waiting = List.of(req(1, 10), req(2, 20));
        when(requests.findAllByTestTrueAndStatusOrderByRequestIdAsc(Status.PENDING)).thenReturn(waiting);
        when(commands.approveRequest(any())).thenThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));
        when(commands.rejectRequest(any(RejectRequestDTO.class))).thenThrow(new IllegalStateException("db"));

        queue.drain();

        verify(commands, times(2)).approveRequest(any());
    }
}
