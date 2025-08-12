package DGU_AI_LAB.admin_be.domain.containerImage.controller;

import DGU_AI_LAB.admin_be.domain.containerImage.controller.docs.ContainerImageApi;
import DGU_AI_LAB.admin_be.domain.containerImage.dto.request.ContainerImageCreateRequest;
import DGU_AI_LAB.admin_be.domain.containerImage.dto.response.ContainerImageResponseDTO;
import DGU_AI_LAB.admin_be.domain.containerImage.service.ContainerImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/images")
public class ContainerImageController implements ContainerImageApi {

    private final ContainerImageService containerImageService;

    @PostMapping
    public ResponseEntity<ContainerImageResponseDTO> createImage(
            @RequestBody @Valid ContainerImageCreateRequest request
    ) {
        ContainerImageResponseDTO createdImage = containerImageService.createImage(request);
        return ResponseEntity.ok(createdImage);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContainerImageResponseDTO> getImageById(@PathVariable Long id) {
        return ResponseEntity.ok(containerImageService.getImageById(id));
    }

    @GetMapping
    public ResponseEntity<List<ContainerImageResponseDTO>> getAllImages() {
        return ResponseEntity.ok(containerImageService.getAllImages());
    }
}