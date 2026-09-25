package DGU_AI_LAB.admin_be.global.util;

import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;
    private final MessageUtils messageUtils;

    private static final long AUTH_CODE_EXPIRE_SECONDS = 60 * 5; // 5분
    private static final String EMAIL_VERIFY_PREFIX = "email:verify:";
    private static final String EMAIL_VERIFY_ATTEMPTS_PREFIX = "email:verify-attempts:";
    // 6자리 코드는 100만 가지뿐이라 시도 횟수를 막지 않으면 5분 안에 대입으로 뚫린다.
    // 한 코드당 이 횟수만큼 틀리면 코드를 폐기해 새로 받게 한다.
    private static final int MAX_AUTH_CODE_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    public void sendEmailVerificationCode(String email) {
        // 이미 가입된 이메일이면 인증 메일 자체를 보내지 않는다 — 어차피 register()에서도
        // 막히지만, 그 전에 걸러야 이미 가입된 사람에게 혼란만 주는 인증 메일 발송과
        // 5분짜리 인증 코드 발급을 아낀다.
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }

        String authCode = createRandomCode();
        String redisKey = EMAIL_VERIFY_PREFIX + email;

        redisTemplate.opsForValue().set(redisKey, authCode, AUTH_CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        redisTemplate.delete(EMAIL_VERIFY_ATTEMPTS_PREFIX + email);
        log.info("이메일 인증번호 저장 완료");

        sendEmail(email, messageUtils.get("email.verify.subject"),
                messageUtils.get("email.verify.body", authCode));
    }

    public void confirmAuthCode(String email, String code) {
        String redisKey = EMAIL_VERIFY_PREFIX + email;
        String stored = redisTemplate.opsForValue().get(redisKey);
        if (stored == null) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_CODE);
        }
        if (!code.equals(stored)) {
            recordFailedAttempt(email, redisKey);
            throw new BusinessException(ErrorCode.INVALID_AUTH_CODE);
        }

        redisTemplate.delete(redisKey); // 인증번호 제거
        redisTemplate.delete(EMAIL_VERIFY_ATTEMPTS_PREFIX + email);
        redisTemplate.opsForValue().set("VERIFIED:" + email, "true", 10, TimeUnit.MINUTES); // 인증 상태 저장
        log.info("이메일 [{}] 인증 성공. VERIFIED:{} 키 저장 완료", email, email);
    }

    private void recordFailedAttempt(String email, String codeKey) {
        String attemptsKey = EMAIL_VERIFY_ATTEMPTS_PREFIX + email;
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(attemptsKey, AUTH_CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        }
        if (attempts != null && attempts >= MAX_AUTH_CODE_ATTEMPTS) {
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
