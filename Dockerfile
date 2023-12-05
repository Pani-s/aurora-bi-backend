# Docker 镜像构建
# @author pani
#
FROM openjdk:8
ADD aurora-bi-backend-0.0.1-SNAPSHOT.jar aurora-bi-backend-0.0.1-SNAPSHOT.jar
ENTRYPOINT java -jar -Duser.timezone=GMT+08  aurora-bi-backend-0.0.1-SNAPSHOT.jar  --spring.profiles.active=prod