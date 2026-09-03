package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.Getter;

/**
 * config-server의 /create-pod 실패 응답에서 파싱한 대상 노드(node) 정보를 함께 들고 다니는
 * 예외. 계정 삭제 보상 트랜잭션(tryCompensateDeleteUser)이 이 노드로 범위를 좁혀서 정리할 수
 * 있도록, 어느 farm에 배포를 시도했는지 알아야 해서 만들었다. node는 응답에 없거나 파싱에
 * 실패하면 null일 수 있다 — 그러면 호출자는 기존처럼(전체 farm 노드 훑기) 처리해야 한다.
 */
@Getter
public class PodCreationFailedException extends BusinessException {
    private final String node;

    public PodCreationFailedException(String message, ErrorCode errorCode, String node) {
        super(message, errorCode);
        this.node = node;
    }
}
