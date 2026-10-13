package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.global.util.EmailAddresses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** 메일 한 통을 보낸다. 실패는 예외로 올리지 않고 결과로 알린다 — 실패를 어디에 알릴지는 부른 쪽이 정한다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationMailer {

    @Value("${spring.mail.username}")
    private String from;

    private final JavaMailSender mailSender;

    /** @return 보냈으면 true. 로그에는 수신자 주소를 가려서 남긴다. */
    public boolean send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            log.error("메일 전송 실패: 수신자={}", EmailAddresses.mask(to), e);
            return false;
        }
    }
}
