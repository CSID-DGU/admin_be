package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record UserCreationRequestDTO(
        // config-server가 작업 이력을 승인 1건 단위로 묶는 키. 안 보내면 config-server가
        // username+시각으로 임시 키를 만들어 쓰는데, 그러면 같은 승인의 계정 생성 이력과
        // Pod 생성 이력(/create-pod는 이 값을 보낸다)이 서로 다른 키로 갈라져
        // 승인 한 건의 소요시간을 산출할 수 없다.
        @JsonProperty("request_id")
        Long requestId,

        @JsonProperty("name")
        String username,
        @JsonProperty("passwd_base64")
        String passwordBase64,
        String gecos,
        @JsonProperty("primary_group_name")
        String primaryGroupName,
        @JsonProperty("enable_sudo")
        boolean enableSudo,
        @JsonProperty("supplementary_groups")
        List<SupplementaryGroup> supplementaryGroups
) {
    public record SupplementaryGroup(
            String name,
            Long gid
    ) {}
}
