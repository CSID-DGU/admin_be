package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import DGU_AI_LAB.admin_be.domain.nodes.repository.NodeRepository;
import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import DGU_AI_LAB.admin_be.domain.portRequests.repository.PortRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.AcceptInfoResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final PortRequestRepository portRequestRepository;
    private final NodeRepository nodeRepository;

    /**
     * ubuntu username 중복 검사 — 웹 계정 기준이다.
     * 유저네임은 신청이 아니라 웹 계정에 귀속되므로 "새 계정이 이 이름을 쓸 수 있는가"를 답한다.
     */
    @Transactional(readOnly = true)
    public boolean isUbuntuUsernameAvailable(String username) {
        return !userRepository.existsByUbuntuUsername(username);
    }

    /**
     * config server용 acceptinfo — username 기준 (레거시).
     * 한 사용자가 신청을 여러 개 동시에 가질 수 있게 되면서 "가장 최근 열린 신청"을
     * 임의로 골라주는 방식이 됐다 — config-server가 지금 만들고/마이그레이션하는
     * 신청이 아닌 엉뚱한 신청의 설정을 가져올 위험이 있다. 가능하면
     * {@link #getAcceptInfoByRequestId}를 쓴다. request_id를 아직 못 넘기는 옛
     * 호출을 위해서만 남겨둔다.
     */
    @Transactional(readOnly = true)
    public AcceptInfoResponseDTO getAcceptInfo(String username) {
        log.info("사용자 승인 정보 조회를 시작합니다. username: {}", username);

        // 승인 처리(PROCESSING) 중에 config-server가 이 API로 스펙을 되물으므로 PENDING/PROCESSING까지
        // 포함한 openStatuses로 찾는다. 종료된 이력 행은 같은 유저네임을 공유하므로 제외해야 한다.
        Request request = requestRepository
                .findByUbuntuUsernameAndStatusInOrderByRequestIdDesc(username, Status.openStatuses())
                .stream().findFirst()
                .orElseThrow(() -> {
                    log.warn("사용자 '{}'에 대한 승인 정보가 존재하지 않습니다.", username);
                    return new BusinessException(ErrorCode.USER_APPROVAL_NOT_FOUND);
                });

        log.info("사용자 '{}'에 대한 AcceptInfoResponseDTO 생성을 완료했습니다.", username);
        return buildAcceptInfo(request);
    }

    /**
     * config server용 acceptinfo — requestId 기준. 정확히 그 신청 하나만 짚으므로
     * 한 사용자가 신청을 여러 개 동시에 가져도 모호함이 없다.
     */
    @Transactional(readOnly = true)
    public AcceptInfoResponseDTO getAcceptInfoByRequestId(Long requestId) {
        log.info("사용자 승인 정보 조회를 시작합니다. requestId: {}", requestId);

        // getAcceptInfo(username)와 동일하게 openStatuses로 좁힌다 — 안 좁히면 취소/만료돼
        // 종료된(DENIED/DELETED) 신청의 스펙도 그대로 내줘서, config-server가 이미 끝난
        // 신청을 근거로 Pod/계정을 구성할 위험이 있다.
        Request request = requestRepository.findById(requestId)
                .filter(r -> Status.openStatuses().contains(r.getStatus()))
                .orElseThrow(() -> {
                    log.warn("requestId '{}'에 대한 승인 정보가 존재하지 않습니다.", requestId);
                    return new BusinessException(ErrorCode.USER_APPROVAL_NOT_FOUND);
                });

        log.info("requestId '{}'에 대한 AcceptInfoResponseDTO 생성을 완료했습니다.", requestId);
        return buildAcceptInfo(request);
    }

    private AcceptInfoResponseDTO buildAcceptInfo(Request request) {
        log.debug("요청 정보를 성공적으로 찾았습니다. 요청 ID: {}", request.getRequestId());

        List<PortRequests> portRequests = portRequestRepository.findByRequestRequestId(request.getRequestId());
        log.debug("요청 '{}'에 속한 포트 요청 {}개를 조회했습니다.", request.getRequestId(), portRequests.size());

        List<Node> nodes = nodeRepository.findAllByResourceGroup(request.getResourceGroup());
        log.debug("리소스 그룹 소속 노드 {}개를 조회했습니다.", nodes.size());

        return AcceptInfoResponseDTO.fromEntity(request, portRequests, nodes);
    }

}
