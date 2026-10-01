package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 로그인 비밀번호 교체 작업 등록 본문. 계정 원장·계정 Secret·떠 있는 컨테이너의 해시를 함께 바꾼다.
 *
 * @param requestId  비밀번호 재설정 신청 번호. 결과도 이 번호로 조회한다
 * @param passwdHash SHA-512 crypt 해시. 평문은 보내지 않는다
 */
public record PasswordChangeRegisterRequestDTO(
        @JsonProperty("request_id") Long requestId,
        String username,
        @JsonProperty("passwd_hash") String passwdHash
) {
    /** 해시가 로그에 찍히지 않게 한다. */
    @Override
    public String toString() {
        return "PasswordChangeRegisterRequestDTO[requestId=" + requestId + ", username=" + username + "]";
    }
}
