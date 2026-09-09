package com.kalcpos.api;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/printers")
@CrossOrigin(maxAge = 3600)
public class PrintersController {
    private final JdbcTemplate jdbc;

    public PrintersController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/installed")
    public List<Map<String, Object>> installed() {
        PrintService defaultPrinter = PrintServiceLookup.lookupDefaultPrintService();
        String defaultName = defaultPrinter == null ? "" : defaultPrinter.getName();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PrintService printer : PrintServiceLookup.lookupPrintServices(null, null)) {
            rows.add(Map.of(
                    "name", printer.getName(),
                    "default", printer.getName().equals(defaultName)
            ));
        }
        return rows;
    }

    @PostMapping("/station")
    public ResponseEntity<Map<String, Object>> setStationPrinter(@RequestBody Map<String, Object> body) {
        String station = String.valueOf(body.getOrDefault("station", "")).trim().toLowerCase();
        String printer = String.valueOf(body.getOrDefault("printer", "")).trim();
        if (!List.of("bar", "kitchen").contains(station)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Station must be bar or kitchen."));
        }
        if (printer.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Printer name is required."));
        }
        ensureSettingsTable();
        jdbc.update("""
                INSERT INTO system_settings(setting_key, setting_value)
                VALUES(?, ?)
                ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)
                """, "printer_" + station, printer);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private void ensureSettingsTable() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS system_settings (
                  setting_key VARCHAR(80) PRIMARY KEY,
                  setting_value LONGTEXT NULL,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """);
    }
}
