package DGU_AI_LAB.admin_be.domain.approval.entity;

import DGU_AI_LAB.admin_be.domain.image.entity.Image;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.UsedId;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Table(name = "approval")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
public class Approval extends BaseTimeEntity{
    @Id
    @GeneratedValue
    private Long approvalId;

    @Column(nullable = false)
    private Integer volumeSize; // Gi

    @Column(name = "validDate")
    private LocalDateTime validDate;

    @Column(name = "username", nullable = false, unique = true)
    private String username; // 우분투 계정 username

    @Column(name = "password", nullable = false)
    private String password; // 컨테이너 접속 비밀번호

    @Column(name = "server_name", nullable = false)
    @Enumerated(EnumType.STRING)
    private ServerName serverName; // 서버명, ex. FARM1, LAB4...

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "uid", referencedColumnName = "uid", nullable = false)
    private UsedId usedId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "resource_group_id")
    private ResourceGroup resourceGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_id", nullable = false)
    private Image image;
}

