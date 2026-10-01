package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.users.dto.response.PasswordResetSummaryDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.PasswordHashes;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 관리자가 사용자 비밀번호를 직접 정한다. 가입한 메일을 받지 못해 본인이 신청할 수 없는 사용자를 위한 경로다.
 *
 * <p>재설정 신청을 대신 내고 곧바로 승인한다. 그래서 컨테이너 반영은 본인 신청과 같은 작업 경로를 타고, 승인이
 * 실패하면(작업 등록 실패 등) 신청이 승인 대기로 남아 목록에서 다시 승인하거나 거절할 수 있다. 사용자가 낸 신청이
 * 승인 대기 중이었다면 그 신청의 새 비밀번호를 관리자가 정한 값으로 바꿔 승인한다.
 */
@Service
@RequiredArgsConstructor
public class AdminPasswordResetService {

    private final PasswordResetService passwordResetService;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetSummaryDTO reset(Long userId, String newPassword, Long adminId) {
        PasswordResetService.Submission submission =
                passwordResetService.submit(userId, PasswordHashes.of(newPassword, passwordEncoder));
        return passwordResetService.approve(submission.request().passwordResetRequestId(), adminId);
    }
}
