# ✅ Tests Reference (대화용)

이 문서는 `admin_be` 레포의 테스트 코드를 **대화/리뷰/온보딩**에 바로 활용할 수 있도록 정리한 레퍼런스입니다.

- **테스트 실행법**: 어떻게/어디까지 돌릴 수 있는지
- **테스트가 고정하는 규칙**: “어떤 조건에서 어떤 결과가 나와야 하는가(상태 전이/예외/알림/저장)”

---

## 1) 테스트 실행 방법

```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스만 실행 (예: TokenServiceTest)
./gradlew test --tests "DGU_AI_LAB.admin_be.domain.users.service.TokenServiceTest"

# 특정 테스트 메서드만 실행
./gradlew test --tests "DGU_AI_LAB.admin_be.domain.users.service.TokenServiceTest.issueToken_createsNewRefreshToken_whenNotInRedis"
```

이 프로젝트는 `build.gradle`에서 `test` 이후 `jacocoTestReport`가 자동 실행되도록 설정되어 있습니다.

```bash
# JaCoCo 커버리지 리포트 생성
./gradlew jacocoTestReport
```

`jacocoTestReport`는 **먼저 `test` 태스크를 실행**한 뒤 리포트를 만듭니다. 그래서 커버리지 생성 중에 테스트가 실패하면, 로그상으로는 `:test` 실패가 먼저 보입니다.

### `./gradlew test` / WebMvcTest에서 `IllegalStateException` (Failed to load ApplicationContext)

터미널에 아래처럼만 나오고 테스트 본문 assertion 오류가 아닌 경우가 많습니다.

- `java.lang.IllegalStateException` … `DefaultCacheAwareContextLoaderDelegate`
- 원인 체인: `BeanCreationException` → `IllegalArgumentException` (Spring `Assert`)

의미는 **슬라이스 테스트(`@WebMvcTest`)용 Spring 컨텍스트를 띄우다가 빈 정의/주입 단계에서 깨졌다**는 뜻입니다. 이 레포에서는 `@WebMvcTest` + 공통 `WebMvcTestSupport`(JPA metamodel / JWT / Redis / UserDetails 등 mock) 조합에서 자주 나옵니다.

- **대응**: `./gradlew clean test`로 `build/`를 깨끗이 한 뒤 재실행
- **코드**: Spring Boot 3.4+에서는 `@MockBean` 대신 **`@MockitoBean`** 권장 — `WebMvcTestSupport`와 컨트롤러 테스트는 이 방식으로 맞춰 두었습니다.

---

## 2) 테스트 종류(패턴) 빠른 이해

테스트 코드는 주로 `src/test/java/DGU_AI_LAB/admin_be/`에 있습니다.

### A. 컨트롤러 테스트 (`@WebMvcTest` + `MockMvc`)

- **목적**: HTTP 요청/응답(상태 코드, JSON 포맷, validation, 예외 → HTTP 매핑)을 빠르게 검증
- **특징**
  - 서비스는 `@MockitoBean`으로 대체
  - Security는 `excludeAutoConfiguration = {SecurityAutoConfiguration.class}`로 비활성화하는 패턴 사용

### B. 서비스 테스트 (Mockito 단위 테스트)

- **목적**: 비즈니스 규칙(예외 조건, 저장/조회 흐름, 외부 의존성과 상호작용)을 고정
- **특징**: Repository/WebClient/Redis/JWT/Alarm 등은 mock으로 대체하고 호출/결과를 단언

### C. 리포지토리 테스트 (`@DataJpaTest` + H2)

- **목적**: Spring Data JPA 쿼리 메서드가 원하는 조건으로 데이터를 반환하는지 검증
- **특징**: `@ActiveProfiles("test")`로 테스트 프로파일에서 동작

### D. 엔티티 테스트 (Plain JUnit)

- **목적**: 엔티티 메서드의 상태 전이, 기본값, 경계조건(중복 삭제 등)을 코드 레벨로 고정

### E. 스케줄러 통합 테스트 (`@SpringBootTest`)

- **목적**: 만료/삭제/사전 알림/유저 수명주기처럼 여러 컴포넌트가 얽힌 “정책”을 DB까지 포함해 검증
- **특징**: 외부로 나가면 안 되는 부분(알림 발송, 계정 삭제 등)은 `@MockitoBean`으로 대체하고 메시지 내용/호출 횟수까지 단언

---

## 3) 도메인별 테스트가 고정하는 비즈니스 규칙(상세)

아래는 테스트 파일을 “대화용”으로 바로 설명할 수 있게 **무엇을 보장하는지** 중심으로 정리했습니다.

### A. Container Image (`domain/containerImage`)

- **`ContainerImageControllerTest`**
  - **POST `/api/images`**: 유효한 요청 → `200 OK` + DTO
  - **GET `/api/images/{id}`**: 존재하면 `200`, 미존재면 `404`
  - **GET `/api/images`**: 목록 조회 `200` + 리스트
- **`ContainerImageServiceTest`**
  - **createImage/getImageById/getAllImages**: 저장/조회/빈 목록 및 미존재 예외 정책 고정

### B. Groups (`domain/groups`)

- **`GroupControllerTest`**
  - **GET `/api/groups`**: 목록 조회 성공/빈 목록일 때의 `404` 매핑
