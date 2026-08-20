FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew ./
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
# bootJar만 실행하므로(build/assemble 아님) plain jar 태스크는 트리거되지 않아
# build/libs/에는 boot jar 1개만 생성된다. 매칭 대상이 늘어나면 COPY가 에러로 실패한다.
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
