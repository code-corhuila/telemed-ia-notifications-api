FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY notifications-core ./notifications-core
COPY notifications-adapters ./notifications-adapters
COPY notifications-app ./notifications-app

# The E2E suite needs Docker and the -db migrations; CI runs it, the image
# build only compiles and runs the unit tests.
RUN mvn -B clean package -Dtest='!*E2ETest' -Dsurefire.failIfNoSpecifiedTests=false

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd --system telemed \
    && useradd --system \
        --gid telemed \
        --home-dir /app \
        --shell /usr/sbin/nologin \
        telemed

COPY --from=build \
    --chown=telemed:telemed \
    /app/notifications-app/target/notifications-app.jar \
    app.jar

USER telemed

EXPOSE 8082 9082

ENTRYPOINT ["java", "-jar", "app.jar"]
