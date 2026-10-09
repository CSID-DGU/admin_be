package DGU_AI_LAB.admin_be.domain.users.repository;

import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.ChangeRequestRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.PasswordHashes;
import DGU_AI_LAB.admin_be.domain.users.entity.PasswordResetRequest;
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
    private static final List<Status> OPEN = List.of(Status.PENDING, Status.PROCESSING);

    @Autowired private PasswordResetRequestRepository resetRepository;
    @Autowired private ChangeRequestRepository changeRequestRepository;
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
        ChangeRequest changeRequest = changeRequestRepository.saveAndFlush(ChangeRequest.password(owner));
        return resetRepository.saveAndFlush(PasswordResetRequest.pending(changeRequest, owner, HASHES));
    }

    @Test
    @DisplayName("새 신청은 승인 대기로 저장되고, 신청자 번호는 엔티티를 싣지 않고 읽는다")
    void savesPendingAndReadsOwner() {
        PasswordResetRequest reset = saved(alice);
        entityManager.clear();

        Long changeRequestId = reset.getChangeRequest().getChangeRequestId();
        assertThat(resetRepository.findUserIdByChangeRequestId(changeRequestId)).contains(alice.getUserId());
        assertThat(resetRepository.findUserIdByChangeRequestId(-1L)).isEmpty();
        PasswordResetRequest found = resetRepository.findByChangeRequestIdForUpdate(changeRequestId).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(Status.PENDING);
        assertThat(found.hashes()).isEqualTo(HASHES);
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("사용자의 열린 신청만 찾는다 — 끝난 신청과 다른 사용자의 신청은 빼고")
    void findsOpenRequestsOfUser() {
        PasswordResetRequest denied = saved(alice);
        denied.deny(admin, "거절");
        PasswordResetRequest open = saved(alice);
        saved(bob);
        resetRepository.flush();

        assertThat(resetRepository.findAllByUserIdAndStatusIn(alice.getUserId(), OPEN))
                .extracting(PasswordResetRequest::getPasswordResetRequestId)
                .containsExactly(open.getPasswordResetRequestId());
    }

    @Test
    @DisplayName("반영 중인 신청은 상태로도, 사용자별 잠금 읽기로도 찾는다")
    void findsProcessing() {
        PasswordResetRequest processing = saved(alice);
        processing.startProcessing(admin, "확인", 77L);
        saved(bob);
        resetRepository.flush();
        entityManager.clear();

        assertThat(resetRepository.findAllByStatus(Status.PROCESSING))
                .extracting(PasswordResetRequest::getJobId).containsExactly(77L);
        assertThat(resetRepository.findAllByUserIdAndStatusForShare(alice.getUserId(), Status.PROCESSING))
                .hasSize(1);
        assertThat(resetRepository.findAllByUserIdAndStatusForShare(bob.getUserId(), Status.PROCESSING))
                .isEmpty();
    }

    @Test
    @DisplayName("적용되거나 거절된 신청에는 해시가 남지 않는다")
    void closedRequestsKeepNoHashes() {
        PasswordResetRequest applied = saved(alice);
        applied.applyWithoutJob(admin, "확인");
        resetRepository.flush();
        entityManager.clear();

        PasswordResetRequest found = resetRepository.findById(applied.getPasswordResetRequestId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(Status.FULFILLED);
        assertThat(found.getPasswordHash()).isNull();
        assertThat(found.getUbuntuPasswordHash()).isNull();
        assertThat(found.getChangeRequest().getReviewedBy().getUserId()).isEqualTo(admin.getUserId());
    }
}
