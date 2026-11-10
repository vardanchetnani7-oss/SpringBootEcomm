# SpringEcom

A REST API for a simple e-commerce backend built with Spring Boot. It manages products (with images) and customer orders, secured with HTTP Basic authentication, a centralized global exception handler and Redis caching for product reads. The app is containerized with Docker and published to Docker Hub.

## Features

- Product management: create, read, update, delete and search, with image upload and download
- Redis caching of product reads (list, by id and search), with automatic eviction on writes
- Order placement with automatic stock deduction
- Transactional order flow: if any item in an order fails, all stock changes roll back
- Global exception handling with one consistent JSON error format
- HTTP Basic authentication with role-based access (USER and ADMIN)
- Interactive API documentation with Swagger UI (springdoc-openapi)
- Docker support: multi-stage Dockerfile and Docker Compose setup (app + PostgreSQL + Redis)
- Image published on Docker Hub: `vardan2612/springecom`

## Tech Stack

- Java 26
- Spring Boot 4.1.0 (Web MVC, Data JPA, Security, Cache, Data Redis)
- PostgreSQL
- Redis
- springdoc-openapi (Swagger UI)
- Lombok
- Maven
- Docker and Docker Compose

## Project Structure

```
SpringBootEcomm
└── SpringEcom
    ├── Dockerfile           multi-stage build for the app image
    ├── docker-compose.yml   app + PostgreSQL + Redis
    ├── .dockerignore
    ├── .env                 database credentials for Compose (not committed)
    ├── pom.xml
    └── src/main/java/com/Ecom/SpringEcom
        ├── Controller       ProductController, OrderController
        ├── services         ProductService (caching), OrderService (cache eviction)
        ├── repository       ProductRepo, OrderRepo
        ├── models           Product, Order, OrderItem
        │   └── dtos         OrderRequest, OrderResponse, OrderItemRequest, OrderItemResponse
        ├── exception        GlobalExceptionHandler, ErrorResponse,
        │                    ResourceNotFoundException, InsufficientStockException
        └── config           SecurityConfig
```

`@EnableCaching` is added on the main application class. No separate cache configuration class is needed.

## Getting Started

You can run the project in two ways: with Docker (recommended) or directly on your machine.

### Option 1: Run with Docker Compose (recommended)

Prerequisite: Docker Desktop or Docker Engine.

1. Go to the project folder:

   ```bash
   cd SpringEcom
   ```

2. Create a `.env` file next to `docker-compose.yml`:

   ```
   POSTGRES_DB=SpringEcom
   POSTGRES_USER=postgres
   POSTGRES_PASSWORD=your_password
   ```

3. Start everything:

   ```bash
   docker compose up --build
   ```

   Add `-d` to run in the background. The app starts at `http://localhost:8080`.

4. Stop it:

   ```bash
   docker compose down
   ```

   Add `-v` to also delete the database and Redis data.

Compose starts three services: `app`, `postgres` and `redis`. The app waits for the database and Redis healthchecks to pass before it starts.

### Option 2: Run the published image from Docker Hub

The image is available at `vardan2612/springecom`. The app needs PostgreSQL and Redis, so start those first on a shared network.

Run each command as a single line:

```bash
docker network create ecom-net
```

```bash
docker run -d --name postgres --network ecom-net -e POSTGRES_DB=SpringEcom -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=your_password -v pg_data:/var/lib/postgresql/data postgres:16
```

```bash
docker run -d --name redis --network ecom-net redis:7-alpine
```

Wait about 10 seconds for PostgreSQL to be ready, then start the app:

```bash
docker run -d --name springecom --network ecom-net -p 8080:8080 -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/SpringEcom -e SPRING_DATASOURCE_USERNAME=postgres -e SPRING_DATASOURCE_PASSWORD=your_password -e SPRING_DATA_REDIS_HOST=redis -e SPRING_DATA_REDIS_PORT=6379 vardan2612/springecom:1.0
```

Useful commands:

```bash
docker ps                       # list running containers
docker logs -f springecom       # follow the app logs
docker rm -f springecom redis postgres   # remove the containers
```

If a command fails with "container name already in use", remove the old container with `docker rm -f <name>` and run the command again.

### Option 3: Run locally without Docker

Prerequisites:

- JDK 26 (or change `java.version` in `pom.xml` to a version you have installed)
- Maven
- A running PostgreSQL database
- A running Redis server

Start Redis with Docker if you don't have it:

```bash
docker run -d --name redis -p 6379:6379 redis
```

Add your database and Redis settings to `src/main/resources/application.properties`:

