FROM eclipse-temurin:17-jre

WORKDIR /app

COPY app/retail-app/target/retail-app.jar app.jar

EXPOSE 8081

ENV APP_VERSION=4.2.0
ENV APP_ENV=UAT
ENV APP_HEALTHY=true

HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=3 \
  CMD ["curl", "-f", "http://localhost:8081/health"]

USER 1001

ENTRYPOINT ["java", "-jar", "app.jar"]