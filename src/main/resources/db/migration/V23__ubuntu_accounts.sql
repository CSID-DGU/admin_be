-- 리눅스 계정을 서버(resource_groups.server_name)별로 나눈다. 서버마다 AD·NAS가 달라 한 사람이 서버 수만큼 계정을 가진다.
-- 1단계: 새 표를 만들고 기존 값을 복사한다. users 의 ubuntu_uid/gid/account_status 컬럼은 롤백 안전을 위해 남겨 두고
-- 코드가 기본 서버 계정의 쓰기를 거기에도 미러링한다. 컬럼 삭제는 검증 뒤 후속 버전에서 한다.
CREATE TABLE `ubuntu_accounts` (
  `ubuntu_account_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `server_name` varchar(300) NOT NULL,
  `username` varchar(100) DEFAULT NULL,
  `uid` bigint DEFAULT NULL,
  `gid` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'NONE',
  PRIMARY KEY (`ubuntu_account_id`),
  UNIQUE KEY `uk_ubuntu_accounts_user_server` (`user_id`,`server_name`),
  UNIQUE KEY `uk_ubuntu_accounts_server_username` (`server_name`,`username`),
  UNIQUE KEY `uk_ubuntu_accounts_server_uid` (`server_name`,`uid`),
  CONSTRAINT `fk_ubuntu_accounts_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- uid 를 받은 적 있는 사용자만 옮긴다(승인 전 사용자는 가입 때 고른 이름만 users 에 있다). 서버는 그 사용자가 처음
-- 승인받은 신청의 리소스 그룹에서 찾고, 승인 기록이 없으면 리소스 그룹 중 이름순 첫 서버로 둔다.
INSERT INTO `ubuntu_accounts` (`created_at`, `updated_at`, `user_id`, `server_name`, `username`, `uid`, `gid`, `status`)
SELECT NOW(6), NOW(6), u.`user_id`,
       COALESCE(
         (SELECT rg.`server_name` FROM `requests` r JOIN `resource_groups` rg ON rg.`rsgroup_id` = r.`rsgroup_id`
           WHERE r.`user_id` = u.`user_id` AND r.`approved_at` IS NOT NULL
           ORDER BY r.`approved_at` LIMIT 1),
         (SELECT MIN(`server_name`) FROM `resource_groups` WHERE `server_name` IS NOT NULL)),
       u.`ubuntu_username`, u.`ubuntu_uid`, u.`ubuntu_gid`, u.`ubuntu_account_status`
FROM `users` u
WHERE u.`ubuntu_uid` IS NOT NULL;
