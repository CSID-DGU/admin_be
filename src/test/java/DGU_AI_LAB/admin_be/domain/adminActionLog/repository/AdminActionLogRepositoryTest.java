package DGU_AI_LAB.admin_be.domain.adminActionLog.repository;

import DGU_AI_LAB.admin_be.domain.adminActionLog.entity.AdminActionLog;
import DGU_AI_LAB.admin_be.domain.adminActionLog.entity.AdminActionResult;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AdminActionLogRepositoryTest {

    @Autowired
    AdminActionLogRepository repository;

    @Autowired
    EntityManager em;

    private AdminActionLog log(String action, String targetType, String targetId, long adminId,
                               LocalDateTime at, int status, String body) {
        return repository.save(AdminActionLog.builder()
                .createdAt(at).action(action).targetType(targetType).targetId(targetId)
                .adminId(adminId).adminName("관리자" + adminId)
                .httpMethod("POST").requestUri("/api/admin/x").httpStatus(status)
                .requestBody(body).build());
    }

    @Test
    @DisplayName("대상으로 찾으면 그 대상의 기록만 최신부터 나온다")
    void searchByTarget() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 16, 10, 0);
        log("REQUEST_APPROVE", "REQUEST", "5", 1, t, 202, null);
        log("REQUEST_REJECT", "REQUEST", "6", 1, t, 200, null);
        AdminActionLog later = log("REQUEST_APPROVE", "REQUEST", "5", 2, t.plusMinutes(1), 409, null);

        List<AdminActionLog> found = repository.search("REQUEST", "5", null, null, null, null, null, null, PageRequest.ofSize(10));

        assertThat(found).hasSize(2);
        assertThat(found.get(0).getAdminActionLogId()).isEqualTo(later.getAdminActionLogId());
        assertThat(found.get(0).result()).isEqualTo(AdminActionResult.REJECTED);
    }

    @Test
    @DisplayName("관리자와 기간으로 찾고, 커서보다 작은 번호만 정해진 건수만큼 나온다")
    void searchByAdminWithCursor() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 9, 0, 0);
        AdminActionLog a = log("WARNING_GRANT", "USER", "3", 7, t.plusDays(1), 201, null);
        AdminActionLog b = log("WARNING_GRANT", "USER", "4", 7, t.plusDays(2), 201, null);
        AdminActionLog c = log("WARNING_GRANT", "USER", "5", 7, t.plusDays(3), 201, null);
        log("WARNING_GRANT", "USER", "6", 7, t.plusDays(8), 201, null);   // 기간 밖
        log("WARNING_GRANT", "USER", "7", 8, t.plusDays(2), 201, null);   // 다른 관리자

        List<AdminActionLog> first = repository.search(null, null, 7L, null, t, t.plusDays(7), null, null, PageRequest.ofSize(2));
        List<AdminActionLog> next = repository.search(null, null, 7L, null, t, t.plusDays(7), null,
                first.get(1).getAdminActionLogId(), PageRequest.ofSize(2));

        assertThat(first).extracting(AdminActionLog::getAdminActionLogId)
                .containsExactly(c.getAdminActionLogId(), b.getAdminActionLogId());
        assertThat(next).extracting(AdminActionLog::getAdminActionLogId)
                .containsExactly(a.getAdminActionLogId());
    }

    @Test
    @DisplayName("본문 JSON이 그대로 저장되고 다시 읽힌다")
    void bodyRoundTrip() {
        AdminActionLog saved = log("USER_ROLE_CHANGE", "USER", "9", 1, LocalDateTime.now(), 200, "{\"role\":\"ADMIN\"}");
        em.flush();
        em.clear();

        List<AdminActionLog> found = repository.search("USER", "9", null, "USER_ROLE_CHANGE", null, null, null, null, PageRequest.ofSize(1));

        assertThat(found.get(0).getAdminActionLogId()).isEqualTo(saved.getAdminActionLogId());
        assertThat(found.get(0).getRequestBody()).contains("\"role\"").contains("ADMIN");
    }

    @Test
    @DisplayName("실패만 보면 400 이상(REJECTED·ERROR)만 나오고 202 같은 성공은 빠진다")
    void searchFailedOnly() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 16, 10, 0);
        log("REQUEST_APPROVE", "REQUEST", "5", 1, t, 202, null);
        AdminActionLog rejected = log("USER_PASSWORD_RESET", "USER", "3", 1, t, 400, null);
        AdminActionLog conflict = log("USER_ROLE_CHANGE", "USER", "3", 1, t, 409, null);
        AdminActionLog error = log("CONTAINER_DELETE", "REQUEST", "6", 1, t, 500, null);
        log("USER_ROLE_CHANGE", "USER", "4", 1, t, 200, null);

        List<AdminActionLog> found = repository.search(null, null, 1L, null, null, null, 400, null, PageRequest.ofSize(10));

        assertThat(found).extracting(AdminActionLog::getAdminActionLogId)
                .containsExactly(error.getAdminActionLogId(), conflict.getAdminActionLogId(),
                        rejected.getAdminActionLogId());
    }
}
