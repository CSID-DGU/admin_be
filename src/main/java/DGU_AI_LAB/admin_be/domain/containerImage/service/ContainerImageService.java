package DGU_AI_LAB.admin_be.domain.containerImage.service;

import DGU_AI_LAB.admin_be.domain.containerImage.dto.request.ContainerImageCreateRequest;
import DGU_AI_LAB.admin_be.domain.containerImage.dto.response.ContainerImageResponseDTO;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContainerImageService {

    private final ContainerImageRepository imageRepository;

    @Transactional
    public ContainerImageResponseDTO createImage(ContainerImageCreateRequest request) {
        if (imageRepository.existsByImageNameAndImageVersion(request.imageName(), request.imageVersion())) {
            throw new BusinessException(ErrorCode.DUPLICATE_CONTAINER_IMAGE);
        }

        ContainerImage image = ContainerImage.builder()
                .imageName(request.imageName())
                .imageVersion(request.imageVersion())
                .cudaVersion(request.cudaVersion())
                .description(request.description())
                .build();

        try {
            return ContainerImageResponseDTO.fromEntity(imageRepository.saveAndFlush(image));
        } catch (DataIntegrityViolationException e) {
            log.warn("[createImage] {}:{} 중복으로 등록 실패(경합)", request.imageName(), request.imageVersion());
            throw new BusinessException(ErrorCode.DUPLICATE_CONTAINER_IMAGE);
        }
    }

    @Transactional(readOnly = true)
    public List<ContainerImageResponseDTO> getAllImages() {
        return imageRepository.findAll().stream()
                .map(ContainerImageResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }
}