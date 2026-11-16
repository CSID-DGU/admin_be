package DGU_AI_LAB.admin_be.domain.adminActionLog.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * 관리자 작업 기록의 한 줄. 쌓기만 하고 고치거나 지우지 않는다 — {@link Immutable}이라 JPA가 UPDATE를 내지 않고,
 * 저장소도 저장·조회만 연다. 결과는 저장하지 않고 {@link #result()}가 상태 코드로 계산한다.
 * 관리자는 번호와 이름을 그때 값으로 복사해 둔다(외래 키 없음).
 */
@Entity
@Immutable
@Getter
@Table(name = "admin_action_logs", indexes = {
        @Index(name = "idx_admin_action_logs_target", columnList = "target_type, target_id, admin_action_log_id"),
        @Index(name = "idx_admin_action_logs_admin_created", columnList = "admin_id, created_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admin_action_log_id")
    private Long adminActionLogId;

    /** 요청이 들어온 시각. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** {@code AdminActionType} 이름. */
    @Column(name = "action", nullable = false, length = 50)
    private String action;

    /** {@code AdminTargetType} 이름. */
    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    /** 대상 경로 변수 값. 파드 이름처럼 숫자가 아닐 수 있어 문자열이다. */
    @Column(name = "target_id", length = 100)
    private String targetId;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "admin_name", nullable = false, length = 100)
    private String adminName;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @Column(name = "request_uri", nullable = false, length = 500)
    private String requestUri;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    /** 실패 응답의 {@code code}. 성공이면 없다. */
    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "path_variables", columnDefinition = "longtext")
    private String pathVariables;

    /** 가린 뒤의 쿼리 파라미터. */
    @Column(name = "query_params", columnDefinition = "longtext")
    private String queryParams;

    /** 가린 뒤의 요청 본문. 본문이 없거나 객체로 읽지 못했으면 없다. */
    @Column(name = "request_body", columnDefinition = "longtext")
    private String requestBody;

    @Builder
    private AdminActionLog(LocalDateTime createdAt, String action, String targetType, String targetId,
                           Long adminId, String adminName, String httpMethod, String requestUri,
                           int httpStatus, String errorCode,
                           String pathVariables, String queryParams, String requestBody) {
        this.createdAt = createdAt;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.adminId = adminId;
        this.adminName = adminName;
        this.httpMethod = httpMethod;
        this.requestUri = requestUri;
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.pathVariables = pathVariables;
        this.queryParams = queryParams;
        this.requestBody = requestBody;
    }

    public AdminActionResult result() {
        return AdminActionResult.of(httpStatus);
    }
}
