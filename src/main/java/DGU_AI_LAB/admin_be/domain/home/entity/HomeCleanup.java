package DGU_AI_LAB.admin_be.domain.home.entity;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용이 끝난 계정의 홈 삭제 시도 한 번. 홈은 신청이 아니라 계정에 딸려 있어, 계정의 마지막 컨테이너가 끝난 시각을
 * 기준으로 센다. 실제 삭제는 config-server 작업이 하고, 이 행은 그 작업을 등록했는지와 결과를 남긴다.
 */
@Entity
@Getter
@Table(name = "home_cleanups")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HomeCleanup extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "home_cleanup_id")
    private Long homeCleanupId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private HomeCleanupStatus status = HomeCleanupStatus.PROCESSING;

    @Column(name = "ubuntu_username", nullable = false, length = 100)
    private String ubuntuUsername;

    /** 지워도 되는 홈인지 config-server가 확인하는 값. 홈 소유자가 이 번호가 아니면 지우지 않는다. */
    @Column(name = "ubuntu_uid", nullable = false)
    private Long ubuntuUid;

    /** 이 시도가 근거로 삼은 종료 시각. 그 뒤 새 컨테이너를 쓰고 다시 끝나면 값이 달라져 새 시도가 생긴다. */
    @Column(name = "last_container_ended_at", nullable = false)
    private LocalDateTime lastContainerEndedAt;

    /** 홈 삭제 작업의 번호. 등록 응답을 받지 못했으면 비어 있다. */
    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    private HomeCleanup(User user, LocalDateTime lastContainerEndedAt) {
        this.user = user;
        this.ubuntuUsername = user.getUbuntuUsername();
        this.ubuntuUid = user.getUbuntuUid();
        this.lastContainerEndedAt = lastContainerEndedAt;
    }

    public static HomeCleanup start(User user, LocalDateTime lastContainerEndedAt) {
        return new HomeCleanup(user, lastContainerEndedAt);
    }

    public void registered(Long jobId) {
        this.jobId = jobId;
    }

    public boolean isProcessing() {
        return this.status == HomeCleanupStatus.PROCESSING;
    }

    public void complete() {
        this.status = HomeCleanupStatus.DELETED;
    }

    public void fail(String failureCode) {
        this.status = HomeCleanupStatus.FAILED;
        this.failureCode = failureCode;
    }
}
