-- 공용 그룹 작업(생성·멤버 추가·제거). 그룹은 AD·계정 원장·NAS·떠 있는 컨테이너에 걸쳐 있어 config-server
-- 작업으로만 바꾸고, 그 작업이 성공해야 groups·user_groups 를 바꾼다. 이 표는 작업이 끝날 때까지의 진행을 담는다.
-- 종류마다 쓰는 칸: CREATE 는 group_name, ADD 는 change_request_id(더할 그룹은 그 변경 요청에 있다),
-- REMOVE 는 group_id.
CREATE TABLE `group_operations` (
  `group_operation_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `kind` varchar(10) NOT NULL,
  `status` varchar(20) NOT NULL,
  `ubuntu_username` varchar(100) DEFAULT NULL,
  `group_name` varchar(100) DEFAULT NULL,
  `job_id` bigint DEFAULT NULL,
  `error_code` varchar(64) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `group_id` bigint DEFAULT NULL,
  `change_request_id` bigint DEFAULT NULL,
  `requested_by` bigint NOT NULL,
  PRIMARY KEY (`group_operation_id`),
  KEY `idx_group_operations_status` (`status`),
  KEY `idx_group_operations_user` (`user_id`),
  KEY `idx_group_operations_group` (`group_id`),
  KEY `idx_group_operations_change_request` (`change_request_id`),
  KEY `idx_group_operations_requested_by` (`requested_by`),
  CONSTRAINT `fk_group_operations_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_group_operations_group` FOREIGN KEY (`group_id`) REFERENCES `groups` (`group_id`),
  CONSTRAINT `fk_group_operations_change_request` FOREIGN KEY (`change_request_id`) REFERENCES `change_request` (`change_request_id`),
  CONSTRAINT `fk_group_operations_requested_by` FOREIGN KEY (`requested_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
