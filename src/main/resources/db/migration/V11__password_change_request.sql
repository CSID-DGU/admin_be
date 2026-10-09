-- 비밀번호 변경을 변경 요청의 한 종류(PASSWORD)로 합친다. 상태·검토자·검토 시각은 change_request 에만 두고,
-- password_reset_requests 는 그 변경 요청을 컨테이너에 반영하는 데 필요한 값(해시 두 개, 작업 번호)만 든다
-- (공유 그룹의 group_operations, 추가 포트의 port_operations 와 같은 구조).
--
-- 비밀번호 변경은 계정 단위라 대상 신청(request_id)·새 값(new_value)·사유(reason)가 없다.
ALTER TABLE `change_request`
  MODIFY `change_type` enum('CONTAINER_IMAGE','EXPIRES_AT','GROUP','PASSWORD','PORT','RESOURCE_GROUP') NOT NULL,
  MODIFY `new_value` json DEFAULT NULL,
  MODIFY `reason` varchar(1000) DEFAULT NULL,
  MODIFY `request_id` bigint DEFAULT NULL,
  -- 기존 재설정 신청을 옮기는 동안만 쓰는 짝 맞춤 칸. 이 파일 끝에서 지운다.
  ADD COLUMN `migrated_password_reset_id` bigint DEFAULT NULL;

INSERT INTO `change_request`
  (`created_at`, `updated_at`, `change_type`, `status`, `requested_by`, `reviewed_by`, `reviewed_at`, `migrated_password_reset_id`)
SELECT `created_at`, `updated_at`, 'PASSWORD',
       CASE `status` WHEN 'APPLIED' THEN 'FULFILLED' ELSE `status` END,
       `user_id`, `reviewed_by`, `reviewed_at`, `password_reset_request_id`
FROM `password_reset_requests`
ORDER BY `password_reset_request_id`;

ALTER TABLE `password_reset_requests`
  ADD COLUMN `change_request_id` bigint DEFAULT NULL;

UPDATE `password_reset_requests` r
  JOIN `change_request` c ON c.`migrated_password_reset_id` = r.`password_reset_request_id`
SET r.`change_request_id` = c.`change_request_id`;

ALTER TABLE `change_request`
  DROP COLUMN `migrated_password_reset_id`;

ALTER TABLE `password_reset_requests`
  DROP FOREIGN KEY `fk_password_reset_requests_reviewed_by`,
  DROP INDEX `idx_password_reset_requests_reviewed_by`,
  DROP INDEX `idx_password_reset_requests_status`,
  DROP INDEX `idx_password_reset_requests_user_status`,
  ADD INDEX `idx_password_reset_requests_user` (`user_id`),
  DROP COLUMN `status`,
  DROP COLUMN `reviewed_at`,
  DROP COLUMN `reviewed_by`,
  MODIFY `change_request_id` bigint NOT NULL,
  ADD UNIQUE KEY `uk_password_reset_requests_change_request` (`change_request_id`),
  ADD CONSTRAINT `fk_password_reset_requests_change_request` FOREIGN KEY (`change_request_id`) REFERENCES `change_request` (`change_request_id`);
