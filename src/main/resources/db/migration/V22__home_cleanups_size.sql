-- 삭제 직전에 잰 홈 폴더 크기(바이트). 어느 정도를 지웠는지의 기록이라 못 쟀거나 이미 없던 홈은 비워 둔다.
ALTER TABLE `home_cleanups` ADD COLUMN `size_bytes` bigint DEFAULT NULL;
