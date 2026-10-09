package DGU_AI_LAB.admin_be.domain.issuanceSheet;

import DGU_AI_LAB.admin_be.domain.home.service.HomeRetentionPolicy;
import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import DGU_AI_LAB.admin_be.domain.portRequests.repository.PortRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.global.server.ServerProfileProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 서버별 발급 내역 표를 DB에서 읽어 만든다. */
@Service
@RequiredArgsConstructor
public class IssuanceSheetQueryService {

    private final RequestRepository requestRepository;
    private final PortRequestRepository portRequestRepository;
    private final ServerProfileProperties serverProfiles;
    private final HomeRetentionPolicy retentionPolicy;

    /**
     * 컨테이너가 살아 있는 신청만 담는다. 대기 중이거나 회수가 끝난 신청은 넣지 않는다.
     *
     * @return 설정된 서버 이름 → 표. 내역이 없는 서버도 머리글만 있는 표로 들어간다
     */
    @Transactional(readOnly = true)
    public Map<String, List<List<String>>> rowsByServer() {
        List<Request> issued = requestRepository.findAllByStatusInWithAssociations(Status.activeStatuses());
        Map<Long, List<Integer>> activePorts = portRequestRepository
                .findByRequestRequestIdIn(issued.stream().map(Request::getRequestId).toList()).stream()
                .filter(port -> Boolean.TRUE.equals(port.getIsActive()))
                .collect(Collectors.groupingBy(port -> port.getRequest().getRequestId(),
                        Collectors.mapping(PortRequests::getInternalPort, Collectors.toList())));

        Map<Long, LocalDate> homeDeletionDates = homeDeletionDates(issued);

        Map<String, List<List<String>>> tables = new LinkedHashMap<>();
        for (String serverName : serverProfiles.servers().keySet()) {
            List<Request> ofServer = issued.stream()
                    .filter(request -> serverName.equalsIgnoreCase(request.getResourceGroup().getServerName()))
                    .toList();
            tables.put(serverName, IssuanceSheetRows.of(ofServer, activePorts, homeDeletionDates));
        }
        return tables;
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
