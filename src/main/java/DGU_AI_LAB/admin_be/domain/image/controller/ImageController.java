package DGU_AI_LAB.admin_be.domain.image.controller;

import DGU_AI_LAB.admin_be.domain.image.controller.docs.ImageApi;
import DGU_AI_LAB.admin_be.domain.image.dto.request.ImageCreateRequest;
import DGU_AI_LAB.admin_be.domain.image.service.ImageService;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/images")
public class ImageController implements ImageApi {
    private final ImageService imageService;

    @PostMapping
    public ResponseEntity<?> createImage(@RequestBody @Valid ImageCreateRequest request) {
        imageService.createImage(request);
        return SuccessResponse.created(null);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getImageById(@PathVariable Long id) {
        return SuccessResponse.ok(imageService.getImageById(id));
    }

    @GetMapping
    public ResponseEntity<?> getAllImages() {
        return SuccessResponse.ok(imageService.getAllImages());
    }
}
