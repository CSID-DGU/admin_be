package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import DGU_AI_LAB.admin_be.domain.portRequests.dto.PortChangeValue;
import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Schema(description = "단일 변경 요청 DTO")
public record SingleChangeRequestDTO(

        @Schema(description = "변경 대상 신청 ID", example = "42")
        @NotNull(message = "변경 대상 신청은 필수입니다.")
        Long requestId,

        @Schema(description = "변경 타입", example = "EXPIRES_AT", allowableValues = {"EXPIRES_AT", "GROUP", "PORT"})
        @NotNull(message = "변경 타입은 필수입니다.")
        ChangeType changeType,

        @Schema(description = "새로운 값", example = "100")
        @NotBlank(message = "새로운 값은 필수입니다.")
        @Size(max = 1000, message = "새로운 값은 1000자 이하여야 합니다.")
        String newValue,

        // 승인자가 이 글만 보고 판단하므로 100자 이상을 요구한다. 상한은 change_request.reason 컬럼(1000자)이다.
        @Schema(description = "변경 요청 사유 (100~1000자)", example = "졸업 프로젝트 최종 발표가 12월 중순으로 미뤄져 모델 학습을 그때까지 이어 가야 합니다. 지금까지 학습한 체크포인트로 추가 실험 세 가지(데이터 증강, 학습률 조정, 앙상블)를 돌릴 예정이고 한 번에 6시간쯤 걸립니다.")
        @NotBlank(message = "변경 사유는 필수입니다.")
        @Size(min = REASON_MIN_LENGTH, max = 1000, message = "변경 사유는 100자 이상 1000자 이하로 적어 주세요.")
        String reason
) {

    static final int REASON_MIN_LENGTH = 100;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 승인 시 실제 계정·컨테이너까지 반영되는 종류만 받는다. RESOURCE_GROUP·CONTAINER_IMAGE는
     * 승인해도 DB 값만 바뀌고 떠 있는 Pod는 그대로라 DB와 실제가 어긋난다 — 받지 않는다.
     */
    public static final Set<ChangeType> SUPPORTED_TYPES =
            EnumSet.of(ChangeType.EXPIRES_AT, ChangeType.GROUP, ChangeType.PORT);

    /**
     * 기존 Request에서 oldValue를 추출하고 ChangeRequest 엔티티 생성
     */
    public static ChangeRequest toEntity(SingleChangeRequestDTO dto, Request originalRequest, User requestedBy,
                                         List<PortRequests> currentPorts, ObjectMapper objectMapper) {
        try {
            String oldValue = extractOldValue(originalRequest, dto.changeType(), currentPorts, objectMapper);

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
                    .reason(dto.reason().trim())
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
    private static String extractOldValue(Request originalRequest, ChangeType changeType, List<PortRequests> currentPorts,
                                          ObjectMapper objectMapper) {
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
                case PORT -> PortChangeValue.currentValue(currentPorts, objectMapper);
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
        return createValidatedChangeRequest(dto, originalRequest, requestedBy, List.of(), objectMapper);
    }

    /** @param currentPorts 원본 신청에 지금 달린 추가 포트. PORT 변경 요청의 이전 값과 "달라진 것이 있는지" 판정에 쓴다 */
    public static ChangeRequest createValidatedChangeRequest(SingleChangeRequestDTO dto, Request originalRequest, User requestedBy,
                                                             List<PortRequests> currentPorts, ObjectMapper objectMapper) {
        dto.validateAndCheckExistence(originalRequest);
        if (dto.changeType() == ChangeType.PORT) {
            dto.requirePortsChanged(currentPorts);
        }
        return toEntity(dto, originalRequest, requestedBy, currentPorts, objectMapper);
    }

    /** 지금과 같은 포트 구성은 받지 않는다 — 승인해도 바뀌는 것이 없다. */
    private void requirePortsChanged(List<PortRequests> currentPorts) {
        Set<Integer> current = currentPorts.stream()
                .map(PortRequests::getInternalPort)
                .filter(port -> !PortChangeValue.isProtected(port))
                .collect(Collectors.toSet());
        if (current.equals(PortChangeValue.numbers(PortChangeValue.validated(newValue, OBJECT_MAPPER)))) {
            throw new BusinessException("지금 열려 있는 추가 포트와 같습니다.", ErrorCode.INVALID_INPUT_VALUE);
        }
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
            throw new BusinessException("여기서는 기간 연장(EXPIRES_AT), 그룹 추가(GROUP), 추가 포트 변경(PORT)만 요청할 수 있습니다.", ErrorCode.UNSUPPORTED_CHANGE_TYPE);
        }

        if (newValue == null || newValue.trim().isEmpty()) {
            throw new BusinessException("새로운 값은 필수입니다.", ErrorCode.INVALID_INPUT_VALUE);
        }

        // 앞뒤 공백으로 글자 수만 채운 사유를 받지 않는다.
        if (reason == null || reason.trim().length() < REASON_MIN_LENGTH) {
            throw new BusinessException("변경 사유는 100자 이상 1000자 이하로 적어 주세요.", ErrorCode.INVALID_INPUT_VALUE);
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
                    // 아직 만들어지지 않은 새 그룹(gid 없음)은 여기서 고를 수 없다 — 새 신청으로만 만들어진다.
                    // 받아 두면 관리자가 승인할 때에야 실패한다.
                    if (groupIds.contains(null)) {
                        throw new BusinessException("아직 만들어지지 않은 그룹은 추가할 수 없습니다.", ErrorCode.INVALID_INPUT_VALUE);
                    }
                }
                case PORT -> PortChangeValue.validated(newValue, OBJECT_MAPPER);
                default -> throw new BusinessException(ErrorCode.UNSUPPORTED_CHANGE_TYPE);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("새로운 값의 형식이 올바르지 않습니다: " + e.getMessage(), ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
