# THE-ARCH Application Wiring & Architecture Guide

## Overview
This document describes the dependency wiring, data flow, and architectural patterns in the KalcPOS backend application after refactoring to extract shared utilities.

---

## Project Structure

```
src/main/java/com/kalcpos/
├── KalcposApplication.java          # Spring Boot entry point
├── api/                             # REST Controllers
│   ├── AuthController.java          # Authentication & sessions
│   ├── OrdersController.java        # Order CRUD & management
│   ├── ProductsController.java      # Product & category management
│   ├── ReceiptsController.java      # Receipt & billing
│   ├── UsersController.java         # User management
│   ├── PaymentNotificationController.java
│   ├── ShiftsController.java
│   ├── SettingsController.java
│   ├── ReportsController.java
│   ├── CommissionsController.java
│   ├── CompaniesController.java
│   ├── InventoryController.java
│   ├── MessagesController.java
│   ├── PrintersController.java
│   ├── BrandingController.java
│   ├── HealthController.java
│   ├── RootController.java
│   ├── SplitBillPaymentFilter.java
│   ├── GlobalExceptionHandler.java
│   └── OrdersWebSocketConfig.java
├── config/                          # Spring Configuration
│   └── WebConfig.java               # CORS, security beans, password encoder
├── service/                         # Business Logic Layer
│   ├── DefaultUserInitializer.java  # Initial user setup
│   └── PaymentService.java          # Payment processing
├── util/                            # Shared Utilities (NEW)
│   ├── TokenExtractor.java          # Bearer token extraction
│   ├── AuthenticationHelper.java    # Auth & authorization checks
│   ├── ControllerUtils.java         # Type conversion & responses
│   └── StringUtils.java             # String normalization
└── resources/
    └── application.properties
```

---

## Dependency Injection & Wiring

### Core Bean Configuration (WebConfig.java)

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        // Centralized password encoding bean
        // Used by: AuthController, UsersController
    }
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Global CORS configuration (removes @CrossOrigin from controllers)
        // Applied to: /api/** endpoints
    }
}
```

### Constructor Injection Pattern

All controllers use **constructor injection** of dependencies:

```java
// Pattern across all controllers
public OrdersController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
}

// After refactoring, controllers also inject passwordEncoder
public UsersController(JdbcTemplate jdbc, BCryptPasswordEncoder passwordEncoder) {
    this.jdbc = jdbc;
    this.passwordEncoder = passwordEncoder;
}
```

**Advantages:**
- Testable (dependencies can be mocked)
- Immutable (final fields)
- No field injection (no @Autowired)
- Clear about dependencies at a glance

---

## Data Flow: Common Scenarios

### 1. Authentication Flow (POST /api/v1/auth/pin-login)

```
Client Request
    ↓
AuthController.pinLogin()
    ↓
1. Extract PIN & username from request body
    ↓
2. Query users table for matching username
    ↓
3. Call verifyPin() → BCryptPasswordEncoder.matches()
    ↓
4. Generate secure token (SecureRandom)
    ↓
5. INSERT auth_session (token, user_id, expires_at)
    ↓
Return token + user info to client
    ↓
Client stores token in Authorization header
```

**Related Utilities Used:**
- `TokenExtractor.extract()` - for token verification
- `ControllerUtils` - for error responses

---

### 2. Authorization Pattern (Protected Endpoint)

```
Request with Authorization Header
    ↓
@RequestHeader("Authorization") String auth
    ↓
AuthenticationHelper.getCurrentUser(jdbc, auth)
    ↓
1. TokenExtractor.extract(auth)
    ↓
2. SELECT user FROM auth_sessions WHERE token = ? AND expires_at > NOW()
    ↓
3. Return user Map or null
    ↓
If null: return 401 Unauthorized
If present: proceed with business logic
```

**All Protected Endpoints Follow This Pattern:**
```java
@PostMapping
public ResponseEntity<Map<String, Object>> create(
    @RequestBody Map<String, Object> body,
    @RequestHeader(value = "Authorization", required = false) String auth) {
    
    // Check authorization
    ResponseEntity<Map<String, Object>> authError = 
        AuthenticationHelper.requireManager(jdbc, auth);
    if (authError != null) return authError;
    
    // Proceed with business logic...
}
```

---

### 3. Order Management Flow (POST /api/v1/orders)

```
Client Request (authenticated)
    ↓
OrdersController.createOrder()
    ↓
1. Validate auth header → AuthenticationHelper.getCurrentUser()
    ↓
2. Generate order number → nextOrderNo()
    ↓
3. INSERT INTO orders (order_no, waiter_id, status='open', total_amount=0)
    ↓
4. SELECT * FROM orders WHERE order_no = ?
    ↓
Return order with HTTP 201 CREATED
    ↓
