package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.global.util.EmailService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 비밀번호 재설정 인증번호 메일을 요청 응답과 따로 보낸다.
 *
 * <p>인증번호 요청은 가입 여부를 숨기려고 누구에게나 200을 돌려주는데, 요청 안에서 메일을 보내면 가입된 주소만
 * 응답이 몇 초 늦고 발송 실패가 500으로 드러나 가입 여부가 새어 나간다. 그래서 발송은 여기 맡기고 요청은 바로
 * 돌아간다. 발송이 실패하거나 밀려 있어 받지 못해도 요청은 성공으로 끝나며, 사용자는 다시 요청하면 된다.
 */
@Slf4j
@Component
public class PasswordResetCodeMailer {

    private static final int THREADS = 2;
    // 주소마다 발송 횟수 제한이 있어 평소에는 쌓이지 않는다. 메일 서버가 멈췄을 때 끝없이 쌓이지 않게만 막는다.
    private static final int QUEUE_CAPACITY = 100;

    private final EmailService emailService;
    private final ExecutorService executor;

    @Autowired
    public PasswordResetCodeMailer(EmailService emailService) {
        this(emailService, new ThreadPoolExecutor(THREADS, THREADS, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY)));
    }

    PasswordResetCodeMailer(EmailService emailService, ExecutorService executor) {
        this.emailService = emailService;
        this.executor = executor;
    }

    /** codeKey는 인증번호를 묶어 둘 주소 표기, to는 실제 받는 주소다({@link EmailService#sendPasswordResetCode}). */
    public void sendLater(String codeKey, String to) {
        try {
            executor.execute(() -> send(codeKey, to));
        } catch (RejectedExecutionException e) {
            log.error("[passwordReset] 메일 발송이 밀려 인증번호를 보내지 못함");
        }
    }

    private void send(String codeKey, String to) {
        try {
            emailService.sendPasswordResetCode(codeKey, to);
        } catch (RuntimeException e) {
            log.error("[passwordReset] 인증번호 메일 발송 실패", e);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
