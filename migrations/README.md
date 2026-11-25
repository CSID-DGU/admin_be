# migrations (수동 실행 기록, 더 추가하지 않음)

2026-09-27부터 스키마 변경은 Flyway 버전 파일로만 한다: `src/main/resources/db/migration/V<번호>__<설명>.sql`.
admin_be가 기동할 때 적용되고, Hibernate는 `ddl-auto: validate`로 엔티티와 스키마가 맞는지만 확인한다.

이 폴더의 스크립트는 Flyway 도입 전에 스택마다 손으로 실행한 기록이다. 모두 적용된 상태를 떠 온 것이
`V1__baseline.sql`이므로 새 스택에서 이 스크립트들을 다시 실행할 필요는 없다.

새 변경을 만들 때:
- 이미 적용된 버전 파일은 고치지 않는다(체크섬이 바뀌면 기동이 실패한다). 새 번호로 추가한다.
- 데이터를 옮기는 변경은 같은 파일 안에서 칸 추가 → 값 채우기 → 옛 칸 삭제 순서로 쓴다.
- enum 값을 추가할 때는 그 칸의 `enum(...)` 목록을 바꾸는 파일도 함께 추가한다.

## V25 FARM/LAB 요청 라우팅 값

- `resource_groups.server_name`을 FARM/LAB 대문자로 정규화하고 두 값만 허용한다.
- 각 `requests` 행에 생성·승인 시점의 `cluster_id`를 별도로 저장한다.
- 진행 중인 작업은 자원그룹 행을 다시 해석하지 않고 요청에 저장된 `cluster_id`로 결과 조회와 회수를 수행한다.
- 운영 적용 전에는 FARM/LAB 이외의 `server_name`이 없는지 README의 점검 SQL로 먼저 확인한다.
