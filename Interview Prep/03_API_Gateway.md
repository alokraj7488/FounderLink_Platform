# API Gateway — Interview Preparation Guide

---

## What is an API Gateway?

An API Gateway is the **single entry point** for all client requests in a microservices system. Instead of the frontend calling 8 different services on 8 different ports, it calls ONE address — the API Gateway — which routes each request to the correct microservice.

**Analogy:** Think of a hotel concierge. A hotel guest (frontend) tells the concierge (API Gateway): "I need room service." The concierge knows which internal department handles room service and routes the request there. The guest doesn't need to know the internal structure.

---

## Role in FounderLink Architecture

```
Frontend (React :5173)
    ↓ all HTTP requests
API Gateway (:8080)
    ├── /auth/**           → AUTH-SERVICE
    ├── /users/**          → USER-SERVICE   (+ JWT filter)
    ├── /startups/**       → STARTUP-SERVICE (+ JWT filter)
    ├── /investments/**    → INVESTMENT-SERVICE (+ JWT filter)
    ├── /teams/**          → TEAM-SERVICE   (+ JWT filter)
    ├── /messages/**       → MESSAGING-SERVICE (+ JWT filter)
    ├── /notifications/**  → NOTIFICATION-SERVICE (+ JWT filter)
    └── /api/payments/**   → PAYMENT-SERVICE (+ JWT filter)
```

**Important:** `/auth/**` (login, register, refresh) does NOT go through the JWT filter — these are public endpoints. All other routes require a valid JWT.

---

## Technology: Spring Cloud Gateway (Reactive / WebFlux)

**Critical interview point:** The API Gateway is built with **Spring Cloud Gateway** which is **reactive** (WebFlux, Netty). This is different from traditional Spring MVC. The gateway is non-blocking — it handles thousands of concurrent requests without spawning a thread per request. This makes it suitable as a high-throughput entry point.

---

## Dependencies (pom.xml key dependencies)

| Dependency | What it does |
|---|---|
| `spring-cloud-starter-gateway` | Core Spring Cloud Gateway (reactive router) |
| `spring-cloud-starter-netflix-eureka-client` | Registers gateway with Eureka and enables `lb://` routing |
| `jjwt-api`, `jjwt-impl`, `jjwt-jackson` | JWT parsing for the authentication filter |
| `spring-boot-starter-actuator` | Health/metrics endpoints |
| `micrometer-registry-prometheus` | Prometheus metrics |
| `lombok` | Reduces boilerplate code |

---

## Main Application File

**File:** `ApiGatewayApplication.java`

```java
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

Notice there is NO `@EnableDiscoveryClient` annotation — in newer Spring Cloud versions (2022+), Eureka client support is **auto-configured** when the `spring-cloud-starter-netflix-eureka-client` jar is on the classpath.

---

## File 1: JwtAuthenticationFilter.java

**File location:** `filter/JwtAuthenticationFilter.java`

This is the most important file in the gateway. It validates JWT tokens on every protected route BEFORE forwarding the request to the target microservice.

```java
@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    @Value("${jwt.secret}")
    private String secret;

    private static final List<String> PUBLIC_PATHS = List.of(
            "/auth/register",
            "/auth/login",
            "/auth/refresh"
    );
