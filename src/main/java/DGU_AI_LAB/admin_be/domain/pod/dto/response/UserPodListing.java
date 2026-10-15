package DGU_AI_LAB.admin_be.domain.pod.dto.response;

import java.util.List;

/**
 * 사용자 Pod 목록을 한 번 읽은 결과.
 *
 * @param pods    사용자 Pod 전체의 상태
 * @param stopped 그중 멈춰서 스스로 돌아오지 않는 Pod
 */
public record UserPodListing(List<PodResponseDTO> pods, List<StoppedPodDTO> stopped) {
}
