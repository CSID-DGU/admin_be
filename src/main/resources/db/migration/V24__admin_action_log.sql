CREATE TABLE `admin_action_logs` (
    `admin_action_log_id` bigint NOT NULL AUTO_INCREMENT,
    `created_at` datetime(6) NOT NULL,
    `updated_at` datetime(6) DEFAULT NULL,
    `action_by` bigint NOT NULL,
    `action_type` varchar(50) NOT NULL,
    `target_type` varchar(30) NOT NULL,
    `target_id` varchar(100) NOT NULL,
    `result` varchar(20) NOT NULL,
    `changed_value` json DEFAULT NULL,
    PRIMARY KEY (`admin_action_log_id`),
    KEY `idx_admin_action_logs_action_by_created_at` (`action_by`, `created_at`),
    KEY `idx_admin_action_logs_target` (`target_type`, `target_id`, `created_at`),
    CONSTRAINT `fk_admin_action_logs_action_by` FOREIGN KEY (`action_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;