- **`GroupServiceTest`**
  - **getAllGroups**: 빈 목록이면 예외
  - **createGroup**
    - 그룹명 중복이면 예외
    - infra 그룹 생성 응답의 GID를 로컬 DB에 저장
    - 외부 그룹 생성 API(WebClient 체인) 호출 흐름이 깨지지 않도록 모킹
    - `ubuntuUsername`이 주어진 경우 “해당 유저 소유 요청인지” 검증

### C. Users/Auth/Token (`domain/users`)

#### 컨트롤러

- **`AuthControllerTest`**
  - 회원가입: 정상/형식 오류(`400`)/중복(`409`) HTTP 규칙
  - 로그인: 정상(`200` + access/refresh 반환)/필수값 오류(`400`) 규칙
- **`AdminUserControllerTest`**
  - 관리자 유저 조회/삭제의 성공/미존재(`404`) 규칙

#### 서비스

- **`UserLoginServiceTest`**
  - 회원가입은 Redis의 `VERIFIED:{email}` 인증키가 있어야 가능(없으면 `UnauthorizedException`)
  - 가입 성공 시 인증키 삭제
  - 로그인 실패 조건(이메일 없음/비활성/비밀번호 불일치) 시 `UnauthorizedException`
  - 로그인 성공 시 JWT 발급 + Redis 저장 흐름 고정
- **`TokenServiceTest`**
  - refresh 토큰 키 네이밍은 `RT:{userId}`
  - Redis에 refresh가 있으면 재사용, 없으면 생성/저장
  - reissue: access subject 디코딩 + refresh 검증 후 재발급
  - logout: Redis 키 삭제
- **`UserServiceTest`**
  - 내 정보/유저 조회의 미존재 예외(`EntityNotFoundException`)
  - 비밀번호 변경 정책(현재 불일치/신규 동일/정상 변경)
  - SSH 인증(userAuth): base64 입력 → 해싱 → request(username/pw) 매칭
- **`AdminUserServiceTest`**
  - soft delete 정책(연결된 Request 없을 때 `isActive=false`)
  - 미존재 시 `EntityNotFoundException`

#### 리포지토리/엔티티

- **`UserRepositoryTest`**: `findByEmail`, 비활성/Hard delete 대상 조회의 기준일(경계조건)
- **`UserTest`**: 기본값(Role/활성화/lastLoginAt), withdraw/recordLogin/update 동작

### D. Requests (`domain/requests`)

#### 서비스

- **`RequestCommandServiceTest`**
  - 요청 생성 시 ubuntu username 중복이면 예외
  - user/resourceGroup 미존재 시 예외
  - 변경요청 requestId 미존재 시 예외
- **`RequestQueryServiceTest`**
  - userId 존재 검증 후 조회(없으면 예외)
  - 승인(FULFILLED) 요청 조회/username 목록 조회 규칙
- **`AdminRequestQueryServiceTest`**
  - 전체/신규(PENDING)/변경요청(PENDING)/리소스 요약 조회 규칙
- **`AdminRequestCommandServiceTest`**
  - 승인 시 infra 사용자 생성 응답의 UID/GID를 Request에 저장
  - 승인 시 `PodService.createPod()` 호출 정책
  - Pod 응답의 external ports → `PodExternalPortRepository` 저장 정책(포트 없으면 저장 없음)
- **`ConfigRequestServiceTest`**
  - `getAcceptInfo`가 `PortRequests` → `additional_ports` 변환(내부포트/목적만 포함)
  - Node 정보 → `gpu_nodes` 변환 시 CPU/메모리 포맷까지 고정

#### 리포지토리/엔티티

- **`RequestRepositoryTest`**: 유저/상태/username 기준 조회 및 username 목록 추출 규칙
- **`RequestTest`**: approve/reject/delete/update 상태 전이 + 중복 삭제 예외 + null 업데이트 정책
- **`RequestGroupTest`**: `@MapsId` 복합키 builder 초기화/`@PrePersist` 동작 규칙

### E. Dashboard (`domain/dashboard`)

- **`DashboardServiceTest`**
  - `Status.ALL` vs 특정 상태(FULFILLED/PENDING)에 따라 어떤 Repository 메서드를 호출해야 하는지 고정

### F. Resource Groups (`domain/resourceGroups`)

- **`ResourceGroupServiceTest`**
  - 전체 조회는 빈 리스트 허용
  - GPU 요약 정보가 비어 있으면 예외(“현황이 없으면 오류” 정책)

### G. Scheduler 통합 테스트 (`domain/scheduler`)

- **`RequestSchedulerServiceTest`**
  - 시간 고정 후 만료 요청의 삭제/알림/관리자 슬랙 알림 정책 검증
  - 1/3/7일 전 알림의 subject/body가 `MessageUtils`와 정확히 일치해야 함
- **`UserSchedulerServiceTest`**
  - 유저 라이프사이클(경고 D-7/D-1, Soft Delete, Hard Delete)
  - “활동 유저 보호/Pod 유저 보호” 정책 검증

---

## 4) 이 문서를 대화에 쓰는 법

- **“이 API는 어떤 상태 코드가 맞아?”** → 컨트롤러 테스트 항목을 보면 됩니다.
- **“만료 삭제, D-7 알림 같은 정책이 요구사항이야?”** → 스케줄러 통합 테스트가 사실상 정책 스펙입니다.
- **“키 네이밍/호출 횟수 같은 규칙은 어디서 보장돼?”** → Token/스케줄러/승인 서비스 테스트가 그 규칙을 고정합니다.
