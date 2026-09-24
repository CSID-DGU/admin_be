-- 우분투 로그인 비밀번호를 평문 대신 SHA-512 crypt 해시로만 보관한다. ddl-auto:update에 맡기지 않고
-- 수동으로 실행한다(~/.claude/CLAUDE.md 규칙). 두 단계로 나눠 배포 중에도 신청이 실패하지 않게 한다.
--
-- [1단계] 해시 컬럼을 이 버전의 admin_be보다 먼저 추가한다. 이 SQL을 실행한 뒤에도 이전 버전 admin_be는
-- 계속 동작한다. 옛 평문 컬럼은 NULL을 허용하도록 바꾼다 — 새 admin_be는 이 컬럼에 값을 넣지 않는데
-- NOT NULL이면 신청 저장이 실패한다.
--
-- 사전 확인: 평문이 남은 신청이 없어야 한다(0이 아니면 해당 신청을 먼저 승인/거절으로 끝낸다).
--   SELECT COUNT(*) FROM requests WHERE ubuntu_password <> '';
ALTER TABLE requests
    ADD COLUMN ubuntu_password_hash VARCHAR(255) NULL,
    MODIFY COLUMN ubuntu_password VARCHAR(255) NULL;
