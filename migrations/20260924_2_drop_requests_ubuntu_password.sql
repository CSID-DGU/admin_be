-- [2단계] 이 버전의 admin_be 배포가 끝난 뒤 옛 평문 비밀번호 컬럼을 지운다.
-- 먼저 1단계(20260924_1_add_requests_ubuntu_password_hash.sql)가 실행돼 있어야 한다.
-- 1단계 이후 이전 버전 admin_be가 받은 신청이 있으면 평문이 남아 있을 수 있으니 먼저 확인한다.
--   SELECT request_id, status FROM requests WHERE ubuntu_password <> '';
-- 남아 있으면 그 신청을 거절한 뒤 사용자에게 다시 신청하게 한다(평문으로 해시를 만들 수 있는 경로는 두지 않는다).
ALTER TABLE requests
    DROP COLUMN ubuntu_password;
