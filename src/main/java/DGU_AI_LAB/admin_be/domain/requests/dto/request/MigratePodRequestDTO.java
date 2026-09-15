package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Pod 마이그레이션 트리거 요청 DTO")
public record MigratePodRequestDTO(

        @Schema(description = "마이그레이션 후보 노드 목록 (현재 노드도 포함해야 함)", example = "[\"farm1\", \"farm2\"]")
        @NotEmpty(message = "후보 노드 목록은 필수로 입력해야 합니다.")
        @Size(max = 50, message = "후보 노드는 50개 이하여야 합니다.")
        List<@NotBlank(message = "후보 노드 이름은 비어 있을 수 없습니다.") String> nodes,

        @Schema(description = "마이그레이션을 실행할 최소 개선 비율 (생략 시 config-server 기본값 사용, force=true면 무시됨)", example = "0.2")
        @DecimalMin(value = "0.0", message = "최소 개선 비율은 0~1 사이여야 합니다.")
        @DecimalMax(value = "1.0", message = "최소 개선 비율은 0~1 사이여야 합니다.")
        Double minImprovementRatio,

        @Schema(description = "true면 개선 비율과 무관하게 마이그레이션을 강제 실행", example = "false")
        Boolean force
) {}
