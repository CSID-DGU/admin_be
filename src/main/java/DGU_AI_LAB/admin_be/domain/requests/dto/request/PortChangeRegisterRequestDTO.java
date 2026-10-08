package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 추가 포트 변경 작업 등록 본문.
 *
 * @param requestId 포트 작업 번호. 결과도 이 번호로 조회한다
 * @param username  컨테이너 주인의 리눅스 계정
 * @param podName   포트를 바꿀 컨테이너
 * @param ports     바뀐 뒤 추가 포트 전체 목록(빈 목록이면 추가 포트를 모두 뺀다)
 */
public record PortChangeRegisterRequestDTO(
        @JsonProperty("request_id") Long requestId,
        String username,
        @JsonProperty("pod_name") String podName,
        List<Port> ports
) {
    public record Port(
            @JsonProperty("internal_port") Integer internalPort,
            @JsonProperty("usage_purpose") String usagePurpose
    ) {}
}
