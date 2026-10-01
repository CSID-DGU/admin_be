-- 비밀번호 재설정 신청. 사용자가 메일 인증을 거쳐 내거나 관리자가 직접 만든다. 관리자가 승인하면 config-server
-- 작업으로 컨테이너에 반영하고, 그 작업이 성공해야 users의 두 해시를 바꾼다.
-- 새 비밀번호는 해시 두 개로만 두며, 적용되거나 거절되면 비운다.
CREATE TABLE `password_reset_requests` (
  `password_reset_request_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `password_hash` varchar(255) DEFAULT NULL,
  `ubuntu_password_hash` varchar(255) DEFAULT NULL,
  `job_id` bigint DEFAULT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `reviewed_by` bigint DEFAULT NULL,
  PRIMARY KEY (`password_reset_request_id`),
  KEY `idx_password_reset_requests_user_status` (`user_id`, `status`),
  KEY `idx_password_reset_requests_status` (`status`),
  KEY `idx_password_reset_requests_reviewed_by` (`reviewed_by`),
  CONSTRAINT `fk_password_reset_requests_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_password_reset_requests_reviewed_by` FOREIGN KEY (`reviewed_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
