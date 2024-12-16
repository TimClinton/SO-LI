# Folosim o imagine Java OpenJDK 17
FROM eclipse-temurin:17-jdk-alpine

# Setăm directorul de lucru
WORKDIR /app

# Copiem fișierul JAR în container
COPY target/memapp-1.0.0.jar app.jar

# Expunem portul pe care va rula aplicația
EXPOSE 7000

# Comanda pentru a porni aplicația
ENTRYPOINT ["java", "-jar", "app.jar"]
