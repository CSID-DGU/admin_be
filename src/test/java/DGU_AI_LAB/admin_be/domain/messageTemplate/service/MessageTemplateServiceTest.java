package DGU_AI_LAB.admin_be.domain.messageTemplate.service;

import DGU_AI_LAB.admin_be.domain.messageTemplate.entity.MessageTemplate;
import DGU_AI_LAB.admin_be.domain.messageTemplate.repository.MessageTemplateRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 메시지 템플릿 수정 시 키 검증 동작을 확인한다.
 * 문서는 존재하지 않는 키에 404를 명시하는데, 기존 구현은 아무 키나 받아 새 row를 만들었다.
 */
@ExtendWith(MockitoExtension.class)
class MessageTemplateServiceTest {

    // messages.properties에 실제로 존재하는 키
    private static final String KNOWN_PROPERTIES_KEY = "notification.expired.dm";
    private static final String UNKNOWN_KEY = "notification.this.key.does.not.exist";

    @InjectMocks
    private MessageTemplateService messageTemplateService;

    @Mock
    private MessageTemplateRepository repository;

    @Test
    @DisplayName("properties에 있는 키는 DB 오버라이드가 없어도 새로 저장한다")
    void update_knownPropertiesKey_savesOverride() {
        when(repository.findById(KNOWN_PROPERTIES_KEY)).thenReturn(Optional.empty());

        assertThatCode(() -> messageTemplateService.update(KNOWN_PROPERTIES_KEY, "새 메시지"))
                .doesNotThrowAnyException();

        verify(repository).save(any(MessageTemplate.class));
    }

    @Test
    @DisplayName("존재하지 않는 키로 수정하면 RESOURCE_NOT_FOUND(404)로 거절하고 저장하지 않는다")
    void update_unknownKey_throwsNotFound() {
        when(repository.findById(UNKNOWN_KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageTemplateService.update(UNKNOWN_KEY, "새 메시지"))
                .isInstanceOf(EntityNotFoundException.class)
                .extracting(e -> ((EntityNotFoundException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verify(repository, never()).save(any(MessageTemplate.class));
    }

    @Test
    @DisplayName("RESOURCE_NOT_FOUND는 404 상태를 가진다 (문서와 동일)")
    void resourceNotFound_maps404() {
        assertThat(ErrorCode.RESOURCE_NOT_FOUND.getHttpStatus().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("properties에 없어도 DB에 이미 오버라이드가 있는 키는 값만 갱신한다")
    void update_dbOnlyKey_updatesExistingRow() {
        // email.* 처럼 DB에만 존재하는 키가 있을 수 있다 — 이미 row가 있으면 갱신 대상이다
        String dbOnlyKey = "email.some.db.only.key";
        MessageTemplate existing = new MessageTemplate(dbOnlyKey, "이전 값");
        when(repository.findById(dbOnlyKey)).thenReturn(Optional.of(existing));

        messageTemplateService.update(dbOnlyKey, "새 값");

        assertThat(existing.getValue()).isEqualTo("새 값");
        verify(repository, never()).save(any(MessageTemplate.class));
    }
}
