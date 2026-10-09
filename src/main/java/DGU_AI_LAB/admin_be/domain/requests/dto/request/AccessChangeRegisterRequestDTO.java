package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 계정 접속 차단·해제 작업 등록 본문. 그 계정의 모든 컨테이너의 모든 접속 포트가 대상이다.
 *
 * @param requestId 접속 작업 번호. 결과도 이 번호로 조회한다
 * @param username  리눅스 계정
 * @param blocked   true면 차단, false면 해제
 */
public record AccessChangeRegisterRequestDTO(
        @JsonProperty("request_id") Long requestId,
        String username,
        boolean blocked
) {}
