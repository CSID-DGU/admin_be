package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ModifyRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.SaveRequestRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ContainerInfoDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ResourceUsageDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.ChangeRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.util.PasswordUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestService {

    private final ObjectMapper objectMapper;
    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ContainerImageRepository containerImageRepository;
    private final GroupRepository groupRepository;
    private final ResourceGroupRepository resourceGroupRepository;
    private final ChangeRequestRepository changeRequestRepository;

    /** 신청 생성 */
    @Transactional
    public SaveRequestResponseDTO createRequest(Long userId, SaveRequestRequestDTO dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        ResourceGroup rg = resourceGroupRepository.findById(dto.resourceGroupId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (requestRepository.existsByUbuntuUsername(dto.ubuntuUsername())) {
            throw new BusinessException(ErrorCode.DUPLICATE_USERNAME);
        }

        ContainerImage img = containerImageRepository.findById(dto.imageId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        String ubuntuPassword = PasswordUtil.encodePassword(dto.ubuntuPassword());

        Request req = dto.toEntity(
                user,
                rg,
                img,
                java.util.Collections.emptySet(),
                ubuntuPassword
        );

        req = requestRepository.save(req);
        requestRepository.flush();

        if (dto.ubuntuGids() != null && !dto.ubuntuGids().isEmpty()) {
            Set<Group> found = new java.util.HashSet<>(groupRepository.findAllByUbuntuGidIn(dto.ubuntuGids()));

            if (found.size() != dto.ubuntuGids().size()) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }

            for (Group g : found) {
                req.addGroup(g);
            }
        }
        
        return SaveRequestResponseDTO.fromEntity(req);
    }


    /** 내 신청 목록 */
    @Transactional(readOnly = true)
    public List<SaveRequestResponseDTO> getRequestsByUserId(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return requestRepository.findAllByUser_UserId(userId).stream()
                .map(this::validateAndConvertToDTO)
                .toList();
    }

    /**
     * 사용 신청 변경 요청
     */
    @Transactional
    public void createModificationRequest(Long userId, Long requestId, ModifyRequestDTO dto) {
        Request originalRequest = requestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // 요청자가 원본 요청의 소유자인지 확인
        if (!originalRequest.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_REQUEST);
        }

        // FULFILLED 상태에서만 변경 요청 가능.
        if (originalRequest.getStatus() != Status.FULFILLED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }

        User requestedBy = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (dto.requestedVolumeSizeGiB() != null) {
            try {
                ChangeRequest changeRequest = ChangeRequest.builder()
                        .request(originalRequest)
                        .changeType(ChangeType.VOLUME_SIZE)
                        .oldValue(objectMapper.writeValueAsString(originalRequest.getVolumeSizeGiB()))
                        .newValue(objectMapper.writeValueAsString(dto.requestedVolumeSizeGiB()))
                        .reason(dto.reason())
                        .requestedBy(requestedBy)
                        .build();
                changeRequestRepository.save(changeRequest);
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        }

        if (dto.requestedExpiresAt() != null) {
            try {
                ChangeRequest changeRequest = ChangeRequest.builder()
                        .request(originalRequest)
                        .changeType(ChangeType.EXPIRES_AT)
                        .oldValue(objectMapper.writeValueAsString(originalRequest.getExpiresAt().toString()))
                        .newValue(objectMapper.writeValueAsString(dto.requestedExpiresAt().toString()))
                        .reason(dto.reason())
                        .requestedBy(requestedBy)
                        .build();
                changeRequestRepository.save(changeRequest);
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        }
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
    
    private SaveRequestResponseDTO validateAndConvertToDTO(Request request) {
        // Validate required entities before DTO conversion
        if (request.getResourceGroup() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_GROUP_NOT_FOUND);
        }
        if (request.getUser() == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (request.getContainerImage() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        
        return SaveRequestResponseDTO.fromEntity(request);
    }

}
