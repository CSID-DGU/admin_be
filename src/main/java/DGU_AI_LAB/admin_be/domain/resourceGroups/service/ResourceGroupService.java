package DGU_AI_LAB.admin_be.domain.resourceGroups.service;

import DGU_AI_LAB.admin_be.domain.gpus.dto.response.GpuTypeResponseDTO;
import DGU_AI_LAB.admin_be.domain.gpus.repository.GpuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResourceGroupService {

    private final GpuRepository gpuRepository;

    /**
     * GPU 기종별 리소스 정보 조회
     * 모든 노드가 사용 가능하다는 가정 하에, 각 GPU 모델의 리소스 그룹 내 노드 개수를 반환합니다.
     */
    public List<GpuTypeResponseDTO> getGpuTypeResources() {
        log.info("[getGpuTypeResources] GPU 기종별 리소스 정보 조회 시작");

        List<Object[]> gpuSummaries = gpuRepository.findGpuSummary();

        List<GpuTypeResponseDTO> response = gpuSummaries.stream()
                .map(obj -> {
                    String gpuModel = (String) obj[0];
                    Integer ramGb = (Integer) obj[1];
                    String resourceGroupName = (String) obj[2];
                    Long availableNodes = ((Number) obj[3]).longValue();

                    return GpuTypeResponseDTO.builder()
                            .gpuModel(gpuModel)
                            .ramGb(ramGb)
                            .resourceGroupName(resourceGroupName)
                            .availableNodes(availableNodes)
                            .isAvailable(true)
                            .build();
                })
                .collect(Collectors.toList());

        log.info("[getGpuTypeResources] GPU 기종별 리소스 정보 조회 완료. {}개 기종", response.size());
        return response;
    }
}
