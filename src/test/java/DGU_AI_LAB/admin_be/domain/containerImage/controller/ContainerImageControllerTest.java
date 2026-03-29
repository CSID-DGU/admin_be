package DGU_AI_LAB.admin_be.domain.containerImage.controller;

import DGU_AI_LAB.admin_be.domain.containerImage.dto.request.ContainerImageCreateRequest;
import DGU_AI_LAB.admin_be.domain.containerImage.dto.response.ContainerImageResponseDTO;
import DGU_AI_LAB.admin_be.domain.containerImage.service.ContainerImageService;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.support.WebMvcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
        value = ContainerImageController.class,
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
    @DisplayName("POST /api/images")
    class CreateImage {

        @Test
        @DisplayName("유효한 요청으로 이미지를 생성하면 200 OK와 DTO를 반환한다")
        void createImage_returns200WithDto() throws Exception {
            when(containerImageService.createImage(any())).thenReturn(sampleDto());

            ContainerImageCreateRequest request = new ContainerImageCreateRequest(
                    "pytorch", "2.1.0", "11.8", "PyTorch 2.1.0"
            );

            mockMvc.perform(post("/api/images")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.imageName").value("pytorch"));
        }
    }

    @Nested
    @DisplayName("GET /api/images/{id}")
    class GetImageById {

        @Test
        @DisplayName("존재하는 id로 조회하면 200 OK와 DTO를 반환한다")
        void getImageById_returns200WithDto() throws Exception {
            when(containerImageService.getImageById(1L)).thenReturn(sampleDto());

            mockMvc.perform(get("/api/images/1").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.imageName").value("pytorch"));
        }

        @Test
        @DisplayName("존재하지 않는 id로 조회하면 404 Not Found를 반환한다")
        void getImageById_returns404_whenNotFound() throws Exception {
            when(containerImageService.getImageById(99L))
                    .thenThrow(new BusinessException(ErrorCode.ENTITY_NOT_FOUND));

            mockMvc.perform(get("/api/images/99").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
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
