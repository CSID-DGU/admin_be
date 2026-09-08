package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ModifyRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.SingleChangeRequestDTO;
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
import DGU_AI_LAB.admin_be.domain.portRequests.service.PortRequestService;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
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
    private final PortRequestService portRequestService;
    private final AlarmService alarmService;

    /**
     * 사용자가 자신의 대기 중(PENDING) 또는 거절된(DENIED) 신청을 취소한다.
     * FULFILLED/MIGRATING 상태는 Request.delete()가 자체적으로 거부한다 — 실행 중인
     * 컨테이너가 있는 신청은 인프라 정리 없이 그냥 지울 수 없기 때문.
     */
    @Transactional
    public void cancelRequest(Long userId, Long requestId) {
        // 행 잠금 조회: delete()의 PROCESSING 가드는 로드 시점의 엔티티 상태를 보므로,
        // 잠그지 않으면 승인이 PENDING -> PROCESSING을 커밋하는 사이에 읽은 낡은 PENDING을
        // 근거로 통과해 DELETED로 덮어쓴다. 그러면 승인 후처리가 상태 재확인에서 실패해
        // 불필요한 보상 트랜잭션과 관리자 알림이 발생한다.
        Request request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!request.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_REQUEST);
        }

        request.delete();
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

        // FULFILLED 상태에서만 변경 요청 가능
        if (originalRequest.getStatus() != Status.FULFILLED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }

        User requestedBy = originalRequest.getUser();

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

    /**
     * 단일 변경 요청 생성 - DTO가 모든 검증을 담당하므로 서비스는 단순히 처리만 함
     */
    @Transactional
    public void createSingleChangeRequest(Long userId, Long requestId, SingleChangeRequestDTO dto) {
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

        User requestedBy = originalRequest.getUser();

        // DTO 팩토리 메서드를 사용하여 검증된 ChangeRequest 생성 및 저장
        ChangeRequest changeRequest = SingleChangeRequestDTO.createValidatedChangeRequest(
                dto, originalRequest, requestedBy, objectMapper, portRequestService);
        changeRequestRepository.save(changeRequest);
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
        // 행 잠금 조회: 아래 "살아있는 신청이 이미 있는가" 검사는 확인 후 저장 사이가 벌어져 있어,
        // 같은 사용자가 신청 두 건을 동시에 넣으면 둘 다 통과한다. 사용자당 하나라는 제약을
        // 걸어줄 DB 유니크 키가 없으므로 User 행 잠금으로 두 트랜잭션을 직렬화한다.
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        ResourceGroup rg = resourceGroupRepository.findById(dto.resourceGroupId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // 유저네임은 신청마다 고르는 값이 아니라 가입 시 정해진 웹 계정의 고정값이다.
        String ubuntuUsername = user.getUbuntuUsername();
        if (ubuntuUsername == null || ubuntuUsername.isBlank()) {
            throw new BusinessException(ErrorCode.UBUNTU_USERNAME_NOT_ASSIGNED);
        }

        // 인프라(config-server)의 Pod 생성·조회·마이그레이션 API는 모두 유저네임을 키로 쓴다.
        // 한 사용자가 같은 유저네임으로 컨테이너를 동시에 두 개 가지면 그 API들이 어느 쪽을
        // 가리키는지 구분할 수 없으므로, 살아있는 신청은 사용자당 하나로 제한한다.
        if (requestRepository.existsByUser_UserIdAndStatusIn(userId, Status.openStatuses())) {
            throw new BusinessException(ErrorCode.ACTIVE_REQUEST_ALREADY_EXISTS);
        }

        ContainerImage img = containerImageRepository.findById(dto.imageId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // addGroup()/포트 신청이 requestId를 요구하므로 여기서 즉시 flush해 ID를 확보한다.
        Request req = requestRepository.saveAndFlush(dto.toEntity(user, rg, img, ubuntuUsername));

        if (dto.ubuntuGids() != null && !dto.ubuntuGids().isEmpty()) {
            Set<Group> found = new java.util.HashSet<>(groupRepository.findAllByUbuntuGidIn(dto.ubuntuGids()));

            if (found.size() != dto.ubuntuGids().size()) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }

            for (Group g : found) {
                req.addGroup(g);
            }
        }

        // === 포트 추가 신청 ===
        if (dto.portRequests() != null && !dto.portRequests().isEmpty()) {
            for (var portRequestDTO : dto.portRequests()) {
                portRequestService.createPortRequest(
                        req,
                        rg,
                        portRequestDTO.internalPort(),
                        portRequestDTO.usagePurpose()
                );
            }
        }

        // === 관리자 채널에 슬랙 알림 전송 ===
        try {
            log.info("새로운 사용 신청에 대한 슬랙 알림을 전송합니다. 요청 ID: {}", req.getRequestId());
            alarmService.sendNewRequestNotification(req);
        } catch (Exception e) {
            // 사용자는 신청을 성공적으로 생성했지만, 관리자에게 알림만 가지 않은 상황입니다.
            log.error("슬랙 알림 전송에 실패했습니다. (요청 ID: {}). 하지만 사용 신청은 정상적으로 처리되었습니다.", req.getRequestId(), e);
        }


        return SaveRequestResponseDTO.fromEntity(req);
    }


}
