package com.kalcpos.util;

/**
 * Utility class for common string normalization and validation operations.
 */
public class StringUtils {
    private StringUtils() {
        // Utility class
    }

    /**
     * Normalizes tab names to lowercase.
     * Maps any non-standard value to "kitchen" (default).
     *
     * @param value The tab name to normalize
     * @return Either "bar" or "kitchen"
     */
    public static String normalizeTab(String value) {
        return "bar".equalsIgnoreCase(value) ? "bar" : "kitchen";
    }

    /**
     * Normalizes a string key for database storage.
     * Converts to lowercase, removes non-alphanumeric characters, and limits length.
     *
     * @param value The value to normalize
     * @return Normalized key (max 40 characters), or "imported" if empty
     */
    public static String normalizeKey(String value) {
        String key = value == null ? "" : value.trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return key.isBlank() ? "imported" : key.substring(0, Math.min(40, key.length()));
    }

    /**
     * Cleans a username for database storage.
     * Converts to lowercase, removes non-alphanumeric characters (except underscore).
     *
     * @param value The username to clean
     * @return Cleaned username with leading/trailing underscores removed
     */
    public static String cleanUsername(String value) {
        return value.trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    /**
     * Checks if a PIN matches the required 8-digit format.
     *
     * @param pin The PIN to validate
     * @return true if PIN is exactly 8 digits, false otherwise
     */
    public static boolean isPinValid(String pin) {
        return pin != null && pin.matches("\\d{8}");
    }

    /**
     * Gets a cell value from a list by index, returning empty string if out of bounds.
     *
     * @param row The list of cell values
     * @param index The index to retrieve
     * @return The cell value, or empty string if index is out of bounds
     */
    public static String cell(java.util.List<String> row, int index) {
        return index >= 0 && index < row.size() ? row.get(index) : "";
    }
}
