package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ModifyRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.SaveRequestRequestDTO;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RequestCommandService {

    private final ObjectMapper objectMapper;
    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ContainerImageRepository containerImageRepository;
    private final GroupRepository groupRepository;
    private final ResourceGroupRepository resourceGroupRepository;
    private final ChangeRequestRepository changeRequestRepository;

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

        // FULFILLED 상태에서만 변경 요청 가능
        if (originalRequest.getStatus() != Status.FULFILLED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }

        User requestedBy = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 저장공간 크기 변경
        if (dto.requestedVolumeSizeGiB() != null) {
            createAndSaveChangeRequest(originalRequest, requestedBy, ChangeType.VOLUME_SIZE,
                    originalRequest.getVolumeSizeGiB(), dto.requestedVolumeSizeGiB(), dto.reason());
        }

        // 만료 기한 변경
        if (dto.requestedExpiresAt() != null) {
            createAndSaveChangeRequest(originalRequest, requestedBy, ChangeType.EXPIRES_AT,
                    originalRequest.getExpiresAt().toString(), dto.requestedExpiresAt().toString(), dto.reason());
        }

        // 그룹 변경
        if (dto.requestedGroupIds() != null && !dto.requestedGroupIds().isEmpty()) {
            // 변경 전 그룹 목록 조회
            Set<Long> oldGroupIds = originalRequest.getRequestGroups().stream()
                    .map(requestGroup -> requestGroup.getGroup().getUbuntuGid())
                    .collect(Collectors.toSet());

            // 변경 후 그룹 존재 여부 확인
            if (groupRepository.findAllByUbuntuGidIn(dto.requestedGroupIds()).size() != dto.requestedGroupIds().size()) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }

            createAndSaveChangeRequest(originalRequest, requestedBy, ChangeType.GROUP,
                    oldGroupIds, dto.requestedGroupIds(), dto.reason());
        }

        // 리소스 그룹 변경
        if (dto.requestedResourceGroupId() != null) {
            ResourceGroup newResourceGroup = resourceGroupRepository.findById(dto.requestedResourceGroupId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

            createAndSaveChangeRequest(originalRequest, requestedBy, ChangeType.RESOURCE_GROUP,
                    originalRequest.getResourceGroup().getRsgroupId(), dto.requestedResourceGroupId(), dto.reason());
        }

        // 도커 이미지 변경
        if (dto.requestedContainerImageId() != null) {
            ContainerImage newImage = containerImageRepository.findById(dto.requestedContainerImageId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

            createAndSaveChangeRequest(originalRequest, requestedBy, ChangeType.CONTAINER_IMAGE,
                    originalRequest.getContainerImage().getImageId(), dto.requestedContainerImageId(), dto.reason());
        }
    }


    // 중복 코드 방지를 위한 헬퍼 메서드
    private <T> void createAndSaveChangeRequest(Request originalRequest, User requestedBy,
                                                ChangeType changeType, T oldValue, T newValue, String reason) {
        try {
            ChangeRequest changeRequest = ChangeRequest.builder()
                    .request(originalRequest)
                    .changeType(changeType)
                    .oldValue(objectMapper.writeValueAsString(oldValue))
                    .newValue(objectMapper.writeValueAsString(newValue))
                    .reason(reason)
                    .requestedBy(requestedBy)
                    .build();
            changeRequestRepository.save(changeRequest);
        } catch (Exception e) {
            log.error("Failed to create change request for type {}: {}", changeType, e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

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


}
