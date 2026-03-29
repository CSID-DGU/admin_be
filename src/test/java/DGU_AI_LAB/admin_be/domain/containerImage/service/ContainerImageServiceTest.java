package DGU_AI_LAB.admin_be.domain.containerImage.service;

import DGU_AI_LAB.admin_be.domain.containerImage.dto.request.ContainerImageCreateRequest;
import DGU_AI_LAB.admin_be.domain.containerImage.dto.response.ContainerImageResponseDTO;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

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
            when(imageRepository.save(any(ContainerImage.class))).thenReturn(mockImage);

            ContainerImageCreateRequest request = new ContainerImageCreateRequest(
                    "pytorch", "2.1.0", "11.8", "PyTorch 2.1.0 with CUDA 11.8"
            );

            ContainerImageResponseDTO result = containerImageService.createImage(request);

            assertThat(result).isNotNull();
            assertThat(result.imageName()).isEqualTo("pytorch");
            assertThat(result.imageVersion()).isEqualTo("2.1.0");
            assertThat(result.cudaVersion()).isEqualTo("11.8");
        }
    }

    @Nested
    @DisplayName("getImageById")
    class GetImageById {

        @Test
        @DisplayName("존재하는 id로 조회하면 DTO를 반환한다")
        void getImageById_returnsDTO_whenExists() {
            when(imageRepository.findById(1L)).thenReturn(Optional.of(mockImage));

            ContainerImageResponseDTO result = containerImageService.getImageById(1L);

            assertThat(result.imageName()).isEqualTo("pytorch");
        }

        @Test
        @DisplayName("존재하지 않는 id로 조회하면 BusinessException을 던진다")
        void getImageById_throwsException_whenNotFound() {
            when(imageRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> containerImageService.getImageById(99L))
                    .isInstanceOf(BusinessException.class);
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
