package DGU_AI_LAB.admin_be.domain.users.entity;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

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

    /**
    ubuntu username은 Approval에서 관리한다.
     따라서 이 name은 사용자의 실제 이름(김XX)을 저장한다.
     */
    // 사용자 실제 이름
    @Column(name = "name", nullable = false)
    private String name;

//    // 웹 아이디
//    @Column(name = "webId", nullable = false)
//    private String webId;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    /**
     * Role은 기본적으로 USER로만 생성되며, ADMIN으로 변경하기 위해서는 서버실에 방문해야 한다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    @Builder.Default
    private Role role = Role.USER;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @OneToMany(mappedBy = "user")
    private List<Request> requests;

    public void updateUserInfo(String password, Boolean isActive) {
        this.password = password;
        this.isActive = isActive;
    }

    /**
     * 한 명의 유저가 여러 개의 키를 사용할 수 있음.
     * 여러 PC에서 접속하면 그만큼 키의 수가 늘어남.
     */
    @OneToMany(mappedBy = "user")
    private List<UserKey> userKeys;


}