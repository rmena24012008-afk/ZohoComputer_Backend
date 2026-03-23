FROM tomcat:9.0-jdk21

# Remove default apps
RUN rm -rf /usr/local/tomcat/webapps/*

# Copy WAR as ROOT
COPY computerMcp.war /usr/local/tomcat/webapps/ROOT.war

# Set Java options (move this above CMD)
ENV JAVA_OPTS="-Djava.security.egd=file:/dev/./urandom"

# Start Tomcat on Render PORT
CMD sed -i "s/port=\"8080\"/port=\"${PORT}\"/" /usr/local/tomcat/conf/server.xml && catalina.sh run