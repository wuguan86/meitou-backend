# 1. 基础镜像：使用轻量级的 Java 17 运行环境
FROM eclipse-temurin:17-jre-alpine

# 2. 设置容器内的工作目录
WORKDIR /app

# 3. 将本地打包好的 jar 包复制到容器中
# 你的项目打包后在 target 目录下，名字通常是 admin-backend-0.0.1-SNAPSHOT.jar
# 为了简单，我们统一改名为 app.jar
COPY target/*.jar app.jar

# 4. 暴露后端端口（对应你 application.yml 里的 8085）
EXPOSE 8085

# 5. 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]