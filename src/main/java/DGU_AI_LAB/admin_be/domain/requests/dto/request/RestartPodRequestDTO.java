package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "컨테이너 재시작 요청 DTO")
public record RestartPodRequestDTO(
        @Schema(description = "컨테이너 안에 설치한 패키지 등 변경분을 유지할지 (생략 시 true). false면 기본 이미지로 초기화", example = "true")
        Boolean keepChanges
) {
    public boolean keepsChanges() {
        return !Boolean.FALSE.equals(keepChanges);
    }
}
