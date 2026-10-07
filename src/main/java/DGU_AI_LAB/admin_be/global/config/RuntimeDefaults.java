package DGU_AI_LAB.admin_be.global.config;

import java.util.Map;

/**
 * 설정 파일(application.yml)에 없을 때 쓰는 실행 기본값. 설정 파일은 저장소에 없고 배포 환경마다 따로
 * 들어가므로, 모든 환경에 같아야 하는 안전 한도는 여기 둔다. {@code SpringApplication#setDefaultProperties}로
 * 넣어 우선순위가 가장 낮다 — 설정 파일·환경 변수에 같은 키가 있으면 그 값이 이긴다.
 */
public final class RuntimeDefaults {

    private RuntimeDefaults() {
    }

    public static Map<String, Object> values() {
        return Map.of(
                // JavaMail 기본값은 무한 대기다. 메일은 요청·스케줄러 스레드에서 보내므로 SMTP가 멈추면 그 스레드도
                // 멈춘다(반려 응답이 30초 넘게 걸린 E2E 실측). 넘기면 발송 실패로 기록하고 흐름은 이어간다.
                "spring.mail.properties.mail.smtp.connectiontimeout", "5000",
                "spring.mail.properties.mail.smtp.timeout", "10000",
                "spring.mail.properties.mail.smtp.writetimeout", "10000",
                // 결과 폴러·재조정·Slack 워커·승인 안내 메일이 모두 스케줄러 스레드에서 돈다. 기본값(1)이면 느린
                // 외부 호출 하나가 나머지를 전부 멈춘다. 예약 작업(@Scheduled)마다 스레드 하나씩 돌아가도록 개수에
                // 맞춘다 — 모자라면 config-server가 멈춘 동안 폴러들이 스레드를 다 쥐어 그 장애를 알릴 Slack 워커까지
                // 선다. 예약 작업을 늘리면 이 값도 늘린다. 같은 신청을 동시에 만지는 경로는 행 잠금 + 상태 재확인을 거친다.
                "spring.task.scheduling.pool.size", "11",
                // API 문서는 전체 경로와 요청 형식을 보여 준다. 운영에서는 끄고, 필요한 개발 환경만 설정 파일에서 켠다.
                "springdoc.api-docs.enabled", "false",
                "springdoc.swagger-ui.enabled", "false"
        );
    }
}