```

**`extends AbstractGatewayFilterFactory<Config>`**
- This is the Spring Cloud Gateway way to create a custom filter that can be applied to specific routes.
- `AbstractGatewayFilterFactory` is a base class that allows you to build a filter factory. You instantiate it with a `Config` class (which can hold filter-specific configuration).
- This filter is then referenced by name `JwtAuthenticationFilter` in the route YAML config.

**`@Value("${jwt.secret}")`**
- Spring injects the JWT secret from the configuration file (which comes from Config Server via the environment variable `JWT_SECRET`).
- This secret is the same secret that AuthService uses to sign tokens. The gateway uses it to VERIFY tokens without calling AuthService for every request.

**`PUBLIC_PATHS`**
- A list of URL paths that don't require authentication. `/auth/register`, `/auth/login`, and `/auth/refresh` are the endpoints where users are not yet authenticated.

### The `apply()` Method — How the Filter Works:

```java
@Override
public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Step 1: Skip filter for public paths
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Step 2: Check Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // Step 3: Extract and validate token
        String token = authHeader.substring(7);
        try {
            Claims claims = extractAllClaims(token);
            String userId = String.valueOf(claims.get("userId"));
            List<String> roles = (List<String>) claims.get("roles");
            String rolesHeader = (roles != null) ? String.join(",", roles) : "";

            // Step 4: Forward user info as headers to downstream services
            ServerHttpRequest modifiedRequest = request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Roles", rolesHeader)
                    .build();

            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    };
}
```

**Step-by-step flow when a request hits the gateway:**

1. **Request arrives** at API Gateway (e.g., `GET /startups/1` with `Authorization: Bearer eyJhbGci...`)

2. **`isPublicPath(path)` check:** Is `/startups/1` in the public paths list? No → proceed to validation.

3. **Header check:** Is there an `Authorization: Bearer <token>` header? If not → return `401 Unauthorized`.

4. **Token extraction:** `authHeader.substring(7)` removes "Bearer " (7 characters) to get just the token string.

5. **JWT validation:** `extractAllClaims(token)` parses the JWT using the same secret key. If the token is expired, tampered, or invalid → exception is thrown → `401 Unauthorized`.

6. **Header injection:** The gateway adds two custom headers to the request:
   - `X-User-Id: 42` (the userId extracted from the JWT claims)
   - `X-User-Roles: ROLE_INVESTOR` (the roles extracted from the JWT claims)

7. **Forward to microservice:** The modified request (with extra headers) is forwarded to the target service (e.g., StartupService).

8. **Downstream services:** StartupService reads `X-User-Id` and `X-User-Roles` headers instead of re-validating the JWT. This avoids every service needing to call AuthService.

**`exchange.getResponse().setComplete()`**
- In reactive (WebFlux) programming, `.setComplete()` is how you end the response and send it back to the client without passing through the rest of the filter chain.

**`chain.filter(exchange)`**
- This passes the request to the NEXT filter in the chain (or to the downstream service if no more filters).

---

## File 2: CorsConfig.java

```java
@Bean
public CorsWebFilter corsWebFilter() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(
        "http://localhost:3000",
        "http://localhost:3002",
        "http://localhost:5173"  // Vite dev server
    ));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);
    ...
}
```

**CORS (Cross-Origin Resource Sharing)** is a browser security mechanism. When a React app running on `localhost:5173` makes an HTTP request to `localhost:8080` (API Gateway), the browser blocks it because the origins are different. CORS configuration tells the browser "it's OK for these origins to make requests."

**`setAllowCredentials(true)`** — Allows cookies and authorization headers to be sent cross-origin. Required for JWT to work.

**`setAllowedHeaders(List.of("*"))`** — Allow any request header.

**`setMaxAge(3600L)`** — Browser caches the CORS preflight response for 3600 seconds (1 hour), reducing the number of OPTIONS preflight requests.

**Why is this in the Gateway?** — All requests pass through the Gateway, so CORS only needs to be configured in ONE place. If CORS were configured in each microservice, you'd need to maintain it in 8 places.

---

## File 3: RequestLoggingFilter.java

```java
@Component
@Slf4j
@Order(1)
public class RequestLoggingFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long startTime = System.currentTimeMillis();

        log.info("[REQUEST] correlationId={} method={} path={}", correlationId, method, path);

        return chain.filter(exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .header("X-Correlation-Id", correlationId)
                        .build())
                .build())
                .doFinally(signal -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("[RESPONSE] correlationId={} status={} duration={}ms", ...);
                });
    }
}
```

**`@Order(1)`** — This filter runs FIRST before any other filter (lower number = higher priority).

**`correlationId`** — A unique ID generated per request. This is passed to all downstream services via the `X-Correlation-Id` header, allowing you to trace a single request across all services in logs. This is called **distributed tracing**.

**`Mono<Void>`** — In reactive programming, `Mono` represents a single asynchronous value (or no value). This is the reactive equivalent of returning `void` — it completes when the filter chain is done.

**`doFinally`** — Executes AFTER the request is complete (whether success or error), logging the response status and total duration.

---

## File 4: api-gateway.yml (Route Configuration)

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: auth-service
              uri: lb://AUTH-SERVICE
              predicates:
                - Path=/auth/**

            - id: startup-service
              uri: lb://STARTUP-SERVICE
              predicates:
                - Path=/startups/**
              filters:
                - name: JwtAuthenticationFilter
```

