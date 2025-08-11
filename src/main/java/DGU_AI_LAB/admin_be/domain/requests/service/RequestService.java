package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.*;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ContainerInfoDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ResourceUsageDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RequestService {

    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ContainerImageRepository containerImageRepository;
    private final GroupRepository groupRepository;
    private final ResourceGroupRepository resourceGroupRepository;

    /** 신청 생성 */
    @Transactional
    public SaveRequestResponseDTO createRequest(SaveRequestRequestDTO dto) {
        User user = userRepository.findById(dto.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        ResourceGroup rg = resourceGroupRepository.findById(dto.resourceGroupId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        ContainerImage img = containerImageRepository.findByImageNameAndImageVersion(
                dto.imageName(), dto.imageVersion()
        ).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        Set<Group> groups = dto.ubuntuGids() == null || dto.ubuntuGids().isEmpty()
                ? Set.of()
                : new HashSet<>(groupRepository.findAllByUbuntuGidIn(dto.ubuntuGids()));

        if (groups.size() != (dto.ubuntuGids() == null ? 0 : dto.ubuntuGids().size())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        Request saved = requestRepository.save(dto.toEntity(user, rg, img, groups));
        return SaveRequestResponseDTO.fromEntity(saved);
    }


    /** 신청 승인 */
    @Transactional
    public SaveRequestResponseDTO approveRequest(ApproveRequestDTO dto) {
        Request request = requestRepository.findById(dto.requestId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (request.getStatus() != Status.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }

        ContainerImage containerImage = containerImageRepository.findByImageNameAndImageVersion(
                dto.imageName(), dto.imageVersion()
        ).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        dto.applyTo(request, containerImage);
        return SaveRequestResponseDTO.fromEntity(request);
    }

    /** 신청 거절 */
    @Transactional
    public SaveRequestResponseDTO rejectRequest(RejectRequestDTO dto) {
        Request request = requestRepository.findById(dto.requestId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (request.getStatus() != Status.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }

        dto.applyTo(request);
        return SaveRequestResponseDTO.fromEntity(request);
    }

    /** 내 신청 목록 */
    @Transactional(readOnly = true)
    public List<SaveRequestResponseDTO> getRequestsByUserId(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return requestRepository.findAllByUser_UserId(userId).stream()
                .map(SaveRequestResponseDTO::fromEntity)
                .toList();
    }

    /** 변경 요청 */
    @Transactional
    public void requestModification(ModifyRequestDTO dto) {
        Request request = requestRepository.findById(dto.requestId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (request.getStatus() != Status.FULFILLED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }

        dto.applyTo(request);
    }

    /** 변경 승인 */
    @Transactional
    public void approveModification(ApproveModificationDTO dto) {
        Request request = requestRepository.findById(dto.requestId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (request.getStatus() != Status.FULFILLED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }

        dto.applyTo(request);
    }

    /** 승인 완료 자원 사용량 */
    @Transactional(readOnly = true)
    public List<ResourceUsageDTO> getAllFulfilledResourceUsage() {
        return requestRepository.findAllByStatus(Status.FULFILLED).stream()
                .map(ResourceUsageDTO::fromEntity)
                .toList();
    }

    /** 활성 컨테이너 */
    @Transactional(readOnly = true)
    public List<ContainerInfoDTO> getActiveContainers() {
        return requestRepository.findAllByStatus(Status.FULFILLED).stream()
                .map(ContainerInfoDTO::fromEntity)
                .toList();
    }
}
