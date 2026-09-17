FROM maven:3.9.6-eclipse-temurin-25 AS builder
WORKDIR /workspace
COPY . .
RUN mvn -f epair-shop-saas/services/pom.xml -pl epair-shop-saas/services/user-service -am -DskipTests package

FROM eclipse-temurin:25-jdk AS runtime
WORKDIR /app
COPY --from=builder /workspace/epair-shop-saas/services/user-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
