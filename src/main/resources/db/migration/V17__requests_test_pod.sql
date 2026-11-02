-- 관리자 테스트 Pod: 신청서 없이 만들고 알림을 알림 기록(noti) 채널로만 보내기 위한 표시.
-- pinned_node 는 이 신청의 Pod 를 만들 노드(비면 리소스 그룹 전체에서 config-server 가 고른다).
ALTER TABLE `requests` ADD COLUMN `is_test` boolean NOT NULL DEFAULT FALSE;
ALTER TABLE `requests` ADD COLUMN `pinned_node` varchar(100) NULL;
