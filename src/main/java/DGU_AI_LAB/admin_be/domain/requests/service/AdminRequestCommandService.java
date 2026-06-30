package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.UserCreationRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.CreatePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
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
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminRequestCommandService {

    private final AlarmService alarmService;

    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ContainerImageRepository containerImageRepository;
    private final ResourceGroupRepository resourceGroupRepository;
    private final ChangeRequestRepository changeRequestRepository;
    private final GroupRepository groupRepository;
    private final PodExternalPortRepository podExternalPortRepository;
    private final PodService podService;
    private final UbuntuAccountService ubuntuAccountService;
    private final ObjectMapper objectMapper;

    private final @Qualifier("configWebClient") WebClient userCreationWebClient;

    @Transactional
    public SaveRequestResponseDTO approveRequest(ApproveRequestDTO dto) {
        Request request = requestRepository.findById(dto.requestId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (request.getStatus() != Status.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }
        // 1. 사용자 생성 API 호출
        UserCreationRequestDTO userCreationDto = new UserCreationRequestDTO(
                request.getUbuntuUsername(),
                request.getUbuntuPasswordBase64(),
                request.getUser().getName(),
                request.getUbuntuUsername(), // primary_group_name = username (Linux 관례)
                false
        );

        UserCreationResponse userResponse;
        try {
            log.info("사용자 생성 API 호출 시작: {}", userCreationDto.username());

            userResponse = userCreationWebClient.put()
                    .uri("/accounts/users")
                    .bodyValue(userCreationDto)
                    .retrieve()
                    .onStatus(HttpStatus.BAD_REQUEST::equals, clientResponse ->
                            Mono.error(new BusinessException(ErrorCode.INVALID_USERNAME_FORMAT))
                    )
                    .onStatus(HttpStatus.CONFLICT::equals, clientResponse ->
                            Mono.error(new BusinessException(ErrorCode.USER_ALREADY_EXISTS))
                    )
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new BusinessException("사용자 생성 실패: " + body, ErrorCode.USER_CREATION_FAILED)))
                    )
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new BusinessException("사용자 생성 실패: " + body, ErrorCode.USER_CREATION_FAILED)))
                    )
                    .bodyToMono(UserCreationResponse.class)
                    .block();

            log.info("사용자 생성 성공: {}", userResponse);
            if (userResponse == null || userResponse.uid() == null || userResponse.gid() == null) {
                log.error("사용자 생성 API 응답에 UID/GID가 없습니다: {}", userResponse);
                throw new BusinessException(ErrorCode.UID_ALLOCATION_FAILED);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("사용자 생성 API 호출 중 예기치 않은 오류 발생.", e);
            throw new BusinessException(ErrorCode.USER_CREATION_FAILED);
        }

        String username = request.getUbuntuUsername();

        // 2. Pod 생성 API 호출
        CreatePodResponseDTO podResponse;
        try {
            podResponse = podService.createPod(username);
        } catch (BusinessException e) {
            log.warn("[보상 트랜잭션] Pod 생성 실패 → 계정 삭제 시작: {}", username);
            tryCompensateDeleteUser(username);
            throw e;
        }

        // 3. API 호출이 모두 성공한 후에 DB 업데이트
        try {
            ContainerImage image = containerImageRepository.findById(dto.imageId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            ResourceGroup rg = resourceGroupRepository.findById(dto.resourceGroupId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            request.assignUbuntuIds(userResponse.uid(), userResponse.gid());
            request.approve(image, rg, dto.volumeSizeGiB(), dto.adminComment());
            request.assignPodInfo(podResponse.podName(), podResponse.node());
            for (CreatePodResponseDTO.PortInfo port : podResponse.ports()) {
                podExternalPortRepository.save(PodExternalPort.builder()
                        .request(request)
                        .internalPort(port.internalPort())
                        .externalPort(port.externalPort())
                        .usagePurpose(port.usagePurpose())
                        .build());
            }
        } catch (Exception e) {
            log.error("[보상 트랜잭션] DB 업데이트 실패 → 전체 infra 리소스 삭제 시작: {}", username, e);
            tryCompensateAll(username, podResponse.podName());
            throw e;
        }
        // requestRepository.flush();

        // 5. 사용자에게 컨테이너 배정 안내 메일 발송 (접속 정보 포함)
        try {
            String sshPort = extractExternalPort(podResponse, "ssh");
            String jupyterPort = extractExternalPort(podResponse, "jupyter");
            alarmService.sendContainerCreatedEmail(request, sshPort, jupyterPort);
            log.info("사용자 '{}'에게 컨테이너 배정 안내 메일을 발송했습니다.", request.getUser().getName());
        } catch (Exception e) {
            log.warn("사용자 '{}'에게 배정 안내 메일 발송 실패. (RequestId: {})",
                    request.getUser().getName(), request.getRequestId(), e);
        }
        return SaveRequestResponseDTO.fromEntity(request);
    }

    /** podResponse 포트 목록에서 usage_purpose가 일치하는 첫 외부 포트(문자열). 없으면 "". */
    private String extractExternalPort(CreatePodResponseDTO podResponse, String purpose) {
        return podResponse.ports().stream()
                .filter(p -> purpose.equalsIgnoreCase(p.usagePurpose()))
                .map(p -> String.valueOf(p.externalPort()))
                .findFirst().orElse("");
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
    public void rejectModification(Long adminId, RejectModificationDTO dto) {
        ChangeRequest changeRequest = changeRequestRepository.findById(dto.changeRequestId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (changeRequest.getStatus() != Status.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        changeRequest.deny(admin, dto.adminComment());
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

        try {
            switch (changeRequest.getChangeType()) {
                case VOLUME_SIZE:
                    // ponytail: NFS 전환 후 PVC 없음 — DB 기록만 갱신, NAS 쿼터 필요 시 여기에 추가
                    Long newVolumeSize = objectMapper.readValue(changeRequest.getNewValue(), Long.class);
                    originalRequest.updateVolumeSize(newVolumeSize);
                    break;
                case EXPIRES_AT:
                    LocalDateTime newExpiresAt = LocalDateTime.parse(objectMapper.readValue(changeRequest.getNewValue(), String.class));
                    originalRequest.updateExpiresAt(newExpiresAt);
                    break;
                case GROUP:
                    // 그룹 변경은 복잡하기 때문에, 엔티티가 아닌 서비스 레이어에서 처리합니다.
                    originalRequest.getRequestGroups().clear();
                    Set<Long> newGroupIds = objectMapper.readValue(changeRequest.getNewValue(), Set.class);
                    Set<Group> newGroups = newGroupIds.stream()
                            .map(gid -> groupRepository.findByUbuntuGid(gid)
                                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)))
                            .collect(Collectors.toSet());

                    for (Group g : newGroups) {
                        originalRequest.addGroup(g);
                    }
                    break;
                case RESOURCE_GROUP:
                    Integer newResourceGroupId = objectMapper.readValue(changeRequest.getNewValue(), Integer.class);
                    ResourceGroup newResourceGroup = resourceGroupRepository.findById(newResourceGroupId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
                    originalRequest.updateResourceGroup(newResourceGroup);
                    break;
                case CONTAINER_IMAGE:
                    Long newImageId = objectMapper.readValue(changeRequest.getNewValue(), Long.class);
                    ContainerImage newContainerImage = containerImageRepository.findById(newImageId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
                    originalRequest.updateContainerImage(newContainerImage);
                    break;
                default:
                    throw new BusinessException(ErrorCode.UNSUPPORTED_CHANGE_TYPE);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to parse change request value: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        changeRequest.approve(admin, dto.adminComment());
    }

    // ── 보상 트랜잭션 헬퍼 ──────────────────────────────────────────────

    private void tryCompensateDeleteUser(String username) {
        try {
            ubuntuAccountService.deleteUbuntuAccount(username);
            log.info("[보상 트랜잭션 완료] 계정 삭제: {}", username);
        } catch (Exception e) {
            log.error("[보상 트랜잭션 실패] 계정 삭제 실패 - 수동 정리 필요: {}", username, e);
        }
    }

    private void tryCompensateAll(String username, String podName) {
        try {
            podService.deletePod(podName);
            log.info("[보상 트랜잭션 완료] Pod 삭제: {}", podName);
        } catch (Exception e) {
            log.error("[보상 트랜잭션 실패] Pod 삭제 실패 - 수동 정리 필요: {}", podName, e);
        }
        tryCompensateDeleteUser(username);
    }

    record UserCreationResponse(
            String status,
            UserInfo user
    ) {
        Long uid() { return user != null ? user.uid() : null; }
        Long gid() { return user != null ? user.gid() : null; }

        record UserInfo(
                @JsonProperty("uid")
                @JsonAlias({"ubuntuUid", "ubuntu_uid"})
                Long uid,
                @JsonProperty("gid")
                @JsonAlias({"ubuntuGid", "ubuntu_gid"})
                Long gid
        ) {}
    }
}