Client polls for order items endpoint
```

**Related Endpoints:**
- POST /api/v1/orders/{orderId}/items - add items
- PATCH /api/v1/orders/{orderId}/items/{itemId} - update qty
- DELETE /api/v1/orders/{orderId}/items/{itemId} - remove item
- POST /api/v1/orders/{orderId}/recall - restore open status

---

### 4. Session Verification (GET /api/v1/auth/verify)

```
Request with Authorization Header
    ↓
AuthController.verify()
    ↓
TokenExtractor.extract(auth)
    ↓
SELECT user FROM auth_sessions 
WHERE token = ? AND expires_at > CURRENT_TIMESTAMP AND active = 1
    ↓
If found: return {valid: true, user: {...}}
If not found: return {valid: false}
```

---

## Authentication & Authorization Architecture

### Token Flow

```
┌─────────────────────────────────────────────────────┐
│ Authentication Layer (AuthController)              │
├─────────────────────────────────────────────────────┤
│ PIN Login:                                          │
│  1. Verify PIN against pin_hash (BCrypt/SHA256)    │
│  2. Generate secure random token (32 bytes)         │
│  3. Store session: (token, user_id, expires_at)    │
│  4. Return token to client                          │
└─────────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────┐
│ Client Storage                                      │
├─────────────────────────────────────────────────────┤
│ LocalStorage / SessionStorage: token                │
│ Attach to every request: Authorization: Bearer {token}
└─────────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────┐
│ Authorization Layer (AuthenticationHelper)          │
├─────────────────────────────────────────────────────┤
│ 1. Extract token from header                        │
│ 2. Query auth_sessions table                        │
│ 3. Join with users table                            │
│ 4. Check: token valid + not expired + user active   │
│ 5. Return user object or error response             │
└─────────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────┐
│ Request Processing                                  │
├─────────────────────────────────────────────────────┤
│ Proceed with business logic using user context      │
└─────────────────────────────────────────────────────┘
```

---

## Utility Classes Reference

### TokenExtractor.java
**Purpose:** Centralized token extraction from Authorization headers

```java
public class TokenExtractor {
    // Extract bearer token from "Bearer abc123" → "abc123"
    public static String extract(String authorizationHeader)
    
    // Check if token is valid (not null/empty)
    public static boolean isValid(String token)
}
```

**Used in:**
- AuthenticationHelper
- AuthController
- Every controller requiring auth

---

### AuthenticationHelper.java
**Purpose:** Centralized authentication & authorization checks

```java
public class AuthenticationHelper {
    // Get current authenticated user
    public static Map<String, Object> getCurrentUser(JdbcTemplate jdbc, String auth)
    
    // Require manager role (admin/manager)
    public static ResponseEntity<Map<String, Object>> requireManager(JdbcTemplate jdbc, String auth)
    
    // Require specific roles
    public static ResponseEntity<Map<String, Object>> requireRole(
        JdbcTemplate jdbc, String auth, Set<String> allowedRoles)
}
```

**Used in:**
- ProductsController.createProduct()
- UsersController.createUser()
- OrdersController.voidOrder()
- All management endpoints

---

### ControllerUtils.java
**Purpose:** Common type conversions & HTTP response builders

```java
public class ControllerUtils {
    // Type conversions with safe defaults
    public static Long longOrNull(Object value)
    public static int intValue(Object value)
    public static int intValueOr(Object value, int defaultValue)
    public static BigDecimal decimalValue(Object value)
    public static BigDecimal decimalValueOr(Object value, BigDecimal defaultValue)
    
    // Response builders
    public static ResponseEntity<Map<String, Object>> badRequest(String message)
    public static ResponseEntity<Map<String, Object>> notFound(String message)
    public static ResponseEntity<Map<String, Object>> unauthorized()
    public static ResponseEntity<Map<String, Object>> forbidden()
    public static Map<String, Object> successResponse()
    public static Map<String, Object> successResponse(String key, Object value)
}
```

**Used in:**
- OrdersController (qty conversions)
- ProductsController (price conversions)
- ReceiptsController (amount calculations)
- All controllers for consistent error responses

---

### StringUtils.java
**Purpose:** Common string normalization operations

```java
public class StringUtils {
    // Normalize tab names: "bar" or "kitchen" (default)
    public static String normalizeTab(String value)
    
    // Normalize keys: lowercase, remove special chars, max 40 chars
    public static String normalizeKey(String value)
    
    // Clean usernames: lowercase, alphanumeric + underscore only
    public static String cleanUsername(String value)
    
    // Validate 8-digit PIN format
    public static boolean isPinValid(String pin)
    
    // Safe cell access from list
    public static String cell(List<String> row, int index)
}
```

**Used in:**
- ProductsController (tab/category normalization)
- ProductsController CSV parsing
- UsersController (username cleaning, PIN validation)

---

## Database Schema Interactions

### auth_sessions Table
```sql
CREATE TABLE auth_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    token VARCHAR(255) UNIQUE NOT NULL,
    user_id BIGINT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

