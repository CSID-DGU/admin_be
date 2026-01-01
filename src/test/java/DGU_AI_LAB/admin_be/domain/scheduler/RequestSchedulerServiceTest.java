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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
    @DisplayName("스케줄러 통합 테스트: 만료 삭제 및 1/3/7일 전 알림 발송 검증")
    void checkAndProcessExpiredRequests_IntegrationTest() {

        // 1. 기초 데이터 세팅 (필수 필드 누락 방지)
        User testUser = userRepository.save(User.builder()
                .email("test@dgu.ac.kr")
                .name("테스트유저")
                .password("test1234")       // 필수 필드 추가
                .studentId("2020111111")
                .phone("010-1234-5678")     // 필수 필드 추가
                .department("컴퓨터공학과")   // 필수 필드 추가
                .build());

        ResourceGroup testRg = resourceGroupRepository.save(ResourceGroup.builder()
                .serverName("FARM")
                .resourceGroupName("RTX 3090")
                .description("Test Cluster") // 혹시 모를 필수 필드 대비
                .build());

        ContainerImage testImage = containerImageRepository.save(ContainerImage.builder()
                .imageName("cuda")
                .imageVersion("11.8")
                .cudaVersion("11.8")         // 필수 필드 추가
                .description("CUDA Test")    // 필수 필드 추가
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
            requestSchedulerService.checkAndProcessExpiredRequests();
        }

        // --- Then: 검증 ---

        // 1. [삭제 검증] reqExpired
        Request deletedResult = requestRepository.findById(reqExpired.getRequestId()).get();
        assertThat(deletedResult.getStatus()).isEqualTo(Status.DELETED);
        assertThat(deletedResult.getUbuntuUid()).isNull();

        // 실제 삭제가 수행되었는지 UsedIdRepository를 통해 확인 (ID가 반납되어 삭제되었거나, 할당이 해제되었는지)
        // 로직상 ID를 releaseId() 하면 DB에서 삭제되는지, 아니면 상태만 바뀌는지에 따라 검증이 달라질 수 있음.
        // 일반적인 경우:
        assertThat(usedIdRepository.findById(uidExpired.getIdValue())).isEmpty();

        verify(ubuntuAccountService, times(1)).deleteUbuntuAccount("user-expired");
        verify(alarmService).sendAllAlerts(eq(testUser.getName()), eq(testUser.getEmail()), contains("삭제 안내"), anyString());
        verify(alarmService).sendAdminSlackNotification(eq("FARM"), contains("삭제 완료"));


        // 2. [알림 검증] 1일 전 (req1Day)
        Request result1Day = requestRepository.findById(req1Day.getRequestId()).get();
        assertThat(result1Day.getStatus()).isEqualTo(Status.FULFILLED);
        verify(alarmService).sendAllAlerts(eq(testUser.getName()), eq(testUser.getEmail()), contains("1일 전 안내"), anyString());
        verify(alarmService).sendAdminSlackNotification(eq("FARM"), contains("1일 전 알림"));


        // 3. [알림 검증] 3일 전 (req3Day)
        Request result3Day = requestRepository.findById(req3Day.getRequestId()).get();
        assertThat(result3Day.getStatus()).isEqualTo(Status.FULFILLED);
        verify(alarmService).sendAllAlerts(eq(testUser.getName()), eq(testUser.getEmail()), contains("3일 전 안내"), anyString());
        verify(alarmService).sendAdminSlackNotification(eq("FARM"), contains("3일 전 알림"));


        // 4. [알림 검증] 7일 전 (req7Day)
        Request result7Day = requestRepository.findById(req7Day.getRequestId()).get();
        assertThat(result7Day.getStatus()).isEqualTo(Status.FULFILLED);
        verify(alarmService).sendAllAlerts(eq(testUser.getName()), eq(testUser.getEmail()), contains("7일 전 안내"), anyString());
        verify(alarmService).sendAdminSlackNotification(eq("FARM"), contains("7일 전 알림"));


        // 5. [총 호출 횟수 검증]
        verify(alarmService, times(4)).sendAllAlerts(anyString(), anyString(), anyString(), anyString());
        verify(alarmService, times(4)).sendAdminSlackNotification(anyString(), anyString());
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

        // 1. DB에 먼저 저장 (ID 생성)
        Request savedReq = requestRepository.save(req);

        // 2. 상태 변경이 필요한 경우 별도 처리
        if (status == Status.FULFILLED) {
            savedReq.approve(testImage, testRg, 10L, "approved");
            savedReq.assignUbuntuUid(usedId);
            requestRepository.saveAndFlush(savedReq);
        } else if (status == Status.DELETED) {
            savedReq.approve(testImage, testRg, 10L, "approved");
            savedReq.assignUbuntuUid(usedId);
            savedReq.delete();
            savedReq.assignUbuntuUid(null);
            requestRepository.saveAndFlush(savedReq);
        }

        return savedReq;
    }
}