package DGU_AI_LAB.admin_be.domain.containerImage.controller;

import DGU_AI_LAB.admin_be.domain.containerImage.dto.request.ContainerImageCreateRequest;
import DGU_AI_LAB.admin_be.domain.containerImage.dto.response.ContainerImageResponseDTO;
import DGU_AI_LAB.admin_be.domain.containerImage.service.ContainerImageService;
import DGU_AI_LAB.admin_be.support.WebMvcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = {ContainerImageController.class, AdminContainerImageController.class},
        excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
class ContainerImageControllerTest extends WebMvcTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ContainerImageService containerImageService;

    private ContainerImageResponseDTO sampleDto() {
        return ContainerImageResponseDTO.builder()
                .imageId(1L)
                .imageName("pytorch")
                .imageVersion("2.1.0")
                .cudaVersion("11.8")
                .description("PyTorch 2.1.0")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("POST /api/admin/images")
    class CreateImage {

        @Test
        @DisplayName("유효한 요청으로 이미지를 생성하면 201 Created와 DTO를 반환한다")
        void createImage_returns200WithDto() throws Exception {
            when(containerImageService.createImage(any())).thenReturn(sampleDto());

            ContainerImageCreateRequest request = new ContainerImageCreateRequest(
                    "pytorch", "2.1.0", "11.8", "PyTorch 2.1.0"
            );

            mockMvc.perform(post("/api/admin/images")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.imageName").value("pytorch"));
        }
    }

    @Nested
    @DisplayName("POST /api/admin/images 입력 검증")
    class CreateImageValidation {

        @ParameterizedTest(name = "이름 [{0}] 버전 [{1}]")
        @CsvSource(value = {
                "'', 2.1.0",
                "'   ', 2.1.0",
                "PyTorch, 2.1.0",
                "pytorch:latest, 2.1.0",
                "pytorch;rm, 2.1.0",
                "/pytorch, 2.1.0",
                "pytorch, ''",
                "pytorch, -2.1.0",
                "pytorch, 2.1.0 x",
                "pytorch, 2.1.0@sha256"
        })
        @DisplayName("이미지 주소가 될 수 없는 이름·버전은 400으로 거절하고 저장하지 않는다")
        void createImage_invalidReference_returns400(String imageName, String imageVersion) throws Exception {
            ContainerImageCreateRequest request = new ContainerImageCreateRequest(
                    imageName, imageVersion, "11.8", "설명"
            );

            mockMvc.perform(post("/api/admin/images")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(containerImageService, never()).createImage(any());
        }

        @ParameterizedTest(name = "이름 [{0}] 버전 [{1}]")
        @CsvSource({
                "cuda, 11.8",
                "decs/pytorch-cuda, 260915",
                "registry.example.com/team/image_name, v1.0-rc.1"
        })
        @DisplayName("저장소 경로가 붙은 이름과 일반적인 태그는 받는다")
        void createImage_validReference_returns201(String imageName, String imageVersion) throws Exception {
            when(containerImageService.createImage(any())).thenReturn(sampleDto());
            ContainerImageCreateRequest request = new ContainerImageCreateRequest(
                    imageName, imageVersion, "11.8", "설명"
            );

            mockMvc.perform(post("/api/admin/images")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("GET /api/images")
    class GetAllImages {

        @Test
        @DisplayName("이미지 목록을 조회하면 200 OK와 리스트를 반환한다")
        void getAllImages_returns200WithList() throws Exception {
            when(containerImageService.getAllImages()).thenReturn(List.of(sampleDto()));

            mockMvc.perform(get("/api/images").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].imageName").value("pytorch"));
        }
    }
}