**Wiring:**
- AuthController.pinLogin() → INSERT
- AuthController.logout() → DELETE
- AuthenticationHelper.getCurrentUser() → SELECT + JOIN
- Every protected endpoint queries this table

### users Table
```sql
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(80) UNIQUE NOT NULL,
    real_name VARCHAR(255),
    role ENUM('admin','manager','supervisor','super_waiter','waiter'),
    pin_hash VARCHAR(255) NOT NULL,
    active TINYINT(1) DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

**Wiring:**
- UsersController.createUser() → INSERT with passwordEncoder.encode()
- AuthController.pinLogin() → SELECT + password verification
- AuthenticationHelper queries on every protected request

---

## Refactoring Checklist (Completed ✓)

### Utility Extraction
- ✓ TokenExtractor.java - Token handling
- ✓ AuthenticationHelper.java - Auth/authz checks
- ✓ ControllerUtils.java - Type conversion & responses
- ✓ StringUtils.java - String normalization

### Configuration Centralization
- ✓ WebConfig.java - CORS, password encoder bean

### Controller Refactoring
- ✓ AuthController - Uses TokenExtractor, injects passwordEncoder
- ✓ UsersController - Uses AuthenticationHelper, ControllerUtils, StringUtils
- ✓ ProductsController - Uses AuthenticationHelper, ControllerUtils, StringUtils
- ✓ OrdersController - Uses AuthenticationHelper, ControllerUtils
- ✓ ReceiptsController - Uses AuthenticationHelper, ControllerUtils

### Remaining Controllers (Can be refactored similarly)
- PaymentNotificationController
- ShiftsController
- SettingsController
- ReportsController
- CommissionsController
- CompaniesController
- InventoryController
- MessagesController
- PrintersController
- BrandingController
- RootController
- SplitBillPaymentFilter
- GlobalExceptionHandler

---

## Benefits of This Architecture

### 1. **Single Responsibility**
- Controllers focus on HTTP handling
- Utilities handle common concerns
- Services handle business logic

### 2. **DRY (Don't Repeat Yourself)**
- Token extraction: 1 place (TokenExtractor)
- Auth checks: 1 place (AuthenticationHelper)
- String normalization: 1 place (StringUtils)
- Type conversion: 1 place (ControllerUtils)

### 3. **Testability**
- Utilities are static/pure functions
- Controllers use constructor injection
- Easy to mock JdbcTemplate & passwordEncoder

### 4. **Maintainability**
- Bug fix in token parsing → update 1 file
- Change auth logic → update 1 file
- Consistent error responses across app

### 5. **Security**
- Centralized password encoding (BCryptPasswordEncoder bean)
- Consistent token validation
- Single source of truth for role checks

---

## Example: Adding New Endpoint

To add a new protected endpoint following this pattern:

```java
@PostMapping("/my-resource")
public ResponseEntity<Map<String, Object>> createResource(
    @RequestBody Map<String, Object> body,
    @RequestHeader(value = "Authorization", required = false) String auth) {
    
    // Step 1: Check authorization
    ResponseEntity<Map<String, Object>> authError = 
        AuthenticationHelper.requireManager(jdbc, auth);
    if (authError != null) return authError;
    
    // Step 2: Validate input
    String name = String.valueOf(body.getOrDefault("name", "")).trim();
    if (name.isBlank()) 
        return ControllerUtils.badRequest("Name is required.");
    
    int amount = ControllerUtils.intValue(body.getOrDefault("amount", 0));
    
    // Step 3: Business logic
    jdbc.update("INSERT INTO resources (name, amount) VALUES (?, ?)", name, amount);
    
    // Step 4: Return success
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ControllerUtils.successResponse());
}
```

---

## Next Steps for Further Refactoring

1. **Service Layer** - Extract business logic from controllers
   - OrderService
   - ProductService
   - UserService
   - PaymentService (already exists, use as model)

2. **DTO Classes** - Replace raw Map<String, Object>
   - OrderRequest / OrderResponse
   - ProductRequest / ProductResponse
   - UserRequest / UserResponse

3. **Validation Framework** - Use @Valid annotations
   - JSR-303 Bean Validation
   - Custom validators

4. **Error Handling** - Enhance GlobalExceptionHandler
   - Map exceptions to error responses
   - Consistent error response format

5. **Logging** - Consistent logging across app
   - Use SLF4J (already started in AuthController)
   - Log all auth attempts, errors

6. **Security** - Additional layers
   - Consider Spring Security
   - Implement proper role-based access control
   - Add CORS headers configuration review

---

## Maintenance Notes

**Important Files to Update When Changing:**
- `TokenExtractor.java` - affects all authentication
- `AuthenticationHelper.java` - affects all auth endpoints
- `WebConfig.java` - affects CORS, password encoding globally
- `StringUtils.java` - affects data normalization across app

**Testing Considerations:**
- All utility classes should have unit tests
- Mock JdbcTemplate in controller tests
- Test token extraction edge cases (null, empty, malformed)
- Test auth checks with different roles
