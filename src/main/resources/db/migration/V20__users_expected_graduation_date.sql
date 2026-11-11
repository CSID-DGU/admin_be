-- 졸업 예정일. 이 날짜 + 30일이 지나면 계정을 비활성화한다(마지막 컨테이너 종료 1년 규칙 대신).
-- 이전에 가입한 사용자는 비어 있고, 접속할 때 입력받는다. 비어 있는 동안은 1년 규칙이 그대로 적용된다.
ALTER TABLE `users` ADD COLUMN `expected_graduation_date` date NULL;
