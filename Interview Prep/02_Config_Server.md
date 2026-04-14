# Config Server — Interview Preparation Guide

---

## What is Config Server?

Config Server is a **Centralized Configuration Management** service provided by Spring Cloud. In a microservices system with 8+ services, each service has its own `application.yml` or `application.properties` file. Managing these separately is painful — if a database password changes, you'd need to update 8 files and restart 8 services. Config Server solves this by storing ALL service configurations in ONE place (a Git repository or local folder) and serving them over HTTP.

**Analogy:** Think of it like a shared settings file on Google Drive. All microservices connect to Config Server on startup and say "Give me my configuration." Config Server reads the file from Git and returns it. Any change to the configuration file in Git immediately reflects in all services (with a refresh).

---

## Role in FounderLink Architecture

```
[Git Repository: /config/*.yml]
         ↓
[Config Server :8888]
         ↓ serves configs to
[AuthService, UserService, StartupService, InvestmentService,
 TeamService, MessagingService, NotificationService, PaymentService,
 API Gateway, Eureka Server]
```

- **Boot order matters:** Config Server must start BEFORE all other services because they need their configuration on startup.
- Each service has a file in the `/config` folder: `auth-service.yml`, `user-service.yml`, `startup-service.yml`, etc.
- Config Server is itself configured via its own `application.yml` (it doesn't use Config Server for itself — that would be a chicken-and-egg problem).

---

## Dependencies (pom.xml)

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-config-server</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

| Dependency | What it does |
|---|---|
| `spring-cloud-config-server` | Provides the Config Server infrastructure — reads from Git/filesystem and serves via HTTP |
| `spring-boot-starter-actuator` | `/actuator/health` endpoint for monitoring |
| `micrometer-registry-prometheus` | Exports metrics for Prometheus/Grafana |

---

## Main Application File

**File:** `ConfigServerApplication.java`

```java
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

### Annotation Explanation:

**`@EnableConfigServer`**
- This is the key annotation. It activates the Config Server infrastructure.
- Spring Cloud adds HTTP endpoints like:
  - `GET /auth-service/default` → returns auth-service's default configuration
  - `GET /startup-service/default` → returns startup-service's default config
  - `GET /user-service/production` → returns user-service's production profile config
- The URL pattern is: `/{application-name}/{profile}/{label}` where label is the Git branch.
- **Without this annotation**, the app would just be a plain Spring Boot app with no config serving capability.

---

## Configuration File (application.yml)

```yaml
server:
  port: 8888

spring:
  application:
    name: config-server
  cloud:
    config:
      server:
        git:
          uri: ${CONFIG_SERVER_GIT_URI:https://github.com/snehalsuman/FounderLink}
          default-label: ${CONFIG_SERVER_GIT_BRANCH:main}
          search-paths: config
          clone-on-start: true
          username: ${CONFIG_SERVER_GIT_USERNAME:}
          password: ${CONFIG_SERVER_GIT_PASSWORD:}
```

### Line-by-Line Explanation:

**`server.port: 8888`**
- Config Server runs on port 8888. All microservices point to `http://config-server:8888` to fetch their config.

**`spring.application.name: config-server`**
- The name of this application in the Spring ecosystem.

**`spring.cloud.config.server.git.uri`**
- This is the URL of the Git repository where all configuration files are stored.
- `${CONFIG_SERVER_GIT_URI:https://github.com/snehalsuman/FounderLink}` — the syntax `${ENV_VAR:default_value}` means: read from environment variable `CONFIG_SERVER_GIT_URI`, and if not set, use the default URL.
- **Security best practice:** The actual Git credentials are never hardcoded here — they come from environment variables injected at runtime.

**`default-label: ${CONFIG_SERVER_GIT_BRANCH:main}`**
- `label` in Spring Cloud Config means the Git **branch**. Default is `main`.
- You could use different branches for different environments: `main` for production, `dev` for development.

**`search-paths: config`**
- Inside the Git repository, look in the `/config` folder for configuration files.
- So for `auth-service`, Config Server looks for `/config/auth-service.yml` in the Git repo.

**`clone-on-start: true`**
- When Config Server starts up, immediately clone the Git repository to local disk.
- Without this, Config Server would clone the repo on the first request, causing a delay.

### Native Profile (for local development):

```yaml
spring:
  config:
    activate:
      on-profile: native
  cloud:
    config:
      server:
        native:
          search-locations: ${CONFIG_NATIVE_SEARCH_PATH:file:./config}
```

**`on-profile: native`**
- This block only activates when you run Config Server with the Spring profile `native`.
- **Native mode** reads config files directly from the local filesystem instead of Git.
- Used in local development so you can edit `config/auth-service.yml` locally without pushing to GitHub.
- To activate: set `SPRING_PROFILES_ACTIVE=native` in the run configuration.

**`search-locations: file:./config`**
- Read configs from the `./config` folder in the current directory (relative to where Config Server runs — the project root).

---

## How Config Server Works (Full Boot Flow)

```
1. Config Server starts on port 8888
2. It clones the Git repo (or reads local /config folder in native mode)
3. AuthService starts and reads:
   spring.config.import: optional:configserver:http://localhost:8888
   spring.application.name: auth-service
4. AuthService calls: GET http://localhost:8888/auth-service/default
5. Config Server finds /config/auth-service.yml in Git
6. Config Server returns the YAML content as JSON to AuthService
7. AuthService uses these values as its configuration
8. AuthService boots up fully
```

---

## The /config Folder Structure

```
/config
  auth-service.yml         → configuration for AuthService
  user-service.yml         → configuration for UserService
  startup-service.yml      → configuration for StartupService
  investment-service.yml   → configuration for InvestmentService
  team-service.yml         → configuration for TeamService
  messaging-service.yml    → configuration for MessagingService
  notification-service.yml → configuration for NotificationService
  payment-service.yml      → configuration for PaymentService
  api-gateway.yml          → configuration for API Gateway
```

Each file contains things like:
- `server.port` (what port the service runs on)
- `spring.datasource.*` (database connection details — from env vars)
- `jwt.secret` (JWT signing secret — from env var)
- `rabbitmq.exchange` (RabbitMQ exchange name)
- `eureka.client.service-url.defaultZone` (where to find Eureka)

---

## Interview Q&A

**Q: Why do we need a centralized Config Server instead of each service having its own config file?**

In a microservices system with 10+ services, configuration management becomes a major operational challenge. If a database host changes, you'd need to update 10 files and restart 10 services. With Config Server, you change ONE file in Git and all services can pick up the change (with a `/actuator/refresh` call or config refresh mechanism). It also provides a single audit trail for configuration changes through Git history. Additionally, sensitive values like database passwords, JWT secrets, and API keys are injected via environment variables and never committed to the repository.

**Q: What is the difference between Git mode and Native mode in Config Server?**

Git mode reads configuration from a Git repository. This is production-grade — changes go through Git (with history, review, rollback capability) and Config Server pulls the latest version. Native mode reads directly from the local filesystem. We use native mode during local development so developers can edit `auth-service.yml` and restart without needing to push to GitHub. The active mode is controlled by the Spring profile: `SPRING_PROFILES_ACTIVE=native`.

**Q: What happens if Config Server is down when a microservice starts?**

The microservice will fail to start because it can't load its configuration. This is why startup order matters in Docker Compose — Config Server and Eureka must start before the business services. In our `docker-compose.yml`, other services have `depends_on: config-server` with health check conditions. For resilience in production, you'd run Config Server in a highly available setup, or use the `optional:configserver:` prefix so services can fall back to local application.yml values.

**Q: How does a microservice know to fetch from Config Server?**

Each microservice has in its own `application.yml`:
```yaml
spring:
  config:
    import: optional:configserver:http://localhost:8888
  application:
    name: auth-service  # This name is used to find the right config file
```
The service name `auth-service` maps to the file `auth-service.yml` in the `/config` folder.

**Q: What is the URL pattern for accessing configs from Config Server?**

The pattern is `/{application}/{profile}/{label}`:
- `GET /auth-service/default` → auth-service.yml with default profile
- `GET /auth-service/production` → auth-service-production.yml
- `GET /auth-service/default/main` → auth-service.yml from the `main` branch

**Q: What port does Config Server run on?**

Port `8888` — this is the Spring Cloud Config Server convention.