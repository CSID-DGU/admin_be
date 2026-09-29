-- 신청 생명주기 정리: UID/GID를 사람(users)에 영구 귀속하고, requests의 사본 컬럼과 작업 번호 칸 둘을 정리한다.
-- ddl-auto:update에 맡기지 않고 수동으로 실행한다(~/.claude/CLAUDE.md 규칙) — update는 컬럼을 지우지 않는다.
--
-- 실행 절차 (옛 admin_be와 새 admin_be가 동시에 돌면 users.ubuntu_uid의 뜻이 섞이므로 한 번에 바꾼다):
--   1) admin_be를 0개로 줄인다:  kubectl -n <ns> scale deploy/admin-prod --replicas=0
--   2) 이 스크립트를 실행한다(DDL은 자동 커밋되므로 위에서부터 차례로, 실패하면 그 줄에서 멈추고 확인).
--   3) 이 버전의 admin_be를 배포한다.
--
-- 사전 확인(0이어야 한다 — 진행 중인 작업이 있으면 끝날 때까지 기다린다):
--   SELECT COUNT(*) FROM requests WHERE status IN ('PROCESSING', 'MIGRATING', 'EXPIRING');
-- 사전 확인(0이어야 한다 — 신청 사본과 사용자 유저네임이 다르면 어느 쪽이 맞는지 먼저 정한다):
--   SELECT COUNT(*) FROM requests r JOIN users u ON u.user_id = r.user_id
--    WHERE NOT (r.ubuntu_username <=> u.ubuntu_username);

-- 1. 계정이 지금 살아 있는지를 UID가 비었는지로 표현하던 것을 명시 칸으로 바꾼다.
ALTER TABLE users
    ADD COLUMN ubuntu_account_active TINYINT(1) NOT NULL DEFAULT 0;

UPDATE users
   SET ubuntu_account_active = 1
 WHERE ubuntu_uid IS NOT NULL AND ubuntu_gid IS NOT NULL;

-- 2. 회수돼서 UID가 비워졌던 사람은 마지막 신청에 남은 UID/GID를 되돌려 받는다(계정은 살아 있지 않음).
--    이 값이 다음 승인의 expected_uid가 된다.
UPDATE users u
  JOIN (SELECT r.user_id, r.ubuntu_uid, r.ubuntu_gid
          FROM requests r
          JOIN (SELECT user_id, MAX(request_id) AS request_id
                  FROM requests
                 WHERE ubuntu_uid IS NOT NULL AND ubuntu_gid IS NOT NULL
                 GROUP BY user_id) last ON last.request_id = r.request_id) h ON h.user_id = u.user_id
   SET u.ubuntu_uid = h.ubuntu_uid,
       u.ubuntu_gid = h.ubuntu_gid
 WHERE u.ubuntu_uid IS NULL;

-- 3. 생성·마이그레이션 작업 번호를 칸 하나로 합친다(한 신청에 동시에 도는 작업은 하나뿐이다).
ALTER TABLE requests
    ADD COLUMN job_id BIGINT NULL;

UPDATE requests
   SET job_id = COALESCE(provision_job_id, migration_job_id);

-- 4. users에 있는 값의 사본과 합쳐진 작업 번호 칸을 지운다.
ALTER TABLE requests
    DROP COLUMN ubuntu_username,
    DROP COLUMN ubuntu_uid,
    DROP COLUMN ubuntu_gid,
    DROP COLUMN provision_job_id,
    DROP COLUMN migration_job_id;
