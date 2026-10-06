-- 사용이 끝난 계정의 홈 삭제 기록. 마지막 컨테이너가 끝나고 보존 기간이 지나면 config-server 작업으로 홈을 지운다.
-- 한 행이 삭제 시도 한 번이다. 같은 종료 시각에 대해 진행 중이거나 끝난 행이 있으면 다시 등록하지 않는다.
-- 유저네임·uid 는 등록할 때의 값을 그대로 남긴다(무엇을 지웠는지의 기록).
CREATE TABLE `home_cleanups` (
  `home_cleanup_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `ubuntu_username` varchar(100) NOT NULL,
  `ubuntu_uid` bigint NOT NULL,
  `last_container_ended_at` datetime(6) NOT NULL,
  `job_id` bigint DEFAULT NULL,
  `failure_code` varchar(64) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`home_cleanup_id`),
  KEY `idx_home_cleanups_user_status` (`user_id`, `status`),
  KEY `idx_home_cleanups_status` (`status`),
  CONSTRAINT `fk_home_cleanups_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
