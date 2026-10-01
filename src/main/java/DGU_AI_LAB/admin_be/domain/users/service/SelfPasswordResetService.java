package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.util.EmailSendThrottle;
import DGU_AI_LAB.admin_be.global.util.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * 비밀번호를 잊은 사용자가 가입한 메일로 받은 인증 코드로 직접 재설정한다. 메일을 못 받는 사용자는 관리자 초기화로 처리한다.
 *
 * <p>인증 코드는 Redis에서만 확인하고 한 번 쓰면 사라지므로, 코드 없이 보낸 요청은 DB에 닿지 않고 한 코드로는
 * 한 요청만 {@link PasswordResetService}의 사용자 행 잠금까지 간다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelfPasswordResetService {

    private final UserRepository userRepository;
    private final EmailSendThrottle emailSendThrottle;
    private final EmailService emailService;
    private final PasswordResetService passwordResetService;
    private final UserLoginService userLoginService;

    /**
     * 가입된 활성 계정이면 인증 코드를 보낸다. 가입 여부가 응답으로 드러나지 않게, 대상이 아니어도 조용히 끝내고
     * 발송 횟수는 가입 여부와 무관하게 센다.
     */
    public void requestCode(String email) {
        String address = normalize(email);
        emailSendThrottle.acquire(address);

        Optional<User> user = findActiveUser(address);
        if (user.isEmpty()) {
            log.info("[passwordReset] 가입되지 않았거나 비활성인 주소라 코드를 보내지 않음");
            return;
        }
        emailService.sendPasswordResetCode(address, user.get().getEmail());
        log.info("[passwordReset] userId={} 재설정 코드 발송", user.get().getUserId());
    }

    public void reset(String email, String code, String newPassword) {
        String address = normalize(email);
        emailService.consumePasswordResetCode(address, code);

        // 코드를 받은 뒤 탈퇴·비활성화된 계정. 코드가 틀린 경우와 구분해 알리지 않는다.
        User user = findActiveUser(address)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_AUTH_CODE));

        passwordResetService.resetPassword(user.getUserId(), newPassword);
        userLoginService.clearFailedAttempts(address);
        notifyChanged(user);
    }

    /** 비밀번호는 이미 바뀌었으므로 알림 메일이 실패해도 재설정을 실패로 돌리지 않는다. */
    private void notifyChanged(User user) {
        try {
            emailService.sendPasswordChangedNotice(user.getEmail());
        } catch (RuntimeException e) {
            log.warn("[passwordReset] userId={} 비밀번호 변경 알림 메일 발송 실패", user.getUserId(), e);
        }
    }

    private Optional<User> findActiveUser(String address) {
        return userRepository.findByEmail(address).filter(User::getIsActive);
    }

    // DB가 이메일 대소문자를 구분하지 않으므로(로그인 잠금 키와 같은 기준) 코드·발송 제한도 한 표기로 묶는다.
    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
