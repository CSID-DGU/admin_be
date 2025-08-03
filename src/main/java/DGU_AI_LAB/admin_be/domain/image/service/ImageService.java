package DGU_AI_LAB.admin_be.domain.image.service;

import DGU_AI_LAB.admin_be.domain.image.dto.request.ImageCreateRequest;
import DGU_AI_LAB.admin_be.domain.image.dto.response.ImageResponseDTO;
import DGU_AI_LAB.admin_be.domain.image.entity.Image;
import DGU_AI_LAB.admin_be.domain.image.repository.ImageRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ImageService {

    private final ImageRepository imageRepository;

    @Transactional
    public void createImage(ImageCreateRequest request) {
        Image image = Image.builder()
                .imageVersion(request.imageVersion())
                .imageTag(request.imageTag())
                .build();

        imageRepository.save(image);
    }

    @Transactional(readOnly = true)
    public ImageResponseDTO getImageById(Long id) {
        Image image = imageRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        return ImageResponseDTO.fromEntity(image);
    }

    @Transactional(readOnly = true)
    public List<ImageResponseDTO> getAllImages() {
        return imageRepository.findAll().stream()
                .map(ImageResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }
}