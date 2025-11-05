package DGU_AI_LAB.admin_be.scheduler;

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
import DGU_AI_LAB.admin_be.domain.scheduler.RequestSchedulerService;
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
import static org.mockito.Mockito.*;

/**
 스케줄러를 위한 통합 테스트
 통합 테스트에서는 H2 database & create-drop 옵션을 사용합니다.
 */
@SpringBootTest(classes = AdminBeApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RequestSchedulerServiceTest {

    // --- System Under Test (SUT) ---
    @Autowired
    private RequestSchedulerService requestSchedulerService;

    // --- Mocks ---
    @MockitoBean
    private AlarmService alarmService;
    @MockitoBean
    private UbuntuAccountService ubuntuAccountService;

    // --- Real Repositories (for DB setup & verification) ---
    @Autowired private RequestRepository requestRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UsedIdRepository usedIdRepository;
    @Autowired private ResourceGroupRepository resourceGroupRepository;
    @Autowired private ContainerImageRepository containerImageRepository;

    // --- Test "Current" Time ---
    private final LocalDateTime MOCK_NOW = LocalDateTime.of(2025, 11, 10, 10, 30, 0);


    @Test
    @DisplayName("스케줄러: 만료/알림/삭제 로직 통합 테스트")
    void checkAndProcessExpiredRequests_IntegrationTest() {

        // 1. 공통 의존 데이터 생성
        User testUser = userRepository.save(User.builder()
                .email("test@dgu.ac.kr")
                .name("테스트유저")
                .password("test1234")
                .studentId("2020111111")
                .phone("010-1234-5678")
                .department("컴퓨터공학과")
                .build());

        ResourceGroup testRg = resourceGroupRepository.save(ResourceGroup.builder()
                .serverName("FARM")
                .resourceGroupName("RTX 3090 Ti Cluster")
                .description("Test Description")
                .build());

        ContainerImage testImage = containerImageRepository.save(ContainerImage.builder()
                .imageName("cuda")
                .imageVersion("11.8")
                .cudaVersion("11.8")
                .description("CUDA 11.8 test env")
                .build());

        // 2. UsedId 생성
        UsedId expiredUsedId = usedIdRepository.save(UsedId.builder().idValue(1001L).build());
        UsedId usedId1 = usedIdRepository.save(UsedId.builder().idValue(1002L).build());
        UsedId usedId7 = usedIdRepository.save(UsedId.builder().idValue(1003L).build());
        UsedId usedIdOk = usedIdRepository.save(UsedId.builder().idValue(1004L).build());

        // 3. 시나리오별 Request 데이터 생성 (헬퍼 메서드 대신 직접 생성)
        Request expiredRequest = createTestRequest(MOCK_NOW.minusDays(1), Status.FULFILLED, expiredUsedId, "expired-user", testUser, testRg, testImage);
        Request request1Day = createTestRequest(MOCK_NOW.plusDays(1).withHour(12), Status.FULFILLED, usedId1, "1day-user", testUser, testRg, testImage);
        Request request7Day = createTestRequest(MOCK_NOW.plusDays(7).withHour(14), Status.FULFILLED, usedId7, "7day-user", testUser, testRg, testImage);
        Request requestOk = createTestRequest(MOCK_NOW.plusDays(30), Status.FULFILLED, usedIdOk, "ok-user", testUser, testRg, testImage);
        Request requestPending = createTestRequest(MOCK_NOW.minusDays(1), Status.PENDING, null, "pending-user", testUser, testRg, testImage);
        Request requestDeleted = createTestRequest(MOCK_NOW.minusDays(10), Status.DELETED, null, "deleted-user", testUser, testRg, testImage);


        // --- Given (Arrange) ---
        // 1. LocalDateTime.now()를 MOCK_NOW로 고정
        try (MockedStatic<LocalDateTime> mockedTime = Mockito.mockStatic(LocalDateTime.class, Mockito.CALLS_REAL_METHODS)) {

            mockedTime.when(LocalDateTime::now).thenReturn(MOCK_NOW);

            // 2. 현재 DB 상태 확인
            assertThat(requestRepository.count()).isEqualTo(6);
            assertThat(usedIdRepository.count()).isEqualTo(4);

            // --- When (Act) ---
            // 3. 스케줄러 메서드 직접 호출
            requestSchedulerService.checkAndProcessExpiredRequests();
        }

        // --- Then (Assert) ---
        Request deletedReq = requestRepository.findById(expiredRequest.getRequestId()).get();
        Request verifiedRequest1Day = requestRepository.findById(request1Day.getRequestId()).get();
        Request verifiedRequest7Day = requestRepository.findById(request7Day.getRequestId()).get();
        Request verifiedRequestOk = requestRepository.findById(requestOk.getRequestId()).get();
        Request verifiedRequestPending = requestRepository.findById(requestPending.getRequestId()).get();
        Request verifiedRequestDeleted = requestRepository.findById(requestDeleted.getRequestId()).get();


        // **검증 1: [삭제 대상] expiredRequest**
        assertThat(deletedReq.getStatus()).isEqualTo(Status.DELETED);
        assertThat(deletedReq.getUbuntuUid()).isNull();
        assertThat(usedIdRepository.findById(expiredUsedId.getIdValue())).isEmpty();
        verify(ubuntuAccountService, times(1)).deleteUbuntuAccount(eq("expired-user"));
        verify(alarmService, times(1)).sendAllAlerts(eq(testUser.getName()), eq(testUser.getEmail()), contains("삭제 안내"), anyString());
        verify(alarmService, times(1)).sendAdminSlackNotification(eq(testRg.getServerName()), contains("삭제 완료"));


        // **검증 2: [1일 전 알림] request1Day**
        assertThat(verifiedRequest1Day.getStatus()).isEqualTo(Status.FULFILLED);
        verify(alarmService, times(1)).sendAllAlerts(eq(testUser.getName()), eq(testUser.getEmail()), contains("1일 전 안내"), anyString());
        verify(alarmService, times(1)).sendAdminSlackNotification(eq(testRg.getServerName()), contains("1일 전 알림"));


        // **검증 3: [7일 전 알림] request7Day**
        assertThat(verifiedRequest7Day.getStatus()).isEqualTo(Status.FULFILLED);
        verify(alarmService, times(1)).sendAllAlerts(eq(testUser.getName()), eq(testUser.getEmail()), contains("7일 전 안내"), anyString());
        verify(alarmService, times(1)).sendAdminSlackNotification(eq(testRg.getServerName()), contains("7일 전 알림"));


        // **검증 4: [무시 대상] requestOk, requestPending, requestDeleted**
        assertThat(verifiedRequestOk.getStatus()).isEqualTo(Status.FULFILLED);
        assertThat(verifiedRequestPending.getStatus()).isEqualTo(Status.PENDING);
        assertThat(verifiedRequestDeleted.getStatus()).isEqualTo(Status.DELETED);


        // **검증 5: [전체 호출 횟수 검증]**
        verify(ubuntuAccountService, times(1)).deleteUbuntuAccount(anyString());
        verify(alarmService, times(3)).sendAllAlerts(anyString(), anyString(), anyString(), anyString());
        verify(alarmService, times(3)).sendAdminSlackNotification(anyString(), anyString());
    }

    // --- 테스트 데이터 생성을 위한 헬퍼 메서드 ---
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

        if (status == Status.FULFILLED) {
            req.approve(testImage, testRg, 10L, "test approve");
            req.assignUbuntuUid(usedId);
        } else if (status == Status.PENDING) { // 기본값이 PENDING
        } else if (status == Status.DELETED) {
            req.approve(testImage, testRg, 10L, "test approve");
            req.assignUbuntuUid(usedId); // 삭제 전 UID가 있었다고 가정
            req.delete(); // DELETED로 상태 변경
            req.assignUbuntuUid(null); // UID 반납
        }
        return requestRepository.saveAndFlush(req);
    }
}