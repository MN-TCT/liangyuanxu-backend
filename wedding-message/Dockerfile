# wedding-message - 对话咨询+消息提醒服务
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY maven-settings.xml maven-settings.xml
COPY wedding-common/ wedding-common/
COPY wedding-message/ wedding-message/
COPY wedding-gateway/ wedding-gateway/
COPY wedding-auth/ wedding-auth/
COPY wedding-package/ wedding-package/
COPY wedding-booking/ wedding-booking/
COPY wedding-video/ wedding-video/
RUN mvn -s maven-settings.xml -q -pl wedding-message -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/wedding-message/target/wedding-message-1.0.0.jar app.jar
EXPOSE 3005
ENTRYPOINT ["java","-Xms48m","-Xmx180m","-XX:MaxMetaspaceSize=90m","-jar","app.jar"]
