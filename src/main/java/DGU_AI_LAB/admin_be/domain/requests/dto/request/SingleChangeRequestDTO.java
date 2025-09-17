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
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Schema(description = "단일 변경 요청 DTO")
public record SingleChangeRequestDTO(

        @Schema(description = "변경 타입", example = "VOLUME_SIZE")
        @NotNull(message = "변경 타입은 필수입니다.")
        ChangeType changeType,

        @Schema(description = "새로운 값", example = "100")
        @NotBlank(message = "새로운 값은 필수입니다.")
        String newValue,

        @Schema(description = "변경 요청 사유", example = "프로젝트 요구사항 변경으로 인한 용량 증설")
        @NotBlank(message = "변경 사유는 필수입니다.")
        String reason
) {

    /**
     * 기존 Request에서 oldValue를 추출하고 ChangeRequest 엔티티 생성
     */
    public static ChangeRequest toEntity(SingleChangeRequestDTO dto, Request originalRequest, User requestedBy, ObjectMapper objectMapper) {
        try {
            String oldValue = extractOldValue(originalRequest, dto.changeType(), objectMapper);

            return ChangeRequest.builder()
                    .request(originalRequest)
                    .changeType(dto.changeType())
                    .oldValue(oldValue)
                    .newValue(dto.newValue())
                    .reason(dto.reason())
                    .requestedBy(requestedBy)
                    .build();
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
                case VOLUME_SIZE -> objectMapper.writeValueAsString(originalRequest.getVolumeSizeGiB());
                case EXPIRES_AT -> objectMapper.writeValueAsString(originalRequest.getExpiresAt());
                case GROUP -> {
                    Set<Long> oldGroupIds = originalRequest.getRequestGroups().stream()
                            .map(requestGroup -> requestGroup.getGroup().getUbuntuGid())
                            .collect(Collectors.toSet());
                    yield objectMapper.writeValueAsString(oldGroupIds);
                }
                case RESOURCE_GROUP -> objectMapper.writeValueAsString(originalRequest.getResourceGroup().getRsgroupId());
                case CONTAINER_IMAGE -> objectMapper.writeValueAsString(originalRequest.getContainerImage().getImageId());
                case PORT -> throw new BusinessException(ErrorCode.UNSUPPORTED_CHANGE_TYPE);
            };
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
                case VOLUME_SIZE -> {
                    Long volumeSize = Long.parseLong(newValue.trim());
                    if (volumeSize <= 0) {
                        throw new BusinessException("볼륨 크기는 양수여야 합니다.", ErrorCode.INVALID_INPUT_VALUE);
                    }
                }
                case EXPIRES_AT -> {
                    LocalDateTime.parse(newValue.trim());
                }
                case GROUP -> {
                    ObjectMapper mapper = new ObjectMapper();
                    Set<Long> groupIds = mapper.readValue(newValue, mapper.getTypeFactory().constructCollectionType(Set.class, Long.class));
                    if (groupIds.isEmpty()) {
                        throw new BusinessException("그룹 ID 목록은 비어있을 수 없습니다.", ErrorCode.INVALID_INPUT_VALUE);
                    }
                }
                case RESOURCE_GROUP -> {
                    Integer resourceGroupId = Integer.parseInt(newValue.trim());
                    if (resourceGroupId <= 0) {
                        throw new BusinessException("리소스 그룹 ID는 양수여야 합니다.", ErrorCode.INVALID_INPUT_VALUE);
                    }
                }
                case CONTAINER_IMAGE -> {
                    Long imageId = Long.parseLong(newValue.trim());
                    if (imageId <= 0) {
                        throw new BusinessException("컨테이너 이미지 ID는 양수여야 합니다.", ErrorCode.INVALID_INPUT_VALUE);
                    }
                }
                case PORT -> throw new BusinessException("PORT 변경은 현재 지원되지 않습니다.", ErrorCode.UNSUPPORTED_CHANGE_TYPE);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("새로운 값의 형식이 올바르지 않습니다: " + e.getMessage(), ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}