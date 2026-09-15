package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.RevokeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UbuntuAccountService")
class UbuntuAccountServiceTest {

    @InjectMocks
    private UbuntuAccountService ubuntuAccountService;

    @Mock
    private OperationJobService operationJobService;

    @Test
    @DisplayName("계정 회수 작업을 신청 번호·노드와 함께 등록하고 끝날 때까지 기다린다 — Pod는 대상이 아니다")
    void registersAccountRevokeAndWaits() {
        ubuntuAccountService.deleteUbuntuAccount("testuser", "farm2", 4821L);

        ArgumentCaptor<RevokeRegisterRequestDTO> captor = ArgumentCaptor.forClass(RevokeRegisterRequestDTO.class);
        verify(operationJobService).revokeAndWait(captor.capture(), eq(ErrorCode.UBUNTU_USER_DELETION_FAILED));
        RevokeRegisterRequestDTO body = captor.getValue();
        assertThat(body.requestId()).isEqualTo(4821L);
        assertThat(body.username()).isEqualTo("testuser");
        assertThat(body.nodeName()).isEqualTo("farm2");
        assertThat(body.podName()).isNull();
        assertThat(body.deleteAccount()).isTrue();
    }

    @Test
    @DisplayName("신청 번호가 없으면 작업을 등록하지 않고 실패한다 — 작업은 신청 번호로만 식별된다")
    void requiresRequestId() {
        assertThatThrownBy(() -> ubuntuAccountService.deleteUbuntuAccount("testuser", "farm2", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UBUNTU_USER_DELETION_FAILED);

        verifyNoInteractions(operationJobService);
    }

    @Test
    @DisplayName("회수 작업이 실패하면 예외를 그대로 전파한다")
    void propagatesJobFailure() {
        doThrow(new BusinessException("회수 작업 실패: ACCOUNT_IN_USE", ErrorCode.UBUNTU_USER_DELETION_FAILED))
                .when(operationJobService).revokeAndWait(any(), any());

        assertThatThrownBy(() -> ubuntuAccountService.deleteUbuntuAccount("testuser", "farm2", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ACCOUNT_IN_USE");
    }
}
