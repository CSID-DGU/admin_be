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
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
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

    @MockitoBean
    private AlarmService alarmService;


    @Test
    @DisplayName("유저 수명주기 통합 테스트: 알림(D-7, D-1), Soft Delete, Hard Delete, 활동 유저 보호")
    void userLifecycleScheduler_IntegrationTest() {
        // --- Given ---
        LocalDateTime now = LocalDateTime.now();

        // 1. [정상 유저]
        User activeUser = createUser("active@test.com", "ActiveUser");
        updateLastLogin(activeUser, now.minusDays(1));

        // 2. [보호 유저]
        User podUser = createUser("pod@test.com", "PodUser");
        updateLastLogin(podUser, now.minusMonths(4));
        createRequestForUser(podUser, now.minusDays(1));

        // 3. [경고 대상 D-7] (Login = Now - 3개월 + 7일)
        User d7User = createUser("d7@test.com", "D7User");
        LocalDateTime d7LoginDate = now.minusMonths(3).plusDays(7);
        updateLastLogin(d7User, d7LoginDate);

        // D-7 예상 메시지 생성
        LocalDateTime d7DeleteDate = d7LoginDate.plusMonths(3);
        String d7Subject = messageUtils.get("notification.user.delete-warning.subject", "7");
        String d7Body = messageUtils.get("notification.user.delete-warning.body",
                "D7User", "7", d7DeleteDate.toLocalDate().toString());


        // 4. [경고 대상 D-1] (Login = Now - 3개월 + 1일)
        User d1User = createUser("d1@test.com", "D1User");
        LocalDateTime d1LoginDate = now.minusMonths(3).plusDays(1);
        updateLastLogin(d1User, d1LoginDate);

        // D-1 예상 메시지 생성
        LocalDateTime d1DeleteDate = d1LoginDate.plusMonths(3);
        String d1Subject = messageUtils.get("notification.user.delete-warning.subject", "1");
        String d1Body = messageUtils.get("notification.user.delete-warning.body",
                "D1User", "1", d1DeleteDate.toLocalDate().toString());


        // 5. [Soft Delete 대상]
        User softTarget = createUser("soft@test.com", "SoftTarget");
        updateLastLogin(softTarget, now.minusMonths(3).minusDays(1));

        // Soft Delete 예상 메시지
        String softSubject = messageUtils.get("notification.user.soft-delete.subject");
        String softBody = messageUtils.get("notification.user.soft-delete.body", "SoftTarget");


        // 6. [Hard Delete 대상]
        User hardTarget = createUser("hard@test.com", "HardTarget");
        hardTarget.withdraw();
        updateDeletedAt(hardTarget, now.minusYears(1).minusDays(1));

        // 7. [Hard Delete 미대상]
        User hardSafe = createUser("hardsafe@test.com", "HardSafe");
        hardSafe.withdraw();
        updateDeletedAt(hardSafe, now.minusMonths(11));


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

        // 5. [Soft Delete] 알림 검증 및 상태 확인
        User resSoft = userRepository.findById(softTarget.getUserId()).get();
        assertThat(resSoft.getIsActive()).isFalse();
        assertThat(resSoft.getDeletedAt()).isNotNull();

        verify(alarmService).sendAllAlerts(
                eq("SoftTarget"),
                eq("soft@test.com"),
                eq(softSubject),
                eq(softBody)
        );

        // 6. [Hard Delete] 삭제 확인
        assertThat(userRepository.findById(hardTarget.getUserId())).isEmpty();

        // 7. [Hard Delete 미대상] 생존 확인
        assertThat(userRepository.findById(hardSafe.getUserId())).isPresent();
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

    private void updateDeletedAt(User user, LocalDateTime time) {
        try {
            var field = User.class.getDeclaredField("deletedAt");
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
                .ubuntuUsername("user_" + user.getUserId())
                .ubuntuPassword("pw")
                .volumeSizeGiB(10L)
                .expiresAt(expiresAt)
                .usagePurpose("test")
                .formAnswers("{}")
                .resourceGroup(rg)
                .containerImage(img)
                .build();

        req.approve(img, rg, 10L, "approved");
        requestRepository.saveAndFlush(req);
    }
}