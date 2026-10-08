-- 추가 포트 변경 작업. 떠 있는 컨테이너의 포트는 config-server 작업으로만 바꾸고, 그 작업이 성공해야
-- port_requests·pod_external_ports 를 바꾼다. 이 표는 작업이 끝날 때까지의 진행을 담는다.
-- pod_name 은 작업을 등록할 때의 컨테이너다 — 끝났을 때 그 컨테이너가 그대로인지 이 값으로 확인한다.
CREATE TABLE `port_operations` (
  `port_operation_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `pod_name` varchar(255) NOT NULL,
  `job_id` bigint DEFAULT NULL,
  `error_code` varchar(64) DEFAULT NULL,
  `request_id` bigint NOT NULL,
  `change_request_id` bigint NOT NULL,
  `requested_by` bigint NOT NULL,
  PRIMARY KEY (`port_operation_id`),
  KEY `idx_port_operations_status` (`status`),
  KEY `idx_port_operations_request` (`request_id`),
  KEY `idx_port_operations_change_request` (`change_request_id`),
  KEY `idx_port_operations_requested_by` (`requested_by`),
  CONSTRAINT `fk_port_operations_request` FOREIGN KEY (`request_id`) REFERENCES `requests` (`request_id`),
  CONSTRAINT `fk_port_operations_change_request` FOREIGN KEY (`change_request_id`) REFERENCES `change_request` (`change_request_id`),
  CONSTRAINT `fk_port_operations_requested_by` FOREIGN KEY (`requested_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
