package DGU_AI_LAB.admin_be.domain.pod;

import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;

import java.util.List;
import java.util.stream.Collectors;

public class PodPortUtils {

    private PodPortUtils() {}

    /** 전체 포트 목록을 "용도(포트번호), ..." 형식으로 반환. 없으면 "없음". */
    public static String formatPortSummary(List<PodExternalPort> ports) {
        if (ports == null || ports.isEmpty()) return "없음";
        return ports.stream()
                .map(p -> p.getUsagePurpose() + "(" + p.getExternalPort() + ")")
                .collect(Collectors.joining(", "));
    }

    /** ssh/jupyter를 제외한 추가 포트만 포맷. 없으면 "없음". */
    public static String formatExtraPortSummary(List<PodExternalPort> ports) {
        if (ports == null) return "없음";
        List<PodExternalPort> extraPorts = ports.stream()
                .filter(p -> !"ssh".equalsIgnoreCase(p.getUsagePurpose())
                        && !"jupyter".equalsIgnoreCase(p.getUsagePurpose()))
                .collect(Collectors.toList());
        return formatPortSummary(extraPorts);
    }
}
