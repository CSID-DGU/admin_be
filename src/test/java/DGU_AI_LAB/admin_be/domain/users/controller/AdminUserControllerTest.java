package DGU_AI_LAB.admin_be.domain.users.controller;

import DGU_AI_LAB.admin_be.domain.users.dto.response.UserResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserSummaryDTO;
import DGU_AI_LAB.admin_be.domain.users.service.AdminUserService;
import DGU_AI_LAB.admin_be.domain.users.service.UserService;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.ConflictException;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import DGU_AI_LAB.admin_be.support.WebMvcTestSupport;
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

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = AdminUserController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
class AdminUserControllerTest extends WebMvcTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminUserService adminUserService;

    @MockitoBean
    private UserService userService;

    @Nested
    @DisplayName("GET /api/admin/users")
    class GetAllUsers {

        @Test
        @DisplayName("전체 유저 목록을 조회하면 200 OK와 목록을 반환한다")
        void getAllUsers_returns200WithList() throws Exception {
            List<UserSummaryDTO> users = List.of(
                    UserSummaryDTO.builder()
                            .userId(1L).name("홍길동").email("test@dgu.ac.kr")
                            .role("USER").studentId("2021001234").phone("010-1111-2222")
                            .department("컴퓨터공학과").isActive(true)
                            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                            .build()
            );
            when(adminUserService.getAllUsers()).thenReturn(users);

            mockMvc.perform(get("/api/admin/users").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].name").value("홍길동"));
        }
    }

    @Nested
    @DisplayName("GET /api/admin/users/{id}")
    class GetUser {

        @Test
        @DisplayName("존재하는 id로 조회하면 200 OK와 유저 정보를 반환한다")
        void getUser_returns200WithUser() throws Exception {
            UserResponseDTO dto = new UserResponseDTO(1L, "홍길동", true);
            when(userService.getUserById(1L)).thenReturn(dto);

            mockMvc.perform(get("/api/admin/users/1").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.username").value("홍길동"));
        }

        @Test
        @DisplayName("존재하지 않는 id로 조회하면 404 Not Found를 반환한다")
        void getUser_returns404_whenNotFound() throws Exception {
            when(userService.getUserById(99L)).thenThrow(new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));

            mockMvc.perform(get("/api/admin/users/99").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/admin/users/{id}")
    class DeleteUser {

        @Test
        @DisplayName("유저를 삭제하면 200 OK를 반환한다")
        void deleteUser_returns200() throws Exception {
            doNothing().when(adminUserService).deleteUser(1L);

            mockMvc.perform(delete("/api/admin/users/1").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("존재하지 않는 유저를 삭제하면 404 Not Found를 반환한다")
        void deleteUser_returns404_whenNotFound() throws Exception {
            doThrow(new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND))
                    .when(adminUserService).deleteUser(99L);

            mockMvc.perform(delete("/api/admin/users/99").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PATCH /api/admin/users/{id}/reactivate")
    class ReactivateUser {

        private final UserSummaryDTO reactivatedUser = UserSummaryDTO.builder()
                .userId(1L).name("홍길동").email("test@dgu.ac.kr")
                .role("USER").studentId("2021001234").phone("010-1111-2222")
                .department("컴퓨터공학과").isActive(true)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        @Test
        @DisplayName("비활성화 유저를 재활성화하면 200 OK와 사용자 정보를 반환한다")
        void reactivateUser_returns200() throws Exception {
            when(adminUserService.reactivateUser(1L)).thenReturn(reactivatedUser);

            mockMvc.perform(patch("/api/admin/users/1/reactivate").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.isActive").value(true))
                    .andExpect(jsonPath("$.data.name").value("홍길동"));
        }

        @Test
        @DisplayName("존재하지 않는 유저면 404 Not Found를 반환한다")
        void reactivateUser_returns404_whenNotFound() throws Exception {
            when(adminUserService.reactivateUser(99L))
                    .thenThrow(new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));

            mockMvc.perform(patch("/api/admin/users/99/reactivate").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("이미 활성화된 유저면 409 Conflict를 반환한다")
        void reactivateUser_returns409_whenAlreadyActive() throws Exception {
            when(adminUserService.reactivateUser(1L))
                    .thenThrow(new ConflictException(ErrorCode.USER_ALREADY_ACTIVE));

            mockMvc.perform(patch("/api/admin/users/1/reactivate").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isConflict());
        }
    }
}
