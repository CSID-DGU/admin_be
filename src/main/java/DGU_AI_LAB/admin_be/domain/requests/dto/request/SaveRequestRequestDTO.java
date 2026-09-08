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
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Builder
public record SaveRequestRequestDTO(
        @Schema(description = "자원 그룹 id", example = "1")
        @NotNull(message = "Resource group ID cannot be null")
        Integer resourceGroupId,

        @Schema(description = "이미지 id", example = "1")
        @NotNull(message = "Image ID cannot be null")
        Long imageId,

        @Schema(description = "Ubuntu 비밀번호", example = "strongPassword123!")
        @NotBlank(message = "Ubuntu Password cannot be blank")
        String ubuntuPassword,

        @Schema(description = "사용 목적", example = "딥러닝 모델 학습")
        @NotBlank(message = "Usage purpose cannot be blank")
        String usagePurpose,

        @Schema(description = "폼 응답", example = "{\"question\": \"answer\"}")
        Map<String, Object> formAnswers,

        @Schema(description = "서버 만료 일시", example = "2026-12-31T23:59:59")
        @NotNull(message = "Expires date cannot be null")
        @Future(message = "Expires date must be in the future")
        LocalDateTime expiresAt,
        @Schema(description = "Ubuntu GID 목록", example = "[1005, 1006]")
        Set<Long> ubuntuGids,

        @Schema(description = "포트 요청 목록")
        List<PortRequestDTO> portRequests
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
