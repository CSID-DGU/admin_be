package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceUsageDTOTest {

    private User user() {
        User user = User.builder()
                .email("test@dgu.ac.kr")
                .password("encoded")
                .name("홍길동")
                .studentId("2021001234")
                .phone("010-1234-5678")
                .department("컴퓨터공학과")
                .build();
        ReflectionTestUtils.setField(user, "userId", 7L);
        return user;
    }

    @Test
    @DisplayName("볼륨 크기는 GiB 값을 그대로 담는다")
    void fromEntity_mapsVolumeSizeGiB() {
        ResourceGroup resourceGroup = ResourceGroup.builder()
                .rsgroupId(3)
                .resourceGroupName("GPU Server A")
                .description("메인 GPU 서버")
                .serverName("LAB")
                .build();
        Request request = Request.builder()
                .user(user())
                .resourceGroup(resourceGroup)
                .volumeSizeGiB(20L)
                .build();

        ResourceUsageDTO dto = ResourceUsageDTO.fromEntity(request);

        assertThat(dto.volumeSizeGiB()).isEqualTo(20L);
        assertThat(dto.userId()).isEqualTo(7L);
        assertThat(dto.userName()).isEqualTo("홍길동");
        assertThat(dto.resourceGroupId()).isEqualTo(3);
    }

    @Test
    @DisplayName("JSON 키는 단위가 드러나는 volumeSizeGiB로 나간다")
    void jsonKeyIsVolumeSizeGiB() throws Exception {
        ResourceGroup resourceGroup = ResourceGroup.builder()
                .rsgroupId(3)
                .resourceGroupName("GPU Server A")
                .description("메인 GPU 서버")
                .serverName("LAB")
                .build();
        Request request = Request.builder()
                .user(user())
                .resourceGroup(resourceGroup)
                .volumeSizeGiB(20L)
                .build();

        String json = new ObjectMapper().writeValueAsString(ResourceUsageDTO.fromEntity(request));

        assertThat(json).contains("\"volumeSizeGiB\":20");
        // 값은 GiB인데 키는 Byte라고 알려주던 이전 표기는 더 이상 나가지 않는다
        assertThat(json).doesNotContain("volumeSizeByte");
    }

    @Test
    @DisplayName("resourceGroup이 없으면 resourceGroupId를 null로 내려보낸다")
    void fromEntity_nullResourceGroup_yieldsNullId() {
        Request request = Request.builder()
                .user(user())
                .volumeSizeGiB(20L)
                .build();

        ResourceUsageDTO dto = ResourceUsageDTO.fromEntity(request);

        assertThat(dto.resourceGroupId()).isNull();
        assertThat(dto.volumeSizeGiB()).isEqualTo(20L);
    }
}
