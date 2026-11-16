CREATE TABLE admin_action_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admin_id BIGINT NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id VARCHAR(255) NOT NULL,
    requested_role VARCHAR(32) NULL,
    http_status INT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    INDEX idx_admin_action_logs_admin_time (admin_id, occurred_at, id),
    INDEX idx_admin_action_logs_target_action_time
        (target_type, target_id, action_type, occurred_at, id),
    INDEX idx_admin_action_logs_time (occurred_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;