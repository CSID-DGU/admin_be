package DGU_AI_LAB.admin_be.domain.issuanceSheet;

import java.util.List;

/**
 * 탭의 모양. 0행부터 {@code headerRow - 1}행은 제목·안내, {@code headerRow}행은 머리글, 그 아래는 본문이다.
 *
 * @param headerRow         머리글 행(0부터). 그 위 첫 행은 제목, 나머지는 안내로 꾸민다
 * @param frozenColumns     왼쪽에 고정할 열 수
 * @param columnWidthsPx    열 너비(px). 열 수와 같아야 한다
 * @param calculatedFrom    자동 계산 열의 첫 열(0부터, 포함). 본문에서 연한 회색 바탕이 된다
 * @param calculatedTo      자동 계산 열의 마지막 열(포함)
 * @param merges            가로로 합칠 칸
 */
public record TabLayout(int headerRow, int frozenColumns, List<Integer> columnWidthsPx,
                        int calculatedFrom, int calculatedTo, List<Merge> merges) {

    /** 한 행 안에서 {@code fromColumn}~{@code toColumn}(포함, 0부터)을 하나로 합친다. */
    public record Merge(int row, int fromColumn, int toColumn) {
    }
}
