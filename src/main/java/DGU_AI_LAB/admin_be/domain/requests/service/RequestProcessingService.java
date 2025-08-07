package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.approval.dto.response.ApprovalResponseDTO;
import DGU_AI_LAB.admin_be.domain.approval.entity.Approval;
import DGU_AI_LAB.admin_be.domain.approval.repository.ApprovalRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RequestApproveDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RequestProcessingService {

    private final UserRepository userRepository;
    private final ApprovalRepository approvalRepository;
    private final ResourceGroupRepository resourceGroupRepository;
    private final PasswordEncoder passwordEncoder;
    private final RequestRepository requestRepository;

    @Value("${app.default-password}")
    private String defaultPassword;

//    @Transactional
//    public ApprovalResponseDTO approveRequest(RequestApproveDTO request) {
//        // 자원그룹 조회
//        ResourceGroup resourceGroup = resourceGroupRepository.findById(request.resource_group_id())
//                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
//
//        // 사용자 및 컨테이너 정보 저장
//        User user = userRepository.save(request.toUserEntity(passwordEncoder, defaultPassword));
//        Approval approval = request.toApprovalEntity(passwordEncoder, defaultPassword, resourceGroup, user);
//        approvalRepository.save(approval);
//
//        return ApprovalResponseDTO.fromEntity(approval);
//    }

    @Transactional
    public ApprovalResponseDTO approveRequest(RequestApproveDTO request) {
        // 자원그룹 조회
        ResourceGroup resourceGroup = resourceGroupRepository.findById(request.resource_group_id())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // 사용자 생성 및 저장
        User user = userRepository.save(
                request.toUserEntity(passwordEncoder, defaultPassword)
        );

        // 승인 정보 저장
        Approval approval = approvalRepository.save(
                request.toApprovalEntity(passwordEncoder, defaultPassword, resourceGroup, user)
        );

        return ApprovalResponseDTO.fromEntity(approval);
    }


    public Approval getApprovalInfoById(Long id) {
        return approvalRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Transactional
    public void rejectRequest(Long requestId) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        request.updateStatus(Status.REJECTED);
    }
}
