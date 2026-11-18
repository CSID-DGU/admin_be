-- 관리자 작업 기록. 관리자 API 요청 하나가 한 줄이며, 쌓기만 하고 고치거나 지우지 않는다(updated_at 없음).
-- 결과(SUCCESS·REJECTED·ERROR)는 저장하지 않고 http_status 로 계산한다.
-- 관리자는 번호와 이름을 그때 값으로 복사해 둔다. 기록을 사용자 표의 사정에 묶지 않도록 외래 키를 두지 않는다.
-- action·target_type 은 enum 값을 늘려도 표를 고치지 않도록 varchar 로 둔다.
CREATE TABLE `admin_action_logs` (
  `admin_action_log_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `action` varchar(50) NOT NULL,
  `target_type` varchar(30) NOT NULL,
  `target_id` varchar(100) DEFAULT NULL,
  `admin_id` bigint NOT NULL,
  `admin_name` varchar(100) NOT NULL,
  `http_method` varchar(10) NOT NULL,
  `request_uri` varchar(500) NOT NULL,
  `http_status` int NOT NULL,
  `error_code` varchar(64) DEFAULT NULL,
  `path_variables` longtext DEFAULT NULL,
  `query_params` longtext DEFAULT NULL,
  `request_body` longtext DEFAULT NULL,
  PRIMARY KEY (`admin_action_log_id`),
  KEY `idx_admin_action_logs_target` (`target_type`,`target_id`,`admin_action_log_id`),
  KEY `idx_admin_action_logs_admin_created` (`admin_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
