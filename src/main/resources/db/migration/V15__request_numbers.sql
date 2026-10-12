-- 신청 번호 대장. 한 행(id = 1)만 두고 next_number 에 다음에 발급할 번호를 적는다. 신청을 저장하는 트랜잭션이
-- 이 행을 잠그고 올리므로 저장이 되돌려지면 번호도 돌아와 번호가 빠지지 않고, 행을 지워도 같은 번호가 다시
-- 나오지 않는다. 지금까지 발급된 가장 큰 번호 다음부터 잇는다.
CREATE TABLE `request_numbers` (
  `id` int NOT NULL,
  `next_number` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `request_numbers` (`id`, `next_number`)
SELECT 1, COALESCE(MAX(`request_id`), 0) + 1 FROM `requests`;
