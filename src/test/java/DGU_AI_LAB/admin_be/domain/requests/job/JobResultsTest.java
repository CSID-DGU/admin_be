package DGU_AI_LAB.admin_be.domain.requests.job;

import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JobResults")
class JobResultsTest {

    private static JobResultResponseDTO result(Long jobId, String phase, String errorCode) {
        return new JobResultResponseDTO("41", JobResults.KIND_REVOKE, jobId, phase, errorCode, null, null);
    }

    @Test
    @DisplayName("작업 번호 없이 방금 상태가 바뀐 신청은 결과를 아직 보지 않는다 — 이전 작업의 결과가 보인다")
    void awaitingRegistration() {
        assertThat(JobResults.awaitingRegistration(null, LocalDateTime.now())).isTrue();
        assertThat(JobResults.awaitingRegistration(7L, LocalDateTime.now())).isFalse();
        assertThat(JobResults.awaitingRegistration(null, LocalDateTime.now().minus(JobResults.REGISTRATION_GRACE).minusSeconds(1)))
                .isFalse();
        assertThat(JobResults.awaitingRegistration(null, null)).isFalse();
    }

    @Test
    @DisplayName("등록한 번호와 다른 작업의 결과만 다른 작업으로 본다")
    void isFromOtherJob() {
        assertThat(JobResults.isFromOtherJob(7L, result(8L, JobResults.PHASE_SUCCESS, null))).isTrue();
        assertThat(JobResults.isFromOtherJob(7L, result(7L, JobResults.PHASE_SUCCESS, null))).isFalse();
        assertThat(JobResults.isFromOtherJob(null, result(8L, JobResults.PHASE_SUCCESS, null))).isFalse();
        assertThat(JobResults.isFromOtherJob(7L, result(null, JobResults.PHASE_NONE, null))).isFalse();
    }

    @Test
    @DisplayName("DEGRADED 오류 코드만 자원을 남긴 실패다")
    void isDegraded() {
        assertThat(JobResults.isDegraded(result(7L, JobResults.PHASE_FAIL, JobResults.ERROR_DEGRADED))).isTrue();
        assertThat(JobResults.isDegraded(result(7L, JobResults.PHASE_FAIL, "KDC_FAILED"))).isFalse();
        assertThat(JobResults.isDegraded(null)).isFalse();
    }

    @Test
    @DisplayName("연결 거부·주소 해석 실패는 요청이 닿지 않은 것이다(원인 사슬 어디에 있어도)")
    void neverReachedServer() {
        assertThat(JobResults.neverReachedServer(new BusinessException("x", ErrorCode.POD_DELETION_FAILED,
                new RuntimeException(new ConnectException("refused"))))).isTrue();
        assertThat(JobResults.neverReachedServer(new UnknownHostException("h"))).isTrue();
    }

    @Test
    @DisplayName("시간 초과·HTTP 오류는 요청이 닿았을 수 있어 아니다")
    void reachedOrUnknown() {
        assertThat(JobResults.neverReachedServer(new SocketTimeoutException("read"))).isFalse();
        assertThat(JobResults.neverReachedServer(new BusinessException("작업 등록 실패", ErrorCode.POD_DELETION_FAILED))).isFalse();
        assertThat(JobResults.neverReachedServer(null)).isFalse();
    }

    @Test
    @DisplayName("이미 없는 계정을 지우려다 실패한 것은 목표 상태에 도달한 것이다")
    void accountAlreadyAbsent() {
        assertThat(JobResults.isAccountAlreadyAbsent(result(7L, JobResults.PHASE_FAIL, "user not found"))).isTrue();
        assertThat(JobResults.isAccountAlreadyAbsent(result(7L, JobResults.PHASE_FAIL, "USER_NOT_FOUND"))).isTrue();
        assertThat(JobResults.isAccountAlreadyAbsent(result(7L, JobResults.PHASE_FAIL, "ACCOUNT_IN_USE"))).isFalse();
        assertThat(JobResults.isAccountAlreadyAbsent(result(7L, JobResults.PHASE_FAIL, null))).isFalse();
        assertThat(JobResults.isAccountAlreadyAbsent(null)).isFalse();
    }
}
