-- 관리자 HTTP 요청 기록. 기존 사용자·신청 삭제와 독립적으로 보존한다.
CREATE TABLE admin_action_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id CHAR(36) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    recorded_at DATETIME(6) NOT NULL,
    admin_id BIGINT NULL,
    action_type VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(255) COLLATE utf8mb4_bin NULL,
    http_status SMALLINT NOT NULL,
    requested_role VARCHAR(32) NULL,
    capture_status VARCHAR(32) NOT NULL,
    request_values TEXT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_action_event (event_id),
    KEY idx_admin_action_time (occurred_at, id),
    KEY idx_admin_action_admin_time (admin_id, occurred_at, id),
    KEY idx_admin_action_target (target_type, target_id, action_type, occurred_at, id),
    KEY idx_admin_action_role (target_type, target_id, action_type, requested_role, http_status, occurred_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
