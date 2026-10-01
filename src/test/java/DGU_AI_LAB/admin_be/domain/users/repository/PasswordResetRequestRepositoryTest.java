package DGU_AI_LAB.admin_be.domain.users.repository;

import DGU_AI_LAB.admin_be.domain.users.entity.PasswordHashes;
import DGU_AI_LAB.admin_be.domain.users.entity.PasswordResetRequest;
import DGU_AI_LAB.admin_be.domain.users.entity.PasswordResetStatus;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class PasswordResetRequestRepositoryTest {

    private static final PasswordHashes HASHES = new PasswordHashes("encoded", "$6$salt$hash");
    private static final List<PasswordResetStatus> OPEN =
            List.of(PasswordResetStatus.PENDING, PasswordResetStatus.PROCESSING);

    @Autowired private PasswordResetRequestRepository resetRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    private User alice;
    private User bob;
    private User admin;

    @BeforeEach
    void setUp() {
        alice = userRepository.save(user("alice@dgu.ac.kr", "2021000001"));
        bob = userRepository.save(user("bob@dgu.ac.kr", "2021000002"));
        admin = userRepository.save(user("admin@dgu.ac.kr", "2021000003"));
    }

    private static User user(String email, String studentId) {
        return User.builder()
                .email(email)
                .password("encoded")
                .name("재설정시험")
                .studentId(studentId)
                .phone("010-0000-0000")
                .department("컴퓨터공학과")
                .build();
    }

    private PasswordResetRequest saved(User owner) {
        return resetRepository.saveAndFlush(PasswordResetRequest.pending(owner, HASHES));
    }

    @Test
    @DisplayName("새 신청은 승인 대기로 저장되고, 신청자 번호는 엔티티를 싣지 않고 읽는다")
    void savesPendingAndReadsOwner() {
        PasswordResetRequest reset = saved(alice);
        entityManager.clear();

        assertThat(resetRepository.findUserIdById(reset.getPasswordResetRequestId())).contains(alice.getUserId());
        assertThat(resetRepository.findUserIdById(-1L)).isEmpty();
        PasswordResetRequest found = resetRepository.findByIdForUpdate(reset.getPasswordResetRequestId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(PasswordResetStatus.PENDING);
        assertThat(found.hashes()).isEqualTo(HASHES);
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("사용자의 열린 신청만 찾는다 — 끝난 신청과 다른 사용자의 신청은 빼고")
    void findsOpenRequestsOfUser() {
        PasswordResetRequest denied = saved(alice);
        denied.deny(admin);
        PasswordResetRequest open = saved(alice);
        saved(bob);
        resetRepository.flush();

        assertThat(resetRepository.findAllByUser_UserIdAndStatusIn(alice.getUserId(), OPEN))
                .extracting(PasswordResetRequest::getPasswordResetRequestId)
                .containsExactly(open.getPasswordResetRequestId());
    }

    @Test
    @DisplayName("반영 중인 신청은 상태로도, 사용자별 잠금 읽기로도 찾는다")
    void findsProcessing() {
        PasswordResetRequest processing = saved(alice);
        processing.startProcessing(admin, 77L);
        saved(bob);
        resetRepository.flush();
        entityManager.clear();

        assertThat(resetRepository.findAllByStatus(PasswordResetStatus.PROCESSING))
                .extracting(PasswordResetRequest::getJobId).containsExactly(77L);
        assertThat(resetRepository.findAllByUserIdAndStatusForShare(alice.getUserId(), PasswordResetStatus.PROCESSING))
                .hasSize(1);
        assertThat(resetRepository.findAllByUserIdAndStatusForShare(bob.getUserId(), PasswordResetStatus.PROCESSING))
                .isEmpty();
    }

    @Test
    @DisplayName("관리자 목록은 열린 신청을 신청자와 함께 읽는다")
    void listsOpenWithUser() {
        saved(alice);
        PasswordResetRequest closed = saved(bob);
        closed.deny(admin);
        resetRepository.flush();
        entityManager.clear();

        List<PasswordResetRequest> open = resetRepository.findAllWithUserByStatusIn(OPEN);

        assertThat(open).hasSize(1);
        assertThat(open.get(0).getUser().getEmail()).isEqualTo("alice@dgu.ac.kr");
    }

    @Test
    @DisplayName("적용되거나 거절된 신청에는 해시가 남지 않는다")
    void closedRequestsKeepNoHashes() {
        PasswordResetRequest applied = saved(alice);
        applied.applyWithoutJob(admin);
        resetRepository.flush();
        entityManager.clear();

        PasswordResetRequest found = resetRepository.findById(applied.getPasswordResetRequestId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(PasswordResetStatus.APPLIED);
        assertThat(found.getPasswordHash()).isNull();
        assertThat(found.getUbuntuPasswordHash()).isNull();
        assertThat(found.getReviewedBy().getUserId()).isEqualTo(admin.getUserId());
    }
}
