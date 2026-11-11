-- 사용자가 신청하는 그룹 탈퇴. 관리자가 승인하면 기존 그룹 제거 작업(group_operations)으로 실제 소속을 뺀다.
-- 한 행이 신청 한 건이다. 같은 사용자·그룹의 PENDING 은 하나만 둔다(서비스가 확인).
CREATE TABLE `group_leave_requests` (
  `group_leave_request_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `user_id` bigint NOT NULL,
  `group_id` bigint NOT NULL,
  `decided_by` bigint DEFAULT NULL,
  `decided_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`group_leave_request_id`),
  KEY `idx_group_leave_requests_status` (`status`),
  KEY `idx_group_leave_requests_user_group_status` (`user_id`, `group_id`, `status`),
  CONSTRAINT `fk_group_leave_requests_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_group_leave_requests_group` FOREIGN KEY (`group_id`) REFERENCES `groups` (`group_id`),
  CONSTRAINT `fk_group_leave_requests_decided_by` FOREIGN KEY (`decided_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
