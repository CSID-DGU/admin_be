package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.*;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
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

    private final @Qualifier("pvcWebClient") WebClient pvcWebClient;
    private final @Qualifier("pvcWebClient") WebClient userCreationWebClient;

    @Transactional
    public SaveRequestResponseDTO approveRequest(ApproveRequestDTO dto) {
        Request request = requestRepository.findById(dto.requestId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (request.getStatus() != Status.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }
        var allocation = idAllocationService.allocateFor(request);

        // 1. 사용자 생성 API 호출
        UserCreationRequestDTO userCreationDto = new UserCreationRequestDTO(
                request.getUbuntuUsername(),
                allocation.getUid().getIdValue().intValue(),
                allocation.getPrimaryGroup().getUbuntuGid().intValue(),
                request.getUbuntuPassword(),
                request.getUser().getName(),
                allocation.getPrimaryGroup().getGroupName(),
                false // sudo 권한은 기본값으로 false를 설정
        );

        try {
            log.info("사용자 생성 API 호출 시작: {}", userCreationDto.username());

            Map userResponse = userCreationWebClient.post()
                    .uri("/accounts/adduser")
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
                    .bodyToMono(Map.class)
                    .block();

            log.info("사용자 생성 성공: {}", userResponse);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("사용자 생성 API 호출 중 예기치 않은 오류 발생.", e);
            throw new BusinessException(ErrorCode.USER_CREATION_FAILED);
        }

        // 2. PVC 생성 API 호출
        PvcRequestDTO pvcDto = new PvcRequestDTO(request.getUbuntuUsername(), request.getVolumeSizeGiB());

        try {
            log.info("PVC 생성 요청 시작: {} 사용자, 용량: {}Gi",
                    pvcDto.ubuntuUsername(), pvcDto.volumeSizeGiB());

            Mono<Map> pvcResponseMono = pvcWebClient.post()
                    .uri("/pvc")
                    .bodyValue(pvcDto)
                    .retrieve()
                    .bodyToMono(Map.class);

            Map response = pvcResponseMono.block();

            log.info("PVC 생성 성공: {}", response);

        } catch (WebClientResponseException e) {
            log.error("PVC API 호출 실패: 상태 코드: {}, 응답 본문: {}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new BusinessException(ErrorCode.PVC_API_FAILURE);
        } catch (Exception e) {
            log.error("PVC API 호출 중 예기치 않은 오류 발생.", e);
            throw new BusinessException(ErrorCode.PVC_API_FAILURE);
        }

        // 3. API 호출이 모두 성공한 후에 DB 업데이트
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