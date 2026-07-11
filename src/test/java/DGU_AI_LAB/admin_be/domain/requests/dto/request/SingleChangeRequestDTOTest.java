package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
