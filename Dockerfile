# ─── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copia o pom.xml primeiro para cachear o layer de dependências.
# Se pom.xml não mudar, `mvn dependency:go-offline` não roda novamente.
COPY pom.xml .
RUN mvn dependency:go-offline -B --no-transfer-progress

COPY src ./src
RUN mvn package -DskipTests -B --no-transfer-progress

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Usuário não-root — princípio do menor privilégio
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build --chown=appuser:appuser /app/target/*.jar app.jar

USER appuser

# Perfil prod por padrão; sobrescreva com -e SPRING_PROFILES_ACTIVE=dev para local
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

# Healthcheck usando wget (disponível no Alpine) contra o endpoint do actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# Exec form — recebe sinais corretamente (SIGTERM → graceful shutdown do Spring)
# UseContainerSupport: respeita cgroup memory limits (padrão no Java 11+, explícito aqui)
# MaxRAMPercentage: usa 75% da RAM do container (evita OOM por heap ilimitada)
# java.security.egd: inicialização mais rápida do SecureRandom em containers Linux
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
