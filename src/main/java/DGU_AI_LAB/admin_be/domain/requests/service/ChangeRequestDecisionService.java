package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType;
import DGU_AI_LAB.admin_be.domain.requests.repository.ChangeRequestRepository;
import DGU_AI_LAB.admin_be.domain.users.service.PasswordResetService;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 변경 요청의 승인·거절을 종류에 맞는 처리로 넘긴다. 신청(컨테이너) 단위 변경은
 * {@link AdminModificationCommandService}가, 계정 단위인 비밀번호 변경은 {@link PasswordResetService}가 맡는다.
 *
 * <p>여기서는 트랜잭션을 열지 않는다. 비밀번호 변경은 사용자 행 잠금이 트랜잭션의 첫 읽기여야 해서, 종류를 먼저
 * 읽는 이 조회와 같은 트랜잭션에 있으면 안 된다.
 */
@Service
@RequiredArgsConstructor
public class ChangeRequestDecisionService {

    private final ChangeRequestRepository changeRequestRepository;
    private final AdminModificationCommandService adminModificationCommandService;
    private final PasswordResetService passwordResetService;

    public void approve(Long adminId, Long changeRequestId, String adminComment) {
        if (typeOf(changeRequestId) == ChangeType.PASSWORD) {
            passwordResetService.approve(changeRequestId, adminId, adminComment);
            return;
        }
        adminModificationCommandService.approveModification(adminId, new ApproveModificationDTO(changeRequestId, adminComment));
    }

    public void reject(Long adminId, Long changeRequestId, String adminComment) {
        if (typeOf(changeRequestId) == ChangeType.PASSWORD) {
            passwordResetService.deny(changeRequestId, adminId, adminComment);
            return;
        }
        adminModificationCommandService.rejectModification(adminId, new RejectModificationDTO(changeRequestId, adminComment));
    }

    private ChangeType typeOf(Long changeRequestId) {
        return changeRequestRepository.findChangeTypeById(changeRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
