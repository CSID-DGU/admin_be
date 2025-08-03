package DGU_AI_LAB.admin_be.domain.approval.service;

import DGU_AI_LAB.admin_be.domain.approval.dto.request.ApprovalCreateRequest;
import DGU_AI_LAB.admin_be.domain.approval.dto.response.ApprovalResponseDTO;
import DGU_AI_LAB.admin_be.domain.approval.dto.response.ApprovalToConfigResponseDTO;
import DGU_AI_LAB.admin_be.domain.approval.entity.Approval;
import DGU_AI_LAB.admin_be.domain.approval.repository.ApprovalRepository;
import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import DGU_AI_LAB.admin_be.domain.nodes.repository.NodeRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserAuthRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.UsedId;
import DGU_AI_LAB.admin_be.domain.users.repository.UsedIdRepository;
import DGU_AI_LAB.admin_be.domain.users.repository.UserGroupRepository;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.domain.users.service.UserService;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.UnauthorizedException;
import DGU_AI_LAB.admin_be.global.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalRepository approvalRepository;
    private final UserRepository userRepository;
    private final ResourceGroupRepository resourceGroupRepository;
    private final RequestRepository requestRepository;
    private final NodeRepository nodeRepository;
    private final UsedIdRepository usedIdRepository;
    private final UserService userService;

    private static final long UID_BASE = 10000;

    public ApprovalResponseDTO getApprovalByUsername(String username) {
        Approval approval = approvalRepository
                .findFirstByUsernameOrderByCreatedAtDesc(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_APPROVAL_NOT_FOUND));

        return ApprovalResponseDTO.fromEntity(approval);
    }

    public ApprovalResponseDTO userAuth(UserAuthRequestDTO request) {
        Approval approval = approvalRepository.findByUsername(request.username())
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.USER_NOT_FOUND));

        String encodedPassword = PasswordUtil.encodePassword(request.passwordBase64());

        if (!encodedPassword.equals(approval.getPassword())) {
            throw new UnauthorizedException(ErrorCode.INVALID_LOGIN_INFO);
        }

        return ApprovalResponseDTO.fromEntity(approval);
    }

    public ApprovalToConfigResponseDTO getApprovalConfigByUsername(String username) {
        Approval approval = approvalRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_APPROVAL_NOT_FOUND));

        ResourceGroup group = approval.getResourceGroup();

        List<Node> nodes = nodeRepository.findAllByResourceGroup(group);

        return ApprovalToConfigResponseDTO.fromEntity(approval, nodes);
    }

    @Transactional
    public void createApproval(ApprovalCreateRequest request) {
        var user = userRepository.findById(request.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        var group = resourceGroupRepository.findById(request.resourceGroupId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_GROUP_NOT_FOUND));

        String encodedPassword = PasswordUtil.encodePassword(request.password());
        String username = request.username();
        String groupname = request.groupname();

        var ids = userService.allocateNextAvailableUidGid(username, groupname);

        UsedId newUsedId = usedIdRepository.save(new UsedId(ids.uid()));
        Approval approval = request.toEntity(user, group, encodedPassword);
        approval.setUsedId(newUsedId);
        approvalRepository.save(approval);

        if (request.requestId() != null) {
            Request updateRequest = requestRepository.findById((request.requestId()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_GROUP_NOT_FOUND));

            updateRequest.updateStatus(Status.APPROVED);
        }
    }
}
