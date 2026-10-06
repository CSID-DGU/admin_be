package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 보존 기간이 지난 홈 삭제 작업 등록 본문.
 *
 * @param requestId   홈 정리 번호. 결과도 이 번호로 조회한다
 * @param expectedUid 이 계정의 uid. 실행기는 홈 소유자가 이 번호일 때만 지운다
 */
public record HomeDeleteRegisterRequestDTO(
        @JsonProperty("request_id") Long requestId,
        String username,
        @JsonProperty("expected_uid") Long expectedUid
) {
}
