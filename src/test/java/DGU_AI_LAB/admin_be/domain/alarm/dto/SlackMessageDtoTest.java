package DGU_AI_LAB.admin_be.domain.alarm.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SlackMessageDto")
class SlackMessageDtoTest {

    /** RedisConfig가 큐 값에 쓰는 것과 같은 직렬화. */
    private final GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();

    @Test
    @DisplayName("모르는 필드가 있는 항목도 읽는다 — 다른 버전이 넣은 항목을 꺼내도 버리지 않는다")
    void unknownFieldsAreIgnored() {
        String withUnknown = "{\"@class\":\"" + SlackMessageDto.class.getName() + "\",\"type\":\"DM\","
                + "\"message\":\"안내\",\"username\":\"홍길동\",\"email\":\"hong@dgu.ac.kr\",\"futureField\":[\"java.util.ArrayList\",[\"x\"]]}";

        SlackMessageDto read = (SlackMessageDto) serializer.deserialize(withUnknown.getBytes(StandardCharsets.UTF_8));

        assertThat(read.getUsername()).isEqualTo("홍길동");
    }
}