**`id`** — A unique name for the route (for logging and debugging purposes).

**`uri: lb://STARTUP-SERVICE`**
- `lb://` means "load balanced." Spring Cloud LoadBalancer asks Eureka for the address of `STARTUP-SERVICE`.
- `STARTUP-SERVICE` must match the `spring.application.name` of the StartupService (case-insensitive).
- The gateway dynamically resolves this to `http://localhost:8083` (or whatever port Eureka says).

**`predicates: Path=/startups/**`**
- A predicate is a condition. This route only matches requests whose path starts with `/startups/`.
- `**` is a wildcard matching any sub-path.

**`filters: name: JwtAuthenticationFilter`**
- Apply our custom JWT filter to this route before forwarding.
- Routes without this filter (like `auth-service`) are public.

---

## Complete Request Flow Through the Gateway

```
1. User logs in: POST /auth/login {email, password}
   → Gateway sees /auth/login → isPublicPath = TRUE
   → No JWT check needed
   → Routes to lb://AUTH-SERVICE → AuthService validates credentials → returns JWT

2. User creates startup: POST /startups {name, industry, ...}
   with header: Authorization: Bearer eyJhbGci...
   → Gateway sees /startups/** → JwtAuthenticationFilter kicks in
   → Validates JWT signature with jwt.secret
   → Extracts userId=42, roles=ROLE_FOUNDER
   → Adds headers: X-User-Id=42, X-User-Roles=ROLE_FOUNDER
   → Routes to lb://STARTUP-SERVICE
   → StartupService reads X-User-Id and X-User-Roles
   → @PreAuthorize("hasAuthority('ROLE_FOUNDER')") passes → creates startup
```

---

## Interview Q&A

**Q: What is an API Gateway and why do we use it?**

An API Gateway serves as the single entry point for all client-to-microservice communication. In FounderLink, the React frontend only talks to one address: `localhost:8080`. The gateway handles cross-cutting concerns like JWT authentication (validating tokens once at the gateway instead of in every service), CORS configuration, request logging, and load-balanced routing to the appropriate microservice. Without a gateway, the frontend would need to know about 8 different service URLs, and every service would need its own CORS and JWT validation logic.

**Q: How does the API Gateway forward user identity to downstream services without them re-validating the JWT?**

The gateway validates the JWT using the shared `jwt.secret`. It extracts the `userId` and `roles` from the JWT claims and injects them as HTTP headers: `X-User-Id` and `X-User-Roles`. Every downstream microservice (StartupService, UserService, etc.) has a `JwtAuthenticationFilter` that reads these headers and creates a Spring Security `Authentication` object. The microservices trust these headers because they can only come through the gateway (the gateway is the only one that knows the secret).

**Q: Why does the API Gateway use WebFlux (reactive) instead of regular Spring MVC?**

The API Gateway is a network proxy that forwards requests to other services. With traditional Spring MVC (thread-per-request model), each concurrent request blocks a thread while waiting for the downstream service to respond. For a gateway handling thousands of concurrent requests, this would require thousands of threads — expensive and memory-intensive. Spring WebFlux is non-blocking: while waiting for the downstream response, the thread is freed to handle other requests. This makes the gateway far more scalable.

**Q: What is a correlationId and why is it useful?**

A correlationId is a unique identifier generated per incoming request. The gateway assigns it, adds it as the `X-Correlation-Id` header, and passes it downstream. Every microservice logs this ID with every log line. When debugging an issue, you search for the correlationId in your centralized logging system (e.g., Grafana/Loki) to see the complete trace of that single request across all services — gateway → startup-service → notification-service, etc.

**Q: What happens if you call an API without a JWT token?**

The gateway's `JwtAuthenticationFilter` checks for the `Authorization: Bearer <token>` header. If it's missing or doesn't start with "Bearer ", the filter immediately returns HTTP `401 Unauthorized` and the request NEVER reaches the microservice. This centralizes authentication enforcement in one place.