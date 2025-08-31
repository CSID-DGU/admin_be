package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PvcRequestDTO(
        /**
         * infra 파트와 합의된 사항으로, username과 storage의 변수명을 바꾸면 안 됩니다.
         * 노션 > kubernetes 진행 > config-server 기준 API 명세 문서 참조 (25.08.30) by 소은
         * @JsonProperty 어노테이션을 사용하면 DTO의 필드명은 ubuntuUsername과 volumeSizeGiB로 유지하면서,
         * 실제로 외부 API로 보낼 JSON 데이터의 키를 username과 storage로 지정할 수 있습니다.
         */
        @JsonProperty("username")
        String ubuntuUsername,
        @JsonProperty("storage")
        Long volumeSizeGiB
) {}