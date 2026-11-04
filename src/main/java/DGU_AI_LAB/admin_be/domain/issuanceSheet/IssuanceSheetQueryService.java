package DGU_AI_LAB.admin_be.domain.issuanceSheet;

import DGU_AI_LAB.admin_be.domain.home.service.HomeRetentionPolicy;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.domain.warnings.entity.UserWarning;
import DGU_AI_LAB.admin_be.domain.warnings.repository.UserSuspensionRepository;
import DGU_AI_LAB.admin_be.domain.warnings.repository.UserWarningRepository;
import DGU_AI_LAB.admin_be.global.server.ServerProfileProperties;
import DGU_AI_LAB.admin_be.global.server.ServerProfileRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 서버별 발급 내역 표를 DB에서 읽어 만든다. */
@Service
@RequiredArgsConstructor
public class IssuanceSheetQueryService {

    private final RequestRepository requestRepository;
    private final PodExternalPortRepository podExternalPortRepository;
    private final ServerProfileProperties serverProfiles;
    private final ServerProfileRegistry serverProfileRegistry;
    private final HomeRetentionPolicy retentionPolicy;
    private final UserRepository userRepository;
    private final UserWarningRepository warningRepository;
    private final UserSuspensionRepository suspensionRepository;

    static final String USERS_TAB = "사용자 목록";
    static final String WARNINGS_TAB = "경고";

    /** @return 서버와 무관한 탭 이름(접미사 제외) → 표. 사용자 전체와 경고 대장 전체를 담는다 */
    @Transactional(readOnly = true)
    public Map<String, List<List<String>>> accountTables() {
        List<UserWarning> warnings = warningRepository.findAll();
        Map<String, List<List<String>>> tables = new LinkedHashMap<>();
        tables.put(USERS_TAB, AccountSheetRows.users(userRepository.findAll(), warnings));
        tables.put(WARNINGS_TAB, AccountSheetRows.warnings(warnings, suspensionRepository.findAll()));
        return tables;
    }

    /**
     * 컨테이너가 살아 있는 신청만 담는다. 대기 중이거나 회수가 끝난 신청은 넣지 않는다.
     *
     * @return 설정된 서버 이름 → 표. 내역이 없는 서버도 머리글만 있는 표로 들어간다
     */
    @Transactional(readOnly = true)
    public Map<String, List<List<String>>> rowsByServer() {
        // 관리자 테스트 Pod는 실제 발급이 아니므로 대장에 올리지 않는다.
        List<Request> issued = requestRepository.findAllByStatusInWithAssociations(Status.activeStatuses()).stream()
                .filter(request -> !request.isTest())
                .toList();
        Map<Long, List<String>> ports = ports(issued);

        Map<Long, LocalDate> homeDeletionDates = homeDeletionDates(issued);

        Map<String, List<List<String>>> tables = new LinkedHashMap<>();
        for (String serverName : serverProfiles.servers().keySet()) {
            List<Request> ofServer = issued.stream()
                    .filter(request -> serverName.equalsIgnoreCase(request.getResourceGroup().getServerName()))
                    .toList();
            tables.put(serverName, IssuanceSheetRows.of(ofServer, ports, homeDeletionDates));
        }
        return tables;
    }

    /**
     * 컨테이너에 실제로 열린 포트를 "용도(내부 포트:외부 포트)"로 적는다. 외부 포트는 접속 안내 메일과 같은 번호다
     * ({@link ServerProfileRegistry#publicPort}). SSH·Jupyter를 앞에, 추가 포트는 내부 포트 순으로 놓는다.
     */
    private Map<Long, List<String>> ports(List<Request> issued) {
        Map<Long, String> serverByRequest = issued.stream().collect(Collectors.toMap(
                Request::getRequestId, request -> request.getResourceGroup().getServerName()));
        return podExternalPortRepository.findByRequestRequestIdIn(serverByRequest.keySet()).stream()
                .sorted(Comparator.comparingInt(IssuanceSheetQueryService::portRank)
                        .thenComparing(PodExternalPort::getInternalPort))
                .collect(Collectors.groupingBy(port -> port.getRequest().getRequestId(),
                        Collectors.mapping(port -> port.getUsagePurpose() + "(" + port.getInternalPort() + ":"
                                        + serverProfileRegistry.publicPort(
                                        serverByRequest.get(port.getRequest().getRequestId()),
                                        String.valueOf(port.getExternalPort())) + ")",
                                Collectors.toList())));
    }

    private static int portRank(PodExternalPort port) {
        if ("ssh".equalsIgnoreCase(port.getUsagePurpose())) {
            return 0;
        }
        return "jupyter".equalsIgnoreCase(port.getUsagePurpose()) ? 1 : 2;
    }

    /**
     * 홈은 서버가 아니라 계정에 딸려 있어, 그 사람의 컨테이너 중 가장 늦게 끝나는 것부터 센다. 사용자에게 종료 안내
     * 메일로 알리는 날짜와 같은 규칙이다({@link HomeRetentionPolicy#announcedDeletionDate}).
     */
    private Map<Long, LocalDate> homeDeletionDates(List<Request> active) {
        Map<Long, LocalDate> lastEndByUser = active.stream().collect(Collectors.toMap(
                request -> request.getUser().getUserId(),
                request -> request.getExpiresAt().toLocalDate(),
                (first, second) -> first.isAfter(second) ? first : second));
        lastEndByUser.replaceAll((userId, lastEnd) -> retentionPolicy.announcedDeletionDate(lastEnd));
        return lastEndByUser;
    }
}
