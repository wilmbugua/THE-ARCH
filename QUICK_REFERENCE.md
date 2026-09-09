# QUICK REFERENCE: Utility Classes & Patterns

## 🚀 Quick Start for New Code

### Authentication Pattern (3 lines)
```java
// Check if user is authenticated
Map<String, Object> user = AuthenticationHelper.getCurrentUser(jdbc, auth);
if (user == null) return ControllerUtils.unauthorized();

// OR check if user is manager
ResponseEntity<Map<String, Object>> err = AuthenticationHelper.requireManager(jdbc, auth);
if (err != null) return err;
```

### Error Response Pattern (1 line)
```java
return ControllerUtils.badRequest("Item name is required.");
return ControllerUtils.notFound("User not found.");
return ControllerUtils.unauthorized();
return ControllerUtils.forbidden();
```

### Type Conversion Pattern (1 line)
```java
int qty = ControllerUtils.intValue(body.get("quantity"));      // Safe with default 1
Long id = ControllerUtils.longOrNull(body.get("productId"));   // Returns null if invalid
BigDecimal price = ControllerUtils.decimalValue(body.get("price")); // Returns 0 if invalid
```

### String Normalization Pattern (1 line)
```java
String tab = StringUtils.normalizeTab(input);          // Returns "bar" or "kitchen"
String key = StringUtils.normalizeKey(input);          // Lowercase, no special chars
String username = StringUtils.cleanUsername(input);    // Safe username
if (!StringUtils.isPinValid(pin)) return error;        // Must be 8 digits
```

---

## 📚 Utility Classes Reference

### TokenExtractor
**Purpose:** Extract bearer token from Authorization header

| Method | Input | Output | Example |
|--------|-------|--------|---------|
| `extract()` | `"Bearer abc123"` | `"abc123"` | `TokenExtractor.extract(auth)` |
| `isValid()` | `"abc123"` | `true` | `if (TokenExtractor.isValid(token))` |

### AuthenticationHelper
**Purpose:** Authentication & authorization checks

| Method | Returns | When to use | Example |
|--------|---------|-------------|---------|
| `getCurrentUser()` | `Map<String, Object>` or `null` | Get current user info | `Map user = AuthenticationHelper.getCurrentUser(jdbc, auth)` |
| `requireManager()` | `ResponseEntity` or `null` | Enforce manager role | `if (AuthenticationHelper.requireManager(jdbc, auth) != null) return error;` |
| `requireRole()` | `ResponseEntity` or `null` | Enforce specific roles | `if (AuthenticationHelper.requireRole(jdbc, auth, roles) != null) return error;` |

### ControllerUtils
**Purpose:** Type conversions & HTTP responses

| Category | Method | Returns | Safe? | Example |
|----------|--------|---------|-------|---------|
| **Conversions** | `longOrNull()` | `Long` | ✓ Returns null | `Long id = ControllerUtils.longOrNull(val)` |
| | `intValue()` | `int` | ✓ Returns 1 | `int qty = ControllerUtils.intValue(val)` |
| | `decimalValue()` | `BigDecimal` | ✓ Returns 0 | `BigDecimal price = ControllerUtils.decimalValue(val)` |
| **Responses** | `badRequest()` | `ResponseEntity` | HTTP 400 | `return ControllerUtils.badRequest("msg")` |
| | `notFound()` | `ResponseEntity` | HTTP 404 | `return ControllerUtils.notFound("msg")` |
| | `unauthorized()` | `ResponseEntity` | HTTP 401 | `return ControllerUtils.unauthorized()` |
| | `forbidden()` | `ResponseEntity` | HTTP 403 | `return ControllerUtils.forbidden()` |
| | `successResponse()` | `Map` | `{success: true}` | `return ResponseEntity.ok(ControllerUtils.successResponse())` |

### StringUtils
**Purpose:** String validation & normalization

| Method | Input | Output | Example |
|--------|-------|--------|---------|
| `normalizeTab()` | `"Bar"`, `"kitchen"`, `"other"` | `"bar"`, `"kitchen"`, `"kitchen"` | `String tab = StringUtils.normalizeTab(input)` |
| `normalizeKey()` | `"User Name!"` | `"user_name"` (max 40 chars) | `String key = StringUtils.normalizeKey(input)` |
| `cleanUsername()` | `"John_Doe!"` | `"john_doe"` | `String user = StringUtils.cleanUsername(input)` |
| `isPinValid()` | `"12345678"` | `true` / `false` | `if (!StringUtils.isPinValid(pin)) return error` |
| `cell()` | `List<String> row, int index` | String or `""` | `String name = StringUtils.cell(row, 0)` |

---

