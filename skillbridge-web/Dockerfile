# SkillBridge Java 主服务 Dockerfile
# 多阶段构建：Maven 编译 → JRE 运行

FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S skillbridge && adduser -S -G skillbridge skillbridge

WORKDIR /app
COPY --from=builder /build/target/skillbridge-web-*.jar app.jar
RUN mkdir -p /app/uploads /app/logs && chown -R skillbridge:skillbridge /app

USER skillbridge
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-Xms256m -Xmx512m"

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
