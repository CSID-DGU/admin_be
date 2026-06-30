package DGU_AI_LAB.admin_be.domain.messageTemplate.service;

import DGU_AI_LAB.admin_be.domain.messageTemplate.entity.MessageTemplate;
import DGU_AI_LAB.admin_be.domain.messageTemplate.repository.MessageTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;

/**
 * 알림 메시지 템플릿을 "DB 우선, properties fallback" 구조로 관리하는 서비스.
 *
 * 동작 원리:
 *   1) messages.properties : JAR에 박혀있는 기본값 (재배포 없이 수정 불가)
 *   2) message_templates 테이블 : 관리자가 런타임에 수정한 오버라이드 값
 *
 *   관리자가 수정하면 DB에 row가 생기고, 삭제(reset)하면 row가 지워져서 다시 기본값으로 돌아감.
 *   실제 알림 발송 시 메시지를 읽는 건 MessageSourceConfig(DbMessageSource)가 담당하고,
 *   이 서비스는 "관리자 화면에서 목록 조회 / 수정 / 초기화"만 담당함.
 */
@Service
@RequiredArgsConstructor
public class MessageTemplateService {

    private final MessageTemplateRepository repository;

    /**
     * 프론트에 내려줄 응답 구조. record는 불변 DTO : getter/equals/hashCode 자동 생성됨.
     *
     * @param key         메시지 키 (ex: "notification.expired.dm")
     * @param defaultValue  messages.properties에 있는 원래 값
     * @param currentValue  현재 적용 중인 값 (DB 오버라이드가 있으면 그 값, 없으면 defaultValue와 동일)
     * @param overridden    DB에 오버라이드가 존재하는지 여부 — 프론트에서 "초기화" 버튼 표시 여부에 사용
     */
    public record TemplateView(String key, String defaultValue, String currentValue, boolean overridden) {}

    /**
     * 전체 메시지 목록을 조회한다.
     *
     * properties 파일의 모든 키를 기준으로, DB에 오버라이드가 있으면 해당 값을 currentValue로 세팅.
     * readOnly = true: SELECT만 하므로 JPA dirty checking을 끄고 성능을 약간 절약.
     */
    @Transactional(readOnly = true)
    public List<TemplateView> getAll() {
        // 1. JAR 안의 기본값 로드
        Properties defaults = loadDefaults();

        // 2. DB에 저장된 오버라이드를 Map으로 변환 (key로 빠르게 찾기 위해)
        Map<String, MessageTemplate> overrides = new HashMap<>();
        repository.findAll().forEach(t -> overrides.put(t.getKey(), t));

        // 3. 기본값 기준으로 순회하면서 DB 오버라이드와 머지
        List<TemplateView> result = new ArrayList<>();
        for (String key : defaults.stringPropertyNames()) {
            String def = defaults.getProperty(key);
            MessageTemplate override = overrides.get(key);
            result.add(new TemplateView(
                    key, def,
                    override != null ? override.getValue() : def,  // DB에 있으면 DB값, 없으면 기본값
                    override != null                                // DB에 row가 있는지 여부
            ));
        }
        // ponytail스킬: O(n) scan, ~20개 템플릿이라 정렬 비용 무시 가능
        result.sort(Comparator.comparing(TemplateView::key));
        return result;
    }

    /**
     * 특정 키의 메시지를 수정한다 (DB에 오버라이드 저장).
     *
     * ifPresentOrElse 패턴:
     *   - DB에 이미 있으면 : updateValue()로 값만 변경 (JPA dirty checking이 UPDATE 쿼리 날림)
     *   - DB에 없으면 : 새 row를 INSERT
     *
     * @Transactional: 이 메서드 안에서 일어나는 모든 DB 작업이 하나의 트랜잭션.
     *   중간에 예외 터지면 전부 롤백됨.
     */
    @Transactional
    public void update(String key, String value) {
        repository.findById(key)
                .ifPresentOrElse(
                        t -> t.updateValue(value),
                        () -> repository.save(new MessageTemplate(key, value))
                );
    }

    /**
     * 오버라이드를 삭제해서 기본값(properties)으로 되돌린다.
     *
     * DB row만 지우면 끝 — MessageSourceConfig의 resolveCode()가 DB에서 못 찾으면
     * 자동으로 parent(ResourceBundleMessageSource -> messages.properties)로 위임함.
     */
    @Transactional
    public void reset(String key) {
        repository.deleteById(key);
    }

    /**
     * classpath의 messages.properties를 읽어서 Properties 객체로 반환.
     *
     * ClassPathResource: src/main/resources 아래 파일을 읽는 Spring 유틸.
     * JAR로 패키징되어도 잘 동작함 (파일시스템이 아니라 클래스패스에서 읽으니까).
     * IOException 시 빈 Properties 반환 : 파일 없으면 DB 값만 보이게 됨.
     */
    private Properties loadDefaults() {
        try {
            return PropertiesLoaderUtils.loadProperties(new ClassPathResource("messages.properties"));
        } catch (IOException e) {
            return new Properties();
        }
    }
}
