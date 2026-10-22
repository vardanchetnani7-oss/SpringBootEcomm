# Spring Ecom

A REST API for a simple e-commerce backend built with Spring Boot. It manages products (with images) and customer orders, secured with HTTP Basic authentication, a centralized global exception handler and Redis caching for product reads.

## Features

- Product management: create, read, update, delete and search, with image upload and download
- Redis caching of product reads (list, by id and search), with automatic eviction on writes
- Order placement with automatic stock deduction
- Transactional order flow: if any item in an order fails, all stock changes roll back
- Global exception handling with one consistent JSON error format
- HTTP Basic authentication with role-based access (`USER` and `ADMIN`)

## Tech Stack

- Java
- Spring Boot (Web, Data JPA, Security, Cache, Data Redis)
- Redis
- Lombok
- Maven
- Any JPA-supported relational database (configure it in `application.properties`)

## Project Structure

```
com.Ecom.SpringEcom
├── Controller        ProductController, OrderController
├── services          ProductService (caching), OrderService (cache eviction)
├── repository        ProductRepo, OrderRepo
├── models            Product, Order, OrderItem
│   └── dtos          OrderRequest, OrderResponse, OrderItemRequest, OrderItemResponse
├── exception         GlobalExceptionHandler, ErrorResponse,
│                     ResourceNotFoundException, InsufficientStockException
└── config            SecurityConfig
```

`@EnableCaching` is added on the main application class. No separate cache configuration class is needed.

## Getting Started

### Prerequisites

- JDK 17 or later
- Maven
- A running database (MySQL, PostgreSQL, H2, etc.)
- A running Redis server (see below)

### Start Redis

The easiest way is Docker:

```bash
docker run -d --name redis -p 6379:6379 redis
```

### Dependencies

Add these to `pom.xml`:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

### Configuration

Add your database and Redis settings to `src/main/resources/application.properties`. Example for PostgreSQL:

```properties
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

### Run

```bash
mvn spring-boot:run
```

The API starts at `http://localhost:8080`. All endpoints are under `/api`.

## Authentication

The API uses HTTP Basic authentication. Send an `Authorization: Basic base64(username:password)` header with each protected request. Spring Security checks the credentials and then the role.

| Username | Password | Role |
|---|---|---|
| `user` | `user123` | USER |
| `admin` | `admin123` | ADMIN |

Requests with missing or wrong credentials get Spring Security's default 401 response, and requests from a user without the required role get its default 403 response.

These users are stored in memory for development only. Change them or load users from the database before any real deployment, and always use HTTPS, because Basic auth credentials are only Base64-encoded.

## API Endpoints

| Method | Endpoint | Description | Access |
|---|---|---|---|
| GET | `/api/products` | List all products (cached) | Public |
| GET | `/api/product/{id}` | Get a product by id (cached) | Public |
| GET | `/api/product/{id}/image` | Get a product image | Public |
| GET | `/api/product/search?keyword=` | Search by name, description, brand or category (cached) | Public |
| POST | `/api/product` | Add a product (multipart: `product` JSON + `imageFile`) | ADMIN |
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

1. Call `GET /api/product/1` twice. With `spring.jpa.show-sql=true`, the first call prints a SQL `select` and the second prints nothing because it came from Redis.
2. Inspect Redis:
   ```bash
   docker exec -it redis redis-cli
   KEYS *
   TTL products::1
   ```
3. Update or delete a product, or place an order, then call `GET /api/products` again. A SQL query appears again, which shows the eviction worked.

### Things to know

- Caching works through a Spring proxy, so it applies only when another class calls the service methods. A call from one method to another inside `ProductService` skips the cache.
- `Product` carries its image bytes (`imageData`), so cached products and the cached product list include them. This is fine for a learning project, but with many large images the cache entries get big.

## Global Exception Handling

All exceptions are handled in one place by `GlobalExceptionHandler`, a class annotated with `@RestControllerAdvice`. Services throw custom exceptions, and the handler maps each one to an HTTP status and a consistent JSON body. Controllers stay free of `try/catch` blocks.

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
