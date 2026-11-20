package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.groups.service.GroupMembershipPolicy;
import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import DGU_AI_LAB.admin_be.domain.nodes.repository.NodeRepository;
import DGU_AI_LAB.admin_be.domain.portRequests.service.PortRequestService;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.TestPodCreateRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 관리자 테스트 Pod 간편 생성. 노드마다 테스트 신청 한 건을 PENDING으로 만들어 두면
 * {@link TestPodApprovalQueue}가 같은 계정의 신청을 한 건씩 기존 승인 경로로 처리한다.
 * 신청서·사용목적 입력과 신청서 채널 알림은 없다. 공유 그룹을 고르면 일반 신청과 같이 신청에 붙여 두고,
 * 승인 경로가 관리자 계정을 그 그룹에 넣는다.
 */
@Service
@RequiredArgsConstructor
public class AdminTestPodService {

    static final String PURPOSE = "관리자 테스트 Pod";
    static final long EXPIRES_IN_DAYS = 1;
    /** 테스트 Pod를 만들지 않는 서버. 노드를 직접 골라도 제외한다(운영 지시: LAB에는 발급하지 않음). */
    static final Set<String> EXCLUDED_SERVERS = Set.of("LAB");

    private final UserRepository userRepository;
    private final NodeRepository nodeRepository;
    private final ContainerImageRepository containerImageRepository;
    private final RequestRepository requestRepository;
    private final PortRequestService portRequestService;
    private final GroupRepository groupRepository;
    private final GroupMembershipPolicy groupMembershipPolicy;

    /** @return 만든 신청 번호들 */
    @Transactional
    public List<Long> create(Long adminUserId, TestPodCreateRequestDTO dto) {
        User admin = userRepository.findByIdForUpdate(adminUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (admin.getUbuntuUsername() == null || admin.getUbuntuUsername().isBlank()) {
            throw new BusinessException(ErrorCode.UBUNTU_USERNAME_NOT_ASSIGNED);
        }
        ContainerImage image = containerImageRepository.findById(dto.imageId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        List<Node> nodes = targetNodes(dto);
        if (nodes.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        List<Group> groups = chosenGroups(dto);
        // Pod가 만들어지면 고른 그룹에 속하게 된다 — 지금 속한 그룹과 합쳐 한도를 넘으면 받지 않는다(일반 신청과 같은 검사).
        groupMembershipPolicy.requireRoomFor(admin, groups);
        List<Integer> ports = dto.internalPorts() == null ? List.of() : dto.internalPorts().stream().distinct().toList();

        LocalDateTime expiresAt = LocalDateTime.now().plusDays(EXPIRES_IN_DAYS);
        return nodes.stream().map(node -> {
            ResourceGroup rg = node.getResourceGroup();
            Request req = Request.builder()
                    .user(admin).resourceGroup(rg).containerImage(image)
                    .usagePurpose(PURPOSE).formAnswers("{}").expiresAt(expiresAt)
                    .build();
            req.markAsTest(node.getNodeId());
            requestRepository.saveAndFlush(req); // 그룹·포트 신청이 신청 번호를 요구한다
            groups.forEach(req::addGroup);
            ports.forEach(p -> portRequestService.createPortRequest(req, rg, p, "테스트"));
            return req.getRequestId();
        }).toList();
    }

    /** 승인 대기 그룹은 그 그룹을 고른 마지막 신청이 거절되면 지워지므로, 지우는 쪽과 엇갈리지 않게 그룹 행을 잠그고 읽는다. */
    private List<Group> chosenGroups(TestPodCreateRequestDTO dto) {
        if (dto.groupIds() == null || dto.groupIds().isEmpty()) {
            return List.of();
        }
        var ids = new LinkedHashSet<>(dto.groupIds());
        List<Group> groups = groupRepository.findAllByIdForUpdate(ids);
        if (groups.size() != ids.size()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return groups;
    }

    private List<Node> targetNodes(TestPodCreateRequestDTO dto) {
        List<Node> nodes = dto.nodeIds() == null || dto.nodeIds().isEmpty()
                ? nodeRepository.findAll()
                : nodeRepository.findAllById(dto.nodeIds());
        boolean allGroups = dto.resourceGroupIds() == null || dto.resourceGroupIds().isEmpty();
        return nodes.stream()
                .filter(n -> !EXCLUDED_SERVERS.contains(n.getResourceGroup().getServerName()))
                .filter(n -> allGroups || dto.resourceGroupIds().contains(n.getResourceGroup().getRsgroupId()))
                .toList();
    }
}
