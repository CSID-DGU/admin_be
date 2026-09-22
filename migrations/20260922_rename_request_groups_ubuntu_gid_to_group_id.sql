-- admin_be#556: request_groups.ubuntu_gid는 이름과 달리 groups.group_id를 저장한다
-- (RequestGroup의 @MapsId("ubuntuGid") 매핑 버그). SQL로 직접 읽는 사람이 계속 틀리게 읽으므로
-- 컬럼명을 실제 값에 맞게 고친다. ddl-auto:update에 맡기지 않고 수동으로 실행한다
-- (~/.claude/CLAUDE.md 규칙). 각 환경(ailab-operation 먼저, 이후 다른 스택/운영)에 직접 접속해 실행할 것.
--
-- MySQL CHANGE COLUMN은 PK/FK 구성 컬럼이라도 제약을 새로 만들 필요 없이 이름만 바꾼다
-- (PK/FK 정의가 컬럼을 그대로 따라간다).
ALTER TABLE request_groups
    CHANGE COLUMN ubuntu_gid group_id BIGINT NOT NULL;
