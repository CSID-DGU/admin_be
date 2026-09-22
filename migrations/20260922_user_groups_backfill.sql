-- 그룹 멤버십을 request(컨테이너) 단위에서 user(계정) 단위로 전환.
-- ddl-auto:update에 맡기지 않고 수동으로 실행한다 (~/.claude/CLAUDE.md 규칙).
-- 각 환경(ailab-operation 먼저, 이후 다른 스택/운영)에 직접 접속해 실행할 것.

CREATE TABLE IF NOT EXISTS user_groups (
    user_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (user_id, group_id),
    CONSTRAINT fk_user_groups_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_user_groups_group FOREIGN KEY (group_id) REFERENCES `groups`(group_id)
);

-- 기존 request_groups에 흩어진 그룹을 계정 단위로 합집합해 백필한다.
-- DELETED 신청은 실제로 정리된 컨테이너라 반영 여부를 신뢰할 수 없어 제외한다.
INSERT IGNORE INTO user_groups (user_id, group_id, created_at)
SELECT r.user_id, rg.group_id, MIN(rg.created_at)
FROM requests r
JOIN request_groups rg ON rg.request_id = r.request_id
WHERE r.status <> 'DELETED'
GROUP BY r.user_id, rg.group_id;
