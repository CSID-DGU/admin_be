package DGU_AI_LAB.admin_be.domain.warnings.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.users.entity.Role;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.domain.warnings.dto.WarningStatusResponseDTO;
import DGU_AI_LAB.admin_be.domain.warnings.entity.UserSuspension;
import DGU_AI_LAB.admin_be.domain.warnings.entity.UserWarning;
import DGU_AI_LAB.admin_be.domain.warnings.entity.WarningType;
import DGU_AI_LAB.admin_be.domain.warnings.repository.UserSuspensionRepository;
import DGU_AI_LAB.admin_be.domain.warnings.repository.UserWarningRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WarningServiceTest {

    private static final Long USER_ID = 5L;
    private static final Long ADMIN_ID = 1L;

    @Mock private UserWarningRepository warningRepository;
    @Mock private UserSuspensionRepository suspensionRepository;
    @Mock private UserRepository userRepository;
    @Mock private AccessEnforcementService accessEnforcementService;
    @Mock private AlarmService alarmService;
    @Mock private MessageUtils messageUtils;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;

    private final List<UserWarning> rows = new ArrayList<>();
    private final List<UserSuspension> suspensions = new ArrayList<>();
    private WarningService service;
    private User user;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        service = new WarningService(warningRepository, suspensionRepository, userRepository, accessEnforcementService,
                alarmService, messageUtils, transactionManager);

        user = User.builder().email("alice@dgu.ac.kr").name("alice").ubuntuUsername("alice").build();
        ReflectionTestUtils.setField(user, "userId", USER_ID);
        User admin = User.builder().email("admin@dgu.ac.kr").name("관리자").build();
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        // 문구 키를 그대로 돌려줘 어떤 안내가 나갔는지 본다.
        when(messageUtils.get(any(), any(Object[].class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 대장과 정지를 메모리에 둔다. 저장하면 번호가 붙는다(IDENTITY).
        when(warningRepository.save(any(UserWarning.class))).thenAnswer(invocation -> {
            UserWarning saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "warningId", (long) rows.size() + 1);
            rows.add(saved);
            return saved;
        });
        when(warningRepository.findAllByUser_UserIdOrderByWarningIdAsc(USER_ID)).thenAnswer(invocation -> List.copyOf(rows));
        when(suspensionRepository.save(any(UserSuspension.class))).thenAnswer(invocation -> {
            suspensions.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(suspensionRepository.findByWarning_WarningId(any())).thenAnswer(invocation -> suspensions.stream()
                .filter(s -> s.getWarning().getWarningId().equals(invocation.getArgument(0))).findFirst());
        when(suspensionRepository.findFirstByUser_UserIdAndEndsAtAfterOrderByEndsAtDesc(eq(USER_ID), any()))
                .thenAnswer(invocation -> suspensions.stream()
                        .filter(s -> s.getEndsAt().isAfter(invocation.<LocalDateTime>getArgument(1)))
                        .max(Comparator.comparing(UserSuspension::getEndsAt)));
    }

    private WarningStatusResponseDTO grant() {
        return service.grant(ADMIN_ID, USER_ID, "절차 미준수");
    }

    private static void assertCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(expected);
    }

    @Test
    @DisplayName("첫 경고는 경고만 남기고 정지하지 않는다")
    void firstWarningOnlyWarns() {
        WarningStatusResponseDTO status = grant();

        assertThat(status.count()).isEqualTo(1);
        assertThat(status.suspendedUntil()).isNull();
        assertThat(status.nextSuspensionDays()).isEqualTo(7);
        assertThat(suspensions).isEmpty();
        verify(alarmService).notifyUser("alice", "alice@dgu.ac.kr",
                "notification.user.warning.granted.subject", "notification.user.warning.granted.body");
        verify(accessEnforcementService).enforce(USER_ID);
    }

    @Test
    @DisplayName("두 번째 경고부터 그 시각에 7 × (횟수 − 1)일 정지가 시작된다")
    void secondAndThirdWarningsSuspend() {
        grant();
        WarningStatusResponseDTO second = grant();
        assertThat(second.count()).isEqualTo(2);
        assertThat(second.suspendedUntil()).isCloseTo(LocalDateTime.now().plusDays(7), within(5, ChronoUnit.SECONDS));

        WarningStatusResponseDTO third = grant();
        assertThat(third.count()).isEqualTo(3);
        assertThat(third.suspendedUntil()).isCloseTo(LocalDateTime.now().plusDays(14), within(5, ChronoUnit.SECONDS));
        assertThat(third.deductible()).isTrue();
        verify(alarmService, times(2)).notifyUser(eq("alice"), any(), any(),
                eq("notification.user.warning.suspended.body"));
        verify(accessEnforcementService, times(3)).enforce(USER_ID);
    }

    @Test
    @DisplayName("관리자에게는 경고를 줄 수 없다")
    void adminCannotBeWarned() {
        ReflectionTestUtils.setField(user, "role", Role.ADMIN);

        assertCode(this::grant, ErrorCode.WARNING_TARGET_IS_ADMIN);
        assertThat(rows).isEmpty();
        verifyNoInteractions(accessEnforcementService, alarmService);
    }

    @Test
    @DisplayName("접속 차단 등록이 실패해도 경고는 남는다 — 주기 점검이 다시 맞춘다")
    void warningSurvivesEnforcementFailure() {
        grant();
        doThrow(new BusinessException(ErrorCode.ACCESS_CHANGE_FAILED)).when(accessEnforcementService).enforce(USER_ID);

        WarningStatusResponseDTO status = grant();

        assertThat(status.count()).isEqualTo(2);
        assertThat(suspensions).hasSize(1);
    }

    @Test
    @DisplayName("3회가 쌓이기 전에는 차감할 수 없다")
    void deductionRefusedBeforeThree() {
        grant();
        grant();

        assertCode(() -> service.deduct(ADMIN_ID, USER_ID, "정상 신고"), ErrorCode.WARNING_NOT_DEDUCTIBLE);
        assertThat(rows).hasSize(2);
    }

    @Test
    @DisplayName("차감은 횟수만 줄이고 진행 중인 정지는 그대로 둔다")
    void deductionKeepsRunningSuspension() {
        grant();
        grant();
        WarningStatusResponseDTO third = grant();

        WarningStatusResponseDTO status = service.deduct(ADMIN_ID, USER_ID, "정상 신고");

        assertThat(status.count()).isEqualTo(2);
        assertThat(status.suspendedUntil()).isEqualTo(third.suspendedUntil());
        assertThat(status.history().get(0).type()).isEqualTo(WarningType.DEDUCT);
        verify(alarmService).notifyUser(eq("alice"), any(), eq("notification.user.warning.reduced.subject"), any());
    }

    @Test
    @DisplayName("경고를 취소하면 횟수에서 빠지고 그 경고로 시작된 정지가 바로 끝난다")
    void cancelEndsTheSuspensionItCaused() {
        grant();
        WarningStatusResponseDTO second = grant();
        Long warningId = second.history().get(0).warningId();

        WarningStatusResponseDTO status = service.cancel(ADMIN_ID, USER_ID, warningId, "잘못 부여");

        assertThat(status.count()).isEqualTo(1);
        assertThat(status.suspendedUntil()).isNull();
        assertThat(status.history()).filteredOn(entry -> entry.warningId().equals(warningId))
                .singleElement().matches(WarningStatusResponseDTO.Entry::canceled);
        assertThat(status.history().get(0).canceledWarningId()).isEqualTo(warningId);
        verify(accessEnforcementService, times(3)).enforce(USER_ID);
    }

    @Test
    @DisplayName("겹친 정지 중 하나의 경고만 취소하면 나머지 정지는 이어진다")
    void cancelingOneOfOverlappingSuspensionsKeepsTheOther() {
        grant();
        WarningStatusResponseDTO second = grant();
        WarningStatusResponseDTO third = grant();
        Long thirdId = third.history().get(0).warningId();

        WarningStatusResponseDTO status = service.cancel(ADMIN_ID, USER_ID, thirdId, "잘못 부여");

        assertThat(status.count()).isEqualTo(2);
        assertThat(status.suspendedUntil()).isEqualTo(second.suspendedUntil());
    }

    @Test
    @DisplayName("같은 경고를 두 번 취소할 수 없고, 부여가 아닌 줄이나 없는 번호는 취소할 수 없다")
    void cancelRejectsInvalidTargets() {
        Long warningId = grant().history().get(0).warningId();
        Long cancelRowId = service.cancel(ADMIN_ID, USER_ID, warningId, "잘못 부여").history().get(0).warningId();

        assertCode(() -> service.cancel(ADMIN_ID, USER_ID, warningId, "또"), ErrorCode.WARNING_ALREADY_CANCELED);
        assertCode(() -> service.cancel(ADMIN_ID, USER_ID, cancelRowId, "취소의 취소"), ErrorCode.WARNING_NOT_FOUND);
        assertCode(() -> service.cancel(ADMIN_ID, USER_ID, 999L, "없음"), ErrorCode.WARNING_NOT_FOUND);
    }

    @Test
    @DisplayName("관리자 조회에는 처리한 관리자 이름이 있고 본인 조회에는 없다")
    void ownStatusHidesIssuer() {
        grant();

        assertThat(service.getStatus(USER_ID).history().get(0).issuedByName()).isEqualTo("관리자");
        assertThat(service.getOwnStatus(USER_ID).history().get(0).issuedByName()).isNull();
        assertThat(service.getOwnStatus(USER_ID).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("없는 사용자의 경고 현황은 조회할 수 없다")
    void statusOfUnknownUser() {
        assertCode(() -> service.getStatus(404L), ErrorCode.USER_NOT_FOUND);
    }
}
