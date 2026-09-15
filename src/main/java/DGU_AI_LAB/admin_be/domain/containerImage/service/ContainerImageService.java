package DGU_AI_LAB.admin_be.domain.containerImage.service;

import DGU_AI_LAB.admin_be.domain.containerImage.dto.request.ContainerImageCreateRequest;
import DGU_AI_LAB.admin_be.domain.containerImage.dto.response.ContainerImageResponseDTO;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContainerImageService {

    private final ContainerImageRepository imageRepository;

    @Transactional
    public ContainerImageResponseDTO createImage(ContainerImageCreateRequest request) {
        ContainerImage image = ContainerImage.builder()
                .imageName(request.imageName())
                .imageVersion(request.imageVersion())
                .cudaVersion(request.cudaVersion())
                .description(request.description())
                .build();

        ContainerImage saved = imageRepository.save(image);
        return ContainerImageResponseDTO.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<ContainerImageResponseDTO> getAllImages() {
        return imageRepository.findAll().stream()
                .map(ContainerImageResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }
}