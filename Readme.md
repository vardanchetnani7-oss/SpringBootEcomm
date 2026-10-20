# Spring Ecom

A REST API for a simple e-commerce backend built with Spring Boot. It manages products (with images) and customer orders, secured with HTTP Basic authentication and a centralized global exception handler.

## Features

- Product management: create, read, update, delete and search, with image upload and download
- Order placement with automatic stock deduction
- Transactional order flow: if any item in an order fails, all stock changes roll back
- Global exception handling with one consistent JSON error format
- HTTP Basic authentication with role-based access (`USER` and `ADMIN`)

## Tech Stack

- Java
- Spring Boot (Web, Data JPA, Security)
- Lombok
- Maven
- Any JPA-supported relational database (configure it in `application.properties`)

## Project Structure

```
com.Ecom.SpringEcom
├── Controller        ProductController, OrderController
├── services          ProductService, OrderService
├── repository        ProductRepo, OrderRepo
├── models            Product, Order, OrderItem
│   └── dtos          OrderRequest, OrderResponse, OrderItemRequest, OrderItemResponse
├── exception         GlobalExceptionHandler, ErrorResponse,
│                     ResourceNotFoundException, InsufficientStockException
└── config            SecurityConfig
```

## Getting Started

### Prerequisites

- JDK 17 or later
- Maven
- A running database (MySQL, PostgreSQL, H2, etc.)

### Configuration

Add your database settings to `src/main/resources/application.properties`. Example for MySQL:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/SpringEcom
spring.datasource.username=your_username
spring.datasource.password=your_password
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
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
| GET | `/api/products` | List all products | Public |
| GET | `/api/product/{id}` | Get a product by id | Public |
| GET | `/api/product/{id}/image` | Get a product image | Public |
| GET | `/api/product/search?keyword=` | Search by name, description, brand or category | Public |
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

1. Finds the product, or throws `ResourceNotFoundException`
2. Checks stock, or throws `InsufficientStockException`
3. Reduces the stock and builds the order item

If any item fails, the transaction rolls back, so earlier items in the same order don't keep their stock changes.

## Possible Improvements

- Request validation with `@Valid` and `spring-boot-starter-validation`
- Users stored in the database with BCrypt-hashed passwords
- Handlers for 404 (unknown route), 405 and 415 responses
- A proper logger (SLF4J) in place of `printStackTrace()`
- Optimistic locking (`@Version`) on `Product` to prevent overselling under concurrent orders
- Pagination for products and orders
- Unit tests for the service layer