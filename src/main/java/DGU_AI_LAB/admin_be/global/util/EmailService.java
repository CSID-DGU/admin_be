package DGU_AI_LAB.admin_be.global.util;

import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.auth.EmailDomainPolicy;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;
    private final MessageUtils messageUtils;
    private final EmailDomainPolicy emailDomainPolicy;
    private final EmailSendThrottle emailSendThrottle;
    private final RedisWindowCounter windowCounter;

    private static final long AUTH_CODE_EXPIRE_SECONDS = 60 * 5; // 5분
    private static final Duration AUTH_CODE_TTL = Duration.ofSeconds(AUTH_CODE_EXPIRE_SECONDS);
    // 6자리 코드는 100만 가지뿐이라 시도 횟수를 막지 않으면 5분 안에 대입으로 뚫린다.
    // 한 코드당 이 횟수만큼 틀리면 코드를 폐기해 새로 받게 한다.
    private static final int MAX_AUTH_CODE_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 인증 코드의 용도. 용도마다 Redis 키가 달라 가입용 코드로 비밀번호를 재설정하지 못한다. */
    private enum CodePurpose {
        SIGNUP("email:verify:", "email:verify-attempts:"),
        PASSWORD_RESET("email:password-reset:", "email:password-reset-attempts:");

        private final String codePrefix;
        private final String attemptsPrefix;

        CodePurpose(String codePrefix, String attemptsPrefix) {
            this.codePrefix = codePrefix;
            this.attemptsPrefix = attemptsPrefix;
        }
    }

    public void sendEmailVerificationCode(String email) {
        emailDomainPolicy.requireAllowed(email);
        // 이미 가입된 이메일이면 인증 메일 자체를 보내지 않는다 — 어차피 register()에서도
        // 막히지만, 그 전에 걸러야 이미 가입된 사람에게 혼란만 주는 인증 메일 발송과
        // 5분짜리 인증 코드 발급을 아낀다.
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }
        emailSendThrottle.acquire(email);

        String authCode = issueCode(CodePurpose.SIGNUP, email);
        log.info("이메일 인증번호 저장 완료");

        sendEmail(email, messageUtils.get("email.verify.subject"),
                messageUtils.get("email.verify.body", authCode));
    }

    public void confirmAuthCode(String email, String code) {
        consumeCode(CodePurpose.SIGNUP, email, code);
        redisTemplate.opsForValue().set("VERIFIED:" + email, "true", 10, TimeUnit.MINUTES); // 인증 상태 저장
        log.info("이메일 [{}] 인증 성공. VERIFIED:{} 키 저장 완료", email, email);
    }

    /**
     * 비밀번호 재설정 코드를 만들어 보낸다. 발송 횟수 제한과 가입 여부 확인은 부르는 쪽이 한다.
     * codeKey는 코드를 묶어 둘 주소 표기(대소문자를 맞춘 것), to는 실제 받는 주소다.
     */
    public void sendPasswordResetCode(String codeKey, String to) {
        String authCode = issueCode(CodePurpose.PASSWORD_RESET, codeKey);
        sendEmail(to, messageUtils.get("email.password-reset.subject"),
                messageUtils.get("email.password-reset.body", authCode));
    }

    /** 비밀번호 재설정 코드를 확인하고 없앤다. 맞는 코드는 한 번만 통과한다. */
    public void consumePasswordResetCode(String codeKey, String code) {
        consumeCode(CodePurpose.PASSWORD_RESET, codeKey, code);
    }

    public void sendPasswordChangedNotice(String to) {
        sendEmail(to, messageUtils.get("email.password-changed.subject"),
                messageUtils.get("email.password-changed.body"));
    }

    private String issueCode(CodePurpose purpose, String key) {
        String authCode = createRandomCode();
        redisTemplate.opsForValue().set(purpose.codePrefix + key, authCode, AUTH_CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        redisTemplate.delete(purpose.attemptsPrefix + key);
        return authCode;
    }

    private void consumeCode(CodePurpose purpose, String key, String code) {
        String redisKey = purpose.codePrefix + key;
        String stored = redisTemplate.opsForValue().get(redisKey);
        if (stored == null) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_CODE);
        }
        if (!code.equals(stored)) {
            recordFailedAttempt(purpose, key, redisKey);
            throw new BusinessException(ErrorCode.INVALID_AUTH_CODE);
        }
        // 같은 코드로 동시에 들어온 요청 중 실제로 키를 지운 하나만 통과시킨다.
        if (!Boolean.TRUE.equals(redisTemplate.delete(redisKey))) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_CODE);
        }
        redisTemplate.delete(purpose.attemptsPrefix + key);
    }

    private void recordFailedAttempt(CodePurpose purpose, String key, String codeKey) {
        String attemptsKey = purpose.attemptsPrefix + key;
        if (windowCounter.increment(attemptsKey, AUTH_CODE_TTL) >= MAX_AUTH_CODE_ATTEMPTS) {
            redisTemplate.delete(codeKey);
            redisTemplate.delete(attemptsKey);
            log.warn("이메일 인증 코드 입력 {}회 실패로 코드 폐기", MAX_AUTH_CODE_ATTEMPTS);
            throw new BusinessException(ErrorCode.TOO_MANY_AUTH_CODE_ATTEMPTS);
        }
    }

    private void sendEmail(String to, String subject, String text) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, false);

            mailSender.send(message);
        } catch (MessagingException | RuntimeException e) {
            log.error("이메일 전송 실패: 수신자={}", to, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String createRandomCode() {
        return String.valueOf(RANDOM.nextInt(900000) + 100000);
    }
}
