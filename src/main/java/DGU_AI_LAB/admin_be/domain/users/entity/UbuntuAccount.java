package DGU_AI_LAB.admin_be.domain.users.entity;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 서버(리소스 그룹의 serverName)마다 따로 가지는 리눅스 계정. 서버마다 AD·NAS가 다르므로 사용자 한 명이 서버 수만큼
 * 계정(유저네임·UID·GID)을 가질 수 있다. UID/GID/유저네임은 사람에게 영구히 남아, 회수돼도 지우지 않는다 — 다시 승인되면
 * 같은 번호·같은 홈으로 돌아온다.
 */
@Entity
@Table(name = "ubuntu_accounts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ubuntu_accounts_user_server", columnNames = {"user_id", "server_name"}),
        @UniqueConstraint(name = "uk_ubuntu_accounts_server_username", columnNames = {"server_name", "username"}),
        @UniqueConstraint(name = "uk_ubuntu_accounts_server_uid", columnNames = {"server_name", "uid"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UbuntuAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ubuntu_account_id")
    private Long ubuntuAccountId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "server_name", nullable = false, length = 300)
    private String serverName;

    /** 이 서버에서 쓰는 유저네임. 가입 때 고른 이름이 기본값이고, 서버가 달라 이름이 다른 사람(이전 계정)은 다른 값을 가진다. */
    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "uid")
    private Long uid;

    @Column(name = "gid")
    private Long gid;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UbuntuAccountStatus status = UbuntuAccountStatus.NONE;

    UbuntuAccount(User user, String serverName, String username) {
        this.user = user;
        this.serverName = serverName;
        this.username = username;
    }

    public boolean isServer(String serverName) {
        return this.serverName.equalsIgnoreCase(serverName);
    }

    public boolean isActive() {
        return status == UbuntuAccountStatus.ACTIVE;
    }

    public boolean isReleasing() {
        return status == UbuntuAccountStatus.RELEASING;
    }

    /**
     * 계정이 만들어졌거나 되살아났음을 기록한다. 예전에 받은 UID/GID가 있으면 같은 값이어야 한다(멱등) — 다른 값이면
     * 보존된 홈의 소유자와 어긋나고 옛 번호가 다른 사람에게 넘어간 것처럼 보인다.
     */
    public void assign(Long uid, Long gid) {
        if (uid == null || gid == null || uid <= 0 || gid <= 0) {
            throw new BusinessException(ErrorCode.UID_ALLOCATION_FAILED);
        }
        if (this.uid != null && (!this.uid.equals(uid) || !gid.equals(this.gid))) {
            throw new BusinessException(
                    "이미 다른 UID/GID가 배정된 계정입니다.", ErrorCode.UBUNTU_ACCOUNT_ALREADY_ASSIGNED);
        }
        if (isReleasing()) {
            throw new BusinessException(ErrorCode.UBUNTU_ACCOUNT_RELEASING);
        }
        this.uid = uid;
        this.gid = gid;
        this.status = UbuntuAccountStatus.ACTIVE;
    }

    /** 회수를 시작한다. 이미 회수 중이면 그대로 둔다(재시도). 계정이 없으면 회수할 것도 없다. */
    public boolean beginRelease() {
        if (status == UbuntuAccountStatus.NONE) {
            return false;
        }
        this.status = UbuntuAccountStatus.RELEASING;
        return true;
    }

    /** 회수할 노드를 몰라 계정을 지우지 못했을 때 살아 있는 상태로 되돌린다. */
    public void abortRelease() {
        requireReleasing();
        this.status = UbuntuAccountStatus.ACTIVE;
    }

    /** 리눅스 계정이 실제로 삭제됐음을 기록한다. 유저네임·UID·GID는 남긴다. */
    public void release() {
        requireReleasing();
        this.status = UbuntuAccountStatus.NONE;
    }

    private void requireReleasing() {
        if (!isReleasing()) {
            throw new BusinessException("회수 중인 계정이 아닙니다.", ErrorCode.INVALID_REQUEST_STATUS);
        }
    }
}
