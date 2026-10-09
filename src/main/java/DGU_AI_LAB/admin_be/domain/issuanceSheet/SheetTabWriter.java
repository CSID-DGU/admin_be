package DGU_AI_LAB.admin_be.domain.issuanceSheet;

import java.util.List;

/** 표 하나를 스프레드시트 탭 하나에 그대로 옮겨 적는다. 어느 서비스의 문서인지는 구현만 안다. */
public interface SheetTabWriter {

    /**
     * 탭의 내용을 rows로 바꾼다. 탭이 없으면 만든다. 첫 행은 머리글이다.
     *
     * @throws RuntimeException 옮겨 적지 못했을 때. 탭에는 이전 내용이나 일부만 바뀐 내용이 남을 수 있다
     */
    void replaceTab(String tabName, List<List<String>> rows);
}
