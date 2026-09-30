-- 전화번호를 한 가지 모양(010-1234-5678, 02-123-4567)으로 맞춘다. 앱은 이제 하이픈 유무와 상관없이 받아
-- PhoneNumbers.normalize로 저장하므로, 그 전에 들어온 값도 같은 규칙으로 고쳐 둔다.
-- 숫자만 남겼을 때 규칙에 맞지 않는 값(해외 번호 등)은 건드리지 않는다.
-- 같은 표를 하위 조회로 다시 읽으면 MySQL이 막으므로(1093) 숫자 추출식을 식마다 되풀이한다.
UPDATE `users`
SET `phone` = CASE
  WHEN REGEXP_REPLACE(`phone`, '[^0-9]', '') LIKE '02%'
    THEN CONCAT(
      LEFT(REGEXP_REPLACE(`phone`, '[^0-9]', ''), 2), '-',
      SUBSTRING(REGEXP_REPLACE(`phone`, '[^0-9]', ''), 3, CHAR_LENGTH(REGEXP_REPLACE(`phone`, '[^0-9]', '')) - 6), '-',
      RIGHT(REGEXP_REPLACE(`phone`, '[^0-9]', ''), 4))
  ELSE CONCAT(
      LEFT(REGEXP_REPLACE(`phone`, '[^0-9]', ''), 3), '-',
      SUBSTRING(REGEXP_REPLACE(`phone`, '[^0-9]', ''), 4, CHAR_LENGTH(REGEXP_REPLACE(`phone`, '[^0-9]', '')) - 7), '-',
      RIGHT(REGEXP_REPLACE(`phone`, '[^0-9]', ''), 4))
END
WHERE REGEXP_LIKE(REGEXP_REPLACE(`phone`, '[^0-9]', ''), '^(02[0-9]{7,8}|0[13-9][0-9]{8,9})$');
