package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.PvcRequest;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ChangeRequestResponseDTO;
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
import DGU_AI_LAB.admin_be.domain.usedIds.service.IdAllocationService;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRequestService {

    @Value("${pvc.base-url}")
    private String pvcBaseUrl;

    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ContainerImageRepository containerImageRepository;
    private final ResourceGroupRepository resourceGroupRepository;
    private final IdAllocationService idAllocationService;
    private final ChangeRequestRepository changeRequestRepository;

    /**
     * 모든 신규 신청 목록 (관리자용)
     * PENDING 상태의 Request만 반환합니다.
     */
    @Transactional(readOnly = true)
    public List<SaveRequestResponseDTO> getAllRequests() {
        return requestRepository.findAllByStatus(Status.PENDING).stream()
                .map(SaveRequestResponseDTO::fromEntity)
                .toList();
    }

    /** 신청 승인 */
    @Transactional
    public SaveRequestResponseDTO approveRequest(ApproveRequestDTO dto) {
        Request request = requestRepository.findById(dto.requestId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (request.getStatus() != Status.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }

        // UID 할당
        var allocation = idAllocationService.allocateFor(request);

        request.assignUbuntuUid(allocation.getUid());

        boolean alreadyLinked = request.getRequestGroups().stream()
                .anyMatch(rg -> rg.getGroup().getUbuntuGid()
                        .equals(allocation.getPrimaryGroup().getUbuntuGid()));
        if (!alreadyLinked) {
            request.addGroup(allocation.getPrimaryGroup());
        }

        ContainerImage image = containerImageRepository.findById(dto.imageId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        ResourceGroup rg = resourceGroupRepository.findById(dto.resourceGroupId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        request.approve(
                image,
                rg,
                dto.volumeSizeGiB(),
                dto.expiresAt(),
                dto.adminComment()
        );

        requestRepository.flush();

        // pvc post
        String url = pvcBaseUrl + "/pvc";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        PvcRequest body = new PvcRequest(
                request.getUbuntuUsername(),
                request.getVolumeSizeGiB()
        );

        return SaveRequestResponseDTO.fromEntity(request);
    }

    /** 신청 거절 */
    @Transactional
    public SaveRequestResponseDTO rejectRequest(RejectRequestDTO dto) {
        Request request = requestRepository.findById(dto.requestId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!(request.getStatus() == Status.PENDING || request.getStatus() == Status.FULFILLED)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }

        request.reject(
                dto.adminComment()
        );

        return SaveRequestResponseDTO.fromEntity(request);
    }

    /**
     * 승인 완료 자원 사용량 (관리자용)
     */
    @Transactional(readOnly = true)
    public List<ResourceUsageDTO> getAllFulfilledResourceUsage() {
        return requestRepository.findAllByStatus(Status.FULFILLED).stream()
                .map(ResourceUsageDTO::fromEntity)
                .toList();
    }

    /**
     * 활성 컨테이너 (관리자용)
     */
    @Transactional(readOnly = true)
    public List<ContainerInfoDTO> getAllActiveContainers() {
        return requestRepository.findAllByStatus(Status.FULFILLED).stream()
                .map(ContainerInfoDTO::fromEntity)
                .toList();
    }

    /**
     * 변경 요청을 승인하고 원본 Request에 반영합니다.
     * @param adminId 승인한 관리자 ID
     * @param dto 변경 요청 승인 DTO
     */
    @Transactional
    public void approveModification(Long adminId, ApproveModificationDTO dto) {
        // 1. 변경 요청 엔티티 조회
        ChangeRequest changeRequest = changeRequestRepository.findById(dto.changeRequestId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // 2. 변경 요청 상태 확인 (PENDING 상태여야 함)
        if (changeRequest.getStatus() != Status.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }

        // 3. 관리자 정보 조회
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 4. 원본 Request 엔티티 조회
        Request originalRequest = changeRequest.getRequest();
        if (originalRequest == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        // 5. 원본 Request 엔티티의 비즈니스 메서드 호출
        originalRequest.applyChange(
                changeRequest.getChangeType(),
                changeRequest.getNewValue()
        );

        // 6. ChangeRequest 상태를 FULFILLED로 변경하고 관리자 정보 기록
        changeRequest.approve(admin, dto.adminComment());
    }

    // 신규 신청 목록 조회 메서드 추가 (기존 getAllRequests 변경)
    public List<SaveRequestResponseDTO> getNewRequests() {
        return requestRepository.findAllByStatus(Status.PENDING).stream()
                .map(SaveRequestResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    // 변경 요청 목록 조회 메서드 추가
    public List<ChangeRequestResponseDTO> getChangeRequests() {
        return changeRequestRepository.findAllByStatus(Status.PENDING).stream()
                .map(ChangeRequestResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }


}