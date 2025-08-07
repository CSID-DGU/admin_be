package DGU_AI_LAB.admin_be.domain.users.entity;

import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "ubuntu_uid")
    private Long ubuntuUid;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.USER;

    @Column(name = "student_id", nullable = false, length = 100)
    private String studentId;

    @Column(name = "phone", nullable = false, length = 100)
    private String phone;

    @Column(name = "department", nullable = false, length = 100)
    private String department;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    public void updateUserInfo(String password, Boolean isActive) {
        this.password = password;
        this.isActive = isActive;
    }

    public void updateUbuntuUid(Long ubuntuUid) {
        this.ubuntuUid = ubuntuUid;
    }

    public void updateUbuntuGroup(Group group) {
        this.ubuntuGroup = group;
    }

    public void updateUnixInfo(Long ubuntuUid, Group ubuntuGroup) {
        this.ubuntuUid = ubuntuUid;
        this.ubuntuGroup = ubuntuGroup;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ubuntu_gid")
    private Group ubuntuGroup;



}
