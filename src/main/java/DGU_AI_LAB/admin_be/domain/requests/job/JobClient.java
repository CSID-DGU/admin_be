package DGU_AI_LAB.admin_be.domain.requests.job;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.MigrateRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ProvisionRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RevokeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobStepsResponseDTO;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;

/**
 * 계정·컨테이너 작업을 등록하고 결과를 조회하는 창구. 실제 실행은 작업 실행기(config-server의 제어기)가 한다.
 * 결과를 어떻게 읽을지(등록 대기, 다른 작업의 결과, DEGRADED 등)는 {@link JobResults}가 정한다.
 *
 * <p>baseline·noprobe·full 세 방식이 모두 이 창구를 쓴다. 방식 차이(재시도, 결과 확인, 접근 시험)는 실행기 쪽에서만
 * 나므로 호출자는 방식을 모른다.
 */
public interface JobClient {

    /**
     * 생성 작업을 등록한다. 등록만 하고 돌아오므로 계정·컨테이너가 아직 만들어지지 않은 상태에서 반환된다.
     *
     * @return 등록된 작업 번호. 응답에 없으면 null
     * @throws BusinessException 같은 신청의 생성 작업이 아직 끝나지 않았거나(409, INVALID_REQUEST_STATUS) 등록이 실패한 경우
     */
    Long registerProvision(ProvisionRegisterRequestDTO body);

    /**
     * 마이그레이션 작업을 등록한다.
     *
     * @return 등록된 작업 번호. 응답에 없으면 null
     * @throws BusinessException 같은 신청의 마이그레이션 작업이 아직 끝나지 않았거나(409) 등록이 거절·실패한 경우
     */
    Long registerMigrate(MigrateRegisterRequestDTO body);

    /**
     * 회수 작업(컨테이너, deleteAccount면 계정까지)을 등록한다.
     *
     * @param failureCode 등록이 거절·실패했을 때 던질 오류 코드
     * @return 등록된 작업 번호. 응답에 없으면 null
     * @throws BusinessException 같은 신청의 회수 작업이 아직 끝나지 않았거나(409, INVALID_REQUEST_STATUS) 등록이 실패한 경우
     */
    Long registerRevoke(RevokeRegisterRequestDTO body, ErrorCode failureCode);

    /**
     * 작업 결과를 조회한다. 등록 이력이 없으면 phase가 {@link JobResults#PHASE_NONE}으로 온다.
     *
     * @param kind {@link JobResults#KIND_PROVISION}·{@link JobResults#KIND_REVOKE}·{@link JobResults#KIND_MIGRATE}
     */
    JobResultResponseDTO getResult(String kind, Long requestId);

    /** 작업 단계 기록을 조회한다. 작업이 없으면 jobs가 빈 목록으로 온다. */
    JobStepsResponseDTO getSteps(String kind, Long requestId);
}
