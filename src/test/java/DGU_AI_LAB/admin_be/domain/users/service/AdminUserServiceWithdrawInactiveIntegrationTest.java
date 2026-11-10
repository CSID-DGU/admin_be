package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.groups.service.PendingGroupService;
import DGU_AI_LAB.admin_be.domain.home.service.HomeRetentionPolicy;
import DGU_AI_LAB.admin_be.domain.requests.service.PodMigrationService;
import DGU_AI_LAB.admin_be.domain.requests.service.RequestExpiryService;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * withdrawInactiveUser가 DB에서 읽은 실제 사용자(requests가 지연 로딩)로 끝까지 가는지 본다. 단위 테스트의 mock 사용자는
 * requests가 일반 리스트라 트랜잭션 밖 판정에서 나던 LazyInitializationException을 잡지 못했다.
 * 서비스가 자기 트랜잭션을 쓰도록 테스트 트랜잭션을 끄고, 픽스처는 커밋한 뒤 정리한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(AdminUserService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminUserServiceWithdrawInactiveIntegrationTest {

    @Autowired private AdminUserService service;
    @Autowired private UserRepository userRepository;
    @MockitoBean private RequestExpiryService requestExpiryService;
    @MockitoBean private PodMigrationService podMigrationService;
    @MockitoBean private PendingGroupService pendingGroupService;
    @MockitoBean private AlarmService alarmService;
    @MockitoBean private MessageUtils messageUtils;
    @MockitoBean private TokenService tokenService;
    @MockitoBean private PasswordResetService passwordResetService;
    @MockitoBean private InactivityNoticeStore inactivityNoticeStore;
    @MockitoBean private HomeRetentionPolicy homeRetentionPolicy;

    @AfterEach
    void cleanup() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("졸업 예정일이 없는 장기 미사용 사용자는 비활성화되고 장기 미사용 안내를 받는다")
    void withdrawsPersistedInactiveUser() {
        Long userId = saveUser("inactive@dgu.ac.kr", "2099003333", null);

        service.withdrawInactiveUser(userId);

        assertThat(userRepository.findById(userId).orElseThrow().getIsActive()).isFalse();
        verify(messageUtils).get("notification.user.soft-delete.subject");
    }

    @Test
    @DisplayName("졸업 기한이 지난 사용자는 비활성화되고 졸업 안내를 받는다")
    void withdrawsPersistedGraduatedUser() {
        Long userId = saveUser("graduated@dgu.ac.kr", "2099004444", LocalDate.of(2020, 2, 28));

        service.withdrawInactiveUser(userId);

        assertThat(userRepository.findById(userId).orElseThrow().getIsActive()).isFalse();
        verify(messageUtils).get("notification.user.soft-delete.graduation.subject");
    }

    private Long saveUser(String email, String studentId, LocalDate graduation) {
        return userRepository.save(User.builder().email(email).password("p").name("이름").studentId(studentId)
                .phone("010-1111-4444").department("컴퓨터공학과").expectedGraduationDate(graduation).build()).getUserId();
    }
}
