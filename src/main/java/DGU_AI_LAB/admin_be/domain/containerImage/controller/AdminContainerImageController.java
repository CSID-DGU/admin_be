package DGU_AI_LAB.admin_be.domain.containerImage.controller;

import DGU_AI_LAB.admin_be.domain.containerImage.controller.docs.AdminContainerImageApi;
import DGU_AI_LAB.admin_be.domain.containerImage.dto.request.ContainerImageCreateRequest;
import DGU_AI_LAB.admin_be.domain.containerImage.dto.response.ContainerImageResponseDTO;
import DGU_AI_LAB.admin_be.domain.containerImage.service.ContainerImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 등록한 "이름:버전"이 그대로 사용자 Pod의 이미지가 되므로 관리자만 등록한다(/api/admin/** 는 ADMIN 전용).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/images")
public class AdminContainerImageController implements AdminContainerImageApi {

    private final ContainerImageService containerImageService;

    @PostMapping
    public ResponseEntity<ContainerImageResponseDTO> createImage(
            @RequestBody @Valid ContainerImageCreateRequest request
    ) {
        ContainerImageResponseDTO createdImage = containerImageService.createImage(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdImage);
    }
}
