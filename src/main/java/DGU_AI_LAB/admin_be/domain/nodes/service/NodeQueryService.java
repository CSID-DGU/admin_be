package DGU_AI_LAB.admin_be.domain.nodes.service;

import DGU_AI_LAB.admin_be.domain.nodes.dto.response.NodeResponseDTO;
import DGU_AI_LAB.admin_be.domain.nodes.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NodeQueryService {

    private final NodeRepository nodeRepository;

    /**
     * 전체 노드 조회 (마이그레이션 후보 노드 선택 등에 사용)
     */
    @Transactional(readOnly = true)
    public List<NodeResponseDTO> getAllNodes() {
        return nodeRepository.findAll().stream()
                .map(NodeResponseDTO::fromEntity)
                .toList();
    }
}
