package DGU_AI_LAB.admin_be.global.util;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.UnauthorizedException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final RedisTemplate<String, String> redisTemplate;

    private static final long AUTH_CODE_EXPIRE_SECONDS = 60 * 5; // 5분

    public void sendEmailVerificationCode(String email) {
        String authCode = createRandomCode();

        redisTemplate.opsForValue().set(email, authCode, AUTH_CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        log.info("이메일 [{}]에 인증번호 [{}] 저장 완료", email, authCode);

        sendEmail(email, "[DGU AI LAB 서버관리팀] 이메일 인증 코드입니다.",
                "인증번호는 다음과 같습니다: " + authCode + "\n5분 안에 입력해주세요.");
    }

    public void confirmAuthCode(String email, String code) {
        String stored = redisTemplate.opsForValue().get(email);
        if (!code.equals(stored)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_CODE);
        }

        redisTemplate.delete(email); // 인증번호 제거
        redisTemplate.opsForValue().set("VERIFIED:" + email, "true", 10, TimeUnit.MINUTES); // 인증 상태 저장
        log.info("이메일 [{}] 인증 성공. VERIFIED:{} 키 저장 완료", email, email);
    }

    private void sendEmail(String to, String subject, String text) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, false);

            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("이메일 전송 실패", e);
        }
    }

    private String createRandomCode() {
        return String.valueOf(new Random().nextInt(900000) + 100000);
    }
}
