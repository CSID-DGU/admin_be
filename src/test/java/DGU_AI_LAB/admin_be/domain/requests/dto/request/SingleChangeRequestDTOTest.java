package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        Request originalRequest = Request.builder()
                .expiresAt(LocalDateTime.of(2026, 7, 14, 23, 59, 59))
                .build();
        SingleChangeRequestDTO dto = new SingleChangeRequestDTO(ChangeType.EXPIRES_AT, "2026-07-28T23:59:59", "reason");

        ChangeRequest changeRequest = SingleChangeRequestDTO.createValidatedChangeRequest(
                dto, originalRequest, null, objectMapper, null);

        // MySQL json 컬럼 제약 — 저장 값은 따옴표 포함 유효 JSON이어야 한다
        assertThat(changeRequest.getNewValue()).isEqualTo("\"2026-07-28T23:59:59\"");
        // 승인 로직(AdminRequestCommandService.EXPIRES_AT)과 동일한 파싱으로 round-trip
        LocalDateTime parsed = LocalDateTime.parse(objectMapper.readValue(changeRequest.getNewValue(), String.class));
        assertThat(parsed).isEqualTo(LocalDateTime.of(2026, 7, 28, 23, 59, 59));
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
}
