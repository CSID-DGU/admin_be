package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "Pod 마이그레이션 트리거 요청 DTO")
public record MigratePodRequestDTO(

        @Schema(description = "마이그레이션 후보 노드 목록 (현재 노드도 포함해야 함)", example = "[\"farm1\", \"farm2\"]")
        @NotEmpty(message = "후보 노드 목록은 필수로 입력해야 합니다.")
        List<String> nodes,

        @Schema(description = "마이그레이션을 실행할 최소 개선 비율 (생략 시 config-server 기본값 사용)", example = "0.2")
        Double minImprovementRatio
) {}
