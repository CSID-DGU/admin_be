package DGU_AI_LAB.admin_be.domain.messageTemplate.service;

import DGU_AI_LAB.admin_be.domain.messageTemplate.entity.MessageTemplate;
import DGU_AI_LAB.admin_be.domain.messageTemplate.repository.MessageTemplateRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageTemplateServiceTest {

    /** messages.properties에 실제로 존재하는 기본값 키 */
    private static final String KNOWN_KEY = "notification.expired.dm";

    @InjectMocks
    private MessageTemplateService messageTemplateService;

    @Mock
    private MessageTemplateRepository repository;

    @Nested
    @DisplayName("getAll")
    class GetAll {

        @Test
        @DisplayName("기본값 키와 DB 오버라이드를 합쳐서 반환한다")
        void getAll_mergesDefaultsAndOverrides() {
            when(repository.findAll()).thenReturn(List.of(new MessageTemplate(KNOWN_KEY, "수정된 값")));

            List<MessageTemplateService.TemplateView> result = messageTemplateService.getAll();

            assertThat(result).isNotEmpty();
            MessageTemplateService.TemplateView overridden = result.stream()
                    .filter(v -> v.key().equals(KNOWN_KEY))
                    .findFirst()
                    .orElseThrow();
            assertThat(overridden.currentValue()).isEqualTo("수정된 값");
            assertThat(overridden.overridden()).isTrue();
            assertThat(overridden.defaultValue()).isNotEqualTo("수정된 값");
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("DB에 오버라이드가 이미 있으면 값만 변경한다")
        void update_updatesExistingOverride() {
            MessageTemplate existing = new MessageTemplate(KNOWN_KEY, "이전 값");
            when(repository.findById(KNOWN_KEY)).thenReturn(Optional.of(existing));

            messageTemplateService.update(KNOWN_KEY, "새 값");

            assertThat(existing.getValue()).isEqualTo("새 값");
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("기본값에만 있는 키는 오버라이드 row를 새로 저장한다")
        void update_insertsOverrideForDefaultOnlyKey() {
            when(repository.findById(KNOWN_KEY)).thenReturn(Optional.empty());

            messageTemplateService.update(KNOWN_KEY, "새 값");

            ArgumentCaptor<MessageTemplate> captor = ArgumentCaptor.forClass(MessageTemplate.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getKey()).isEqualTo(KNOWN_KEY);
            assertThat(captor.getValue().getValue()).isEqualTo("새 값");
        }

        @Test
        @DisplayName("지원하지 않는 키로 수정하면 404 예외를 던지고 저장하지 않는다")
        void update_throwsNotFoundForUnknownKey() {
            when(repository.findById("notification.does.not.exist")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> messageTemplateService.update("notification.does.not.exist", "새 값"))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MESSAGE_TEMPLATE_NOT_FOUND);

            verify(repository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("reset")
    class Reset {

        @Test
        @DisplayName("오버라이드 row를 삭제한다")
        void reset_deletesOverride() {
            messageTemplateService.reset(KNOWN_KEY);

            verify(repository).deleteById(KNOWN_KEY);
        }
    }
}
