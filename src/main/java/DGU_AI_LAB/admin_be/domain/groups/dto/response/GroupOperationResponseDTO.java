package DGU_AI_LAB.admin_be.domain.groups.dto.response;

import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.entity.GroupOperation;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공용 그룹 작업(생성·멤버 추가·제거)의 진행 상태")
public record GroupOperationResponseDTO(
        @Schema(description = "작업 번호. 리눅스 계정이 없어 작업 없이 바로 끝난 제거는 null", example = "7")
        Long operationId,
        @Schema(description = "CREATE / ADD / REMOVE", example = "CREATE")
        String kind,
        @Schema(description = "PROCESSING(반영 중) / APPLIED(끝남) / FAILED(실패 — 다시 요청하면 이어서 끝난다)", example = "PROCESSING")
        String status,
        @Schema(description = "그룹명. 추가 작업은 null", example = "developers")
        String groupName,
        @Schema(description = "실패한 작업의 오류 코드", example = "AD_GROUP_CREATE_FAILED")
        String errorCode,
        @Schema(description = "생성이 끝났을 때 만들어진 그룹")
        GroupResponseDTO group
) {
    public static GroupOperationResponseDTO of(GroupOperation operation, Group createdGroup) {
        return new GroupOperationResponseDTO(
                operation.getGroupOperationId(),
                operation.getKind().name(),
                operation.getStatus().name(),
                operation.getGroupName(),
                operation.getErrorCode(),
                createdGroup == null ? null : GroupResponseDTO.fromEntity(createdGroup));
    }

    /** 리눅스 계정이 없어 DB 만 고치고 끝낸 제거. */
    public static GroupOperationResponseDTO appliedWithoutJob(String kind, String groupName) {
        return new GroupOperationResponseDTO(null, kind, "APPLIED", groupName, null, null);
    }
}
