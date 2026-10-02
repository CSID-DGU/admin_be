-- 공유 그룹은 "새로 만들기" 때 DB 에만 만들고(ubuntu_gid 없음), 그 그룹을 고른 신청이 승인돼 생성 작업이
-- 성공할 때 config-server 가 발급한 gid 를 채운다. gid 가 비어 있는 그룹을 승인 대기 그룹이라 부른다.
-- unique 는 유지한다 — MySQL unique 인덱스는 NULL 을 여러 개 허용한다.
ALTER TABLE `groups` MODIFY `ubuntu_gid` bigint NULL;
