package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 컨테이너 생성 진행 상황. 실행기가 주는 값을 그대로 전한다.
 *
 * @param stage   진행 단계(started, pulling_image, ready, failed 등). 생성 이력이 없으면 unknown
 * @param message 단계 설명. 실패면 원인 분류만 담긴다(상세 오류 원문은 실행기 로그에만 남는다)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProvisioningStatusResponseDTO(
        String stage,
        String message
) {
}
