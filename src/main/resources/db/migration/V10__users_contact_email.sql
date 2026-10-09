-- 학교 이메일 말고 평소에 쓰는 이메일. Slack에 이 주소로 가입한 사람을 같은 사람으로 알아보는 데 쓴다.
-- 비어 있으면 학교 이메일과 같다는 뜻이다.
ALTER TABLE `users` ADD COLUMN `contact_email` varchar(100) NULL;
