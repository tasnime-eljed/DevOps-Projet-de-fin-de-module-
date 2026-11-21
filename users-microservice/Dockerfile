# =====================================================================
#  Dockerfile du microservice Spring Boot users-microservice
#  Construction en 2 étapes (multi-stage) :
#    1) "build"   : une image avec Maven + JDK 17 compile le JAR
#    2) "runtime" : une image légère avec seulement le JRE 17 exécute le JAR
#  Résultat : l'image finale ne contient ni Maven, ni le code source.
# =====================================================================

# ---------- Étape 1 : construction du JAR ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# On copie d'abord uniquement le pom.xml et on télécharge les dépendances.
# Docker met cette couche en cache : tant que le pom.xml ne change pas,
# les dépendances ne sont pas re-téléchargées à chaque build.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Puis on copie le code source et on construit le JAR.
# Les tests sont exécutés dans la pipeline CI (Phase 8/9), pas ici.
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- Étape 2 : image d'exécution ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Utilisateur système sans privilèges : l'application ne tourne pas en root.
RUN groupadd --system spring && useradd --system --gid spring --no-create-home spring

# On récupère uniquement le JAR construit à l'étape 1.
COPY --from=build --chown=spring:spring /build/target/*.jar app.jar

USER spring

# Port HTTP de l'application (server.port=8082, context path /users).
EXPOSE 8082

# La JVM adapte sa mémoire à la limite du conteneur.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

# Aucune variable secrète ici : DB_URL, DB_USER, DB_PASSWORD, JWT_SECRET, MAIL_*, ADMIN_*
# et CORS_ALLOWED_ORIGINS sont fournies au démarrage (docker run -e / docker compose).
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
