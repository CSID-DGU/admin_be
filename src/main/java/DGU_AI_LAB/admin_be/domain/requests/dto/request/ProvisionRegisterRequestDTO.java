package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 제안 시스템(v2.0) 생성 작업 등록 본문. config-server가 이 작업을 받아 두고, 제어기가 계정과
 * 컨테이너를 단계별로 만든다.
 *
 * <p>account는 계정을 새로 만들어야 할 때만 담는다. 이미 리눅스 계정이 있는 사용자는 빼고 보내야
 * 계정 단계를 건너뛰고 컨테이너만 만든다 — 다시 만들면 UID가 바뀌어 기존 홈 디렉터리의 소유권이
 * 어긋난다(동기 경로가 계정 생성 API 자체를 건너뛰는 것과 같은 이유).
 * 
 * <p>supplementary_groups는 account 여부와 무관하게 Pod 생성 후 사용자를 추가할 보조 그룹들을 담는다.
 * account가 있을 때는 account.supplementary_groups에, 없을 때는 여기 최상위 필드에 담긴다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProvisionRegisterRequestDTO(
        @JsonProperty("request_id") Long requestId,
        String username,
        Account account,
        @JsonProperty("supplementary_groups") List<UserCreationRequestDTO.SupplementaryGroup> supplementaryGroups
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Account(
            @JsonProperty("passwd_hash") String passwordHash,
            String gecos,
            @JsonProperty("primary_group_name") String primaryGroupName,
            @JsonProperty("supplementary_groups") List<UserCreationRequestDTO.SupplementaryGroup> supplementaryGroups,
            @JsonProperty("expected_uid") Long expectedUid
    ) {}

    /** 계정 생성이 필요한 승인 — 동기 경로가 계정 생성 API에 보내던 것과 같은 값을 작업으로 넘긴다. */
    public static ProvisionRegisterRequestDTO withAccount(UserCreationRequestDTO creation) {
        return new ProvisionRegisterRequestDTO(
                creation.requestId(),
                creation.username(),
                new Account(creation.passwordHash(), creation.gecos(),
                        creation.primaryGroupName(), creation.supplementaryGroups(), creation.expectedUid()),
                null);
    }

    /** 계정을 재사용하는 승인 — 컨테이너만 만들고, 보조 그룹을 별도로 추가한다. */
    public static ProvisionRegisterRequestDTO podOnly(Long requestId, String username,
                                                      List<UserCreationRequestDTO.SupplementaryGroup> supplementaryGroups) {
        return new ProvisionRegisterRequestDTO(requestId, username, null, supplementaryGroups);
    }

    /** 계정을 재사용하는 승인(그룹 없음) — 컨테이너만 만든다. */
    public static ProvisionRegisterRequestDTO podOnly(Long requestId, String username) {
        return podOnly(requestId, username, null);
    }
}
