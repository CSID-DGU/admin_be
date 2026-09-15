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

        // 화면(신청 마법사)도 8자 이상을 요구한다.
        @Schema(description = "Ubuntu 비밀번호", example = "strongPassword123!")
        @NotBlank(message = "우분투 비밀번호는 필수입니다.")
        @Size(min = 8, max = 128, message = "우분투 비밀번호는 8~128자여야 합니다.")
        String ubuntuPassword,

        // requests.usage_purpose가 1000자다.
        @Schema(description = "사용 목적", example = "딥러닝 모델 학습")
        @NotBlank(message = "사용 목적은 필수입니다.")
        @Size(max = 1000, message = "사용 목적은 1000자 이하여야 합니다.")
        String usagePurpose,

        @Schema(description = "폼 응답", example = "{\"question\": \"answer\"}")
        @Size(max = 50, message = "폼 응답 항목은 50개 이하여야 합니다.")
        Map<String, Object> formAnswers,

        @Schema(description = "서버 만료 일시", example = "2026-12-31T23:59:59")
        @NotNull(message = "만료 일시는 필수입니다.")
        @Future(message = "만료 일시는 미래여야 합니다.")
        LocalDateTime expiresAt,

        @Schema(description = "Ubuntu GID 목록", example = "[1005, 1006]")
        @Size(max = 20, message = "그룹은 20개 이하로 선택해야 합니다.")
        Set<@NotNull(message = "그룹 GID는 비어 있을 수 없습니다.") @Positive(message = "그룹 GID는 양수여야 합니다.") Long> ubuntuGids,

        // 목록 요소에 @Valid가 없으면 PortRequestDTO 안의 포트 범위 검사가 실행되지 않는다.
        @Schema(description = "포트 요청 목록")
        @Size(max = 10, message = "포트 요청은 10개 이하여야 합니다.")
        List<@NotNull(message = "포트 요청 항목은 비어 있을 수 없습니다.") @Valid PortRequestDTO> portRequests
) {
    /**
     * @param ubuntuUsername 신청자가 입력하는 값이 아니라 가입 시 정해진 User.ubuntuUsername을
     *                       그대로 복사해 넣는다 — 서비스에서 꺼내 넘긴다.
     */
    public Request toEntity(
            User user,
            ResourceGroup resourceGroup,
            ContainerImage image,
            String ubuntuUsername
    ) {
        String formAnswersJson;
        try {
            formAnswersJson = new ObjectMapper().writeValueAsString(formAnswers);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Request req = Request.builder()
                .user(user)
                .resourceGroup(resourceGroup)
                .containerImage(image)
                .ubuntuUsername(ubuntuUsername)
                .ubuntuPassword(ubuntuPassword)
                .usagePurpose(usagePurpose)
                .formAnswers(formAnswersJson)
                .expiresAt(expiresAt)
                .build();

        return req;
    }
}
