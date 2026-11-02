-- 테스트 Pod 신청 번호 대장(id = 2). 실 사용자 번호(id = 1)와 따로 세어, 테스트 신청이 실 번호를 쓰지 않게 한다.
-- config-server 가 번호를 int 로 다룰 수 있으니 int 최댓값(2147483647) 안의 2,000,000,001부터 센다.
INSERT INTO `request_numbers` (`id`, `next_number`) VALUES (2, 2000000001);
