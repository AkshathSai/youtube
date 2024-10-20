#Start with a base image containing Java runtime
FROM openjdk:23-jdk-slim

# MAINTAINER instruction is deprecated in favor of using label
# MAINTAINER akshathsaipittala
#Information around who maintains the image
LABEL "org.opencontainers.image.authors"="akshathsaipittala"

# Add the application's jar to the image
COPY target/youtube-0.0.1-SNAPSHOT.jar youtube-0.0.1-SNAPSHOT.jar

# execute the application
ENTRYPOINT ["java", "-jar", "youtube-0.0.1-SNAPSHOT.jar"]

# In the project source folder run - docker build . -t akshathsaipittala/youtube:m1