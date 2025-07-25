package DGU_AI_LAB.admin_be.domain.approval.service;

import DGU_AI_LAB.admin_be.domain.approval.dto.request.ApprovalCreateRequest;
import DGU_AI_LAB.admin_be.domain.approval.dto.response.ApprovalResponseDTO;
import DGU_AI_LAB.admin_be.domain.approval.entity.Approval;
import DGU_AI_LAB.admin_be.domain.approval.repository.ApprovalRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalRepository approvalRepository;
    private final UserRepository userRepository;
    private final ResourceGroupRepository resourceGroupRepository;
    private final RequestRepository requestRepository;

    public ApprovalResponseDTO getApprovalByUsername(String username) {
        Approval approval = approvalRepository
                .findFirstByUsernameOrderByCreatedAtDesc(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_APPROVAL_NOT_FOUND));

        return ApprovalResponseDTO.fromEntity(approval);
    }


    @Transactional
    public void createApproval(ApprovalCreateRequest request) {
        var user = userRepository.findById(request.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        var group = resourceGroupRepository.findById(request.resourceGroupId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_GROUP_NOT_FOUND));

        Approval approval = request.toEntity(user, group);
        approvalRepository.save(approval);

        if (request.requestId() != null) {
            Request updateRequest = requestRepository.findById((request.requestId()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_GROUP_NOT_FOUND));

            updateRequest.updateStatus(Status.APPROVED);
        }
    }
}
