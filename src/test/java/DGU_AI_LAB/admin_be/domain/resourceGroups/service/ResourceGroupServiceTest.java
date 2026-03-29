package DGU_AI_LAB.admin_be.domain.resourceGroups.service;

import DGU_AI_LAB.admin_be.domain.gpus.repository.GpuRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.dto.response.ResourceGroupResponseDTO;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResourceGroupServiceTest {

    @InjectMocks
    private ResourceGroupService resourceGroupService;

    @Mock
    private GpuRepository gpuRepository;

    @Mock
    private ResourceGroupRepository resourceGroupRepository;

    @Nested
    @DisplayName("getAllResourceGroups")
    class GetAllResourceGroups {

        @Test
        @DisplayName("리소스 그룹이 있으면 목록을 반환한다")
        void getAllResourceGroups_returnsList() {
            ResourceGroup rg = ResourceGroup.builder()
                    .resourceGroupName("GPU-Server-A")
                    .description("메인 GPU 서버")
                    .serverName("server-01")
                    .build();

            when(resourceGroupRepository.findAll()).thenReturn(List.of(rg));

            List<ResourceGroupResponseDTO> result = resourceGroupService.getAllResourceGroups();

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("리소스 그룹이 없어도 빈 리스트를 반환한다")
        void getAllResourceGroups_returnsEmptyList_whenNoGroups() {
            when(resourceGroupRepository.findAll()).thenReturn(List.of());

            List<ResourceGroupResponseDTO> result = resourceGroupService.getAllResourceGroups();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getGpuTypeResources")
    class GetGpuTypeResources {

        @Test
        @DisplayName("GPU 요약 정보가 없으면 BusinessException을 던진다")
        void getGpuTypeResources_throwsException_whenEmpty() {
            when(gpuRepository.findGpuSummary()).thenReturn(List.of());

            assertThatThrownBy(() -> resourceGroupService.getGpuTypeResources())
                    .isInstanceOf(BusinessException.class);
        }
    }
}
