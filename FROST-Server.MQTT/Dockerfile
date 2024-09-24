FROM openjdk:17-slim

EXPOSE 1883
EXPOSE 9876

# Copy to images tomcat path
ARG ARTIFACT_FILE
COPY target/FROST-Server.MQTT-2.3.4-SNAPSHOT.jar /usr/local/FROST/FROST-Mqtt.jar
WORKDIR /usr/local/FROST
CMD ["java", "-jar", "FROST-Mqtt.jar"]