## 🔐 Security & Auth Flows

### Complete Auth Check (5 lines)
```java
@PostMapping("/protected-endpoint")
public ResponseEntity<Map<String, Object>> create(
    @RequestBody Map<String, Object> body,
    @RequestHeader(value = "Authorization", required = false) String auth) {
    
    // Single check for manager role
    ResponseEntity<Map<String, Object>> authError = 
        AuthenticationHelper.requireManager(jdbc, auth);
    if (authError != null) return authError;
    
    // Proceed with business logic
    return ResponseEntity.ok(ControllerUtils.successResponse());
}
```

### Get User Info (3 lines)
```java
Map<String, Object> user = AuthenticationHelper.getCurrentUser(jdbc, auth);
if (user == null) return ControllerUtils.unauthorized();
Object userId = user.get("id");
String role = String.valueOf(user.get("role"));
```

### Query by User Role (4 lines)
```java
Map<String, Object> user = AuthenticationHelper.getCurrentUser(jdbc, auth);
if (user == null) return List.of();
String role = String.valueOf(user.get("role"));
if ("waiter".equals(role)) { /* waiter-specific query */ }
```

---

## 💾 Database Query Patterns

### Insert with User Info
```java
jdbc.update(
    "INSERT INTO orders (order_no, waiter_id, status) VALUES (?, ?, ?)",
    orderNo, 
    user.get("id"),  // From AuthenticationHelper.getCurrentUser()
    "open"
);
```

### Select with Auth Check
```java
if (AuthenticationHelper.getCurrentUser(jdbc, auth) == null) 
    return ControllerUtils.unauthorized();

List<Map<String, Object>> items = jdbc.queryForList(
    "SELECT * FROM order_items WHERE order_id = ?",
    orderId
);
```

### Update with Type Conversion
```java
int qty = ControllerUtils.intValue(body.get("quantity"));
BigDecimal price = ControllerUtils.decimalValue(body.get("price"));
jdbc.update(
    "UPDATE items SET qty = ?, price = ? WHERE id = ?",
    qty, price, itemId
);
```

---

## 🛠️ Common Scenarios

### Scenario: Validate & Create Resource
```java
@PostMapping
public ResponseEntity<Map<String, Object>> create(
    @RequestBody Map<String, Object> body,
    @RequestHeader(value = "Authorization", required = false) String auth) {
    
    // 1. Auth
    ResponseEntity err = AuthenticationHelper.requireManager(jdbc, auth);
    if (err != null) return err;
    
    // 2. Validate input
    String name = String.valueOf(body.getOrDefault("name", "")).trim();
    if (name.isBlank()) 
        return ControllerUtils.badRequest("Name required");
    
    // 3. Convert & validate
    BigDecimal amount = ControllerUtils.decimalValue(body.get("amount"));
    if (amount.compareTo(BigDecimal.ZERO) <= 0)
        return ControllerUtils.badRequest("Amount must be positive");
    
    // 4. Create
    jdbc.update("INSERT INTO resources (name, amount) VALUES (?, ?)", name, amount);
    
    // 5. Return
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ControllerUtils.successResponse());
}
```

### Scenario: Get & Filter by User Role
```java
@GetMapping
public List<Map<String, Object>> list(
    @RequestHeader(value = "Authorization", required = false) String auth) {
    
    Map<String, Object> user = AuthenticationHelper.getCurrentUser(jdbc, auth);
    if (user == null) return List.of();
    
    String role = String.valueOf(user.get("role"));
    Long userId = (Long) user.get("id");
    
    if ("waiter".equals(role)) {
        return jdbc.queryForList(
            "SELECT * FROM orders WHERE waiter_id = ? AND DATE(created_at) = CURDATE()",
            userId
        );
    }
    return jdbc.queryForList("SELECT * FROM orders WHERE DATE(created_at) = CURDATE()");
}
```

### Scenario: Update with Multiple Validations
```java
@PatchMapping("/{id}")
public ResponseEntity<Map<String, Object>> update(
    @PathVariable long id,
    @RequestBody Map<String, Object> body,
    @RequestHeader(value = "Authorization", required = false) String auth) {
    
    // Auth
    ResponseEntity err = AuthenticationHelper.requireManager(jdbc, auth);
    if (err != null) return err;
    
    // Validate
    String name = String.valueOf(body.getOrDefault("name", "")).trim();
    if (name.isBlank()) 
        return ControllerUtils.badRequest("Name required");
    
    String tab = StringUtils.normalizeTab(String.valueOf(body.get("tab")));
    int qty = ControllerUtils.intValue(body.get("qty"));
    
    // Update
    int updated = jdbc.update(
        "UPDATE products SET name = ?, tab = ?, qty = ? WHERE id = ?",
        name, tab, qty, id
    );
    
    // Response
    if (updated == 0) return ControllerUtils.notFound("Product not found");
    return ResponseEntity.ok(ControllerUtils.successResponse());
}
```

