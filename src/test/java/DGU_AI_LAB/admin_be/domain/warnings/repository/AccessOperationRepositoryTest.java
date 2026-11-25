package DGU_AI_LAB.admin_be.domain.warnings.repository;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.domain.warnings.entity.AccessOperation;
import DGU_AI_LAB.admin_be.domain.warnings.entity.AccessOperationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AccessOperationRepositoryTest {

    @Autowired private AccessOperationRepository operationRepository;
    @Autowired private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("access@dgu.ac.kr")
                .password("encoded")
                .name("접속 사용자")
                .studentId("2026000001")
                .phone("010-0000-0000")
                .department("AI")
                .ubuntuUsername("alice")
                .build());
    }

    private AccessOperation applied(String clusterId, String username, boolean blocked) {
        AccessOperation operation = new AccessOperation(user, clusterId, username, blocked);
        operation.markApplied();
        return operationRepository.saveAndFlush(operation);
    }

    @Test
    @DisplayName("마지막 APPLIED 상태는 사용자 전체가 아니라 클러스터별로 계산한다")
    void blockedUsersAreCalculatedFromLatestOperationPerCluster() {
        applied("FARM", "alice", true);
        applied("LAB", "alice-lab", true);
        AccessOperation farmUnblocked = applied("FARM", "alice", false);

        assertThat(operationRepository.findFirstByUser_UserIdAndClusterIdAndStatusOrderByAccessOperationIdDesc(
                user.getUserId(), "FARM", AccessOperationStatus.APPLIED))
                .contains(farmUnblocked);
        // FARM의 최신 행은 해제지만 LAB의 최신 행이 차단이므로 사용자는 계속 점검 대상이다.
        assertThat(operationRepository.findBlockedUserIds(AccessOperationStatus.APPLIED))
                .containsExactly(user.getUserId());

        applied("LAB", "alice-lab", false);

        assertThat(operationRepository.findBlockedUserIds(AccessOperationStatus.APPLIED)).isEmpty();
    }
}
