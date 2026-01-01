# 🖥️ DGU AI LAB GPU Server Admin Backend

![Java](https://img.shields.io/badge/Java-17-blue?logo=openjdk&logoColor=white) 
![Spring Boot](https://img.shields.io/badge/SpringBoot-3.2-green?logo=springboot&logoColor=white) 
![JPA](https://img.shields.io/badge/JPA-Hibernate-red) 
![Redis](https://img.shields.io/badge/Redis-MessageQueue-red?logo=redis&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Container-blue?logo=docker&logoColor=white)

> **동국대학교 GPU 서버실 자원 관리 및 자동화 시스템**
> 의 사용자의 서버 신청부터 계정 생성, 만료 안내, 자원 회수(삭제)까지의 수명 주기를 관리하는 백엔드 서버입니다.

<br>

## 📝 프로젝트 소개
본 프로젝트는 제한된 GPU 서버 자원(Farm/Lab)을 효율적으로 관리하기 위해 개발되었습니다.  
기존의 수동 관리 방식을 탈피하여 **Linux 계정 생성/삭제 자동화**, **만료일 기반 자동 회수**, **Slack/Email 알림 시스템**을 제공합니다.

<br>

## 💡 핵심 기술적 특징
* **Automated Resource Lifecycle:** 신청 → 승인 → 생성 → 만료 임박 알림 → 자동 삭제(Soft/Hard) 프로세스 구축.
* **Event-Driven Architecture:** DB 트랜잭션과 외부 알림 발송 로직을 분리하여 데이터 정합성 보장
* **Non-blocking Notification:** Redis 메시지 큐를 활용한 비동기 알림 처리로 대량 발송 시 부하 방지.
* **CQS Pattern:** Command와 Query를 분리하여 UID/GID를 유지보수가 용이하도록 관리.
<br>

## 🌟 주요 기능

### 0. 유저 관리
- 사용자는 동국대학교 이메일을 통해 가입.
- Spring Security & JWT 기반의 사용자/관리자 권한 관리.
- 관리자용 자동 탈퇴 및 알림 기능 지원.

### 1. 자원 신청 및 승인
- 사용자는 원하는 GPU 용량, 기간, 이미지를 선택하여 신청.
- 관리자 승인 시 **UsedId(UID/GID)** 자동 할당 및 **Ubuntu 계정 생성 API** 호출.

### 2. 자동화된 스케줄러 (매일 10:00 실행)
- **만료 예고:** 만료 전 정해진 날짜(7, 3, 1일 전)에 사용자에게 알림 발송.
- **자동 회수:** 만료일 도래 시 Linux 계정 삭제, DB 데이터 정리(Cascade), UID 반납.

### 3. 알림 시스템 (Slack & Email)
- **사용자:** 신청 결과, 만료 예고, 삭제 완료 안내.
- **관리자:** 서버 오류, 자원 삭제 리포트 (Lab/Farm 태그 구분).

<br>

## 🛠 기술 스택

| 분류 | 기술 | 비고 |
| :--- | :--- | :--- |
| **Language** | Java 17 | |
| **Framework** | Spring Boot 3.2 | Web, Security |
| **Database** | MySQL 8.0 | 운영 DB |
| **ORM** | Spring Data JPA | Hibernate 6.x |
| **Message Queue** | Redis | 알림 비동기 처리 |
| **Infrastructure** | Docker, Linux | Ubuntu 계정 연동 |
| **Build Tool** | Gradle | |

<br>

## 🚀 실행 방법

### 1. 사전 요구사항 (Prerequisites)
* Java 17+
* Redis
* MySQL

### 2. 로컬 환경 실행
```bash
# 1. Repository Clone
git clone [https://github.com/DGU-AI-LAB/admin-be.git](https://github.com/DGU-AI-LAB/admin-be.git)
cd admin-be

# 2. Redis & DB 실행 (Docker 활용 시)
docker run -d -p 6379:6379 --name redis redis

# 3. 애플리케이션 빌드 및 실행
./gradlew clean build
java -jar build/libs/admin-be-0.0.1-SNAPSHOT.jar
```

### 3. ⚙️ 환경 변수 설정
`src/main/resources/application.yml` 파일에 노션에 정리된 설정값을 필수로 입력해야 정상 동작합니다.

### 1. 브랜치 전략 (Branch Strategy)
우리는 Git Flow 전략을 기반으로 운영하며, main 브랜치에 코드가 통합될 때만 실제 서버 배포가 이루어집니다.
| 브랜치 이름 | 역할 | 배포 여부 | 비고 |
| :--- | :--- | :---: | :--- |
| **`main`** | **운영(Production) 환경** | **O (자동)** | 배포 시점: PR Merge 직후 |
| **`develop`** | **개발(Development) 통합** | X | 기능 개발 후 통합 테스트 용도 |
| `feature/*` | 개별 기능 개발 | X | `develop`에서 분기하여 작업 |
| `hotfix/*` | 운영 이슈 긴급 수정 | O | `main`에서 분기, Merge 후 즉시 배포 (사용 권장 X)|

---

### 2. CI/CD 파이프라인 (Deployment Pipeline)

배포 자동화는 **GitHub Actions**를 사용하며, 오직 `main` 브랜치에 `push` 이벤트가 발생할 때 실행됩니다.

### 🔄 배포 흐름 (Workflow)
1.  **Trigger**: `develop` → `main`으로 PR이 Merge 되면 워크플로우가 시작됩니다.
2.  **Build & Push**:
    * 소스 코드를 기반으로 Docker 이미지를 빌드합니다.
    * 이미지 태그는 `latest`와 `Git Commit Hash` 두 가지로 생성됩니다.
    * Docker Hub의 팀/조직 레포지토리로 Push 됩니다.
3.  **Deploy (Helm Upgrade)**:
    * GitHub Actions가 운영 서버(`farm8`)에 SSH로 접속합니다.
    * `helm upgrade` 명령어를 통해 Kubernetes 배포를 수행합니다.
    * **Key Config**: `--set image.pullPolicy=Always` 옵션을 통해 항상 최신 이미지를 다운로드 받도록 강제합니다.

---

## 3. 작업 및 배포 규칙 (Workflow Rules)

팀원 간 충돌을 방지하고 안정적인 배포를 위해 아래 절차를 준수해 주세요.

### 🛠 기능 개발 (Feature)
1.  본인이 생성한 Github 이슈 번호에 맞춰 `develop` 브랜치에서 `feature/#기능번호-기능명` 브랜치를 생성합니다. (e.g. feat/#155-scheduler)
3.  로컬에서 개발 및 테스트를 진행합니다.
4.  커밋 메시지 양식: [분류] #issue 설명 (e.g. `[feat] #4 메인 기능 만들기`)
6.  작업이 완료되면 `feature` → `develop` 브랜치로 Pull Request(PR)를 생성합니다.

### 🚀 정기 배포 (Release)
1.  `develop` 브랜치에 충분한 기능이 모이고 테스트가 완료되면 배포를 준비합니다.
2.  PR 제목: `[deploy] develop -> main (또는 부가 설명)`  **`develop` → `main`** 으로 PR을 생성합니다. 
3.  코드 리뷰(Approve) 후 Merge 버튼을 누르면, **즉시 운영 서버에 배포됩니다.** 최소 한 명 이상의 Approve를 받아야 합니다.

---

## 4. API 문서 및 모니터링

서버가 정상적으로 실행 중일 때, 아래 주소에서 API 명세(Swagger)를 확인할 수 있습니다.

* **Swagger UI**: `http://{farm_server_ip}:{port}/apidocs/`
* **Health Check**: `http://{farm_server_ip}:{port}/health`

> **참고**: NodePort는 `values.yaml` 설정에 따릅니다.

---

## 5. 트러블슈팅 (Troubleshooting)

배포 후 문제가 발생했을 때 확인 및 조치 방법입니다.

### 1. Pod 상태 확인
```bash
kubectl get pods -n cssh
```
- 정상: Running (READY 1/1)
- 오류: CrashLoopBackOff, ImagePullBackOff, Pending

### 2. 로그 확인 

서버가 뜨지 않거나 동작이 이상할 때 실시간 로그를 확인합니다.
```bash
# Pod 이름 확인 후
kubectl logs -f <POD_NAME> -n cssh
```

## 6. 환경 변수 및 시크릿
CI/CD 작동을 위해 GitHub Repository Secrets에 다음 변수들이 등록되어 있습니다.
- Docker Hub: `DOCKER_USERNAME`, `DOCKER_PASSWORD`
- Kubernetes Access: `K8S_HOST`, `K8S_USERNAME`, `K8S_PRIVATE_KEY`, `K8S_PORT`
> 현재는 username이 toni와 {key}로 되어있으며, 관리자 변경 시 인수인계가 필요합니다.

## 📚 문서 및 위키
더 자세한 개발 가이드와 트러블슈팅 로그는 **GitHub Wiki**를 참고해 주세요.

> 관련 링크 <br>
> [인프라 서버](https://github.com/CSID-DGU/admin_infra) <br>
> [프론트엔드](https://github.com/CSID-DGU/AILab-FE) <br>

