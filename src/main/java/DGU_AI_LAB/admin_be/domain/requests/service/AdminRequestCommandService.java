package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.ChangeRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.domain.usedIds.service.IdAllocationService;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminRequestCommandService {

    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ContainerImageRepository containerImageRepository;
    private final ResourceGroupRepository resourceGroupRepository;
    private final IdAllocationService idAllocationService;
    private final ChangeRequestRepository changeRequestRepository;

    @Transactional
    public SaveRequestResponseDTO approveRequest(ApproveRequestDTO dto) {
        Request request = requestRepository.findById(dto.requestId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (request.getStatus() != Status.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }
        var allocation = idAllocationService.allocateFor(request);
        request.assignUbuntuUid(allocation.getUid());
        boolean alreadyLinked = request.getRequestGroups().stream()
                .anyMatch(rg -> rg.getGroup().getUbuntuGid().equals(allocation.getPrimaryGroup().getUbuntuGid()));
        if (!alreadyLinked) {
            request.addGroup(allocation.getPrimaryGroup());
        }
        ContainerImage image = containerImageRepository.findById(dto.imageId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        ResourceGroup rg = resourceGroupRepository.findById(dto.resourceGroupId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        request.approve(image, rg, dto.volumeSizeGiB(), dto.expiresAt(), dto.adminComment());
        requestRepository.flush();
        // pvc post logic (rest client)
        return SaveRequestResponseDTO.fromEntity(request);
    }

    @Transactional
    public SaveRequestResponseDTO rejectRequest(RejectRequestDTO dto) {
        Request request = requestRepository.findById(dto.requestId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!(request.getStatus() == Status.PENDING || request.getStatus() == Status.FULFILLED)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }
        request.reject(dto.adminComment());
        return SaveRequestResponseDTO.fromEntity(request);
    }

    @Transactional
    public void approveModification(Long adminId, ApproveModificationDTO dto) {
        ChangeRequest changeRequest = changeRequestRepository.findById(dto.changeRequestId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (changeRequest.getStatus() != Status.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Request originalRequest = changeRequest.getRequest();
        if (originalRequest == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        originalRequest.applyChange(changeRequest.getChangeType(), changeRequest.getNewValue());
        changeRequest.approve(admin, dto.adminComment());
    }
}