package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.RevokeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Ubuntu 서버 계정 회수 서비스. config-server에 계정 회수 작업을 등록하고 결과를 기다린다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UbuntuAccountService {

    private final OperationJobService operationJobService;

    /**
     * 우분투 계정과 Kerberos principal을 회수한다. 홈 디렉터리는 보존한다. 이미 없는 계정은 삭제된 것으로 본다.
     *
     * @param nodeName  이 계정의 keytab이 배포된 farm 노드. 모르면 config-server가 회수를 보류한다
     *                  (모든 farm 노드를 훑으면 같은 이름의 무관한 레거시 계정까지 건드릴 수 있다).
     * @param requestId 작업을 식별하는 신청 번호. 계정은 웹 계정에 귀속되지만 작업은 신청 번호로만
     *                  등록되므로, 호출자는 그 노드에서 이 계정을 마지막으로 쓴 신청 번호를 넘긴다.
     */
    public void deleteUbuntuAccount(String username, String nodeName, Long requestId) {
        if (requestId == null) {
            throw new BusinessException("신청 번호 없이 계정을 회수할 수 없습니다: " + username,
                    ErrorCode.UBUNTU_USER_DELETION_FAILED);
        }
        log.info("계정 회수 작업 요청: {}, node={}, requestId={}", username, nodeName, requestId);
        operationJobService.revokeAndWait(
                new RevokeRegisterRequestDTO(requestId, null, username, nodeName, true),
                ErrorCode.UBUNTU_USER_DELETION_FAILED);
        log.info("계정 회수 완료: {}", username);
    }
}
