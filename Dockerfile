# jar를 계층(의존성 / 로더 / 우리 코드)으로 풀어 따로 쌓는다. 한 층으로 넣으면 코드 한 줄만 바뀌어도
# 배포 노드가 96MB를 새로 받는다. 의존성 층은 바뀔 때만 다시 받고, 배포마다 받는 것은 우리 코드 층뿐이다.
FROM eclipse-temurin:17-jre-jammy AS extract

WORKDIR /extract

COPY build/libs/admin_be-0.0.1-SNAPSHOT.jar app.jar

RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# root로 돌릴 이유가 없다 — 8080을 열고 /app만 읽는다. 번호로 적어 두면 쿠버네티스가 runAsNonRoot를 확인할 수 있다.
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --no-create-home app
COPY --from=extract /extract/extracted/dependencies/ ./
COPY --from=extract /extract/extracted/spring-boot-loader/ ./
COPY --from=extract /extract/extracted/snapshot-dependencies/ ./
COPY --from=extract /extract/extracted/application/ ./

EXPOSE 8080

ENV TZ=Asia/Seoul

USER 10001:10001

ENTRYPOINT ["java", "-jar", "app.jar"]
