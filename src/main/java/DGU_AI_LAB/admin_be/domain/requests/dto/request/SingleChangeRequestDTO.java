package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Schema(description = "단일 변경 요청 DTO")
public record SingleChangeRequestDTO(

        @Schema(description = "변경 타입", example = "EXPIRES_AT", allowableValues = {"EXPIRES_AT", "GROUP"})
        @NotNull(message = "변경 타입은 필수입니다.")
        ChangeType changeType,

        @Schema(description = "새로운 값", example = "100")
        @NotBlank(message = "새로운 값은 필수입니다.")
        @Size(max = 1000, message = "새로운 값은 1000자 이하여야 합니다.")
        String newValue,

        // change_request.reason이 1000자다.
        @Schema(description = "변경 요청 사유", example = "프로젝트 요구사항 변경으로 인한 용량 증설")
        @NotBlank(message = "변경 사유는 필수입니다.")
        @Size(max = 1000, message = "변경 사유는 1000자 이하여야 합니다.")
        String reason
) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 승인 시 실제 계정·컨테이너까지 반영되는 종류만 받는다. RESOURCE_GROUP·CONTAINER_IMAGE·PORT는
     * 승인해도 DB 값만 바뀌고 떠 있는 Pod는 그대로라 DB와 실제가 어긋난다 — 받지 않는다.
     */
    public static final Set<ChangeType> SUPPORTED_TYPES = EnumSet.of(ChangeType.EXPIRES_AT, ChangeType.GROUP);

    /**
     * 기존 Request에서 oldValue를 추출하고 ChangeRequest 엔티티 생성
     */
    public static ChangeRequest toEntity(SingleChangeRequestDTO dto, Request originalRequest, User requestedBy, ObjectMapper objectMapper) {
        try {
            String oldValue = extractOldValue(originalRequest, dto.changeType(), objectMapper);

            // new_value는 MySQL json 컬럼이고 승인 로직은 readValue(String.class)로 읽는다.
            // EXPIRES_AT의 생 날짜 문자열은 유효한 JSON이 아니므로 저장 직전에 JSON 인코딩한다. (#367)
            String storedNewValue = dto.changeType() == ChangeType.EXPIRES_AT
                    ? objectMapper.writeValueAsString(dto.newValue().trim())
                    : dto.newValue();

            return ChangeRequest.builder()
                    .request(originalRequest)
                    .changeType(dto.changeType())
                    .oldValue(oldValue)
                    .newValue(storedNewValue)
                    .reason(dto.reason())
                    .requestedBy(requestedBy)
                    .build();
        } catch (BusinessException e) {
            // 입력값 문제로 이미 판정된 예외는 그대로 올려보낸다. 여기서 삼키면 400이 500으로 나간다.
            throw e;
        } catch (Exception e) {
            log.error("Failed to create ChangeRequest entity: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 변경 타입에 따라 기존 값을 추출
     */
    private static String extractOldValue(Request originalRequest, ChangeType changeType, ObjectMapper objectMapper) {
        try {
            return switch (changeType) {
                case EXPIRES_AT -> objectMapper.writeValueAsString(originalRequest.getExpiresAt());
                case GROUP -> {
                    // 계정(User) 단위 현재 그룹이 old value다 — 이 컨테이너만의 그룹이 아니라
                    // 같은 계정이 실제로 AD에 갖고 있는 그룹 전체를 기준으로 비교해야 한다.
                    Set<Long> oldGroupIds = originalRequest.getUser().getUserGroups().stream()
                            .map(userGroup -> userGroup.getGroup().getUbuntuGid())
                            .collect(Collectors.toSet());
                    yield objectMapper.writeValueAsString(oldGroupIds);
                }
                default -> throw new BusinessException(ErrorCode.UNSUPPORTED_CHANGE_TYPE);
            };
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to extract old value for change type {}: {}", changeType, e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * DTO 내부에서 자체적으로 유효성을 검증하고 데이터베이스 존재 여부까지 확인하는 팩토리 메서드
     */
    public static ChangeRequest createValidatedChangeRequest(SingleChangeRequestDTO dto, Request originalRequest, User requestedBy, ObjectMapper objectMapper) {
        dto.validateAndCheckExistence(originalRequest);
        return toEntity(dto, originalRequest, requestedBy, objectMapper);
    }

    /**
     * 유효성 검증 및 데이터베이스 존재 여부 확인
     */
    private void validateAndCheckExistence(Request originalRequest) {
        validateBasicFormat();
        validateValueFormat();
        // 필요한 경우 데이터베이스 존재 여부 검증 로직 추가 가능
    }

    /**
     * 기본 형식 유효성 검증
     */
    private void validateBasicFormat() {
        if (changeType == null) {
            throw new BusinessException("변경 타입은 필수입니다.", ErrorCode.INVALID_INPUT_VALUE);
        }

        if (!SUPPORTED_TYPES.contains(changeType)) {
            throw new BusinessException("기간 연장(EXPIRES_AT)과 그룹 추가(GROUP)만 변경 요청할 수 있습니다.", ErrorCode.UNSUPPORTED_CHANGE_TYPE);
        }

        if (newValue == null || newValue.trim().isEmpty()) {
            throw new BusinessException("새로운 값은 필수입니다.", ErrorCode.INVALID_INPUT_VALUE);
        }

        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException("변경 사유는 필수입니다.", ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /**
     * 변경 타입에 따른 값 형식 검증
     */
    private void validateValueFormat() {
        try {
            switch (changeType) {
                case EXPIRES_AT -> {
                    // SaveRequestRequestDTO.expiresAt의 @Future 계약과 동일한 기준을 적용한다.
                    LocalDateTime parsed = LocalDateTime.parse(newValue.trim());
                    if (!parsed.isAfter(LocalDateTime.now())) {
                        throw new BusinessException("만료 일시는 미래여야 합니다.", ErrorCode.INVALID_INPUT_VALUE);
                    }
                }
                case GROUP -> {
                    Set<Long> groupIds = OBJECT_MAPPER.readValue(newValue, OBJECT_MAPPER.getTypeFactory().constructCollectionType(Set.class, Long.class));
                    if (groupIds.isEmpty()) {
                        throw new BusinessException("그룹 ID 목록은 비어있을 수 없습니다.", ErrorCode.INVALID_INPUT_VALUE);
                    }
                }
                default -> throw new BusinessException(ErrorCode.UNSUPPORTED_CHANGE_TYPE);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("새로운 값의 형식이 올바르지 않습니다: " + e.getMessage(), ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
