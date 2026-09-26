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

## 🧭 후속 구현 필요 사항

### 셀프 서비스 컨테이너 재시작 (보류)
- 사용자가 관리자 개입 없이 본인의 실행 중인 컨테이너를 직접 재시작하는 기능. API/UI는 설계·구현했으나(admin_be #481, admin_infra #152, admin_fe #124, 전부 미머지) **보류** 상태.
- 보류 사유: 재시작을 "홈 디렉토리 밖에 설치한 패키지까지 보존"하려면 실행 중인 컨테이너를 이미지로 커밋해야 하는데, 그 커밋을 수행하기로 되어 있던 `save_image.sh`가 실제 base 이미지 어디에도 존재하지 않음을 확인함(빈 껍데기 기능이었음 — 지금까지의 관리자 마이그레이션도 실제로는 커밋 없이 base 이미지로 재생성되고 있었음).
- 근본적으로 pod에는 `/var/run/docker.sock`이 마운트되어 있지 않아 컨테이너가 스스로를 커밋할 수 없고, krb5 배포처럼 farm 노드에 SSH로 직접 `docker commit`을 실행하는 방식이 필요함. 이 경우 기존 `_farm_ssh`의 하드코딩된 60초 타임아웃으로는 부족해, 원격에서 백그라운드 실행 후 상태를 폴링하는 비동기 방식으로 재설계가 필요함.
- 재개 시 확인할 것: (1) farm 노드에 배포된 forced-command 스크립트(`ailab-krb5-admin`) 확장 또는 별도 스크립트 추가, (2) 이미지 커밋을 비동기(백그라운드 실행 + 폴링)로 처리하는 config-server 쪽 설계.

<br>

## 🛠 기술 스택

| 분류 | 기술 | 비고 |
| :--- | :--- | :--- |
| **Language** | Java 17 | |
| **Framework** | Spring Boot 3.2 | Spring Security, WebClient 등|
| **Database** | MySQL 8.0 | 운영 DB |
| **ORM** | Spring Data JPA | Hibernate 6.x |
| **Message Queue** | Redis | 알림 비동기 처리 & JWT |
| **Infrastructure** | Docker, Ubuntu Linux, k8s | - |
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

### 브랜치·배포 규칙

admin_fe·admin_be·admin_infra·admin_infra-proposed 네 저장소가 같은 규칙을 쓴다. 전문은 admin_wiki [`md/브랜치-규칙.md`](https://github.com/CSID-DGU/admin_wiki/blob/main/md/브랜치-규칙.md).

- 브랜치는 `main` 하나다. 작업은 `main`에서 `<커밋 타입>/v<버전>-<짧은 설명>` 브랜치를 따서(예: `fix/v3.0-returning-user-uid`) PR로 `main`에 squash 병합한다.
- PR은 CI(테스트) 통과가 필요하다.
- 어느 브랜치에 push해도 자동 배포는 없다. 배포는 admin_infra의 **Deploy Proposed Stack** 워크플로로만 한다.
- 실험 스택은 `main`(또는 작업 브랜치)을, 운영(operation)은 네 저장소에 같은 이름으로 찍은 릴리스 태그 `vX.Y.Z`만 배포한다.

예전의 main push 자동 배포(`deploy.yml`)와 Helm 차트(`helm/admin-prod`)는 배포 대상이 은퇴해 제거했습니다.

---

## API 문서 및 모니터링

서버가 정상적으로 실행 중일 때, 아래 주소에서 API 명세(Swagger)를 확인할 수 있습니다.

* **Swagger UI**: `http://{farm_server_ip}:{port}/apidocs/`
* **Health Check**: `http://{farm_server_ip}:{port}/health`

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
더 자세한 개발 가이드와 트러블슈팅 로그는 **[GitHub Wiki](https://github.com/CSID-DGU/admin_be/wiki/Getting-Started)**를 참고해 주세요.

> 관련 링크 <br>
> [인프라 서버](https://github.com/CSID-DGU/admin_infra) <br>
> [프론트엔드](https://github.com/CSID-DGU/AILab-FE) <br>

