-- 관리자가 지도교수 승인을 확인하고 승인했다는 기록. 확인한 관리자(users.user_id)와 시각이다.
-- 이전에 승인된 신청과 관리자가 신청 없이 만드는 테스트 Pod 신청은 비어 있다.
ALTER TABLE `requests`
  ADD COLUMN `professor_approval_confirmed_by` bigint NULL,
  ADD COLUMN `professor_approval_confirmed_at` datetime(6) NULL;
