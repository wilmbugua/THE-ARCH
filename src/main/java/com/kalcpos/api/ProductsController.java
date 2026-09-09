package com.kalcpos.api;

import com.kalcpos.util.AuthenticationHelper;
import com.kalcpos.util.ControllerUtils;
import com.kalcpos.util.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Product management controller.
 * Handles product CRUD, categories, and CSV import.
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(maxAge = 3600)
public class ProductsController {
    private static final String DEFAULT_IMAGE = "/brand/restaurant-service.svg";
    private final JdbcTemplate jdbc;

    public ProductsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/products")
    public List<Map<String, Object>> products(
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) String subcategory) {
        if (tab != null && !tab.isBlank() && subcategory != null && !subcategory.isBlank()) {
            return jdbc.queryForList("""
                    SELECT id, name, tab, subcategory, price_ksh, image_url, active
                    FROM products
                    WHERE active = 1 AND tab = ? AND subcategory = ?
                    ORDER BY name
                    """, StringUtils.normalizeTab(tab), StringUtils.normalizeKey(subcategory));
        }
        return jdbc.queryForList("""
                SELECT id, name, tab, subcategory, price_ksh, image_url, active
                FROM products
                WHERE active = 1
                ORDER BY tab, subcategory, name
                """);
    }

    @GetMapping("/inventory/products")
    public List<Map<String, Object>> inventoryProducts() {
        return jdbc.queryForList("""
                SELECT id, name, tab, subcategory, price_ksh, image_url, active, stock_qty, stock_unit
                FROM products
                ORDER BY active DESC, tab, subcategory, name
                """);
    }

    @PostMapping("/products")
    public ResponseEntity<Map<String, Object>> createProduct(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        ResponseEntity<Map<String, Object>> auth = AuthenticationHelper.requireManager(jdbc, authorization);
        if (auth != null) return auth;
        
        ProductInput input = productInput(body);
        if (input.error() != null) return ControllerUtils.badRequest(input.error());

        jdbc.update("""
                INSERT INTO products (name, tab, subcategory, price_ksh, image_url, active)
                VALUES (?, ?, ?, ?, ?, 1)
                """, input.name(), input.tab(), input.subcategory(), input.price(), input.imageUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(ControllerUtils.successResponse());
    }

    @PatchMapping("/products/{id}")
    public ResponseEntity<Map<String, Object>> updateProduct(
            @PathVariable long id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        ResponseEntity<Map<String, Object>> auth = AuthenticationHelper.requireManager(jdbc, authorization);
        if (auth != null) return auth;
        
        ProductInput input = productInput(body);
        if (input.error() != null) return ControllerUtils.badRequest(input.error());

        int updated = jdbc.update("""
                UPDATE products
                SET name = ?, tab = ?, subcategory = ?, price_ksh = ?, image_url = ?
                WHERE id = ?
                """, input.name(), input.tab(), input.subcategory(), input.price(), input.imageUrl(), id);
        if (updated == 0) return ControllerUtils.notFound("Product not found.");
        return ResponseEntity.ok(ControllerUtils.successResponse());
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Map<String, Object>> deleteProduct(
            @PathVariable long id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        ResponseEntity<Map<String, Object>> auth = AuthenticationHelper.requireManager(jdbc, authorization);
        if (auth != null) return auth;
        
        jdbc.update("UPDATE products SET active = 0 WHERE id = ?", id);
        return ResponseEntity.ok(ControllerUtils.successResponse());
    }

    @PostMapping("/products/import")
    public ResponseEntity<Map<String, Object>> importProducts(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "bar") String tab,
            @RequestHeader(value = "Authorization", required = false) String authorization) throws Exception {
        ResponseEntity<Map<String, Object>> auth = AuthenticationHelper.requireManager(jdbc, authorization);
        if (auth != null) return auth;
        
        if (file.isEmpty()) return ControllerUtils.badRequest("Choose a CSV spreadsheet file.");

        String targetTab = StringUtils.normalizeTab(tab);
        String csv = new String(file.getBytes(), StandardCharsets.UTF_8).replace("\uFEFF", "");
        List<List<String>> rows = parseCsv(csv);
        if (rows.isEmpty()) return ControllerUtils.badRequest("Spreadsheet is empty.");

        Map<String, Integer> headers = headerMap(rows.get(0));
        int nameCol = firstHeader(headers, "item", "name", "product", "menu item");
        int categoryCol = firstHeader(headers, "category", "subcategory", "group");
        int priceCol = firstHeader(headers, "selling price", "price", "price_ksh", "amount");
        if (nameCol < 0 || priceCol < 0) {
            return ControllerUtils.badRequest("CSV must include Item and Selling Price columns.");
        }

        int imported = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            String name = StringUtils.cell(row, nameCol).trim();
            String category = categoryCol >= 0 ? StringUtils.cell(row, categoryCol).trim() : "Imported";
            String priceText = StringUtils.cell(row, priceCol).replace(",", "").trim();
            if (name.isBlank()) {
                skipped++;
                continue;
            }
            BigDecimal price;
            try {
                price = new BigDecimal(priceText);
            } catch (Exception ex) {
                skipped++;
                if (errors.size() < 8) errors.add("Row " + (i + 1) + ": invalid price for " + name);
                continue;
            }

            String subcategory = StringUtils.normalizeKey(category.isBlank() ? "Imported" : category);
            ensureCategory(targetTab, subcategory, category.isBlank() ? "Imported" : category);
            List<Map<String, Object>> existing = jdbc.queryForList(
                    "SELECT id FROM products WHERE LOWER(name) = LOWER(?) AND tab = ? LIMIT 1", name, targetTab);
            if (existing.isEmpty()) {
                jdbc.update("""
                        INSERT INTO products (name, tab, subcategory, price_ksh, image_url, active)
                        VALUES (?, ?, ?, ?, ?, 1)
                        """, name, targetTab, subcategory, price, DEFAULT_IMAGE);
                imported++;
            } else {
                jdbc.update("""
                        UPDATE products
                        SET subcategory = ?, price_ksh = ?, active = 1
                        WHERE id = ?
                        """, subcategory, price, existing.get(0).get("id"));
                updated++;
            }
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "imported", imported,
                "updated", updated,
                "skipped", skipped,
                "errors", errors
        ));
    }

    @GetMapping("/product-categories")
    public List<Map<String, Object>> categories() {
        ensureCategoriesTable();
        return jdbc.queryForList("""
                SELECT id, tab, category_key, label, active
                FROM product_categories
                WHERE active = 1
                ORDER BY tab, label
                """);
    }

    @PostMapping("/product-categories")
    public ResponseEntity<Map<String, Object>> createCategory(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        ResponseEntity<Map<String, Object>> auth = AuthenticationHelper.requireManager(jdbc, authorization);
        if (auth != null) return auth;
        
        String tab = StringUtils.normalizeTab(String.valueOf(body.getOrDefault("tab", "kitchen")));
        String label = String.valueOf(body.getOrDefault("label", "")).trim();
        if (label.isBlank()) return ControllerUtils.badRequest("Category name is required.");
        ensureCategory(tab, StringUtils.normalizeKey(label), label);
        return ResponseEntity.ok(ControllerUtils.successResponse());
    }

    @PatchMapping("/product-categories/{id}")
    public ResponseEntity<Map<String, Object>> updateCategory(@PathVariable long id, @RequestBody Map<String, Object> body) {
        String label = String.valueOf(body.getOrDefault("label", "")).trim();
        if (label.isBlank()) return ControllerUtils.badRequest("Category name is required.");
        jdbc.update("UPDATE product_categories SET label = ?, category_key = ? WHERE id = ?", label, StringUtils.normalizeKey(label), id);
        return ResponseEntity.ok(ControllerUtils.successResponse());
    }

    @DeleteMapping("/product-categories/{id}")
    public ResponseEntity<Map<String, Object>> deleteCategory(@PathVariable long id) {
        jdbc.update("UPDATE product_categories SET active = 0 WHERE id = ?", id);
        return ResponseEntity.ok(ControllerUtils.successResponse());
    }

    private ProductInput productInput(Map<String, Object> body) {
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        String tab = StringUtils.normalizeTab(String.valueOf(body.getOrDefault("tab", "kitchen")));
        String subcategory = StringUtils.normalizeKey(String.valueOf(body.getOrDefault("subcategory", "mains")));
        String imageUrl = String.valueOf(body.getOrDefault("imageUrl", body.getOrDefault("image_url", DEFAULT_IMAGE))).trim();
        if (imageUrl.isBlank()) imageUrl = DEFAULT_IMAGE;
        if (name.isBlank()) return new ProductInput(null, null, null, null, null, "Item name is required.");
        BigDecimal price;
        try {
            price = new BigDecimal(String.valueOf(body.getOrDefault("priceKsh", body.getOrDefault("price_ksh", "0"))));
        } catch (Exception ex) {
            return new ProductInput(null, null, null, null, null, "Price is invalid.");
        }
        return new ProductInput(name, tab, subcategory, price, imageUrl, null);
    }

    private void ensureCategory(String tab, String key, String label) {
        ensureCategoriesTable();
        jdbc.update("""
                INSERT INTO product_categories(tab, category_key, label, active)
                VALUES (?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE label = VALUES(label), active = 1
                """, tab, key, label);
    }

    private void ensureCategoriesTable() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS product_categories (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  tab ENUM('kitchen','bar') NOT NULL,
                  category_key VARCHAR(40) NOT NULL,
                  label VARCHAR(80) NOT NULL,
                  active TINYINT(1) NOT NULL DEFAULT 1,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_product_categories_tab_key (tab, category_key)
                )
                """);
    }

    private Map<String, Integer> headerMap(List<String> row) {
        Map<String, Integer> headers = new HashMap<>();
        for (int i = 0; i < row.size(); i++) {
            headers.put(row.get(i).trim().toLowerCase(), i);
        }
        return headers;
    }

    private int firstHeader(Map<String, Integer> headers, String... names) {
        for (String name : names) {
            if (headers.containsKey(name)) return headers.get(name);
        }
        return -1;
    }

    private List<List<String>> parseCsv(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < csv.length(); i++) {
            char ch = csv.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (ch == '\n') {
                row.add(cell.toString().replace("\r", ""));
                rows.add(row);
                row = new ArrayList<>();
                cell.setLength(0);
            } else {
                cell.append(ch);
            }
        }
        row.add(cell.toString().replace("\r", ""));
        if (row.stream().anyMatch(value -> !value.isBlank())) rows.add(row);
        return rows;
    }

    private record ProductInput(String name, String tab, String subcategory, BigDecimal price, String imageUrl, String error) {
    }
}
