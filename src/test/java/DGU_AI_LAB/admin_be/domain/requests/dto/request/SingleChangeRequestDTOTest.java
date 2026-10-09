package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SingleChangeRequestDTOTest {

    /** 사유는 100자 이상이어야 받는다. */
    private static final String REASON = "가".repeat(100);

    @Test
    @DisplayName("GROUP 타입에 빈 JSON 배열을 전달하면 BusinessException을 던진다")
    void createValidatedChangeRequest_group_emptyList_throws() {
        SingleChangeRequestDTO dto = new SingleChangeRequestDTO(1L, ChangeType.GROUP, "[]", REASON);

        assertThatThrownBy(() ->
                SingleChangeRequestDTO.createValidatedChangeRequest(dto, null, null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("GROUP 타입에 gid 없는 그룹(아직 만들어지지 않은 새 그룹)이 섞이면 받을 때 거절한다 — 승인할 때에야 실패하지 않게")
    void createValidatedChangeRequest_group_pendingGroup_throws() {
        SingleChangeRequestDTO dto = new SingleChangeRequestDTO(1L, ChangeType.GROUP, "[1005,null]", REASON);

        assertThatThrownBy(() ->
                SingleChangeRequestDTO.createValidatedChangeRequest(dto, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("아직 만들어지지 않은 그룹");
    }

    @Test
    @DisplayName("GROUP 타입에 잘못된 JSON을 전달하면 BusinessException을 던진다")
    void createValidatedChangeRequest_group_invalidJson_throws() {
        SingleChangeRequestDTO dto = new SingleChangeRequestDTO(1L, ChangeType.GROUP, "not-json", REASON);

        assertThatThrownBy(() ->
                SingleChangeRequestDTO.createValidatedChangeRequest(dto, null, null, null))
                .isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @EnumSource(value = ChangeType.class, names = {"RESOURCE_GROUP", "CONTAINER_IMAGE"})
    @DisplayName("승인해도 떠 있는 Pod에 반영되지 않는 종류는 값이 올바라도 UNSUPPORTED_CHANGE_TYPE으로 거절한다")
    void createValidatedChangeRequest_rejectsTypesThatOnlyChangeDb(ChangeType type) {
        SingleChangeRequestDTO dto = new SingleChangeRequestDTO(1L, type, "2", REASON);

        assertThatThrownBy(() ->
                SingleChangeRequestDTO.createValidatedChangeRequest(dto, Request.builder().build(), null, new ObjectMapper()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNSUPPORTED_CHANGE_TYPE);
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
        SingleChangeRequestDTO dto = new SingleChangeRequestDTO(1L, 
                ChangeType.EXPIRES_AT, newExpiresAt.toString(), REASON);

        ChangeRequest changeRequest = SingleChangeRequestDTO.createValidatedChangeRequest(
                dto, originalRequest, null, objectMapper);

        // MySQL json 컬럼 제약 — 저장 값은 따옴표 포함 유효 JSON이어야 한다
        assertThat(changeRequest.getNewValue()).isEqualTo("\"" + newExpiresAt + "\"");
        // 승인 로직(AdminModificationCommandService.EXPIRES_AT)과 동일한 파싱으로 round-trip
        LocalDateTime parsed = LocalDateTime.parse(objectMapper.readValue(changeRequest.getNewValue(), String.class));
        assertThat(parsed).isEqualTo(newExpiresAt);
    }

    @Test
    @DisplayName("EXPIRES_AT 이외 타입의 newValue는 그대로 저장된다")
    void createValidatedChangeRequest_group_keepsRawNewValue() {
        // 운영에서는 Spring 주입 ObjectMapper에 JavaTimeModule이 등록돼 있다
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        // GROUP 타입의 oldValue는 이제 originalRequest.getUser().getUserGroups()를 읽으므로 user가 필요하다.
        Request originalRequest = Request.builder()
                .user(User.builder().build())
                .build();
        SingleChangeRequestDTO dto = new SingleChangeRequestDTO(1L, ChangeType.GROUP, "[1005,1006]", REASON);

        ChangeRequest changeRequest = SingleChangeRequestDTO.createValidatedChangeRequest(
                dto, originalRequest, null, objectMapper);

        assertThat(changeRequest.getNewValue()).isEqualTo("[1005,1006]");
    }

    @Test
    @DisplayName("EXPIRES_AT에 과거 시점을 전달하면 INVALID_INPUT_VALUE로 거절한다")
    void createValidatedChangeRequest_expiresAt_past_throws() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Request originalRequest = Request.builder()
                .expiresAt(LocalDateTime.now().plusDays(1).withNano(0))
                .build();
        SingleChangeRequestDTO dto = new SingleChangeRequestDTO(1L, 
                ChangeType.EXPIRES_AT, LocalDateTime.now().minusDays(1).withNano(0).toString(), REASON);

        assertThatThrownBy(() -> SingleChangeRequestDTO.createValidatedChangeRequest(
                dto, originalRequest, null, objectMapper))
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
        SingleChangeRequestDTO dto = new SingleChangeRequestDTO(1L, 
                ChangeType.EXPIRES_AT, LocalDateTime.now().minusSeconds(1).withNano(0).toString(), REASON);

        assertThatThrownBy(() -> SingleChangeRequestDTO.createValidatedChangeRequest(
                dto, originalRequest, null, objectMapper))
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
        SingleChangeRequestDTO dto = new SingleChangeRequestDTO(1L, 
                ChangeType.EXPIRES_AT, newExpiresAt.toString(), REASON);

        // oldValue 추출은 성공시키고, newValue 인코딩 단계에서만 입력값 예외가 발생하도록 만든다
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(originalRequest.getExpiresAt())).thenReturn("\"old\"");
        when(objectMapper.writeValueAsString(newExpiresAt.toString()))
                .thenThrow(new BusinessException("입력값 문제", ErrorCode.INVALID_INPUT_VALUE));

        assertThatThrownBy(() ->
                SingleChangeRequestDTO.toEntity(dto, originalRequest, null, List.of(), objectMapper))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    private static PortRequests port(int internalPort, String purpose) {
        return PortRequests.builder().internalPort(internalPort).usagePurpose(purpose).build();
    }

    @Test
    @DisplayName("PORT - 이전 값은 지금 달린 추가 포트이고(noVNC 제외), 새 값은 보낸 목록 그대로 저장한다")
    void createValidatedChangeRequest_port_storesCurrentAndWantedPorts() {
        SingleChangeRequestDTO dto = new SingleChangeRequestDTO(1L, 
                ChangeType.PORT, "[{\"internalPort\":3000,\"usagePurpose\":\"web\"}]", REASON);

        ChangeRequest changeRequest = SingleChangeRequestDTO.createValidatedChangeRequest(
                dto, Request.builder().build(), null, List.of(port(5000, "api"), port(6080, "novnc")), new ObjectMapper());

        assertThat(changeRequest.getChangeType()).isEqualTo(ChangeType.PORT);
        assertThat(changeRequest.getOldValue()).isEqualTo("[{\"internalPort\":5000,\"usagePurpose\":\"api\"}]");
        assertThat(changeRequest.getNewValue()).isEqualTo("[{\"internalPort\":3000,\"usagePurpose\":\"web\"}]");
    }

    @Test
    @DisplayName("PORT - 빈 목록은 추가 포트를 모두 빼는 요청이다")
    void createValidatedChangeRequest_port_emptyListRemovesAll() {
        SingleChangeRequestDTO dto = new SingleChangeRequestDTO(1L, ChangeType.PORT, "[]", REASON);

        ChangeRequest changeRequest = SingleChangeRequestDTO.createValidatedChangeRequest(
                dto, Request.builder().build(), null, List.of(port(5000, "api")), new ObjectMapper());

        assertThat(changeRequest.getNewValue()).isEqualTo("[]");
    }

    @Test
    @DisplayName("PORT - 지금 열려 있는 포트와 같으면 받지 않는다")
    void createValidatedChangeRequest_port_unchanged_throws() {
        SingleChangeRequestDTO dto = new SingleChangeRequestDTO(1L, 
                ChangeType.PORT, "[{\"internalPort\":5000,\"usagePurpose\":\"api\"}]", REASON);

        assertThatThrownBy(() -> SingleChangeRequestDTO.createValidatedChangeRequest(
                dto, Request.builder().build(), null, List.of(port(5000, "api"), port(6080, "novnc")), new ObjectMapper()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("PORT - 기본 포트는 바꿀 수 없다")
    void createValidatedChangeRequest_port_protectedPort_throws() {
        SingleChangeRequestDTO dto = new SingleChangeRequestDTO(1L, 
                ChangeType.PORT, "[{\"internalPort\":22,\"usagePurpose\":\"ssh2\"}]", REASON);

        assertThatThrownBy(() -> SingleChangeRequestDTO.createValidatedChangeRequest(
                dto, Request.builder().build(), null, List.of(), new ObjectMapper()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }
}