---

## 🚫 Patterns to AVOID (Old Way)

```java
// ❌ DON'T: Inline token extraction
String token = authorization == null ? "" : 
    authorization.replaceFirst("(?i)^Bearer\\s+", "");

// ✅ DO: Use TokenExtractor
String token = TokenExtractor.extract(authorization);

---

// ❌ DON'T: Repeated auth checks
if (rows.isEmpty()) 
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(...);
String role = String.valueOf(rows.get(0).get("role"));
if (!Set.of("admin", "manager").contains(role))
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(...);

// ✅ DO: Use AuthenticationHelper
ResponseEntity err = AuthenticationHelper.requireManager(jdbc, auth);
if (err != null) return err;

---

// ❌ DON'T: Inconsistent error responses
return ResponseEntity.badRequest().body(...);
return ResponseEntity.status(HttpStatus.NOT_FOUND).body(...);
throw new Exception(...);

// ✅ DO: Use ControllerUtils
return ControllerUtils.badRequest("message");
return ControllerUtils.notFound("message");

---

// ❌ DON'T: Unsafe type conversion
int qty = Integer.parseInt(String.valueOf(value)); // Throws exception
BigDecimal price = new BigDecimal(String.valueOf(value)); // May throw

// ✅ DO: Use ControllerUtils
int qty = ControllerUtils.intValue(value); // Returns 1 on error
BigDecimal price = ControllerUtils.decimalValue(value); // Returns 0 on error
```

---

## 📋 Endpoint Template

Use this template for creating new endpoints:

```java
@PostMapping("/{id}")
public ResponseEntity<Map<String, Object>> operation(
        @PathVariable long id,
        @RequestBody Map<String, Object> body,
        @RequestHeader(value = "Authorization", required = false) String auth) {
    
    // Step 1: Authorization (choose one)
    // Option A: Just check if authenticated
    Map<String, Object> user = AuthenticationHelper.getCurrentUser(jdbc, auth);
    if (user == null) return ControllerUtils.unauthorized();
    
    // Option B: Check if manager
    ResponseEntity<Map<String, Object>> authErr = 
        AuthenticationHelper.requireManager(jdbc, auth);
    if (authErr != null) return authErr;
    
    // Step 2: Validate input
    String name = String.valueOf(body.getOrDefault("name", "")).trim();
    if (name.isBlank()) return ControllerUtils.badRequest("Name required");
    
    int qty = ControllerUtils.intValue(body.get("qty"));
    BigDecimal price = ControllerUtils.decimalValue(body.get("price"));
    
    // Step 3: Database operation
    int updated = jdbc.update(
        "UPDATE table SET name = ?, qty = ?, price = ? WHERE id = ?",
        name, qty, price, id
    );
    
    // Step 4: Return response
    if (updated == 0) return ControllerUtils.notFound("Resource not found");
    return ResponseEntity.ok(ControllerUtils.successResponse());
}
```

---

## 🔗 Import Statements

Copy this block to use all utilities:

```java
import com.kalcpos.util.TokenExtractor;
import com.kalcpos.util.AuthenticationHelper;
import com.kalcpos.util.ControllerUtils;
import com.kalcpos.util.StringUtils;
```

---

## 📊 At a Glance

| Utility | Use For | Example |
|---------|---------|---------|
| **TokenExtractor** | Extract auth token | `TokenExtractor.extract(header)` |
| **AuthenticationHelper** | Auth checks | `AuthenticationHelper.getCurrentUser(jdbc, auth)` |
| **ControllerUtils** | Error responses | `ControllerUtils.badRequest("msg")` |
| **ControllerUtils** | Type conversions | `ControllerUtils.intValue(obj)` |
| **StringUtils** | String validation | `StringUtils.isPinValid(pin)` |
| **StringUtils** | String normalization | `StringUtils.normalizeTab(input)` |

---

## 🎯 Code Quality Checklist

Before committing code:
- ✓ Use `AuthenticationHelper` for all auth checks
- ✓ Use `ControllerUtils` for all error responses
- ✓ Use `ControllerUtils` for type conversions
- ✓ Use `StringUtils` for string normalization
- ✓ Use `TokenExtractor` for token handling
- ✓ No inline token extraction regex
- ✓ No ResponseEntity boilerplate
- ✓ No try-catch for type conversions
- ✓ Constructor injection (no @Autowired fields)
- ✓ All endpoints follow standard pattern
