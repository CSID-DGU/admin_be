package DGU_AI_LAB.admin_be.domain.users.entity;

import DGU_AI_LAB.admin_be.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "email", callSuper = false)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "email", nullable = false, length = 100, unique = true)
    private String email;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role = Role.USER;

    @Column(name = "student_id", nullable = false, length = 100)
    private String studentId;

    @Column(name = "phone", nullable = false, length = 100)
    private String phone;

    @Column(name = "department", nullable = false, length = 100)
    private String department;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Builder
    public User(String email, String password, String name, String studentId, String phone, String department) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.studentId = studentId;
        this.phone = phone;
        this.department = department;
    }

    // ===== 비즈니스 메서드 =====

    public void updateUserInfo(String encodedPassword, Boolean isActive) {
        if (encodedPassword != null) this.password = encodedPassword;
        if (isActive != null) this.isActive = isActive;
    }

    public void updatePassword(String newEncodedPassword) {
        this.password = newEncodedPassword;
    }

    public void updatePhone(String newPhone) {
        this.phone = newPhone;
    }
}
