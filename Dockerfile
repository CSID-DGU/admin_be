# jar를 계층(의존성 / 로더 / 우리 코드)으로 풀어 따로 쌓는다. 한 층으로 넣으면 코드 한 줄만 바뀌어도
# 배포 노드가 96MB를 새로 받는다. 의존성 층은 바뀔 때만 다시 받고, 배포마다 받는 것은 우리 코드 층뿐이다.
FROM eclipse-temurin:17-jre-jammy AS extract

WORKDIR /extract

COPY build/libs/admin_be-0.0.1-SNAPSHOT.jar app.jar

RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=extract /extract/extracted/dependencies/ ./
COPY --from=extract /extract/extracted/spring-boot-loader/ ./
COPY --from=extract /extract/extracted/snapshot-dependencies/ ./
COPY --from=extract /extract/extracted/application/ ./

EXPOSE 8080

ENV TZ=Asia/Seoul

ENTRYPOINT ["java", "-jar", "app.jar"]
