-- 신청서 채널에 올린 신청서 메시지의 Slack 식별자(ts). 이 신청의 후속 알림(취소 등)을 그 메시지의 스레드 댓글로
-- 달 때 쓴다. 웹훅으로 보낸 신청서는 식별자를 돌려받지 못하므로 비어 있고, 그때는 후속 알림도 일반 메시지로 간다.
ALTER TABLE `requests` ADD COLUMN `slack_message_ts` varchar(32) NULL;
