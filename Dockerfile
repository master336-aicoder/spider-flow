FROM eclipse-temurin:21-jre

LABEL org.opencontainers.image.title="spider-flow"
LABEL org.opencontainers.image.description="spider-flow 0.5.0 (JDK 21 适配分支)"

RUN mkdir -p /spider-flow /data/spider
WORKDIR /spider-flow

EXPOSE 8088

ADD ./spider-flow-web/target/spider-flow.jar ./

# JDK 16+ 默认强封装 JDK 内部 API，--add-opens 为运行必需；
# 容器内工作目录与镜像内 properties 的 Windows 路径不同，必须覆盖
ENTRYPOINT ["java", \
  "-Dfile.encoding=UTF-8", \
  "--add-opens", "java.base/java.lang=ALL-UNNAMED", \
  "--add-opens", "java.base/java.util=ALL-UNNAMED", \
  "-jar", "/spider-flow/spider-flow.jar"]

# 追加 Spring Boot 参数覆盖配置，例如：
#   docker run -it --rm -p 8088:8088 -v /data/spider:/data/spider \
#     spider-flow:0.5.0-jdk21 --spider.workspace=/data/spider \
#     --spring.datasource.url=jdbc:mysql://mysql:3306/spiderflow?...
