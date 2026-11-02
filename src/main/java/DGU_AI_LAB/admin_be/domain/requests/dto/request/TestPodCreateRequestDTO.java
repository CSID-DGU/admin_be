package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "관리자 테스트 Pod 간편 생성 요청")
public record TestPodCreateRequestDTO(
        @Schema(description = "컨테이너 이미지 ID", example = "1")
        @NotNull(message = "이미지 ID는 필수입니다.")
        Long imageId,

        @Schema(description = "대상 리소스 그룹 ID. 비우면 모든 리소스 그룹")
        List<Integer> resourceGroupIds,

        @Schema(description = "대상 노드 이름. 비우면 선택한 리소스 그룹의 모든 노드", example = "[\"farm1\"]")
        List<String> nodeIds,

        @Schema(description = "추가로 열 내부 포트", example = "[3000]")
        @Size(max = 10, message = "추가 포트는 10개까지입니다.")
        List<@NotNull @Min(1) @Max(65535) Integer> internalPorts
) {
}
