package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import DGU_AI_LAB.admin_be.domain.nodes.repository.NodeRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.*;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.AcceptInfoResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ContainerInfoDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ResourceUsageDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.domain.usedIds.entity.UsedId;
import DGU_AI_LAB.admin_be.domain.usedIds.repository.UsedIdRepository;
import DGU_AI_LAB.admin_be.domain.usedIds.service.IdAllocationService;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestService {

    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ContainerImageRepository containerImageRepository;
    private final GroupRepository groupRepository;
    private final ResourceGroupRepository resourceGroupRepository;
    private final UsedIdRepository usedIdRepository;
    private final PasswordEncoder passwordEncoder;
    private final RestTemplate restTemplate;
    private final IdAllocationService idAllocationService;
    private final NodeRepository nodeRepository;

    @Value("${pvc.base-url}")
    private String pvcBaseUrl;

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
        
        // Validate required entities before DTO conversion
        if (req.getResourceGroup() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_GROUP_NOT_FOUND);
        }
        if (req.getUser() == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (req.getContainerImage() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        
        return SaveRequestResponseDTO.fromEntity(req);
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

        // Should enable when pvc service is ready

//        HttpEntity<PvcRequest> entity = new HttpEntity<>(body, headers);
//
//        try {
//            ResponseEntity<Void> res = restTemplate.postForEntity(url, entity, Void.class);
//            if (!res.getStatusCode().is2xxSuccessful()) {
//                throw new BusinessException(ErrorCode.EXTERNAL_API_FAILED);
//            }
//        } catch (Exception ex) {
//            throw new BusinessException(ErrorCode.EXTERNAL_API_FAILED);
//        }

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

    /** 모든 신청 목록 (관리자용) */
    @Transactional(readOnly = true)
    public List<SaveRequestResponseDTO> getAllRequests() {
        return requestRepository.findAll().stream()
                .map(this::validateAndConvertToDTO)
                .toList();
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

    /** ubuntu username 중복 검사 */
    @Transactional(readOnly = true)
    public boolean isUbuntuUsernameAvailable(String username) {
        return !requestRepository.existsByUbuntuUsername(username);
    }

    /** config server용 acceptinfo */
    public AcceptInfoResponseDTO getAcceptInfo(String username) {
        log.info("사용자 승인 정보 조회를 시작합니다. username: {}", username);

        // 사용자 요청 정보 조회
        Request request = requestRepository.findByUbuntuUsername(username)
                .orElseThrow(() -> {
                    log.warn("사용자 '{}'에 대한 승인 정보가 존재하지 않습니다.", username);
                    return new BusinessException(ErrorCode.USER_APPROVAL_NOT_FOUND);
                });

        log.debug("사용자 '{}'의 요청 정보를 성공적으로 찾았습니다. 요청 ID: {}", username, request.getRequestId());

        // 리소스 그룹 및 노드 정보 조회
        ResourceGroup group = request.getResourceGroup();
        log.info("사용자 '{}'의 리소스 그룹 ID: {}", username, group.getRsgroupId());

        List<Node> nodes = nodeRepository.findAllByResourceGroup(group);
        log.debug("리소스 그룹 '{}'에 속한 노드 {}개를 조회했습니다.", group.getRsgroupId(), nodes.size());

        // 조회된 노드 이름 목록을 로그에 기록
        List<String> nodeNames = nodes.stream()
                .map(Node::getNodeId)
                .collect(Collectors.toList());
        log.debug("조회된 노드 목록: {}", nodeNames);

        // 응답 DTO 생성 및 반환
        AcceptInfoResponseDTO response = AcceptInfoResponseDTO.fromEntity(request, nodes);
        log.info("사용자 '{}'에 대한 AcceptInfoResponseDTO 생성을 완료했습니다.", username);

        return response;
    }

    /** 변경 요청 */
    /*@Transactional
    public void requestModification(ModifyRequestDTO dto) {
        Request request = requestRepository.findById(dto.requestId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (request.getStatus() != Status.FULFILLED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }

        dto.applyTo(request);
    }*/

    /** 변경 승인 */
    /*@Transactional
    public void approveModification(ApproveModificationDTO dto) {
        Request request = requestRepository.findById(dto.requestId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (request.getStatus() != Status.FULFILLED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }

        dto.applyTo(request);
    }*/

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
