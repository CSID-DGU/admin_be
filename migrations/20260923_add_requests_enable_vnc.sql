-- admin_be#235: 신청 시 noVNC 활성화 여부를 저장한다. config-server가 Pod 생성 전
-- GET /api/requests/config/{username}로 조회하는 설정에 이 값을 실어 보내 ENABLE_VNC를
-- 주입할지 결정한다. ddl-auto:update에 맡기지 않고 수동으로 실행한다(~/.claude/CLAUDE.md 규칙).
ALTER TABLE requests
    ADD COLUMN enable_vnc TINYINT(1) NOT NULL DEFAULT 0;
