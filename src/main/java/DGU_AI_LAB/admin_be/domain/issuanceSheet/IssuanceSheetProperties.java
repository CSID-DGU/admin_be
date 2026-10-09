package DGU_AI_LAB.admin_be.domain.issuanceSheet;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 컨테이너 발급 내역을 Google 스프레드시트에 옮겨 적는 설정. {@code enabled}를 켠 환경만 문서를 고친다 — 문서 ID와
 * 키 경로는 운영 설정 파일을 복사해 쓰는 실험 스택에도 그대로 들어가므로, 값이 있다는 것만으로는 켜지지 않게 한다.
 * 켜는 것은 배포 스크립트가 운영 스택에만 한다.
 *
 * <pre>
 * issuance-sheet:
 *   enabled: true                                        # 기본값 false
 *   spreadsheet-id: 문서 주소의 /d/ 뒤 값
 *   credentials-path: /etc/admin-be/google-client.json   # 서비스 계정 키(JSON). 그 계정을 문서 편집자로 공유해 둔다
 *   tab-suffix: "(자동화)"                                # 생략 가능. 탭 이름은 서버 이름 + 이 값
 *   retry-ms: 60000                                      # 생략 가능. 옮기지 못했을 때 다시 하기까지
 * </pre>
 *
 * <p>탭은 {@code app.servers}의 서버마다 하나다. 서버 이름만으로 된 탭(LAB, FARM)은 기존 수동 발급 도구가 통째로
 * 다시 쓰는 자리라, 접미사를 붙여 다른 탭을 쓴다.
 */
@ConfigurationProperties(prefix = "issuance-sheet")
public record IssuanceSheetProperties(boolean enabled, String spreadsheetId, String credentialsPath, String tabSuffix) {

    static final String DEFAULT_TAB_SUFFIX = "(자동화)";

    public IssuanceSheetProperties {
        if (tabSuffix == null || tabSuffix.isBlank()) {
            tabSuffix = DEFAULT_TAB_SUFFIX;
        }
    }

    /** 켜져 있고 필요한 값이 다 있는가. */
    public boolean active() {
        return enabled && spreadsheetId != null && !spreadsheetId.isBlank()
                && credentialsPath != null && !credentialsPath.isBlank();
    }

    public String tabName(String serverName) {
        return serverName + tabSuffix;
    }
}