```properties
spring.application.name=SpringEcom

spring.datasource.url=jdbc:postgresql://localhost:5432/SpringEcom
spring.datasource.username=your_username
spring.datasource.password=your_password
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

# Redis cache
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.cache.type=redis
spring.cache.redis.time-to-live=10m
spring.cache.redis.cache-null-values=false
```

Run the app:

```bash
mvn spring-boot:run
```

The API starts at `http://localhost:8080`. All endpoints are under `/api`.

### Dependencies

These are already in `pom.xml`:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
  <version>3.1.0</version>
</dependency>
```

## Docker

### Dockerfile

The Dockerfile uses a multi-stage build:

1. **Build stage:** a Maven + JDK image copies `pom.xml` and `src`, then runs `mvn clean package -DskipTests` to produce the jar. `pom.xml` is copied first so dependencies are cached between builds.
2. **Run stage:** a small JRE image copies only the jar and runs it as a non-root user (`spring`) on port 8080.

```dockerfile
# Stage 1: build
FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: run the application
FROM eclipse-temurin:26-jre
WORKDIR /app
RUN useradd -r -u 1001 spring
COPY --from=build /app/target/*.jar app.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Environment variables

Spring Boot maps environment variables onto `application.properties`, so the same image works locally and in containers without code changes.

| Variable | Overrides | Example |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `spring.datasource.url` | `jdbc:postgresql://postgres:5432/SpringEcom` |
| `SPRING_DATASOURCE_USERNAME` | `spring.datasource.username` | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | `spring.datasource.password` | `your_password` |
| `SPRING_DATA_REDIS_HOST` | `spring.data.redis.host` | `redis` |
| `SPRING_DATA_REDIS_PORT` | `spring.data.redis.port` | `6379` |

Inside a container, `localhost` means the container itself, so the app must use the service names (`postgres`, `redis`) as hostnames.

### Build and push your own image

```bash
cd SpringEcom
docker build -t yourusername/springecom:1.0 .
docker login
docker push yourusername/springecom:1.0
```

The image name must start with your Docker Hub username.

### Use the Docker Hub image in Compose

In `docker-compose.yml`, replace `build: .` in the `app` service with:

```yaml
image: vardan2612/springecom:1.0
```

## Deployment

The image is published on Docker Hub, so a server does not need the source code. Use the Hub image in `docker-compose.yml` (see above), then run:

```bash
docker compose pull
docker compose up -d
```

Never put passwords or tokens in the Dockerfile or the repository. Keep them in `.env` or in your host's secret store.

## API Documentation (Swagger UI)

With the app running, open:

```
http://localhost:8080/swagger-ui.html
```

It lists every endpoint and lets you try them from the browser. The OpenAPI JSON is at `/v3/api-docs`. Because the API is secured, Swagger and the endpoints may ask for the credentials listed below. If you want the docs page to be public, permit `/swagger-ui/**` and `/v3/api-docs/**` in `SecurityConfig`.

## Authentication

The API uses HTTP Basic authentication. Send an `Authorization: Basic base64(username:password)` header with each protected request. Spring Security checks the credentials and then the role.

| Username | Password | Role |
|---|---|---|
| user | user123 | USER |
| admin | admin123 | ADMIN |

Requests with missing or wrong credentials get Spring Security's default 401 response, and requests from a user without the required role get its default 403 response.

These users are stored in memory for development only. Change them or load users from the database before any real deployment, and always use HTTPS, because Basic auth credentials are only Base64-encoded.

## API Endpoints

| Method | Endpoint | Description | Access |
|---|---|---|---|
| GET | `/api/products` | List all products (cached) | Public |
| GET | `/api/product/{id}` | Get a product by id (cached) | Public |
| GET | `/api/product/{id}/image` | Get a product image | Public |
| GET | `/api/product/search?keyword=` | Search by name, description, brand or category (cached) | Public |
| POST | `/api/product` | Add a product (multipart: product JSON + imageFile) | ADMIN |
| PUT | `/api/product/{id}` | Update a product (multipart) | ADMIN |
| DELETE | `/api/product/{id}` | Delete a product | ADMIN |
| POST | `/api/place` | Place an order | USER, ADMIN |
| GET | `/api/orders` | List all orders | ADMIN |

### Example: place an order

`POST /api/place`

```json
{
  "customerName": "Rahul Shah",
  "email": "rahul@example.com",
  "items": [
    { "productId": 1, "quantity": 2 }
  ]
}
```

### Example: add a product

`POST /api/product` as `multipart/form-data` with two parts:

- `product`: JSON (content type `application/json`), for example `{ "name": "iPhone 15", "brand": "Apple", "category": "Mobile", "price": 79999.00, "stockQuantity": 10 }`
- `imageFile`: the image file

### Example: call an endpoint with curl

```bash
curl -u admin:admin123 http://localhost:8080/api/orders
```

## Caching with Redis

Product reads are cached in Redis using Spring's cache abstraction (`@Cacheable` and `@CacheEvict` on `ProductService`). Controllers and the global exception handler are unchanged.

### What is cached

| Method | Cache | Key |
|---|---|---|
| `getAllproducts()` | `products` | `all` |
| `getProductById(id)` | `products` | the product id |
| `searchProducts(keyword)` | `productSearch` | the lowercase keyword |

All entries expire after the TTL set by `spring.cache.redis.time-to-live` (10 minutes by default). Null values are not cached, and if a method throws (for example `ResourceNotFoundException`), nothing is cached.

### When the cache is cleared

- Adding, updating or deleting a product clears the `products` and `productSearch` caches.
- Placing an order clears the `products` and `productSearch` caches, because stock quantities change. It happens only after the order method succeeds, so a rolled-back order leaves the cache untouched.

### Stock checks always use the database

`OrderService.placeOrder` reads products through `ProductRepo` inside the transaction and never through the cached `ProductService` methods. A cached stock value could be stale and cause overselling, so the order flow always uses the database as the source of truth. Product listings may show slightly stale stock until the next eviction or TTL expiry.

### Seeing the cache work

1. Call `GET /api/product/1` twice. With `spring.jpa.show-sql=true`, the first call prints a SQL select and the second prints nothing because it came from Redis.
2. Inspect Redis (with Docker Compose, the container is named by Compose, so use `docker compose exec redis redis-cli`; with the `docker run` setup above, use `docker exec -it redis redis-cli`):

   ```
   KEYS *
   TTL products::1
   ```

3. Update or delete a product, or place an order, then call `GET /api/products` again. A SQL query appears again, which shows the eviction worked.

### Things to know

- Caching works through a Spring proxy, so it applies only when another class calls the service methods. A call from one method to another inside `ProductService` skips the cache.
- `Product` carries its image bytes (`imageData`), so cached products and the cached product list include them. This is fine for a learning project, but with many large images the cache entries get big.

## Global Exception Handling

All exceptions are handled in one place by `GlobalExceptionHandler`, a class annotated with `@RestControllerAdvice`. Services throw custom exceptions, and the handler maps each one to an HTTP status and a consistent JSON body. Controllers stay free of try/catch blocks.

### Error response format

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Product not found with id: 999",
  "path": "/api/product/999",
  "timestamp": "2026-10-20T14:32:10.123"
}
```

### Exception mapping

| Exception | HTTP status | When |
|---|---|---|
| `ResourceNotFoundException` | 404 | Product not found |
| `InsufficientStockException` | 409 | Order quantity exceeds available stock |
| `MethodArgumentNotValidException` | 400 | Request body fails validation |
| `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException`, `MissingServletRequestPartException` | 400 | Bad id type, missing parameter or missing multipart part |
| `DataIntegrityViolationException` | 409 | Database constraint violation |
| `IOException` | 500 | Image processing failed |
| `Exception` | 500 | Any other unexpected error (fallback) |

## Order Processing

`OrderService.placeOrder` is annotated with `@Transactional`. For each item it:

1. Finds the product in the database, or throws `ResourceNotFoundException`
2. Checks stock, or throws `InsufficientStockException`
3. Reduces the stock and builds the order item

If any item fails, the transaction rolls back, so earlier items in the same order don't keep their stock changes. When the order succeeds, the product caches are cleared so listings show the new stock.

## Troubleshooting

| Problem | Likely cause and fix |
|---|---|
| `docker: invalid reference format` | A `\` was pasted into a single-line command. Remove the backslashes or put each flag on its own line ending with `\`. |
| `container name already in use` | An old container has that name. Run `docker rm -f <name>` and try again. |
| App exits right after starting | PostgreSQL or Redis is not running or not ready, or the database name or credentials don't match. Check `docker logs springecom`. |
| Connection refused to the database | The datasource URL uses `localhost` instead of the service name `postgres`. |
| 401 Unauthorized | Send Basic auth credentials (see Authentication). |
| `manifest unknown` when building | The base image tag does not exist on Docker Hub. Use a Java version that has published images, and match `java.version` in `pom.xml`. |