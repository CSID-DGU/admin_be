package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Set;

@Schema(description = "사용자용 서버 설정 변경 요청 DTO")
public record ModifyRequestDTO(

        // change_request.reason이 1000자다.
        @Schema(description = "변경 요청 사유 (필수)", example = "프로젝트 요구사항 변경으로 인한 용량 증설")
        @NotBlank(message = "변경 사유는 필수입니다.")
        @Size(max = 1000, message = "변경 사유는 1000자 이하여야 합니다.")
        String reason,

        @Schema(description = "요청하는 새로운 만료 기한", example = "2026-09-02T10:00:00")
        @Future(message = "새 만료 일시는 미래여야 합니다.")
        LocalDateTime requestedExpiresAt,

        @Schema(description = "요청하는 새로운 그룹 ID 목록", example = "[1001, 1002]")
        @Size(max = 20, message = "그룹은 20개 이하로 선택해야 합니다.")
        Set<@NotNull(message = "그룹 ID는 비어 있을 수 없습니다.") @Positive(message = "그룹 ID는 양수여야 합니다.") Long> requestedGroupIds,

        @Schema(description = "요청하는 새로운 리소스 그룹 ID", example = "2")
        @Positive(message = "리소스 그룹 ID는 양수여야 합니다.")
        Integer requestedResourceGroupId,

        @Schema(description = "요청하는 새로운 도커 이미지 ID", example = "3")
        @Positive(message = "컨테이너 이미지 ID는 양수여야 합니다.")
        Long requestedContainerImageId
) {

    /**
     * 변경 항목이 하나도 없으면 서비스는 아무 변경 요청도 만들지 않고 성공으로 끝난다. 사용자는 요청이 접수된 줄
     * 알게 되므로 요청 경계에서 거절한다. 빈 그룹 목록은 서비스가 무시하므로 변경 없음으로 본다.
     */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "변경할 항목을 하나 이상 선택해야 합니다.")
    public boolean isAnyChangeRequested() {
        return requestedExpiresAt != null
                || (requestedGroupIds != null && !requestedGroupIds.isEmpty())
                || requestedResourceGroupId != null
                || requestedContainerImageId != null;
    }
}
