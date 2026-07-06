package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import DGU_AI_LAB.admin_be.domain.nodes.repository.NodeRepository;
import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import DGU_AI_LAB.admin_be.domain.portRequests.repository.PortRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.AcceptInfoResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigRequestService {

    private final RequestRepository requestRepository;
    private final PortRequestRepository portRequestRepository;
    private final NodeRepository nodeRepository;

    /** ubuntu username 중복 검사 */
    @Transactional(readOnly = true)
    public boolean isUbuntuUsernameAvailable(String username) {
        return !requestRepository.existsByUbuntuUsername(username);
    }

    /** config server용 acceptinfo */
    @Transactional(readOnly = true)
    public AcceptInfoResponseDTO getAcceptInfo(String username) {
        log.info("사용자 승인 정보 조회를 시작합니다. username: {}", username);

        Request request = requestRepository.findByUbuntuUsername(username)
                .orElseThrow(() -> {
                    log.warn("사용자 '{}'에 대한 승인 정보가 존재하지 않습니다.", username);
                    return new BusinessException(ErrorCode.USER_APPROVAL_NOT_FOUND);
                });

        log.debug("사용자 '{}'의 요청 정보를 성공적으로 찾았습니다. 요청 ID: {}", username, request.getRequestId());

        List<PortRequests> portRequests = portRequestRepository.findByRequestRequestId(request.getRequestId());
        log.debug("요청 '{}'에 속한 포트 요청 {}개를 조회했습니다.", request.getRequestId(), portRequests.size());

        List<Node> nodes = nodeRepository.findAllByResourceGroup(request.getResourceGroup());
        log.debug("리소스 그룹 소속 노드 {}개를 조회했습니다.", nodes.size());

        AcceptInfoResponseDTO response = AcceptInfoResponseDTO.fromEntity(request, portRequests, nodes);
        log.info("사용자 '{}'에 대한 AcceptInfoResponseDTO 생성을 완료했습니다.", username);

        return response;
    }

}
