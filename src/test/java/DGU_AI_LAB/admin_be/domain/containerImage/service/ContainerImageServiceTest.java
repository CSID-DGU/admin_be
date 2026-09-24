package DGU_AI_LAB.admin_be.domain.containerImage.service;

import DGU_AI_LAB.admin_be.domain.containerImage.dto.request.ContainerImageCreateRequest;
import DGU_AI_LAB.admin_be.domain.containerImage.dto.response.ContainerImageResponseDTO;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContainerImageServiceTest {

    @InjectMocks
    private ContainerImageService containerImageService;

    @Mock
    private ContainerImageRepository imageRepository;

    private ContainerImage mockImage;

    @BeforeEach
    void setUp() {
        mockImage = ContainerImage.builder()
                .imageName("pytorch")
                .imageVersion("2.1.0")
                .cudaVersion("11.8")
                .description("PyTorch 2.1.0 with CUDA 11.8")
                .build();
    }

    @Nested
    @DisplayName("createImage")
    class CreateImage {

        @Test
        @DisplayName("이미지를 생성하면 저장된 DTO를 반환한다")
        void createImage_success() {
            when(imageRepository.saveAndFlush(any(ContainerImage.class))).thenReturn(mockImage);

            ContainerImageCreateRequest request = new ContainerImageCreateRequest(
                    "pytorch", "2.1.0", "11.8", "PyTorch 2.1.0 with CUDA 11.8"
            );

            ContainerImageResponseDTO result = containerImageService.createImage(request);

            assertThat(result).isNotNull();
            assertThat(result.imageName()).isEqualTo("pytorch");
            assertThat(result.imageVersion()).isEqualTo("2.1.0");
            assertThat(result.cudaVersion()).isEqualTo("11.8");
        }

        @Test
        @DisplayName("같은 이름·버전이 이미 있으면 저장하지 않고 409 예외를 던진다")
        void createImage_duplicate_throws() {
            when(imageRepository.existsByImageNameAndImageVersion("pytorch", "2.1.0")).thenReturn(true);

            ContainerImageCreateRequest request = new ContainerImageCreateRequest(
                    "pytorch", "2.1.0", "11.8", "PyTorch 2.1.0 with CUDA 11.8"
            );

            assertThatThrownBy(() -> containerImageService.createImage(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_CONTAINER_IMAGE);
            verify(imageRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("동시 등록으로 유니크 제약에 걸리면 409 예외로 바꾼다")
        void createImage_uniqueViolation_throws() {
            when(imageRepository.saveAndFlush(any(ContainerImage.class)))
                    .thenThrow(new DataIntegrityViolationException("uk_container_image_name_version"));

            ContainerImageCreateRequest request = new ContainerImageCreateRequest(
                    "pytorch", "2.1.0", "11.8", "PyTorch 2.1.0 with CUDA 11.8"
            );

            assertThatThrownBy(() -> containerImageService.createImage(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_CONTAINER_IMAGE);
        }
    }

    @Nested
    @DisplayName("getAllImages")
    class GetAllImages {

        @Test
        @DisplayName("이미지 목록을 반환한다")
        void getAllImages_returnsList() {
            ContainerImage image2 = ContainerImage.builder()
                    .imageName("tensorflow")
                    .imageVersion("2.13.0")
                    .cudaVersion("11.8")
                    .description("TensorFlow 2.13.0")
                    .build();

            when(imageRepository.findAll()).thenReturn(List.of(mockImage, image2));

            List<ContainerImageResponseDTO> result = containerImageService.getAllImages();

            assertThat(result).hasSize(2);
            assertThat(result).extracting("imageName").containsExactlyInAnyOrder("pytorch", "tensorflow");
        }

        @Test
        @DisplayName("이미지가 없으면 빈 리스트를 반환한다")
        void getAllImages_returnsEmptyList_whenNoImages() {
            when(imageRepository.findAll()).thenReturn(List.of());

            List<ContainerImageResponseDTO> result = containerImageService.getAllImages();

            assertThat(result).isEmpty();
        }
    }
}
