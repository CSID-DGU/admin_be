package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import DGU_AI_LAB.admin_be.domain.approval.entity.Approval;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import DGU_AI_LAB.admin_be.domain.users.entity.Role;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

public record RequestApproveDTO(
        @NotBlank String name,
        @NotBlank String email,
        @NotNull Long resource_group_id,
        @NotNull String username,
        @NotNull String serverName,
        @NotNull LocalDateTime validDate,
        @NotNull Long volumeSize
) {
    public static final Role DEFAULT_ROLE = Role.USER;

    public User toUserEntity(PasswordEncoder passwordEncoder, String defaultPassword) {
        return User.builder()
                .name(name)
                .email(email)
                //.webId(username)
                .password(passwordEncoder.encode(defaultPassword))
                .role(DEFAULT_ROLE)
                .build();
    }

    public Approval toApprovalEntity(PasswordEncoder passwordEncoder, String defaultPassword, ResourceGroup resourceGroup, User user) {
        return Approval.builder()
                .user(user)
                .resourceGroup(resourceGroup)
                .password(passwordEncoder.encode(defaultPassword))
                .validDate(validDate)
                .volumeSize(volumeSize.intValue()) // TODO: 여기 수정 필요 (Integer, Long)
                .build();
    }
}
