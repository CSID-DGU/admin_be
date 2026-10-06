package DGU_AI_LAB.admin_be.support;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 목(mock) AlarmService 가 받은 알림을 "문구 키 값 값 ..." 한 줄씩으로 돌려준다. 문구는 messages.properties 에 있고
 * 호출부는 키와 값만 넘기므로, 테스트는 어떤 알림(키)이 어떤 값으로 나갔는지를 이 줄에서 확인한다.
 */
public final class Alerts {

    private Alerts() {
    }

    /** 조치 필요 알림(오류 채널). */
    public static List<String> needsAction(AlarmService mock) {
        return callsOf(mock, "alertNeedsAction");
    }

    /** 처리 기록(알림 기록 채널). */
    public static List<String> recorded(AlarmService mock) {
        return callsOf(mock, "recordLog");
    }

    private static List<String> callsOf(AlarmService mock, String method) {
        return Mockito.mockingDetails(mock).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals(method))
                .map(invocation -> Arrays.stream(invocation.getArguments())
                        .flatMap(arg -> arg instanceof Object[] values ? Arrays.stream(values) : Stream.of(arg))
                        .map(String::valueOf)
                        .collect(Collectors.joining(" ")))
                .toList();
    }
}
