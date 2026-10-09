package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.users.entity.PasswordHashes;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.util.EmailSendThrottle;
import DGU_AI_LAB.admin_be.global.util.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * 비밀번호를 잊은 사용자가 가입한 메일로 받은 인증 코드로 재설정을 신청한다. 신청만으로는 아무것도 바뀌지 않고,
 * 관리자가 승인해야 적용된다({@link PasswordResetService}). 메일을 못 받는 사용자는 관리자가 직접 정해 준다.
 *
 * <p>인증 코드는 Redis에서만 확인하고 한 번 쓰면 사라지므로, 코드 없이 보낸 요청은 DB에 닿지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelfPasswordResetService {

    private final UserRepository userRepository;
    private final EmailSendThrottle emailSendThrottle;
    private final EmailService emailService;
    private final PasswordResetCodeMailer codeMailer;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetService passwordResetService;
    private final PasswordResetNotifier notifier;

    /**
     * 가입된 활성 계정이면 인증 코드를 보낸다. 가입 여부가 응답으로 드러나지 않게, 대상이 아니어도 조용히 끝내고
     * 발송 횟수는 가입 여부와 무관하게 센다. 메일은 응답과 따로 보내 응답 시간과 발송 실패로도 드러나지 않게 한다.
     */
    public void requestCode(String email) {
        String address = normalize(email);
        emailSendThrottle.acquire(address);

        Optional<User> user = findActiveUser(address);
        if (user.isEmpty()) {
            log.info("[passwordReset] 가입되지 않았거나 비활성인 주소라 코드를 보내지 않음");
            return;
        }
        codeMailer.sendLater(address, user.get().getEmail());
        log.info("[passwordReset] userId={} 재설정 코드 발송 맡김", user.get().getUserId());
    }

    public void submit(String email, String code, String newPassword) {
        String address = normalize(email);
        emailService.consumePasswordResetCode(address, code);

        // 코드를 받은 뒤 탈퇴·비활성화된 계정. 코드가 틀린 경우와 구분해 알리지 않는다.
        User user = findActiveUser(address)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_AUTH_CODE));

        PasswordResetService.Submission submission =
                passwordResetService.submit(user.getUserId(), PasswordHashes.of(newPassword, passwordEncoder));
        // 승인 전에 다시 낸 신청은 새 비밀번호만 바뀐 것이라 관리자에게 또 알리지 않는다.
        if (submission.created()) {
            notifier.requested(submission.notice());
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
