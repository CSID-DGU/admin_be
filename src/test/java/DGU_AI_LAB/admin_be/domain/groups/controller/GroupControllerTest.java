package DGU_AI_LAB.admin_be.domain.groups.controller;

import DGU_AI_LAB.admin_be.domain.groups.dto.response.GroupResponseDTO;
import DGU_AI_LAB.admin_be.domain.groups.service.GroupService;
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

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = GroupController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
class GroupControllerTest extends WebMvcTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GroupService groupService;

    @Nested
    @DisplayName("GET /api/groups")
    class GetGroups {

        @Test
        @DisplayName("그룹 목록을 조회하면 200 OK와 리스트를 반환한다")
        void getGroups_returns200WithList() throws Exception {
            List<GroupResponseDTO> groups = List.of(
                    GroupResponseDTO.builder().ubuntuGid(2000L).groupName("developers").build()
            );
            when(groupService.getAllGroups()).thenReturn(groups);

            mockMvc.perform(get("/api/groups").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].groupName").value("developers"));
        }

        @Test
        @DisplayName("그룹이 없으면 BusinessException에 의해 404를 반환한다")
        void getGroups_returns404_whenEmpty() throws Exception {
            when(groupService.getAllGroups())
                    .thenThrow(new BusinessException(ErrorCode.NO_AVAILABLE_GROUPS));

            mockMvc.perform(get("/api/groups").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }
}
