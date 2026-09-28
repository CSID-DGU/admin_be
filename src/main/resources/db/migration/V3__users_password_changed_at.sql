-- 비밀번호를 바꾼 시각. 그보다 먼저 발급된 JWT는 인증 필터가 거절한다(토큰을 도난당했어도 비밀번호 변경으로 끊는다).
-- MySQL 8은 NULL 허용 열 끝 추가를 즉시(INSTANT) 처리한다.
ALTER TABLE `users` ADD COLUMN `password_changed_at` datetime(6) DEFAULT NULL;
