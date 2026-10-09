package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 실제 Slack API와 통신하는 transport 담당입니다.
 * 관심 있음: 어떻게 보내는지, 누구인지
 * 관심 없음: 누구한테 왜 보냈는지
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SlackApiService {

    @Value("${slack.bot-token}")
    private String botToken;

    private final RestTemplate restTemplate = createRestTemplate();

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);
        return new RestTemplate(factory);
    }
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String SLACK_USERS_CACHE_KEY = "slack:cache:users:list";
    private static final long CACHE_TTL_HOURS = 1;
    private static final String USERS_LIST_URL = "https://slack.com/api/users.list?limit=1000";
    private static final int MAX_USER_LIST_PAGES = 20;

    // =========================================================================
    // 1. Webhook 전송
    // =========================================================================
    public void sendWebhook(String webhookUrl, String message) {
        sendWebhook(webhookUrl, message, List.of());
    }

    /** blocks가 있으면 그 양식으로 보이고, message는 알림 미리보기에 쓰인다. */
    public void sendWebhook(String webhookUrl, String message, List<Map<String, Object>> blocks) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", message);
        if (!blocks.isEmpty()) {
            payload.put("blocks", blocks);
        }
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Slack Webhook 전송 응답 이상: {}", response.getStatusCode());
                throw new BusinessException(ErrorCode.SLACK_SEND_FAILED);
            }
        } catch (HttpClientErrorException e) {
            // 웹훅이 지워졌거나(404/410) 채널·앱 권한이 없어진(401/403) 경우다. 네트워크가 살아있고
            // Slack이 명확히 응답했다는 뜻이라 재시도해도 똑같이 실패한다 — SLACK_SEND_FAILED와 달리
            // 구분해서 던져야 Worker가 조용히 버리지 않고 격상할 수 있다.
            HttpStatusCode status = e.getStatusCode();
            log.error("Slack Webhook 전송 실패(설정 문제로 추정): status={}", status);
            if (status.value() == 404 || status.value() == 410 || status.value() == 401 || status.value() == 403) {
                throw new BusinessException(ErrorCode.SLACK_CONFIG_DEAD);
            }
            throw new BusinessException(ErrorCode.SLACK_SEND_FAILED);
        } catch (Exception e) {
            // 주의: webhookUrl 자체가 인증 정보다. ResourceAccessException 등은 메시지에 요청 URL을
            // 그대로 담아서 만들어지므로(RestTemplate가 "I/O error on POST request for \"<url>\": ..."
            // 형태로 직접 구성한다) e.getMessage()/e.toString()이나 e 객체를 로그 인자로 그대로
            // 넘기면 안 된다 — logger가 예외 인자를 렌더링할 때도 그 메시지가 그대로 찍힌다.
            // 원인 예외(cause, 예: ConnectException/SocketTimeoutException)의 메시지에는
            // URL이 없으므로 그것만 안전하게 남긴다.
            Throwable cause = e.getCause();
            if (cause != null) {
                // cause를 {} 자리에 그대로 넘기면 SLF4J가 Throwable 인자를 스택 트레이스용으로
                // 다뤄 마지막 자리 치환이 비어버린다 — 문자열로 미리 바꿔 넘긴다.
                log.error("Slack Webhook 전송 실패: {}: {}", e.getClass().getSimpleName(), cause.toString());
            } else {
                log.error("Slack Webhook 전송 실패: {}", e.getClass().getName());
            }
            throw new BusinessException(ErrorCode.SLACK_SEND_FAILED);
        }
    }

    // =========================================================================
    // 2. DM 전송
    // =========================================================================

    public void sendDM(String username, String email, String message) {
        String userId = getSlackUserId(username, email);

        if (userId == null) {
            log.warn("Slack User Not Found: username={}", username);
            throw new BusinessException(ErrorCode.SLACK_USER_NOT_FOUND);
        }

        String channelId = openDMChannel(userId, botToken);
        if (channelId == null) {
            throw new BusinessException(ErrorCode.SLACK_DM_CHANNEL_FAILED);
        }

        postToChannel(channelId, message, null);
    }

    /**
     * 관리자용: Slack 사용자 캐시 강제 새로고침
     * (신규 사용자 발생 시 Admin API 등을 통해 호출)
     */
    public void refreshSlackUserCache() {
        redisTemplate.delete(SLACK_USERS_CACHE_KEY);
        getSlackMembersWithCache(); // 즉시 재호출하여 캐시 워밍
        log.info("Slack User Cache 강제 초기화 완료");
    }

    /** {@link #findMembership}의 답. */
    public enum Membership {
        /** 이름과 이메일이 모두 같은 회원이 있다. */
        CONFIRMED,
        /** 이름이 같은 회원은 있으나, Slack이 회원 이메일을 주지 않아 이메일은 보지 못했다. */
        NAME_ONLY,
        NOT_FOUND
    }

    /**
     * 워크스페이스에 그 이름과 이메일의 회원이 있는가. 이름은 DM을 보낼 사람을 찾을 때와 같은 규칙으로 보고, 이메일은
     * 주어진 것 가운데 하나와 같으면 된다(대소문자 무시). 탈퇴 처리된 회원과 봇은 세지 않는다.
     *
     * <p>봇에 이메일 조회 권한(users:read.email)이 없으면 Slack은 어느 회원의 이메일도 주지 않는다. 그때는 이름만 보고
     * {@link Membership#NAME_ONLY}로 알린다. 회원 목록을 받지 못하면 예외를 낸다 — "없다"와 "확인하지 못했다"를
     * 호출자가 구분할 수 있다.
     */
    public Membership findMembership(String username, Collection<String> emails) {
        List<Map<String, Object>> active = getSlackMembersWithCache().stream()
                .filter(member -> !Boolean.TRUE.equals(member.get("deleted"))
                        && !Boolean.TRUE.equals(member.get("is_bot")))
                .toList();
        List<Map<String, Object>> named = active.stream().filter(member -> hasName(member, username)).toList();
        if (named.isEmpty()) {
            return Membership.NOT_FOUND;
        }
        if (active.stream().noneMatch(member -> emailOf(member) != null)) {
            return Membership.NAME_ONLY;
        }
        return named.stream().anyMatch(member -> hasEmail(member, emails))
                ? Membership.CONFIRMED : Membership.NOT_FOUND;
    }

    // --- Private Helper Methods ---

    @SuppressWarnings("unchecked") // IDE에서 Redis 캐스팅 경고를 억제하기 위해서 추가 (깔끔함용)
    private List<Map<String, Object>> getSlackMembersWithCache() {
        try {
            Object cachedData = redisTemplate.opsForValue().get(SLACK_USERS_CACHE_KEY);
            if (cachedData != null) {
                log.debug("Slack User List: Redis 캐시 히트");
                return (List<Map<String, Object>>) cachedData;
            }
        } catch (Exception e) {
            log.warn("Redis 조회 실패, API 직접 호출 진행: {}", e.getMessage());
        }

        log.info("Slack User List: API 직접 호출 (Refresh)");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(botToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            // 한 번에 다 오지 않으면 다음 쪽 표시(next_cursor)가 온다. 첫 쪽만 읽으면 뒤쪽 회원은 없는 사람이 된다.
            List<Map<String, Object>> members = new ArrayList<>();
            String cursor = "";
            for (int page = 0; page < MAX_USER_LIST_PAGES; page++) {
                // 문자열 주소로 넘기면 RestTemplate이 한 번 더 인코딩해 cursor가 깨진다. 완성된 URI로 넘긴다.
                URI url = URI.create(USERS_LIST_URL + (cursor.isEmpty() ? "" : "&cursor=" + cursor));
                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
                Map<?, ?> body = response.getBody();
                if (body == null || !Boolean.TRUE.equals(body.get("ok"))) {
                    throw new BusinessException(ErrorCode.SLACK_USER_NOT_FOUND);
                }
                members.addAll((List<Map<String, Object>>) body.get("members"));
                cursor = nextCursor(body);
                if (cursor.isEmpty()) {
                    break;
                }
            }

            // Redis 저장
            try {
                redisTemplate.opsForValue().set(SLACK_USERS_CACHE_KEY, members, Duration.ofHours(CACHE_TTL_HOURS));
            } catch (Exception e) {
                log.error("Redis 저장 실패: {}", e.getMessage());
            }

            return members;

        } catch (Exception e) {
            log.error("Slack users.list API 호출 실패", e);
            throw new BusinessException(ErrorCode.SLACK_USER_NOT_FOUND);
        }
    }

    private static String nextCursor(Map<?, ?> body) {
        if (body.get("response_metadata") instanceof Map<?, ?> metadata
                && metadata.get("next_cursor") instanceof String cursor) {
            // 값 끝의 '='가 그대로 가면 Slack이 invalid_cursor로 거절한다.
            return URLEncoder.encode(cursor, StandardCharsets.UTF_8);
        }
        return "";
    }

    /** 표시 이름·실명·계정 이름 가운데 하나가 정확히 같은가. */
    @SuppressWarnings("unchecked")
    private static boolean hasName(Map<String, Object> user, String username) {
        Map<String, Object> profile = (Map<String, Object>) user.get("profile");
        if (profile == null) return false;

        String displayName = (String) profile.get("display_name");
        String realName = (String) profile.get("real_name");
        String name = (String) user.get("name");

        return (displayName != null && displayName.equals(username)) ||
                (realName != null && realName.equals(username)) ||
                (name != null && name.equals(username));
    }

    @SuppressWarnings("unchecked")
    private static String emailOf(Map<String, Object> user) {
        Map<String, Object> profile = (Map<String, Object>) user.get("profile");
        if (profile == null || !(profile.get("email") instanceof String email) || email.isBlank()) {
            return null;
        }
        return email;
    }

    private static boolean hasEmail(Map<String, Object> user, Collection<String> emails) {
        String email = emailOf(user);
        return email != null && emails.stream().anyMatch(email::equalsIgnoreCase);
    }

    private String getSlackUserId(String username, String email) {
        List<Map<String, Object>> members = getSlackMembersWithCache();

        // 1차: 이름 매칭
        List<Map<String, Object>> matchedUsers = members.stream()
                .filter(user -> hasName(user, username))
                .collect(Collectors.toList());

        if (matchedUsers.isEmpty()) return null;
        if (matchedUsers.size() == 1) return (String) matchedUsers.get(0).get("id");

        // 2차: 이메일 매칭
        Map<String, Object> selectedUser = matchedUsers.stream()
                .filter(user -> {
                    Map<String, Object> profile = (Map<String, Object>) user.get("profile");
                    String userEmail = (String) profile.get("email");
                    return userEmail != null && userEmail.equalsIgnoreCase(email);
                }).findFirst().orElse(null);

        return selectedUser != null ? (String) selectedUser.get("id") : null;
    }

    private String openDMChannel(String userId, String token) {
        String url = "https://slack.com/api/conversations.open";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of("users", userId), headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            Map<?, ?> body = response.getBody();
            if (body == null || !Boolean.TRUE.equals(body.get("ok"))) {
                log.warn("conversations.open API 응답 이상: ok=false 또는 body null");
                return null;
            }
            Map<?, ?> channel = (Map<?, ?>) body.get("channel");
            if (channel == null) {
                log.warn("conversations.open 응답에 channel 필드 없음");
                return null;
            }
            return (String) channel.get("id");
        } catch (Exception e) {
            log.error("DM 채널 오픈 API 오류", e);
        }
        return null;
    }

    /**
     * 봇으로 채널에 글을 올리고 그 메시지의 식별자(ts)를 돌려준다. threadTs가 있으면 그 메시지의 스레드 댓글로 달되
     * 채널에도 함께 보이게 한다 — 스레드에만 달면 채널을 보는 사람에게 알림이 가지 않는다.
     * 봇이 그 채널에 들어가 있어야 한다.
     */
    public String postToChannel(String channelId, String message, String threadTs) {
        return postToChannel(channelId, message, threadTs, List.of());
    }

    /** blocks가 있으면 그 양식으로 보이고, message는 알림 미리보기에 쓰인다. */
    public String postToChannel(String channelId, String message, String threadTs, List<Map<String, Object>> blocks) {
        String url = "https://slack.com/api/chat.postMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(botToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> payload = new HashMap<>();
        payload.put("channel", channelId);
        payload.put("text", message);
        if (!blocks.isEmpty()) {
            payload.put("blocks", blocks);
        }
        if (threadTs != null) {
            payload.put("thread_ts", threadTs);
            payload.put("reply_broadcast", true);
        }
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        Map<?, ?> body;
        try {
            body = restTemplate.postForEntity(url, request, Map.class).getBody();
        } catch (Exception e) {
            log.error("Slack 채널 메시지 전송 실패: {}", e.toString(), e);
            throw new BusinessException(ErrorCode.SLACK_SEND_FAILED);
        }
        if (body == null || !Boolean.TRUE.equals(body.get("ok"))) {
            log.error("Slack 메시지 전송 실패: {}", body == null ? "응답 없음" : body.get("error"));
            throw new BusinessException(ErrorCode.SLACK_SEND_FAILED);
        }
        return (String) body.get("ts");
    }
}
