FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src

RUN mvn -f backend/pom.xml clean package -DskipTests

FROM tomcat:10.1-jdk21-temurin

RUN rm -rf /usr/local/tomcat/webapps/*

COPY --from=build /app/backend/target/ai-task-agent.war /usr/local/tomcat/webapps/ROOT.war

ENV JAVA_OPTS="-Djava.security.egd=file:/dev/./urandom"

CMD ["sh", "-c", "sed -i \"s/port=\\\"8080\\\"/port=\\\"${PORT:-8080}\\\"/\" /usr/local/tomcat/conf/server.xml && catalina.sh run"]