-- 채널에 올린 변경 요청 접수 알림의 Slack 식별자(ts). 그 요청의 결과(승인 완료·거절)를 접수 알림의 스레드 댓글로
-- 달 때 쓴다(requests.slack_message_ts 와 같은 쓰임). 웹훅으로 보낸 알림은 식별자를 돌려받지 못하므로 비어 있고,
-- 그때는 결과 알림도 일반 메시지로 간다.
ALTER TABLE `change_request` ADD COLUMN `slack_message_ts` varchar(32) NULL;
