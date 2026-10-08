package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Builder
public record SaveRequestRequestDTO(
        @Schema(description = "자원 그룹 id", example = "1")
        @NotNull(message = "리소스 그룹 ID는 필수입니다.")
        @Positive(message = "리소스 그룹 ID는 양수여야 합니다.")
        Integer resourceGroupId,

        @Schema(description = "이미지 id", example = "1")
        @NotNull(message = "이미지 ID는 필수입니다.")
        @Positive(message = "이미지 ID는 양수여야 합니다.")
        Long imageId,

        // 승인자가 이 글만 보고 판단하므로 200자 이상을 요구한다. 상한은 requests.usage_purpose 컬럼(1000자)이다.
        @Schema(description = "사용 목적 (200~1000자)", example = "졸업 프로젝트로 PyTorch를 사용해 의료 영상(흉부 X-ray) 분류 모델을 학습하려고 합니다. 데이터는 약 2만 장이고, 한 번 학습에 GPU 1장으로 6시간 정도 걸릴 것으로 예상합니다. 일주일에 2~3번 학습할 예정이며, 12월 중순 최종 발표 전까지 사용하려고 합니다. 학습한 모델과 실험 기록은 홈 디렉터리에 저장하고, 결과 확인에는 Jupyter와 TensorBoard를 쓸 예정입니다.")
        @NotBlank(message = "사용 목적은 필수입니다.")
        @Size(min = 200, max = 1000, message = "사용 목적은 200자 이상 1000자 이하로 적어 주세요.")
        String usagePurpose,

        @Schema(description = "폼 응답", example = "{\"question\": \"answer\"}")
        @Size(max = 50, message = "폼 응답 항목은 50개 이하여야 합니다.")
        Map<String, Object> formAnswers,

        @Schema(description = "서버 만료 일시", example = "2026-12-31T23:59:59")
        @NotNull(message = "만료 일시는 필수입니다.")
        @Future(message = "만료 일시는 미래여야 합니다.")
        LocalDateTime expiresAt,

        // 예전 화면이 보내는 값. 승인 대기 그룹(gid 없음)은 담을 수 없어 groupIds로 바꿨다. 둘 중 하나만 보낸다.
        @Schema(description = "Ubuntu GID 목록 (예전 방식 — groupIds를 쓴다)", example = "[1005, 1006]", deprecated = true)
        @Size(max = 20, message = "그룹은 20개 이하로 선택해야 합니다.")
        Set<@NotNull(message = "그룹 GID는 비어 있을 수 없습니다.") @Positive(message = "그룹 GID는 양수여야 합니다.") Long> ubuntuGids,

        // 목록 요소에 @Valid가 없으면 PortRequestDTO 안의 포트 범위 검사가 실행되지 않는다.
        @Schema(description = "포트 요청 목록")
        @Size(max = 10, message = "포트 요청은 10개 이하여야 합니다.")
        List<@NotNull(message = "포트 요청 항목은 비어 있을 수 없습니다.") @Valid PortRequestDTO> portRequests,

        @Schema(description = "noVNC GUI 활성화 여부", example = "false")
        Boolean enableVnc,

        @Schema(description = "고른 공유 그룹의 그룹 ID 목록(GET /api/groups의 groupId). 아직 만들어지지 않은 새 그룹도 고를 수 있다", example = "[3, 4]")
        @Size(max = 20, message = "그룹은 20개 이하로 선택해야 합니다.")
        Set<@NotNull(message = "그룹 ID는 비어 있을 수 없습니다.") @Positive(message = "그룹 ID는 양수여야 합니다.") Long> groupIds
) {
    static final int MAX_FORM_ANSWERS_JSON_LENGTH = 10_000;

    public Request toEntity(
            User user,
            ResourceGroup resourceGroup,
            ContainerImage image
    ) {
        String formAnswersJson;
        try {
            formAnswersJson = new ObjectMapper().writeValueAsString(formAnswers);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        // 항목 수(@Size)만으로는 값 하나의 길이를 막지 못한다. 신청마다 DB에 그대로 쌓이므로 전체 크기로 제한한다.
        if (formAnswersJson.length() > MAX_FORM_ANSWERS_JSON_LENGTH) {
            throw new BusinessException("폼 응답이 너무 깁니다.", ErrorCode.INVALID_INPUT_VALUE);
        }

        Request req = Request.builder()
                .user(user)
                .resourceGroup(resourceGroup)
                .containerImage(image)
                .usagePurpose(usagePurpose)
                .formAnswers(formAnswersJson)
                .expiresAt(expiresAt)
                .enableVnc(enableVnc != null && enableVnc)
                .build();

        return req;
    }
}
