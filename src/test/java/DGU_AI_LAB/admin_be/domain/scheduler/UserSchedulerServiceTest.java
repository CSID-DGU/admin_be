package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.domain.users.service.AdminUserService;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UserSchedulerServiceTest {

    @Autowired
    private UserSchedulerService userSchedulerService;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RequestRepository requestRepository;
    @Autowired
    private ResourceGroupRepository resourceGroupRepository;
    @Autowired
    private ContainerImageRepository containerImageRepository;

    @Autowired
    private MessageUtils messageUtils;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private AlarmService alarmService;

    // 탈퇴 경로(컨테이너·계정 회수)는 AdminUserServiceTest에서 검증한다. 여기선 대상 선정과 위임만 본다.
    @MockitoBean
    private AdminUserService adminUserService;


    @Test
    @DisplayName("유저 수명주기 통합 테스트: 알림(D-7, D-1), 탈퇴 위임, 컨테이너 사용자·신규 가입자 보호")
    void userLifecycleScheduler_IntegrationTest() {
        // --- Given ---
        // 서비스와 같은 시간대로 잡는다. JVM 기본 시간대(CI는 UTC)로 잡으면 UTC 15시~24시에 서울 날짜와 하루 어긋나
        // 남은 일수가 달라진다.
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        // 1. [정상 유저] 막 가입
        User activeUser = createUser("active@test.com", "ActiveUser");

        // 2. [보호 유저] 가입은 오래됐지만 쓰고 있는 컨테이너가 있다
        User podUser = createUser("pod@test.com", "PodUser");
        setCreatedAt(podUser, now.minusYears(2));
        createRequestForUser(podUser, now.minusDays(1));

        // 3. [경고 대상 D-7] 컨테이너를 쓴 적 없이 가입 = Now + 7일 - 1년 → deleteDate = Now + 7일
        // plusDays 후 minusYears 순서여야 달력 연산 오차 없이 정확히 7일이 남음
        User d7User = createUser("d7@test.com", "D7User");
        LocalDateTime d7Since = now.plusDays(7).minusYears(1);
        setCreatedAt(d7User, d7Since);
        String d7Subject = messageUtils.get("notification.user.delete-warning.subject", "7");
        String d7Body = messageUtils.get("notification.user.delete-warning.body",
                "D7User", "7", d7Since.plusMonths(12).toLocalDate().toString());

        // 4. [경고 대상 D-1]
        User d1User = createUser("d1@test.com", "D1User");
        LocalDateTime d1Since = now.minusYears(1).plusDays(1);
        setCreatedAt(d1User, d1Since);
        String d1Subject = messageUtils.get("notification.user.delete-warning.subject", "1");
        String d1Body = messageUtils.get("notification.user.delete-warning.body",
                "D1User", "1", d1Since.plusMonths(12).toLocalDate().toString());

        // 5. [탈퇴 대상] 어제 로그인했어도 가입(컨테이너 사용 없음) 1년이 지났다
        User softTarget = createUser("soft@test.com", "SoftTarget");
        setCreatedAt(softTarget, now.minusYears(1).minusDays(1));
        updateLastLogin(softTarget, now.minusDays(1));

        // 가입 시각은 SQL로 바꿨으니 스케줄러가 DB 값을 다시 읽게 한다.
        entityManager.flush();
        entityManager.clear();


        // --- When ---
        userSchedulerService.runUserLifecycleScheduler();


        // --- Then ---

        // 1. 정상 유저 생존
        assertThat(userRepository.findById(activeUser.getUserId()).get().getIsActive()).isTrue();

        // 2. 보호 유저 생존
        assertThat(userRepository.findById(podUser.getUserId()).get().getIsActive()).isTrue();

        // 3. [D-7] 알림 검증 (정확한 메시지 매칭)
        verify(alarmService).sendAllAlerts(
                eq("D7User"),
                eq("d7@test.com"),
                eq(d7Subject),
                eq(d7Body)
        );

        // 4. [D-1] 알림 검증
        verify(alarmService).sendAllAlerts(
                eq("D1User"),
                eq("d1@test.com"),
                eq(d1Subject),
                eq(d1Body)
        );

        // 5. [탈퇴 대상]만 탈퇴 경로로 넘긴다
        verify(adminUserService).withdrawInactiveUser(softTarget.getUserId());
        verify(adminUserService, never()).withdrawInactiveUser(activeUser.getUserId());
        verify(adminUserService, never()).withdrawInactiveUser(podUser.getUserId());
        verify(adminUserService, never()).withdrawInactiveUser(d7User.getUserId());
        verify(adminUserService, never()).withdrawInactiveUser(d1User.getUserId());
    }


    // --- Helper Methods ---

    private User createUser(String email, String name) {
        return userRepository.save(User.builder()
                .email(email)
                .name(name)
                .password("pw")
                .studentId("1234")
                .phone("010-0000-0000")
                .department("CS")
                .build());
    }

    /** created_at은 JPA로 수정할 수 없는 칸(updatable=false)이라 SQL로 바꾼다. */
    private void setCreatedAt(User user, LocalDateTime time) {
        entityManager.createNativeQuery("UPDATE users SET created_at = :t WHERE user_id = :id")
                .setParameter("t", time)
                .setParameter("id", user.getUserId())
                .executeUpdate();
    }

    private void updateLastLogin(User user, LocalDateTime time) {
        try {
            var field = User.class.getDeclaredField("lastLoginAt");
            field.setAccessible(true);
            field.set(user, time);
            userRepository.saveAndFlush(user);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void createRequestForUser(User user, LocalDateTime expiresAt) {
        ResourceGroup rg = resourceGroupRepository.findAll().stream().findFirst()
                .orElseGet(() -> resourceGroupRepository.save(ResourceGroup.builder().serverName("TestServer").resourceGroupName("G").build()));
        ContainerImage img = containerImageRepository.findAll().stream().findFirst()
                .orElseGet(() -> containerImageRepository.save(ContainerImage.builder().imageName("cuda").imageVersion("1").cudaVersion("1").description("d").build()));

        Request req = Request.builder()
                .user(user)
                .expiresAt(expiresAt)
                .usagePurpose("test")
                .formAnswers("{}")
                .resourceGroup(rg)
                .containerImage(img)
                .build();

        req.markAsProcessing();

        req.prepareAsyncApproval(img, rg, "approved");

        req.completeApproval();
        requestRepository.saveAndFlush(req);
    }
}
