package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreatePodRequestDTO(
        @JsonProperty("username")
        String username,
        // 한 사용자가 컨테이너를 여러 개 동시에 신청할 수 있어, config-server가 진행 상황을
        // username이 아니라 이 값으로 추적한다 — 그래야 서로 다른 신청의 진행 상황이 안 섞인다.
        @JsonProperty("request_id")
        Long requestId
) {}