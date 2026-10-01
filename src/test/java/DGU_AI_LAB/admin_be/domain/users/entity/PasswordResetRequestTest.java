package DGU_AI_LAB.admin_be.domain.users.entity;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordResetRequestTest {

    private static final PasswordHashes HASHES = new PasswordHashes("encoded", "$6$salt$hash");

    private final User user = User.builder().email("test@dgu.ac.kr").password("p").name("홍길동").build();
    private final User admin = User.builder().email("admin@dgu.ac.kr").password("p").name("관리자").build();

    private PasswordResetRequest pending() {
        return PasswordResetRequest.pending(user, HASHES);
    }

    @Test
    @DisplayName("새 신청은 승인 대기이고 두 해시를 들고 있다")
    void startsPending() {
        PasswordResetRequest reset = pending();

        assertThat(reset.getStatus()).isEqualTo(PasswordResetStatus.PENDING);
        assertThat(reset.hashes()).isEqualTo(HASHES);
    }

    @Test
    @DisplayName("작업이 성공하면 적용됨이 되고 해시를 비운다")
    void processingThenApplied() {
        PasswordResetRequest reset = pending();

        reset.startProcessing(admin, 77L);
        assertThat(reset.getStatus()).isEqualTo(PasswordResetStatus.PROCESSING);
        assertThat(reset.getJobId()).isEqualTo(77L);
        assertThat(reset.getReviewedAt()).isNotNull();

        reset.completeJob();
        assertThat(reset.getStatus()).isEqualTo(PasswordResetStatus.APPLIED);
        assertThat(reset.getPasswordHash()).isNull();
        assertThat(reset.getUbuntuPasswordHash()).isNull();
    }

    @Test
    @DisplayName("작업이 실패하면 승인 전으로 돌아가고 해시는 남는다 — 다시 승인하거나 거절할 수 있다")
    void failedJobReturnsToPending() {
        PasswordResetRequest reset = pending();
        reset.startProcessing(admin, 77L);

        reset.returnToPending();

        assertThat(reset.getStatus()).isEqualTo(PasswordResetStatus.PENDING);
        assertThat(reset.getJobId()).isNull();
        assertThat(reset.getReviewedBy()).isNull();
        assertThat(reset.getReviewedAt()).isNull();
        assertThat(reset.hashes()).isEqualTo(HASHES);

        reset.startProcessing(admin, 78L);
        assertThat(reset.getJobId()).isEqualTo(78L);
    }

    @Test
    @DisplayName("거절하면 해시를 비우고 다시 열 수 없다")
    void deniedIsClosed() {
        PasswordResetRequest reset = pending();

        reset.deny(admin);

        assertThat(reset.getStatus()).isEqualTo(PasswordResetStatus.DENIED);
        assertThat(reset.getPasswordHash()).isNull();
        assertThatThrownBy(() -> reset.startProcessing(admin, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PASSWORD_RESET_ALREADY_CLOSED);
        assertThatThrownBy(() -> reset.replacePassword(HASHES)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("반영 중에는 새 비밀번호를 바꾸거나 거절하지 못한다")
    void processingIsLocked() {
        PasswordResetRequest reset = pending();
        reset.startProcessing(admin, 77L);

        assertThatThrownBy(() -> reset.replacePassword(new PasswordHashes("other", "$6$other$hash")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PASSWORD_RESET_IN_PROGRESS);
        assertThatThrownBy(() -> reset.deny(admin))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PASSWORD_RESET_IN_PROGRESS);
        assertThat(reset.hashes()).isEqualTo(HASHES);
    }

    @Test
    @DisplayName("반영 중이 아닌 신청은 작업 결과로 끝내거나 되돌리지 못한다")
    void jobResultNeedsProcessing() {
        PasswordResetRequest reset = pending();

        assertThatThrownBy(reset::completeJob).isInstanceOf(BusinessException.class);
        assertThatThrownBy(reset::returnToPending).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("해시는 문자열로 찍히지 않는다")
    void hashesAreNotPrinted() {
        assertThat(HASHES.toString()).doesNotContain("encoded").doesNotContain("$6$");
    }
}
