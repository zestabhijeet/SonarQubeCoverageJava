# Pwned Passwords Validator demo — Tomcat image
#
# Stage 1 builds the validator library (JAR) and the demo web app (WAR).
# Stage 2 runs the WAR on Tomcat 9. Wicket 8 uses javax.servlet, so Tomcat 9 is
# required; Tomcat 10+ (jakarta.servlet) will not run it.
#
# Build:  docker build -t <dockerhub-user>/pwned-passwords-demo:1.0 .
# Run:    docker run -d -p 8080:8080 --name pwned-demo <dockerhub-user>/pwned-passwords-demo:1.0
# Open:   http://localhost:8080/

############################ Stage 1: build ############################
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src

# Dependencies first, so they are cached between builds when only code changes
COPY pom.xml ./
COPY webapp/pom.xml ./webapp/
RUN mvn -B -q dependency:go-offline || true

# Library JAR -> local Maven repo, then the WAR that depends on it
COPY src ./src
COPY webapp/src ./webapp/src
RUN mvn -B -q install -DskipTests \
 && mvn -B -q -f webapp/pom.xml package

############################ Stage 2: run ##############################
FROM tomcat:9.0-jdk17-temurin

LABEL org.opencontainers.image.title="pwned-passwords-demo" \
      org.opencontainers.image.description="Apache Wicket demo of the Pwned Passwords validator on Tomcat 9" \
      org.opencontainers.image.source="https://github.com/zestabhijeet/SonarQubeCoverageJava"

# Remove Tomcat's sample apps and deploy ours as the root application
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /src/webapp/target/pwned-passwords-demo.war /usr/local/tomcat/webapps/ROOT.war

EXPOSE 8080

CMD ["catalina.sh", "run"]
