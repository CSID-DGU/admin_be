package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.AdminBeApplication;
import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.UbuntuAccountService;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.domain.usedIds.entity.UsedId;
import DGU_AI_LAB.admin_be.domain.usedIds.repository.UsedIdRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


/**
 * 작동하지 않을 시 ./gradlew clean test 시도해보세요.
 */
@SpringBootTest(classes = AdminBeApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RequestSchedulerServiceTest {

    @Autowired
    private RequestSchedulerService requestSchedulerService;

    // --- Mocks ---
    @MockitoBean
    private AlarmService alarmService;

    @MockitoBean
    private UbuntuAccountService ubuntuAccountService;

    // --- Repositories ---
    @Autowired private RequestRepository requestRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UsedIdRepository usedIdRepository;
    @Autowired private ResourceGroupRepository resourceGroupRepository;
    @Autowired private ContainerImageRepository containerImageRepository;

    // 테스트 기준 시간 고정
    private final LocalDateTime MOCK_NOW = LocalDateTime.of(2025, 11, 10, 10, 30, 0);

    @Test
    @DisplayName("스케줄러 통합 테스트: 만료 삭제(이벤트) 및 1/3/7일 전 알림 발송 검증")
    void runScheduler_IntegrationTest() {

        // 1. 기초 데이터 세팅
        User testUser = userRepository.save(User.builder()
                .email("test@dgu.ac.kr")
                .name("테스트유저")
                .password("encoded_pw")
                .studentId("2020111111")
                .phone("010-1234-5678")
                .department("AI융합학부")
                .build());

        ResourceGroup testRg = resourceGroupRepository.save(ResourceGroup.builder()
                .serverName("FARM-01")
                .resourceGroupName("RTX 3090")
                .build());

        ContainerImage testImage = containerImageRepository.save(ContainerImage.builder()
                .imageName("cuda")
                .imageVersion("11.8")
                .cudaVersion("11.8")
                .description("Test Image")
                .build());

        // 2. UsedId 생성
        UsedId uidExpired = usedIdRepository.save(UsedId.builder().idValue(1000L).build());
        UsedId uid1Day = usedIdRepository.save(UsedId.builder().idValue(1001L).build());
        UsedId uid3Day = usedIdRepository.save(UsedId.builder().idValue(1002L).build());
        UsedId uid7Day = usedIdRepository.save(UsedId.builder().idValue(1003L).build());
        UsedId uidOk = usedIdRepository.save(UsedId.builder().idValue(1004L).build());

        // 3. 시나리오별 Request 생성
        // (1) 만료되어 삭제될 요청 (어제 만료됨)
        Request reqExpired = createTestRequest(MOCK_NOW.minusDays(1), Status.FULFILLED, uidExpired, "user-expired", testUser, testRg, testImage);

        // (2) 1일 남은 요청 (내일 만료)
        Request req1Day = createTestRequest(MOCK_NOW.plusDays(1).withHour(12), Status.FULFILLED, uid1Day, "user-1day", testUser, testRg, testImage);

        // (3) 3일 남은 요청 (3일 뒤 만료)
        Request req3Day = createTestRequest(MOCK_NOW.plusDays(3).withHour(14), Status.FULFILLED, uid3Day, "user-3day", testUser, testRg, testImage);

        // (4) 7일 남은 요청 (7일 뒤 만료)
        Request req7Day = createTestRequest(MOCK_NOW.plusDays(7).withHour(15), Status.FULFILLED, uid7Day, "user-7day", testUser, testRg, testImage);

        // (5) 아직 넉넉한 요청
        Request reqOk = createTestRequest(MOCK_NOW.plusDays(30), Status.FULFILLED, uidOk, "user-ok", testUser, testRg, testImage);


        // --- Given: 시간 고정 ---
        try (MockedStatic<LocalDateTime> mockedTime = Mockito.mockStatic(LocalDateTime.class, Mockito.CALLS_REAL_METHODS)) {
            mockedTime.when(LocalDateTime::now).thenReturn(MOCK_NOW);

            // --- When: 스케줄러 실행 ---
            requestSchedulerService.runScheduler();
        }

        // --- Then: 검증 ---

        // 1. [삭제 검증] reqExpired
        Request deletedResult = requestRepository.findById(reqExpired.getRequestId()).orElseThrow();
        assertThat(deletedResult.getStatus()).isEqualTo(Status.DELETED);
        assertThat(deletedResult.getUbuntuUid()).isNull();

        verify(ubuntuAccountService, times(1)).deleteUbuntuAccount("user-expired");

        // [이벤트 리스너 검증] -> 삭제 완료 알림
        verify(alarmService).sendAllAlerts(
                eq(testUser.getName()),
                eq(testUser.getEmail()),
                contains("삭제 완료 안내"),
                contains("삭제되었습니다")
        );

        verify(alarmService).sendAdminSlackNotification(
                eq("FARM-01"),
                contains("삭제 완료")
        );


        // 2. [알림 검증] 1일 전 (req1Day)
        Request result1Day = requestRepository.findById(req1Day.getRequestId()).get();
        assertThat(result1Day.getStatus()).isEqualTo(Status.FULFILLED);
        verify(alarmService).sendAllAlerts(
                eq(testUser.getName()),
                eq(testUser.getEmail()),
                contains("안내 (1일 전)"),
                contains("삭제될 예정")
        );


        // 3. [알림 검증] 3일 전 (req3Day)
        Request result3Day = requestRepository.findById(req3Day.getRequestId()).get();
        assertThat(result3Day.getStatus()).isEqualTo(Status.FULFILLED);
        verify(alarmService).sendAllAlerts(
                eq(testUser.getName()),
                eq(testUser.getEmail()),
                contains("안내 (3일 전)"),
                contains("삭제될 예정")
        );


        // 4. [알림 검증] 7일 전 (req7Day)
        Request result7Day = requestRepository.findById(req7Day.getRequestId()).get();
        assertThat(result7Day.getStatus()).isEqualTo(Status.FULFILLED);
        verify(alarmService).sendAllAlerts(
                eq(testUser.getName()),
                eq(testUser.getEmail()),
                contains("안내 (7일 전)"),
                contains("삭제될 예정")
        );


        // 5. [총 호출 횟수 검증]
        verify(alarmService, times(4)).sendAllAlerts(anyString(), anyString(), anyString(), anyString());
        verify(alarmService, times(1)).sendAdminSlackNotification(anyString(), anyString());
    }

    // --- Helper Method ---
    private Request createTestRequest(LocalDateTime expiresAt, Status status, UsedId usedId, String ubuntuUsername,
                                      User testUser, ResourceGroup testRg, ContainerImage testImage) {
        Request req = Request.builder()
                .ubuntuUsername(ubuntuUsername)
                .ubuntuPassword("password")
                .volumeSizeGiB(10L)
                .expiresAt(expiresAt)
                .usagePurpose("test")
                .formAnswers("{}")
                .user(testUser)
                .resourceGroup(testRg)
                .containerImage(testImage)
                .build();

        if (status == Status.FULFILLED || status == Status.DELETED) {
            req.approve(testImage, testRg, 10L, "approved");
            req.assignUbuntuUid(usedId);
        }

        if (status == Status.DELETED) {
            req.delete();
            req.assignUbuntuUid(null);
        }

        return requestRepository.saveAndFlush(req);
    }
}