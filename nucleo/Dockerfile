FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/nucleo.jar /app/nucleo.jar
ENV PLUGINS_DIR=/app/plugins
VOLUME /app/plugins
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/nucleo.jar"]
