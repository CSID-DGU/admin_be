package DGU_AI_LAB.admin_be.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 특정 클래스가 남긴 로그를 테스트에서 들여다보기 위한 헬퍼.
 *
 * 민감 정보(토큰, 인증번호, 이메일 등)가 로그로 새어나가지 않는지 검증하는 회귀 테스트에 사용한다.
 * try-with-resources로 감싸면 원래 로거 상태(appender/level)를 자동으로 되돌린다.
 */
public final class LogCaptor implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender;
    private final Level originalLevel;

    private LogCaptor(Class<?> target) {
        this.logger = (Logger) LoggerFactory.getLogger(target);
        this.originalLevel = logger.getLevel();
        this.appender = new ListAppender<>();
        this.appender.start();
        logger.setLevel(Level.TRACE);
        logger.addAppender(appender);
    }

    public static LogCaptor forClass(Class<?> target) {
        return new LogCaptor(target);
    }

    /** 파라미터가 치환된 최종 로그 문장 목록. */
    public List<String> messages() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList());
    }

    /** 모든 로그 문장을 하나로 이어붙인 문자열. 특정 값의 포함 여부 검증에 쓴다. */
    public String joined() {
        return String.join("\n", messages());
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(originalLevel);
    }
}
