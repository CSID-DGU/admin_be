package DGU_AI_LAB.admin_be.domain.users.entity;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import DGU_AI_LAB.admin_be.domain.groups.entity.Group;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_ubuntu_username", columnNames = "ubuntu_username"),
        @UniqueConstraint(name = "uk_users_ubuntu_uid", columnNames = "ubuntu_uid")
})
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

    /**
     * 웹 계정 하나당 우분투 계정 하나. 회원가입 시 사용자가 직접 고르며, 이후 이 사용자의
     * 모든 컨테이너가 같은 유저네임/UID/GID를 쓴다 — 홈 디렉터리(/home/&lt;username&gt;)가
     * NFS에서 유저네임으로만 결정되므로, 유저네임이 고정되면 컨테이너를 새로 받아도
     * 이전 홈이 그대로 이어진다.
     */
    @Column(name = "ubuntu_username", length = 100)
    private String ubuntuUsername;

    /**
     * 실제 리눅스 계정이 만들어진 시점(첫 승인)에만 채워진다. 승인 없이 탈락하는 사용자에게
     * 리눅스 계정을 미리 할당하지 않으려고 회원가입 시점에는 비워둔다.
     */
    @Column(name = "ubuntu_uid")
    private Long ubuntuUid;

    @Column(name = "ubuntu_gid")
    private Long ubuntuGid;

    /**
     * 우분투 로그인 비밀번호의 SHA-512 crypt 해시($6$...). 유저네임처럼 웹 계정에 하나다 — 첫 신청에서
     * 받고, 이후 모든 컨테이너가 이 값으로 만들어진다. 평문은 받자마자 해시로 바꾸고 저장하지 않는다.
     */
    @Column(name = "ubuntu_password_hash")
    private String ubuntuPasswordHash;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<Request> requests = new ArrayList<>();

    /**
     * 이 계정이 실제로 가진 그룹. Request 단위가 아니라 계정 단위다 — 같은 웹 계정의
     * 모든 컨테이너가 같은 우분투 계정을 쓰므로(ubuntuUsername), 그룹도 계정 하나에 대해
     * 한 벌만 존재한다. AD에 실제로 반영된 뒤에만(승인 성공 확인 후) 채워진다.
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserGroup> userGroups = new HashSet<>();

    @Builder
    public User(String email, String password, String name, String studentId, String phone, String department, String ubuntuUsername) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.studentId = studentId;
        this.phone = phone;
        this.department = department;
        this.ubuntuUsername = ubuntuUsername;
        this.lastLoginAt = LocalDateTime.now();
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

    public void recordLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public void withdraw() {
        this.isActive = false;
        this.deletedAt = LocalDateTime.now();
    }

    public void reactivate() {
        this.isActive = true;
        this.deletedAt = null;
    }

    /**
     * withdraw()와 달리 deletedAt은 남기지 않는 임시 비활성화 — 로그인 차단 여부만 관리한다.
     * 소유 Request(우분투 계정/컨테이너) 정리는 AdminUserService.deactivateUser()가
     * withdraw()와 동일하게 별도로 수행하므로, 재활성화 후에는 컨테이너를 다시 신청해야 한다.
     */
    public void deactivate() {
        this.isActive = false;
    }

    public void changeRole(Role newRole) {
        this.role = newRole;
    }

    public boolean hasUbuntuPassword() {
        return this.ubuntuPasswordHash != null && !this.ubuntuPasswordHash.isBlank();
    }

    public void changeUbuntuPasswordHash(String ubuntuPasswordHash) {
        this.ubuntuPasswordHash = ubuntuPasswordHash;
    }

    /** 실제 리눅스 계정(UID/GID)이 이미 배정되어 있는지. false면 승인 시 계정 생성 API를 호출해야 한다. */
    public boolean hasUbuntuAccount() {
        return this.ubuntuUid != null && this.ubuntuGid != null;
    }

    /**
     * 첫 승인에서 발급받은 UID/GID를 이 계정에 귀속시킨다.
     * 같은 사용자의 다른 신청이 먼저 같은 값을 배정했다면 그대로 통과시키고(멱등),
     * 다른 값이 이미 배정돼 있으면 실패시킨다 — 한 웹 계정이 서로 다른 리눅스 계정
     * 두 개를 가리키면 홈 디렉터리 소유권이 어긋난다.
     */
    public void assignUbuntuAccount(Long ubuntuUid, Long ubuntuGid) {
        if (ubuntuUid == null || ubuntuGid == null || ubuntuUid <= 0 || ubuntuGid <= 0) {
            throw new BusinessException(ErrorCode.UID_ALLOCATION_FAILED);
        }
        if (hasUbuntuAccount()) {
            if (!this.ubuntuUid.equals(ubuntuUid) || !this.ubuntuGid.equals(ubuntuGid)) {
                throw new BusinessException(
                        "이미 다른 UID/GID가 배정된 계정입니다.", ErrorCode.UBUNTU_ACCOUNT_ALREADY_ASSIGNED);
            }
            return;
        }
        this.ubuntuUid = ubuntuUid;
        this.ubuntuGid = ubuntuGid;
    }

    /**
     * 리눅스 계정이 실제로 삭제됐을 때 UID/GID만 비운다. 유저네임은 남긴다 —
     * 유저네임은 컨테이너가 아니라 웹 계정에 평생 귀속되고(재가입/재활성화 후 같은 홈으로
     * 돌아와야 한다), UID는 계정 삭제와 동시에 config-server가 다른 사용자에게 재할당할 수
     * 있어 그대로 들고 있으면 다음 승인이 남의 UID로 Pod를 만든다.
     */
    public void releaseUbuntuAccount() {
        this.ubuntuUid = null;
        this.ubuntuGid = null;
        // 계정 삭제는 AD 사용자째 지우므로 그룹 소속도 함께 사라진다. 남겨 두면 화면엔 여전히
        // 멤버로 보이고, 다시 승인될 때 AD에 없는 소속이 새 Pod 에 실린다.
        this.userGroups.clear();
    }

    /**
     * 이 계정에 그룹을 추가한다. config-server의 add_user_groups가 집합-추가라 DB도 같은
     * 의미로 맞춘다 — 이미 속한 그룹이면 조용히 무시한다(멱등). 제거는 removeGroup.
     * AD에 실제로 반영된 뒤에만(completeApprovalJob/approveModification의 그룹 반영
     * 3단계) 호출해야 DB가 AD보다 앞서가는 거짓 상태가 생기지 않는다.
     */
    public void addGroupIfAbsent(Group group) {
        boolean already = this.userGroups.stream()
                .anyMatch(ug -> ug.getGroup().getGroupId().equals(group.getGroupId()));
        if (already) {
            return;
        }
        this.userGroups.add(UserGroup.builder().user(this).group(group).build());
    }

    /**
     * 이 계정에서 그룹을 뺀다. AD에서 실제로 뺀 뒤에만 호출해야 한다(addGroupIfAbsent와 같은 이유).
     * 속하지 않은 그룹이면 아무 일도 하지 않는다.
     */
    public void removeGroup(Long groupId) {
        this.userGroups.removeIf(ug -> ug.getGroup().getGroupId().equals(groupId));
    }

    public Set<Long> getUbuntuGidsOfGroups() {
        return this.userGroups.stream()
                .map(ug -> ug.getGroup().getUbuntuGid())
                .collect(Collectors.toSet());
    }

    /**
     * 회원가입 이후에 우분투 유저네임을 등록한다. 이 필드가 도입되기 전에 가입한 계정처럼
     * ubuntuUsername이 비어있는 사용자를 위한 1회성 자가 등록 경로다 — 가입 폼에서 못
     * 받았다고 영원히 컨테이너를 신청 못 하게 둘 수는 없다. 한 번 정해지면 그 사용자의
     * 모든 컨테이너가 이 이름의 홈 디렉터리를 공유하게 되므로, 이미 값이 있으면(가입 시
     * 정했든 이 메서드로 나중에 정했든) 다시 바꾸지 못하게 막는다 — 바꾸면 기존 홈
     * 디렉터리와 새 이름이 어긋난다.
     */
    public void registerUbuntuUsername(String ubuntuUsername) {
        if (this.ubuntuUsername != null && !this.ubuntuUsername.isBlank()) {
            throw new BusinessException(ErrorCode.UBUNTU_USERNAME_ALREADY_ASSIGNED);
        }
        this.ubuntuUsername = ubuntuUsername;
    }
}
