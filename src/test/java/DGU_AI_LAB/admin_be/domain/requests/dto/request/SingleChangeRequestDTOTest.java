package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SingleChangeRequestDTOTest {

    @Test
    @DisplayName("GROUP 타입에 빈 JSON 배열을 전달하면 BusinessException을 던진다")
    void createValidatedChangeRequest_group_emptyList_throws() {
        SingleChangeRequestDTO dto = new SingleChangeRequestDTO(ChangeType.GROUP, "[]", "reason");

        assertThatThrownBy(() ->
                SingleChangeRequestDTO.createValidatedChangeRequest(dto, null, null, null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("GROUP 타입에 잘못된 JSON을 전달하면 BusinessException을 던진다")
    void createValidatedChangeRequest_group_invalidJson_throws() {
        SingleChangeRequestDTO dto = new SingleChangeRequestDTO(ChangeType.GROUP, "not-json", "reason");

        assertThatThrownBy(() ->
                SingleChangeRequestDTO.createValidatedChangeRequest(dto, null, null, null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("PORT 타입에 잘못된 JSON을 전달하면 BusinessException을 던진다")
    void createValidatedChangeRequest_port_invalidJson_throws() {
        SingleChangeRequestDTO dto = new SingleChangeRequestDTO(ChangeType.PORT, "not-json", "reason");

        assertThatThrownBy(() ->
                SingleChangeRequestDTO.createValidatedChangeRequest(dto, null, null, null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("VOLUME_SIZE 타입에 음수를 전달하면 BusinessException을 던진다")
    void createValidatedChangeRequest_volumeSize_negative_throws() {
        SingleChangeRequestDTO dto = new SingleChangeRequestDTO(ChangeType.VOLUME_SIZE, "-1", "reason");

        assertThatThrownBy(() ->
                SingleChangeRequestDTO.createValidatedChangeRequest(dto, null, null, null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("EXPIRES_AT의 생 날짜 문자열은 JSON 인코딩되어 저장되고 승인 파서와 round-trip 된다 (#367)")
    void createValidatedChangeRequest_expiresAt_storesJsonEncodedValue() throws Exception {
        // 운영에서는 Spring 주입 ObjectMapper에 JavaTimeModule이 등록돼 있다
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        // 만료 일시는 미래여야 하므로 고정 날짜 대신 현재 기준 상대 시점을 쓴다.
        // 고정 날짜를 쓰면 그 날짜가 지나는 순간 테스트가 깨진다.
        LocalDateTime newExpiresAt = LocalDateTime.now().plusDays(14).withNano(0);
        Request originalRequest = Request.builder()
                .expiresAt(LocalDateTime.now().plusDays(1).withNano(0))
                .build();
        SingleChangeRequestDTO dto = new SingleChangeRequestDTO(
                ChangeType.EXPIRES_AT, newExpiresAt.toString(), "reason");

        ChangeRequest changeRequest = SingleChangeRequestDTO.createValidatedChangeRequest(
                dto, originalRequest, null, objectMapper, null);

        // MySQL json 컬럼 제약 — 저장 값은 따옴표 포함 유효 JSON이어야 한다
        assertThat(changeRequest.getNewValue()).isEqualTo("\"" + newExpiresAt + "\"");
        // 승인 로직(AdminRequestCommandService.EXPIRES_AT)과 동일한 파싱으로 round-trip
        LocalDateTime parsed = LocalDateTime.parse(objectMapper.readValue(changeRequest.getNewValue(), String.class));
        assertThat(parsed).isEqualTo(newExpiresAt);
    }

    @Test
    @DisplayName("EXPIRES_AT 이외 타입의 newValue는 그대로 저장된다")
    void createValidatedChangeRequest_volumeSize_keepsRawNewValue() {
        // 운영에서는 Spring 주입 ObjectMapper에 JavaTimeModule이 등록돼 있다
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Request originalRequest = Request.builder()
                .volumeSizeGiB(20L)
                .build();
        SingleChangeRequestDTO dto = new SingleChangeRequestDTO(ChangeType.VOLUME_SIZE, "100", "reason");

        ChangeRequest changeRequest = SingleChangeRequestDTO.createValidatedChangeRequest(
                dto, originalRequest, null, objectMapper, null);

        assertThat(changeRequest.getNewValue()).isEqualTo("100");
    }

    @Test
    @DisplayName("EXPIRES_AT에 과거 시점을 전달하면 INVALID_INPUT_VALUE로 거절한다")
    void createValidatedChangeRequest_expiresAt_past_throws() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Request originalRequest = Request.builder()
                .expiresAt(LocalDateTime.now().plusDays(1).withNano(0))
                .build();
        SingleChangeRequestDTO dto = new SingleChangeRequestDTO(
                ChangeType.EXPIRES_AT, LocalDateTime.now().minusDays(1).withNano(0).toString(), "reason");

        assertThatThrownBy(() -> SingleChangeRequestDTO.createValidatedChangeRequest(
                dto, originalRequest, null, objectMapper, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("EXPIRES_AT에 현재와 같은 시점을 전달하면 거절한다 (미래여야 함)")
    void createValidatedChangeRequest_expiresAt_notFuture_throws() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Request originalRequest = Request.builder()
                .expiresAt(LocalDateTime.now().plusDays(1).withNano(0))
                .build();
        // 이미 지나간 시점 — 파싱은 되지만 미래가 아니다
        SingleChangeRequestDTO dto = new SingleChangeRequestDTO(
                ChangeType.EXPIRES_AT, LocalDateTime.now().minusSeconds(1).withNano(0).toString(), "reason");

        assertThatThrownBy(() -> SingleChangeRequestDTO.createValidatedChangeRequest(
                dto, originalRequest, null, objectMapper, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("toEntity 내부에서 발생한 BusinessException은 원래 ErrorCode를 유지한다")
    void toEntity_propagatesBusinessExceptionErrorCode() throws Exception {
        LocalDateTime newExpiresAt = LocalDateTime.now().plusDays(7).withNano(0);
        Request originalRequest = Request.builder()
                .expiresAt(LocalDateTime.now().plusDays(1).withNano(0))
                .build();
        SingleChangeRequestDTO dto = new SingleChangeRequestDTO(
                ChangeType.EXPIRES_AT, newExpiresAt.toString(), "reason");

        // oldValue 추출은 성공시키고, newValue 인코딩 단계에서만 입력값 예외가 발생하도록 만든다
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(originalRequest.getExpiresAt())).thenReturn("\"old\"");
        when(objectMapper.writeValueAsString(newExpiresAt.toString()))
                .thenThrow(new BusinessException("입력값 문제", ErrorCode.INVALID_INPUT_VALUE));

        assertThatThrownBy(() ->
                SingleChangeRequestDTO.toEntity(dto, originalRequest, null, objectMapper, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }
}
