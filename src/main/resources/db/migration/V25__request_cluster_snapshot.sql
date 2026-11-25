-- FARM/LAB 라우팅 값은 자원그룹과 요청에 정규화해 보존한다.
-- 예상하지 못한 server_name이 있으면 CHECK/NOT NULL 적용 단계에서 배포를 중단하여
-- 임의의 기본 클러스터로 잘못 보내지 않게 한다.

UPDATE `resource_groups`
SET `server_name` = UPPER(TRIM(`server_name`))
WHERE `server_name` IS NOT NULL;

ALTER TABLE `resource_groups`
  MODIFY COLUMN `server_name` varchar(16) NOT NULL,
  ADD CONSTRAINT `chk_resource_groups_server_name`
    CHECK (`server_name` IN ('FARM', 'LAB'));

UPDATE `ubuntu_accounts`
SET `server_name` = UPPER(TRIM(`server_name`));

ALTER TABLE `ubuntu_accounts`
  MODIFY COLUMN `server_name` varchar(16) NOT NULL,
  ADD CONSTRAINT `chk_ubuntu_accounts_server_name`
    CHECK (`server_name` IN ('FARM', 'LAB'));

ALTER TABLE `requests`
  ADD COLUMN `cluster_id` varchar(16) NULL AFTER `rsgroup_id`;

UPDATE `requests` r
JOIN `resource_groups` rg ON rg.`rsgroup_id` = r.`rsgroup_id`
SET r.`cluster_id` = rg.`server_name`;

ALTER TABLE `requests`
  MODIFY COLUMN `cluster_id` varchar(16) NOT NULL,
  ADD CONSTRAINT `chk_requests_cluster_id`
    CHECK (`cluster_id` IN ('FARM', 'LAB')),
  ADD KEY `idx_requests_cluster_status` (`cluster_id`, `status`);

-- 기존 접속 작업은 단일 FARM config-server만 사용했다. 이후 작업은 등록 시점 클러스터를 보존해
-- 같은 곳에서 결과를 조회하고 사용자별 차단 상태도 클러스터마다 독립적으로 계산한다.
ALTER TABLE `access_operations`
  ADD COLUMN `cluster_id` varchar(16) NULL AFTER `user_id`;

UPDATE `access_operations`
SET `cluster_id` = 'FARM';

ALTER TABLE `access_operations`
  MODIFY COLUMN `cluster_id` varchar(16) NOT NULL,
  ADD CONSTRAINT `chk_access_operations_cluster_id`
    CHECK (`cluster_id` IN ('FARM', 'LAB')),
  ADD KEY `idx_access_operations_user_cluster_status`
    (`user_id`, `cluster_id`, `status`, `access_operation_id`);
