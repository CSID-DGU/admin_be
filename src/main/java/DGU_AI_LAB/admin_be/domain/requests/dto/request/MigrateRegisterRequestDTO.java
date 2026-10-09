package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * config-server 마이그레이션 작업 등록 본문. 비율·force가 없으면 키를 빼 config-server 기본값(비율 0.2)을 쓴다.
 * recreate면 노드를 고르지 않고 현재 노드에서 Pod를 다시 만든다(재시작).
 * accessBlocked면 새 Pod의 접속 포트를 막힌 채로 만든다(이용 정지 중인 사용자).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MigrateRegisterRequestDTO(
        @JsonProperty("request_id") Long requestId,
        @JsonProperty("pod_name") String podName,
        String username,
        List<String> nodes,
        @JsonProperty("min_improvement_ratio") Double minImprovementRatio,
        Boolean force,
        Boolean recreate,
        @JsonProperty("keep_changes") Boolean keepChanges,
        @JsonProperty("access_blocked") Boolean accessBlocked
) {
    /** 다른 노드로 옮기는 작업. */
    public static MigrateRegisterRequestDTO move(Long requestId, String podName, String username, List<String> nodes,
                                                 Double minImprovementRatio, Boolean force) {
        return new MigrateRegisterRequestDTO(requestId, podName, username, nodes, minImprovementRatio, force, null, null, null);
    }

    /** 현재 노드에서 다시 만드는 작업. keepChanges면 컨테이너 변경분을 그 노드에 구워 새 Pod에 이어 준다. */
    public static MigrateRegisterRequestDTO restart(Long requestId, String podName, String username, boolean keepChanges) {
        return new MigrateRegisterRequestDTO(requestId, podName, username, null, null, null, true, keepChanges, null);
    }

    /** 접속이 막혀 있어야 하는 사용자의 작업이면 그 표시를 단다. 아니면 키를 뺀다. */
    public MigrateRegisterRequestDTO accessBlocked(boolean blocked) {
        return new MigrateRegisterRequestDTO(requestId, podName, username, nodes, minImprovementRatio, force, recreate,
                keepChanges, blocked ? Boolean.TRUE : null);
    }
}
