# Eureka Server — Interview Preparation Guide

---

## What is Eureka Server?

Eureka Server is a **Service Discovery Server** provided by Netflix and integrated into Spring Cloud. In a microservices architecture, multiple services run on different ports and machines. They need to find each other dynamically without hardcoding IP addresses or ports. Eureka solves this problem.

**Analogy:** Think of Eureka like a phone directory. Every microservice (like AuthService, UserService) registers itself with Eureka when it starts up — like adding your number to the directory. When one service wants to call another, it asks Eureka: "Where is the startup-service?" Eureka gives back the address, and the call is made.

---

## Role in FounderLink Architecture

```
[All Microservices] ──register──► [Eureka Server :8761]
[API Gateway]       ──discover──► [Eureka Server :8761]
[Feign Clients]     ──lookup──►   [Eureka Server :8761]
```

- **Every microservice** (Auth, User, Startup, Investment, Team, Messaging, Notification, Payment) registers with Eureka on startup.
- **API Gateway** fetches the list of registered services and does **load-balanced routing** using `lb://SERVICE-NAME`.
- **Feign clients** (used in InvestmentService and TeamService) also resolve service names through Eureka.

---

## Dependencies (pom.xml)

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-config</artifactId>
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

### Dependency Explanations:

| Dependency | What it does |
|---|---|
| `spring-cloud-starter-netflix-eureka-server` | Turns this Spring Boot app into a Eureka service registry |
| `spring-cloud-starter-config` | Allows Eureka Server to fetch its own configuration from Config Server |
| `spring-boot-starter-actuator` | Exposes health, info, and metrics endpoints at `/actuator/health` |
| `micrometer-registry-prometheus` | Exports metrics in Prometheus format for Grafana monitoring |

---

## Main Application File

**File:** `EurekaServerApplication.java`

```java
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

### Annotation-by-Annotation Explanation:

**`@SpringBootApplication`**
- This is a shortcut annotation that combines three annotations:
  - `@Configuration` — marks this class as a source of Spring bean definitions
  - `@EnableAutoConfiguration` — tells Spring Boot to automatically configure the app based on jars in the classpath
  - `@ComponentScan` — tells Spring to scan this package and subpackages for components, services, and controllers
- **In the code:** Applied to `EurekaServerApplication` class — this is the entry point of the application.

**`@EnableEurekaServer`**
- This single annotation transforms the Spring Boot application into a **Eureka Registry Server**.
- Behind the scenes, it activates all Eureka server infrastructure — the dashboard, the registration endpoints (`/eureka/apps`), and the heartbeat mechanism.
- **Without this annotation**, the application would just be a plain Spring Boot app with no service registry capabilities.
- **In the code:** When `EurekaServerApplication` starts, Spring sees this annotation and starts the Eureka server on port 8761 (configured in Config Server).

**`SpringApplication.run(EurekaServerApplication.class, args)`**
- This bootstraps the entire Spring application context, loads all configurations, creates all beans, and starts the embedded Tomcat/Netty server.
- `EurekaServerApplication.class` tells Spring where to start scanning.
- `args` passes any command-line arguments (like `--server.port=8761`).

---

## How Service Registration Works (Full Flow)

```
1. AuthService starts up
2. AuthService reads eureka.client.service-url.defaultZone from Config Server
3. AuthService sends HTTP POST to http://eureka-server:8761/eureka/apps/AUTH-SERVICE
   Body contains: hostname, IP, port, health check URL, status=UP
4. Eureka Server stores this registration in memory
5. AuthService sends a HEARTBEAT every 30 seconds (PUT /eureka/apps/AUTH-SERVICE/{instanceId})
6. If no heartbeat for 90 seconds → Eureka marks the instance as DOWN and removes it
```

---

## How Service Discovery Works (Full Flow)

```
1. API Gateway needs to route to startup-service
2. API Gateway's route config says: uri: lb://STARTUP-SERVICE
3. Spring Cloud LoadBalancer asks Eureka: "Give me all instances of STARTUP-SERVICE"
4. Eureka returns: [{host: "localhost", port: 8083, status: UP}]
5. LoadBalancer picks one instance (round-robin by default)
6. API Gateway forwards the request to http://localhost:8083/startups/...
```

---

## Key Configuration (in Config Server's eureka-server.yml)

```yaml
eureka:
  client:
    register-with-eureka: false   # Eureka server doesn't register with itself
    fetch-registry: false          # Eureka server doesn't fetch registry from itself
  server:
    wait-time-in-ms-when-sync-empty: 0  # Don't wait on startup
```

**`register-with-eureka: false`** — By default, a Eureka server is also a Eureka client. We set this to false because a Eureka server doesn't need to register with itself. If you had multiple Eureka servers for high availability, you'd set this to true and point them at each other.

**`fetch-registry: false`** — Same reason. The server doesn't need to fetch its own registry.

---

## Eureka Dashboard

Once running, you can visit `http://localhost:8761` to see:
- All registered services (instances)
- Their health status (UP/DOWN)
- Their IP addresses and ports

---

## Interview Q&A

**Q: What is Eureka and why do we need it in microservices?**

In a microservices architecture, services are deployed independently on different hosts and ports. These addresses can change dynamically — for example, in a Kubernetes cluster, a pod can restart with a new IP. Hardcoding service URLs would be brittle and unmaintainable. Eureka Server is a service registry where each microservice registers itself on startup and deregisters when it shuts down. Other services or the API Gateway query Eureka to discover where a service is running at a given moment. This enables dynamic, resilient service-to-service communication without manual configuration of IP addresses.

**Q: What happens if Eureka Server goes down?**

Eureka clients have a **local cache** of the registry. If Eureka goes down, the clients continue to use the cached service addresses for a period. This is why Eureka is designed with **self-preservation mode** — it doesn't remove registrations aggressively during network partitions. In production, you'd run multiple Eureka instances in a cluster for high availability.

**Q: What is the difference between `register-with-eureka` and `fetch-registry`?**

`register-with-eureka: true` means "this service should announce itself to Eureka so others can find it." `fetch-registry: true` means "this service should periodically download the full list of other services from Eureka." In our setup, the Eureka Server itself sets both to `false` because it IS the registry — it doesn't need to register with itself or fetch from itself. All business microservices set both to `true`.

**Q: What Spring Cloud version does FounderLink use?**

Spring Cloud `2025.0.1` with Spring Boot `3.5.11` and Java 17.