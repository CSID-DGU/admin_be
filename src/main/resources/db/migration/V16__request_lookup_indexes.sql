-- 컨테이너 이름 중복 확인(pod_name), 만료 임박·만료 신청 조회(status + expires_at), 상태별 변경 요청 조회(status)가
-- 표 전체를 읽지 않게 한다. MySQL 8은 보조 인덱스를 온라인(INPLACE, 쓰기 허용)으로 추가한다.
CREATE INDEX `idx_requests_pod_name` ON `requests` (`pod_name`);
CREATE INDEX `idx_requests_status_expires_at` ON `requests` (`status`, `expires_at`);
CREATE INDEX `idx_change_request_status` ON `change_request` (`status`);
