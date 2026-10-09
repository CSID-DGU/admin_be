package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.ChangeRequestRepository;
import DGU_AI_LAB.admin_be.domain.users.dto.response.PasswordResetSummaryDTO;
import DGU_AI_LAB.admin_be.domain.users.service.PasswordResetService;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeRequestDecisionServiceTest {

    private static final Long ADMIN_ID = 9L;
    private static final Long CHANGE_ID = 40L;

    @Mock private ChangeRequestRepository changeRequestRepository;
    @Mock private AdminModificationCommandService adminModificationCommandService;
    @Mock private PasswordResetService passwordResetService;
    @InjectMocks private ChangeRequestDecisionService service;

    @Test
    @DisplayName("비밀번호 변경의 승인·거절은 해시를 함께 다루는 비밀번호 처리로 넘긴다")
    void passwordGoesToPasswordResetService() {
        when(changeRequestRepository.findChangeTypeById(CHANGE_ID)).thenReturn(Optional.of(ChangeType.PASSWORD));
        when(passwordResetService.approve(CHANGE_ID, ADMIN_ID, "확인")).thenReturn(
                new PasswordResetSummaryDTO(CHANGE_ID, 5L, "홍길동", "a@dgu.ac.kr", "hong", "PROCESSING", null, null));

        assertThat(service.approve(ADMIN_ID, CHANGE_ID, "확인")).isEqualTo(Status.PROCESSING);
        service.reject(ADMIN_ID, CHANGE_ID, "거절");

        verify(passwordResetService).approve(CHANGE_ID, ADMIN_ID, "확인");
        verify(passwordResetService).deny(CHANGE_ID, ADMIN_ID, "거절");
        verifyNoInteractions(adminModificationCommandService);
    }

    @ParameterizedTest
    @EnumSource(value = ChangeType.class, names = {"EXPIRES_AT", "GROUP", "PORT"})
    @DisplayName("신청 단위 변경의 승인·거절은 기존 처리로 넘긴다")
    void requestScopedTypesGoToModificationService(ChangeType type) {
        when(changeRequestRepository.findChangeTypeById(CHANGE_ID)).thenReturn(Optional.of(type));
        when(adminModificationCommandService.approveModification(ADMIN_ID, new ApproveModificationDTO(CHANGE_ID, "확인")))
                .thenReturn(Status.FULFILLED);

        assertThat(service.approve(ADMIN_ID, CHANGE_ID, "확인")).isEqualTo(Status.FULFILLED);
        service.reject(ADMIN_ID, CHANGE_ID, "거절");

        verify(adminModificationCommandService).approveModification(ADMIN_ID, new ApproveModificationDTO(CHANGE_ID, "확인"));
        verify(adminModificationCommandService).rejectModification(ADMIN_ID, new RejectModificationDTO(CHANGE_ID, "거절"));
        verifyNoInteractions(passwordResetService);
    }

    @Test
    @DisplayName("없는 변경 요청이면 404")
    void unknownChangeRequestIsNotFound() {
        when(changeRequestRepository.findChangeTypeById(CHANGE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(ADMIN_ID, CHANGE_ID, "확인"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(adminModificationCommandService, passwordResetService);
    }
}
