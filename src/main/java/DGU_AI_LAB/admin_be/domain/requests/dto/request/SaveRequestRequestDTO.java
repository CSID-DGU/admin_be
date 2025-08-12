package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.usedIds.entity.UsedId;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Builder
public record SaveRequestRequestDTO(
        Integer resourceGroupId,
        Long imageId,
        String ubuntuUsername,
        String ubuntuPassword,
        Long ubuntuUid,
        Long volumeSizeGiB,
        String usagePurpose,
        Map<String, Object> formAnswers,
        LocalDateTime expiresAt,
        Set<Long> ubuntuGids
) {
    public Request toEntity(
            User user,
            ResourceGroup resourceGroup,
            ContainerImage image,
            UsedId ubuntuUid,
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
                .ubuntuUid(ubuntuUid)
                .ubuntuUsername(ubuntuUsername)
                .ubuntuPassword(ubuntuPassword)
                .volumeSizeGiB(volumeSizeGiB)
                .usagePurpose(usagePurpose)
                .formAnswers(formAnswersJson)
                .expiresAt(expiresAt)
                .status(Status.PENDING)
                .build();

        // 2) 그룹 연결은 addGroup()으로 — 중간 엔티티(request_groups) 생성
        if (groups != null && !groups.isEmpty()) {
            groups.forEach(req::addGroup);
        }

        return req;
    }
}
