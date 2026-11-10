package DGU_AI_LAB.admin_be.global.util;

import DGU_AI_LAB.admin_be.domain.alarm.service.NotificationMailer;
import DGU_AI_LAB.admin_be.domain.messageTemplate.repository.MessageTemplateRepository;
import DGU_AI_LAB.admin_be.global.config.MessageSourceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 계정·홈 안내 메일을 배포본과 같은 경로(MessageSourceConfig → MessageUtils → NotificationMailer)로 만들어
 * 본문이 비지 않고, 값이 빠짐없이 들어가고, 홈 삭제 일수가 설정값을 따르고, 줄바꿈이 살아 있는지 고정한다.
 */
class UserNoticeRenderingTest {

    private static final String NAME = "홍길동";
    private static final String ANNOUNCED_DAYS = "45"; // 기본값(30)과 달라야 하드코딩을 잡는다
    private static final String FORM_URL = "https://forms.gle/nACaxj2UeJF56V2i7";

    private MessageUtils messageUtils;

    @BeforeEach
    void setUp() {
        MessageTemplateRepository repository = mock(MessageTemplateRepository.class);
        when(repository.findById(anyString())).thenReturn(Optional.empty()); // DB 양식이 없으면 messages.properties
        messageUtils = new MessageUtils(new MessageSourceConfig(repository).dbMessageSource());
    }

    private void assertWellFormed(String key, String text) {
        assertThat(text).as(key).isNotBlank()
                .doesNotContainPattern("\\{\\d+}")          // 채워지지 않은 자리표시자
                .doesNotContain("\\n")                       // 줄바꿈이 글자 그대로 남음
                .doesNotContain("30일")                       // 홈 삭제 일수 하드코딩
                .contains("\n\n");                           // 문단 구분이 살아 있음
        assertThat(text.lines()).as(key + " 들여쓰기 잔류").noneMatch(line -> line.startsWith(" "));
        assertThat(text).as(key).startsWith("안녕하세요, " + NAME + "님.");
    }

    @Test
    @DisplayName("1년 미사용 경고: 남은 일수·예정일·홈 삭제 일수·재활성화 방법이 들어간다")
    void deleteWarning() {
        String body = messageUtils.get("notification.user.delete-warning.body", NAME, "7", "2026-12-01", ANNOUNCED_DAYS);

        assertWellFormed("delete-warning", body);
        assertThat(body).contains("7일 뒤(2026-12-01)").contains("컨테이너를 사용한 지 45일이 지나면 삭제")
                .contains("이력은 비활성화 후에도 보존됩니다").contains("컨테이너를 새로 신청하시기 바랍니다");
        assertThat(messageUtils.get("notification.user.delete-warning.subject", "7")).isEqualTo("[Provi] 7일 뒤에 계정이 비활성화됩니다");
    }

    @Test
    @DisplayName("졸업 경고: 남은 일수·예정일·홈 삭제 일수가 들어가고 졸업이 미뤄졌을 때의 방법을 알려 준다")
    void graduationWarning() {
        String body = messageUtils.get("notification.user.graduation-warning.body", NAME, "1", "2027-03-28", ANNOUNCED_DAYS);

        assertWellFormed("graduation-warning", body);
        assertThat(body).contains("졸업 예정일").contains("1일 뒤(2027-03-28)").contains("45일이 지나면 삭제")
                .contains("이력은 비활성화 후에도 보존됩니다").contains("졸업 예정일을 변경하시기 바랍니다");
    }

    @Test
    @DisplayName("비활성화 완료(1년 미사용·졸업): 홈 삭제 일수·이력 보존·재활성화 방법(문의 폼)이 정확히 들어간다")
    void softDeleteNotices() {
        for (String key : new String[]{"notification.user.soft-delete", "notification.user.soft-delete.graduation"}) {
            String body = messageUtils.get(key + ".body", NAME, ANNOUNCED_DAYS);

            assertWellFormed(key, body);
            assertThat(body).as(key).contains("계정을 비활성화하였습니다").contains("로그인할 수 없으며")
                    .contains("컨테이너를 사용한 지 45일이 지나면 삭제").contains("이미 삭제되었을 수 있습니다")
                    .contains("이력은 보존됩니다").contains("문의 폼으로 접수하시기 바랍니다").contains(FORM_URL);
            assertThat(messageUtils.get(key + ".subject")).as(key).contains("비활성화되었습니다").doesNotContain("{");
        }
    }

    @Test
    @DisplayName("홈 삭제 직전 안내: 계정 이름·남은 일수·삭제 안내일·다시 신청하는 방법이 들어간다")
    void homeDeleteReminder() {
        String body = messageUtils.get("notification.user.home-delete-reminder.body", NAME, "honggildong", "7", "2026-12-08");

        assertWellFormed("home-delete-reminder", body);
        assertThat(body).contains("/home/honggildong").contains("7일 뒤인 2026-12-08부터 삭제").contains("[GPU 신청]")
                .contains("기존 홈 디렉터리를 그대로 사용");
        assertThat(messageUtils.get("notification.user.home-delete-reminder.subject", "1"))
                .isEqualTo("[Provi] 홈 디렉터리가 1일 뒤에 삭제됩니다");
    }

    @Test
    @DisplayName("NotificationMailer: 렌더링한 제목·본문을 그대로 한 통의 메일로 보낸다")
    void mailerSendsRenderedBody() {
        JavaMailSender sender = mock(JavaMailSender.class);
        NotificationMailer mailer = new NotificationMailer(sender);
        ReflectionTestUtils.setField(mailer, "from", "noreply@example.com");
        String subject = messageUtils.get("notification.user.home-delete-reminder.subject", "7");
        String body = messageUtils.get("notification.user.home-delete-reminder.body", NAME, "honggildong", "7", "2026-12-08");

        assertThat(mailer.send("user@dgu.ac.kr", subject, body)).isTrue();

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue().getSubject()).isEqualTo(subject);
        assertThat(sent.getValue().getText()).isEqualTo(body).isNotBlank();
        assertThat(sent.getValue().getTo()).containsExactly("user@dgu.ac.kr");
    }
}
