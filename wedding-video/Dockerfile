# wedding-video - 婚庆视频服务
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY maven-settings.xml maven-settings.xml
COPY wedding-common/ wedding-common/
COPY wedding-video/ wedding-video/
COPY wedding-gateway/ wedding-gateway/
COPY wedding-auth/ wedding-auth/
COPY wedding-package/ wedding-package/
COPY wedding-booking/ wedding-booking/
COPY wedding-message/ wedding-message/
RUN mvn -s maven-settings.xml -q -pl wedding-video -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/wedding-video/target/wedding-video-1.0.0.jar app.jar
EXPOSE 3004
VOLUME ["/app/uploads/videos"]
ENV WEDDING_UPLOAD_DIR=/app/uploads/videos
ENTRYPOINT ["java","-Xms48m","-Xmx180m","-XX:MaxMetaspaceSize=90m","-jar","app.jar"]
