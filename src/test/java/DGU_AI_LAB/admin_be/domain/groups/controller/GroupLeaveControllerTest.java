package DGU_AI_LAB.admin_be.domain.groups.controller;

import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.entity.GroupLeaveRequest;
import DGU_AI_LAB.admin_be.domain.groups.service.GroupLeaveService;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 그룹 탈퇴 신청(사용자)·승인(관리자) 두 컨트롤러의 경로·상태 코드·로그인 사용자 전달을 확인한다. */
@ExtendWith(MockitoExtension.class)
class GroupLeaveControllerTest {

    @Mock private GroupLeaveService groupLeaveService;
    @InjectMocks private GroupLeaveController userController;
    @InjectMocks private AdminGroupLeaveController adminController;

    private MockMvc userMvc;
    private MockMvc adminMvc;
    private GroupLeaveRequest leave;

    @BeforeEach
    void setUp() {
        User user = User.builder().email("a@dgu.ac.kr").password("p").name("홍길동")
                .studentId("2021001234").phone("010-1111-2222").department("컴퓨터공학과").build();
        ReflectionTestUtils.setField(user, "userId", 7L);
        Group group = Group.builder().groupName("vision-lab").ubuntuGid(2000L).build();
        ReflectionTestUtils.setField(group, "groupId", 12L);
        leave = GroupLeaveRequest.pending(user, group);
        ReflectionTestUtils.setField(leave, "groupLeaveRequestId", 3L);

        CustomUserDetails principal = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        userMvc = MockMvcBuilders.standaloneSetup(userController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        adminMvc = MockMvcBuilders.standaloneSetup(adminController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/groups/{id}/leave-requests: 201과 대기 상태 신청을 돌려주고 로그인 사용자로 신청한다")
    void request() throws Exception {
        when(groupLeaveService.request(7L, 12L)).thenReturn(leave);

        userMvc.perform(post("/api/groups/12/leave-requests"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.leaveRequestId").value(3))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.groupName").value("vision-lab"));
    }

    @Test
    @DisplayName("GET /api/groups/leave-requests/me: 내 신청 목록을 돌려준다")
    void mine() throws Exception {
        when(groupLeaveService.mine(7L)).thenReturn(List.of(leave));

        userMvc.perform(get("/api/groups/leave-requests/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(7));
    }

    @Test
    @DisplayName("GET /api/admin/group-leave-requests: 승인 대기 목록을 돌려준다")
    void pending() throws Exception {
        when(groupLeaveService.pending()).thenReturn(List.of(leave));

        adminMvc.perform(get("/api/admin/group-leave-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("POST .../approval: 202로 돌려주고 승인한 관리자 번호를 넘긴다")
    void approve() throws Exception {
        when(groupLeaveService.approve(3L, 7L)).thenReturn(leave);

        adminMvc.perform(post("/api/admin/group-leave-requests/3/approval"))
                .andExpect(status().isAccepted());
        verify(groupLeaveService).approve(3L, 7L);
    }

    @Test
    @DisplayName("POST .../rejection: 200으로 돌려주고 거절한 관리자 번호를 넘긴다")
    void reject() throws Exception {
        when(groupLeaveService.reject(3L, 7L)).thenReturn(leave);

        adminMvc.perform(post("/api/admin/group-leave-requests/3/rejection"))
                .andExpect(status().isOk());
        verify(groupLeaveService).reject(3L, 7L);
    }
}
