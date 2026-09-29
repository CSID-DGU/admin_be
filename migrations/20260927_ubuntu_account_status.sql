-- 회수 비동기화: 우분투 계정의 살아 있음 여부(참/거짓)를 생명주기 상태(NONE·ACTIVE·RELEASING)로 바꾸고,
-- DELETED 신청의 작업 번호 칸을 "계정 회수 작업 번호" 전용으로 비운다.
-- ddl-auto:update에 맡기지 않고 수동으로 실행한다(~/.claude/CLAUDE.md 규칙) — update는 컬럼을 지우지 않는다.
--
-- 실행 절차 (옛 admin_be는 ubuntu_account_active를, 새 admin_be는 ubuntu_account_status를 읽으므로 한 번에 바꾼다):
--   1) admin_be를 0개로 줄인다:  kubectl -n <ns> scale deploy/admin-prod --replicas=0
--   2) 이 스크립트를 실행한다(DDL은 자동 커밋되므로 위에서부터 차례로, 실패하면 그 줄에서 멈추고 확인).
--   3) 이 버전의 admin_be를 배포한다.
--
-- 사전 확인(0이어야 한다 — 옛 admin_be는 회수를 요청 안에서 기다리므로, 멈춘 뒤 EXPIRING이 남았으면
-- 그 회수 결과를 확인하고 FULFILLED/DELETED로 먼저 정리한다):
--   SELECT COUNT(*) FROM requests WHERE status = 'EXPIRING';

-- 1. 계정 상태 칸을 만들고 옛 참/거짓 값을 옮긴다.
ALTER TABLE users
    ADD COLUMN ubuntu_account_status VARCHAR(20) NOT NULL DEFAULT 'NONE';

UPDATE users
   SET ubuntu_account_status = 'ACTIVE'
 WHERE ubuntu_account_active = 1;

-- 2. DELETED 신청의 작업 번호는 이제 계정 회수 작업 번호다. 옛 값(생성·회수 작업 번호)이 남아 있으면
--    계정 회수 폴러가 이미 등록된 것으로 보고 결과만 기다리다 멈춘다.
UPDATE requests
   SET job_id = NULL
 WHERE status = 'DELETED';

-- 3. 옛 칸을 지운다.
ALTER TABLE users
    DROP COLUMN ubuntu_account_active;
