FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x ./gradlew
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test \
    && app_jar="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print -quit)" \
    && test -n "$app_jar" \
    && cp "$app_jar" /workspace/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
