# wedding-package - 套餐服务
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY maven-settings.xml maven-settings.xml
COPY wedding-common/ wedding-common/
COPY wedding-package/ wedding-package/
COPY wedding-gateway/ wedding-gateway/
COPY wedding-auth/ wedding-auth/
COPY wedding-booking/ wedding-booking/
COPY wedding-video/ wedding-video/
COPY wedding-message/ wedding-message/
RUN mvn -s maven-settings.xml -q -pl wedding-package -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/wedding-package/target/wedding-package-1.0.0.jar app.jar
EXPOSE 3002
ENTRYPOINT ["java","-Xms48m","-Xmx180m","-XX:MaxMetaspaceSize=90m","-jar","app.jar"]
