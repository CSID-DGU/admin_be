package DGU_AI_LAB.admin_be.domain.requests.job;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.GroupChangeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.HomeDeleteRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.MigrateRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.PasswordChangeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.PortChangeRegisterRequestDTO;
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
 * <p>등록 요청이 닿았을 수 있는데 답을 받지 못하면 등록 메서드는 {@link JobRegistrationUnconfirmedException}을
 * 던진다. 작업이 등록돼 돌고 있을 수 있으므로 호출자는 등록되지 않았다고 단정하면 안 된다.
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
     * 로그인 비밀번호 교체 작업을 등록한다. 실행기는 계정 원장·계정 Secret·떠 있는 컨테이너를 같은 해시로 바꾸고,
     * 중간에 실패하면 옛 해시로 되돌린 뒤 작업을 실패로 끝낸다.
     *
     * @return 등록된 작업 번호. 응답에 없으면 null
     * @throws BusinessException 같은 재설정 신청의 작업이 아직 끝나지 않았거나(409, INVALID_REQUEST_STATUS) 등록이 실패한 경우
     */
    Long registerPasswordChange(PasswordChangeRegisterRequestDTO body);

    /**
     * 공용 그룹 작업(생성·멤버 추가·제거)을 등록한다. 실행기는 AD·계정 원장·떠 있는 컨테이너를 차례로
     * 맞추고, 이미 맞춰진 조각은 그대로 두므로 실패한 작업은 다시 등록하면 이어서 끝난다.
     *
     * @return 등록된 작업 번호. 응답에 없으면 null
     * @throws BusinessException 이름 충돌·없는 계정이나 그룹처럼 요청 내용 때문에 거절됐거나(각각의 오류 코드),
     *                           같은 번호의 작업이 아직 끝나지 않았거나(409, GROUP_OPERATION_IN_PROGRESS) 등록이 실패한 경우
     */
    Long registerGroupChange(GroupChangeRegisterRequestDTO body);

    /**
     * 보존 기간이 지난 홈 삭제 작업을 등록한다. 실행기는 그 계정의 컨테이너가 없고 홈 소유자가 보낸 uid일 때만
     * 지우며, 홈이 이미 없으면 성공으로 끝낸다. 계정과 uid는 그대로 둔다.
     *
     * @return 등록된 작업 번호. 응답에 없으면 null
     * @throws BusinessException 같은 번호의 작업이 아직 끝나지 않았거나(409, INVALID_REQUEST_STATUS) 등록이 실패한 경우
     */
    Long registerHomeDelete(HomeDeleteRegisterRequestDTO body);

    /**
     * 추가 포트 변경 작업을 등록한다. 실행기는 컨테이너를 다시 만들지 않고 빠진 포트를 닫고 새 포트를 연다.
     * 이미 맞춰진 포트는 그대로 두므로 실패한 작업은 다시 등록하면 이어서 끝난다.
     *
     * @return 등록된 작업 번호. 응답에 없으면 null
     * @throws BusinessException 같은 번호의 작업이 아직 끝나지 않았거나(409, INVALID_REQUEST_STATUS) 등록이 거절·실패한 경우
     */
    Long registerPortChange(PortChangeRegisterRequestDTO body);

    /**
     * 작업 결과를 조회한다. 등록 이력이 없으면 phase가 {@link JobResults#PHASE_NONE}으로 온다.
     *
     * @param kind      {@link JobResults#KIND_PROVISION}·{@link JobResults#KIND_REVOKE}·{@link JobResults#KIND_MIGRATE}·
     *                  {@link JobResults#KIND_PASSWORD}·{@link JobResults#KIND_GROUP}·{@link JobResults#KIND_HOME}·
     *                  {@link JobResults#KIND_PORT}
     * @param requestId 신청 번호. 비밀번호 교체는 비밀번호 재설정 신청 번호, 그룹 작업은 그룹 작업 번호, 홈 삭제는 홈 정리 번호,
     *                  포트 변경은 포트 작업 번호
     */
    JobResultResponseDTO getResult(String kind, Long requestId);

    /** 작업 단계 기록을 조회한다. 작업이 없으면 jobs가 빈 목록으로 온다. */
    JobStepsResponseDTO getSteps(String kind, Long requestId);
}
