package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "컨테이너 재시작 요청 DTO")
public record RestartPodRequestDTO(
        @Schema(description = "컨테이너 안에 설치한 패키지 등 변경분을 유지할지 (생략 시 true, imageId를 주면 false). false면 기본 이미지로 초기화", example = "true")
        Boolean keepChanges,
        @Schema(description = "관리자만: 이 이미지로 다시 만든다(생략하면 신청에 묶인 이미지). 기존 변경분은 이어 줄 수 없어 keepChanges를 true로 줄 수 없다", example = "3")
        Long imageId
) {
    public boolean keepsChanges() {
        return keepChanges == null ? imageId == null : keepChanges;
    }
}
