-- 경고 대장. 행을 고치거나 지우지 않고 더하기만 한다 — 현재 횟수와 "3회 이상 쌓인 적이 있는가"는 이 표를 처음부터
-- 다시 읽어 구한다. GRANT 는 부여, DEDUCT 는 절차를 지킨 신고에 따른 차감, CANCEL 은 잘못 준 경고의 취소다.
-- canceled_warning_id 는 CANCEL 행이 취소한 GRANT 행이다. 같은 경고를 두 번 취소하지 못하게 유일하다.
CREATE TABLE `user_warnings` (
  `warning_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `type` varchar(20) NOT NULL,
  `reason` varchar(500) NOT NULL,
  `user_id` bigint NOT NULL,
  `issued_by` bigint NOT NULL,
  `canceled_warning_id` bigint DEFAULT NULL,
  PRIMARY KEY (`warning_id`),
  UNIQUE KEY `uk_user_warnings_canceled_warning` (`canceled_warning_id`),
  KEY `idx_user_warnings_user` (`user_id`),
  KEY `idx_user_warnings_issued_by` (`issued_by`),
  CONSTRAINT `fk_user_warnings_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_user_warnings_issued_by` FOREIGN KEY (`issued_by`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_user_warnings_canceled_warning` FOREIGN KEY (`canceled_warning_id`) REFERENCES `user_warnings` (`warning_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 이용 정지. 경고(GRANT) 하나가 정지 하나를 만든다. ends_at 이 지나지 않은 행이 하나라도 있으면 그 사용자는 정지 중이다.
-- 경고를 취소하면 그 경고의 정지는 ends_at 을 그 시각으로 당긴다.
CREATE TABLE `user_suspensions` (
  `suspension_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `starts_at` datetime(6) NOT NULL,
  `ends_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `warning_id` bigint NOT NULL,
  PRIMARY KEY (`suspension_id`),
  UNIQUE KEY `uk_user_suspensions_warning` (`warning_id`),
  KEY `idx_user_suspensions_user_ends_at` (`user_id`, `ends_at`),
  CONSTRAINT `fk_user_suspensions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_user_suspensions_warning` FOREIGN KEY (`warning_id`) REFERENCES `user_warnings` (`warning_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 계정의 접속 차단·해제 작업. 접속은 config-server 작업으로만 막고 풀며, 마지막으로 성공한(APPLIED) 행의 blocked 가
-- 지금 실제로 적용된 상태다. username 은 작업을 등록할 때의 리눅스 계정이다.
CREATE TABLE `access_operations` (
  `access_operation_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `blocked` bit(1) NOT NULL,
  `username` varchar(100) NOT NULL,
  `job_id` bigint DEFAULT NULL,
  `error_code` varchar(64) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`access_operation_id`),
  KEY `idx_access_operations_status` (`status`),
  KEY `idx_access_operations_user` (`user_id`),
  CONSTRAINT `fk_access_operations_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
