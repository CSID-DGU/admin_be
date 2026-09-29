-- 작업 결과 폴러(3초마다 status로 조회)와 재조정(5분마다 status + updated_at으로 조회)이 신청 표 전체를 읽지 않게 한다.
-- MySQL 8은 보조 인덱스를 온라인(INPLACE, 쓰기 허용)으로 추가한다.
CREATE INDEX `idx_requests_status_updated_at` ON `requests` (`status`, `updated_at`);
