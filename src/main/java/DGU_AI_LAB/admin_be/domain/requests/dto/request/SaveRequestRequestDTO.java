package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Builder
public record SaveRequestRequestDTO(
        @Schema(description = "자원 그룹 id", example = "1")
        Integer resourceGroupId,

        @Schema(description = "이미지 id", example = "1")
        Long imageId,

        String ubuntuUsername,
        String ubuntuPassword,

        @Schema(description = "볼륨 사이즈", example = "20")
        Long volumeSizeGiB,

        String usagePurpose,

        @Schema(description = "폼 응답", example = "{\"question\": \"answer\"}")
        Map<String, Object> formAnswers,

        LocalDateTime expiresAt,
        Set<Long> ubuntuGids
) {
    public Request toEntity(
            User user,
            ResourceGroup resourceGroup,
            ContainerImage image,
            Set<Group> groups,
            String ubuntuPassword
    ) {
        String formAnswersJson;
        try {
            formAnswersJson = new ObjectMapper().writeValueAsString(formAnswers);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 1) 본체 먼저 생성
        Request req = Request.builder()
                .user(user)
                .resourceGroup(resourceGroup)
                .containerImage(image)
                .ubuntuUsername(ubuntuUsername)
                .ubuntuPassword(ubuntuPassword)
                .volumeSizeGiB(volumeSizeGiB)
                .usagePurpose(usagePurpose)
                .formAnswers(formAnswersJson)
                .expiresAt(expiresAt)
                .status(Status.PENDING)
                .build();

        return req;
    }
}
