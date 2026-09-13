package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 제안 시스템(v2.0) 회수 작업 등록 본문. 컨테이너(Service·NodePort·Pod·그 노드의 keytab)를 회수하고,
 * deleteAccount가 참이면 계정과 Kerberos까지 회수한다. 홈 디렉터리는 보존한다.
 *
 * <p>nodeName은 keytab을 지울 노드다. 모르면 비워도 되며, 그때는 지운 컨테이너가 있던 노드를 쓴다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RevokeRegisterRequestDTO(
        @JsonProperty("request_id") Long requestId,
        @JsonProperty("pod_name") String podName,
        String username,
        @JsonProperty("node_name") String nodeName,
        @JsonProperty("delete_account") boolean deleteAccount
) {}
