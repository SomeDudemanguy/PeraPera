package viewer;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.io.*;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Service class for application-level operations and startup logic.
 * Handles configuration loading, password authentication, and application initialization.
 * Now uses JSON-based configuration instead of properties files.
 */
public class ApplicationService {
    
    private static final String USER_DATA_DIR = "userData";
    private static final String CONFIG_FILE_NAME = "config.json";
    private static final String THEME_FILE_NAME = "theme.json";
    private static String configFilePath;
    private static String themeFilePath;
    private static Map<String, String> config = new HashMap<>();
    private static String mainDirectoryPath = null; // No hardcoded default - start empty
    
    /**
     * Gets the base application directory (where the JAR is running).
     * Uses user.dir system property for portable path resolution.
     */
    public static String getAppBasePath() {
        return System.getProperty("user.dir");
    }
    
    /**
     * Gets the full path to the userData directory.
     */
    public static String getUserDataPath() {
        return getAppBasePath() + File.separator + USER_DATA_DIR;
    }
    
    /**
     * Gets the full path to the config file.
     */
    public static String getConfigFilePath() {
        if (configFilePath == null) {
            configFilePath = getUserDataPath() + File.separator + CONFIG_FILE_NAME;
        }
        return configFilePath;
    }
    
    /**
     * Gets the full path to the theme config file.
     */
    public static String getThemeFilePath() {
        if (themeFilePath == null) {
            themeFilePath = getUserDataPath() + File.separator + THEME_FILE_NAME;
        }
        return themeFilePath;
    }
    public static void loadConfig() {
        File jsonFile = new File(getConfigFilePath());
        
        if (!jsonFile.getParentFile().exists()) {
            jsonFile.getParentFile().mkdirs();
        }
        
        if (jsonFile.exists()) {
            // Load from JSON
            loadConfigFromJson();
        } else {
            // Use empty config - no defaults
            System.out.println("No config found, starting with empty configuration");
        }
        
        // Set main directory path from config (null if not set)
        mainDirectoryPath = config.get("main.directory");
        if (mainDirectoryPath != null && !mainDirectoryPath.isEmpty()) {
            System.out.println("Main directory configured: " + mainDirectoryPath);
        } else {
            System.out.println("No main directory configured - welcome screen will show");
        }
        
        // Load theme colors from theme.json
        loadThemeConfig();
    }
    
    /**
     * Loads configuration from JSON file.
     */
    private static void loadConfigFromJson() {
        File jsonFile = new File(getConfigFilePath());
        boolean needsCleanSave = false;
        try (Scanner scanner = new Scanner(jsonFile)) {
            StringBuilder content = new StringBuilder();
            while (scanner.hasNextLine()) {
                content.append(scanner.nextLine());
            }
            
            String json = content.toString().trim();
            if (json.startsWith("{") && json.endsWith("}")) {
                json = json.substring(1, json.length() - 1);
                // Parse key-value pairs more carefully to handle commas in values
                parseJsonPairs(json, needsCleanSave);
            }
            System.out.println("Loaded config from JSON: " + config.size() + " settings");
            // Re-save immediately if we cleaned corrupted data to shrink the file
            if (needsCleanSave) {
                System.out.println("Cleaned corrupted paths in config, re-saving...");
                saveConfig();
            }
        } catch (IOException e) {
            System.err.println("Failed to load config JSON: " + e.getMessage());
        }
    }
    
    /**
     * Parses JSON key-value pairs, handling commas within quoted strings.
     */
    private static void parseJsonPairs(String json, boolean needsCleanSave) {
        int i = 0;
        while (i < json.length()) {
            // Skip whitespace
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
                i++;
            }
            if (i >= json.length()) break;
            
            // Find key (quoted string)
            if (json.charAt(i) != '"') {
                i++;
                continue;
            }
            int keyStart = i;
            String key = extractJsonStringAt(json, i);
            i = findNextUnescapedQuote(json, keyStart) + 1;
            
            // Skip whitespace and find colon
            while (i < json.length() && json.charAt(i) != ':') {
                i++;
            }
            if (i >= json.length()) break;
            i++; // skip colon
            
            // Skip whitespace
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
                i++;
            }
            if (i >= json.length()) break;
            
            // Find value (quoted string)
            if (json.charAt(i) != '"') {
                i++;
                continue;
            }
            String value = extractJsonStringAt(json, i);
            i = findNextUnescapedQuote(json, i) + 1;
            
            if (key != null && value != null) {
                // Emergency clean: fix corrupted paths with backslash explosion
                if (value.contains("\\\\")) {
                    value = value.replaceAll("\\\\+", "/");
                    needsCleanSave = true;
                }
                config.put(key, value);
            }
        }
    }
    
    /**
     * Finds the next unescaped quote starting from the given position.
     */
    private static int findNextUnescapedQuote(String json, int start) {
        int i = start + 1; // skip opening quote
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '"') {
                // Check if it's escaped
                if (i > 0 && json.charAt(i - 1) == '\\') {
                    // Check if the backslash itself is escaped
                    if (i > 1 && json.charAt(i - 2) == '\\') {
                        return i; // The quote is not escaped
                    }
                    i++;
                } else {
                    return i;
                }
            } else {
                i++;
            }
        }
        return -1;
    }
    
    /**
     * Extracts a JSON string starting at the given position (which should be a quote).
     */
    private static String extractJsonStringAt(String json, int start) {
        if (json.charAt(start) != '"') return null;
        int end = findNextUnescapedQuote(json, start);
        if (end == -1) return null;
        String extracted = json.substring(start + 1, end);
        // Unescape the string
        return unescapeJson(extracted);
    }
    
    /**
     * Unescapes a JSON string.
     */
    private static String unescapeJson(String value) {
        return value.replace("\\\"", "\"")
                   .replace("\\n", "\n")
                   .replace("\\r", "\r")
                   .replace("\\t", "\t")
                   .replace("\\\\", "\\");
    }
    
    /**
     * Escapes special characters in JSON strings.
     */
    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
    
    /**
     * Saves the current configuration to JSON storage.
     */
    public static void saveConfig() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        
        boolean first = true;
        for (Map.Entry<String, String> entry : config.entrySet()) {
            if (!first) json.append(",\n");
            first = false;
            String value = entry.getValue() != null ? escapeJson(entry.getValue()) : "";
            json.append("  \"").append(escapeJson(entry.getKey())).append("\": \"").append(value).append("\"");
        }
        
        json.append("\n}\n");
        
        try {
            File configFile = new File(getConfigFilePath());
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(json.toString());
            }
        } catch (IOException e) {
            System.err.println("Failed to save config JSON: " + e.getMessage());
        }
    }
    
    /**
     * Sanitizes a file path by normalizing slashes and removing duplicates.
     */
    private static String sanitizePath(String path) {
        if (path == null) return null;
        // Replace all backslashes with forward slashes
        path = path.replace("\\", "/");
        // Replace multiple consecutive forward slashes with single one
        path = path.replaceAll("/{2,}", "/");
        // Trim whitespace
        return path.trim();
    }
    
    public static String getConfig(String key, String defaultValue) {
        return config.getOrDefault(key, defaultValue);
    }
    
    /**
     * Sets a config value and saves.
     * Automatically sanitizes path values.
     */
    public static void setConfig(String key, String value) {
        // Sanitize path/directory values to prevent backslash explosion
        if (key != null && (key.contains("path") || key.contains("directory"))) {
            value = sanitizePath(value);
        }
        config.put(key, value);
        saveConfig();
    }
    
    /**
     * Gets the saved sort option.
     */
    public static String getSortOption() {
        return getConfig("sort.option", "Rating ↓");
    }
    
    /**
     * Sets and saves the sort option.
     */
    public static void setSortOption(String sortOption) {
        setConfig("sort.option", sortOption);
    }
    
    /**
     * Gets the saved page size.
     */
    public static int getPageSize() {
        String pageSizeStr = getConfig("page.size", "100");
        try {
            return Integer.parseInt(pageSizeStr);
        } catch (NumberFormatException e) {
            return 100;
        }
    }
    
    /**
     * Sets and saves the page size.
     */
    public static void setPageSize(int pageSize) {
        setConfig("page.size", String.valueOf(pageSize));
    }
    
    /**
     * Gets the main directory path.
     * Returns null if not configured (empty library state).
     */
    public static String getMainDirectoryPath() {
        return mainDirectoryPath;
    }
    
    /**
     * Checks if main directory is configured.
     */
    public static boolean hasMainDirectoryConfigured() {
        return mainDirectoryPath != null && !mainDirectoryPath.isEmpty();
    }
    
    /**
     * Sets the main directory path and persists to config.
     */
    public static void setMainDirectoryPath(String path) {
        mainDirectoryPath = sanitizePath(path);
        if (mainDirectoryPath != null && !mainDirectoryPath.isEmpty()) {
            setConfig("main.directory", mainDirectoryPath);
        }
    }
    
    /**
     * Gets the background image path.
     */
    public static String getBackgroundImagePath() {
        return getConfig("background.image.path", "");
    }
    
    /**
     * Sets and saves the background image path.
     */
    public static void setBackgroundImagePath(String path) {
        setConfig("background.image.path", sanitizePath(path));
    }
    
    /**
     * Gets the browser background image path.
     */
    public static String getBrowserBackgroundImagePath() {
        return getConfig("browser.background.path", "");
    }
    
    /**
     * Sets and saves the browser background image path.
     */
    public static void setBrowserBackgroundImagePath(String path) {
        setConfig("browser.background.path", sanitizePath(path));
    }
    
    /**
     * Gets the viewer background image path.
     */
    public static String getViewerBackgroundImagePath() {
        return getConfig("viewer.background.path", "");
    }
    
    /**
     * Sets and saves the viewer background image path.
     */
    public static void setViewerBackgroundImagePath(String path) {
        setConfig("viewer.background.path", sanitizePath(path));
    }
    
    /**
     * Gets the thumbnail background transparency (0.0 to 1.0).
     */
    public static float getThumbnailAlpha() {
        return Float.parseFloat(getConfig("thumbnail.alpha", "1.0"));
    }
    
    /**
     * Sets the thumbnail background transparency (0.0 to 1.0).
     */
    public static void setThumbnailAlpha(float alpha) {
        setConfig("thumbnail.alpha", String.valueOf(Math.max(0.0f, Math.min(1.0f, alpha))));
    }
    
    /**
     * Gets the browser background opacity (0.0 to 1.0).
     */
    public static float getBrowserBackgroundOpacity() {
        return Float.parseFloat(getConfig("browser.background.opacity", "1.0"));
    }
    
    /**
     * Sets the browser background opacity (0.0 to 1.0).
     */
    public static void setBrowserBackgroundOpacity(float opacity) {
        setConfig("browser.background.opacity", String.valueOf(Math.max(0.0f, Math.min(1.0f, opacity))));
    }
    
    /**
     * Gets the viewer background opacity (0.0 to 1.0).
     */
    public static float getViewerBackgroundOpacity() {
        return Float.parseFloat(getConfig("viewer.background.opacity", "1.0"));
    }
    
    /**
     * Sets the viewer background opacity (0.0 to 1.0).
     */
    public static void setViewerBackgroundOpacity(float opacity) {
        setConfig("viewer.background.opacity", String.valueOf(Math.max(0.0f, Math.min(1.0f, opacity))));
    }
    
    /**
     * Gets the configuration map.
     */
    public static Map<String, String> getConfig() {
        return new HashMap<>(config);
    }
    
    /**
     * Gets the reading mode (comic, webtoon, or manga).
     * @return The reading mode string, defaults to "webtoon"
     */
    public static String getReadingMode() {
        return getConfig("reading.mode", "webtoon");
    }
    
    /**
     * Sets the reading mode and saves to config.
     * @param mode The reading mode: "comic", "webtoon", or "manga"
     */
    public static void setReadingMode(String mode) {
        if ("comic".equals(mode) || "webtoon".equals(mode) || "manga".equals(mode)) {
            setConfig("reading.mode", mode);
        }
    }
    
    /**
     * Checks if webtoon mode is currently enabled.
     * @return true if reading mode is "webtoon"
     */
    public static boolean isWebtoonMode() {
        return "webtoon".equals(getReadingMode());
    }
    
    /**
     * Checks if manga mode is currently enabled.
     * @return true if reading mode is "manga"
     */
    public static boolean isMangaMode() {
        return "manga".equals(getReadingMode());
    }
    
    /**
     * Gets the config file.
     */
    public static File getConfigFile() {
        return new File(getConfigFilePath());
    }
    
    /**
     * Gets the global webtoon zoom level.
     * @return The zoom scale (0.3 to 1.0), defaults to 1.0
     */
    public static double getWebtoonZoom() {
        String zoomStr = getConfig("webtoon.zoom", "0.5");
        try {
            double zoom = Double.parseDouble(zoomStr);
            return Math.max(0.3, Math.min(1.0, zoom));
        } catch (NumberFormatException e) {
            return 0.5;
        }
    }
    
    /**
     * Sets and saves the global webtoon zoom level.
     * @param zoom The zoom scale (0.3 to 1.0)
     */
    public static void setWebtoonZoom(double zoom) {
        double clampedZoom = Math.max(0.3, Math.min(1.0, zoom));
        setConfig("webtoon.zoom", String.valueOf(clampedZoom));
    }
    
    /**
     * Gets the default viewer zoom level (for both webtoon and standard modes).
     * @return The zoom scale (0.3 to 1.0), defaults to 1.0
     */
    public static double getViewerDefaultZoom() {
        String zoomStr = getConfig("viewer.default.zoom", "1.0");
        try {
            double zoom = Double.parseDouble(zoomStr);
            return Math.max(0.3, Math.min(1.0, zoom));
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }
    
    /**
     * Sets and saves the default viewer zoom level.
     * @param zoom The zoom scale (0.3 to 1.0)
     */
    public static void setViewerDefaultZoom(double zoom) {
        double clampedZoom = Math.max(0.3, Math.min(1.0, zoom));
        setConfig("viewer.default.zoom", String.valueOf(clampedZoom));
    }
    
    /**
     * Gets whether the control panel should be visible by default on startup.
     * @return true if visible (default), false if hidden
     */
    public static boolean getControlPanelDefaultVisible() {
        String value = getConfig("viewer.control.panel.visible", "false");
        return Boolean.parseBoolean(value);
    }
    
    /**
     * Sets and saves the control panel default visibility.
     * @param visible true to show by default, false to hide
     */
    public static void setControlPanelDefaultVisible(boolean visible) {
        setConfig("viewer.control.panel.visible", String.valueOf(visible));
    }
    
    /**
     * Gets the webtoon smooth scroll speed (Timer delay in milliseconds).
     * @return The delay in ms (5 to 50), defaults to 15
     */
    public static int getWebtoonScrollSpeed() {
        String speedStr = getConfig("webtoon.scroll.speed", "15");
        try {
            int speed = Integer.parseInt(speedStr);
            return Math.max(5, Math.min(50, speed));
        } catch (NumberFormatException e) {
            return 15;
        }
    }
    
    /**
     * Sets and saves the webtoon smooth scroll speed.
     * @param speed The Timer delay in milliseconds (5 to 50)
     */
    public static void setWebtoonScrollSpeed(int speed) {
        int clampedSpeed = Math.max(5, Math.min(50, speed));
        setConfig("webtoon.scroll.speed", String.valueOf(clampedSpeed));
    }
    
    /**
     * Gets the webtoon smooth scroll distance (pixels per keypress).
     * @return The scroll distance in pixels (50 to 500), defaults to 150
     */
    public static int getWebtoonScrollDistance() {
        String distStr = getConfig("webtoon.scroll.distance", "150");
        try {
            int distance = Integer.parseInt(distStr);
            return Math.max(50, Math.min(500, distance));
        } catch (NumberFormatException e) {
            return 150;
        }
    }
    
    /**
     * Sets and saves the webtoon smooth scroll distance.
     * @param distance The scroll distance in pixels (50 to 500)
     */
    public static void setWebtoonScrollDistance(int distance) {
        int clampedDistance = Math.max(50, Math.min(500, distance));
        setConfig("webtoon.scroll.distance", String.valueOf(clampedDistance));
    }
    
    // ==================== LIBRARY FILTER METHODS ====================
    
    /**
     * Gets whether to show manual collections in library view.
     * @return true if collections should be shown (default: true)
     */
    public static boolean getFilterCollections() {
        return Boolean.parseBoolean(getConfig("filter.collections", "true"));
    }
    
    /**
     * Sets whether to show manual collections in library view.
     * @param show true to show collections
     */
    public static void setFilterCollections(boolean show) {
        setConfig("filter.collections", String.valueOf(show));
    }
    
    /**
     * Gets whether to show auto-generated series in library view.
     * @return true if series should be shown (default: true)
     */
    public static boolean getFilterSeries() {
        return Boolean.parseBoolean(getConfig("filter.series", "true"));
    }
    
    /**
     * Sets whether to show auto-generated series in library view.
     * @param show true to show series
     */
    public static void setFilterSeries(boolean show) {
        setConfig("filter.series", String.valueOf(show));
    }
    
    /**
     * Gets whether to show individual chapters (comics in collections) in library view.
     * @return true if chapters should be shown (default: true)
     */
    public static boolean getFilterChapters() {
        return Boolean.parseBoolean(getConfig("filter.chapters", "true"));
    }
    
    /**
     * Sets whether to show individual chapters in library view.
     * @param show true to show chapters
     */
    public static void setFilterChapters(boolean show) {
        setConfig("filter.chapters", String.valueOf(show));
    }
    
    /**
     * Gets whether to show standalone comics (not in any collection) in library view.
     * @return true if standalones should be shown (default: true)
     */
    public static boolean getFilterStandalones() {
        return Boolean.parseBoolean(getConfig("filter.standalones", "true"));
    }
    
    /**
     * Sets whether to show standalone comics in library view.
     * @param show true to show standalones
     */
    public static void setFilterStandalones(boolean show) {
        setConfig("filter.standalones", String.valueOf(show));
    }
    
    /**
     * Gets whether auto-tracking of currently reading collections is enabled.
     * @return true if auto-tracking is enabled (default: true)
     */
    public static boolean getAutoTrackReading() {
        return Boolean.parseBoolean(getConfig("auto.track.reading", "true"));
    }
    
    /**
     * Sets whether auto-tracking of currently reading collections is enabled.
     * @param enabled true to enable auto-tracking
     */
    public static void setAutoTrackReading(boolean enabled) {
        setConfig("auto.track.reading", String.valueOf(enabled));
    }
    
    /**
     * Gets whether to show title overlays on thumbnails.
     * @return true if titles should be shown on thumbnails (default: true)
     */
    public static boolean isShowThumbnailTitles() {
        return Boolean.parseBoolean(getConfig("thumbnail.show.titles", "true"));
    }
    
    /**
     * Sets whether to show title overlays on thumbnails.
     * @param show true to show titles on thumbnails
     */
    public static void setShowThumbnailTitles(boolean show) {
        setConfig("thumbnail.show.titles", String.valueOf(show));
    }
    
    /**
     * Handles password authentication and application startup.
     * This method contains the main application entry logic.
     * Password can be enabled/disabled via settings.
     */
    public static void startApplication() {
        // Load config first to ensure password settings are available
        loadConfig();
        
        // Check if password protection is enabled
        if (!isPasswordEnabled()) {
            // Password disabled - launch directly
            ImageIO.scanForPlugins();
            SwingUtilities.invokeLater(() -> {
                ImageBrowserApp app = new ImageBrowserApp();
                app.setVisible(true);
            });
            return;
        }
        
        // Password enabled - ask for password
        String password = JOptionPane.showInputDialog(null, "Enter password:", "Password Required", JOptionPane.PLAIN_MESSAGE);
        
        // Check if user cancelled or closed the dialog (returns null)
        if (password == null) {
            // User cancelled or closed dialog - exit application
            System.exit(0);
        } else if (password.equals(getPassword())) {
            // Correct password - launch the application
            ImageIO.scanForPlugins();
            SwingUtilities.invokeLater(() -> {
                ImageBrowserApp app = new ImageBrowserApp();
                app.setVisible(true);
            });
        } else {
            // Wrong password - exit application
            JOptionPane.showMessageDialog(null, "Incorrect password. Exiting.");
            System.exit(0);
        }
    }
    
    /**
     * Checks if password protection is enabled.
     * @return true if password is enabled
     */
    public static boolean isPasswordEnabled() {
        return Boolean.parseBoolean(getConfig("password.enabled", "false"));
    }
    
    /**
     * Sets whether password protection is enabled.
     * @param enabled true to enable password, false to disable
     */
    public static void setPasswordEnabled(boolean enabled) {
        setConfig("password.enabled", String.valueOf(enabled));
    }
    
    /**
     * Gets the stored password.
     * @return The stored password (empty string by default)
     */
    public static String getPassword() {
        return getConfig("password.value", "");
    }
    
    /**
     * Sets the password.
     * @param password The new password to store
     */
    public static void setPassword(String password) {
        if (password != null) {
            setConfig("password.value", password);
        }
    }
    
    /**
     * Ensures the userData directory exists.
     * Creates it relative to the application base path.
     */
    public static void ensureUserDataDirectory() {
        File userDataDir = new File(getUserDataPath());
        if (!userDataDir.exists()) {
            userDataDir.mkdirs();
            System.out.println("Created userData directory: " + userDataDir.getAbsolutePath());
        }
    }
    
    /**
     * Gets the cache directory path.
     */
    public static String getCacheDirectoryPath() {
        return getUserDataPath() + File.separator + "cache";
    }
    
    /**
     * Ensures the cache directory exists.
     */
    public static void ensureCacheDirectory() {
        File cacheDir = new File(getCacheDirectoryPath());
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
            System.out.println("Created cache directory: " + cacheDir.getAbsolutePath());
        }
    }

    // ==================== WORKSPACE MANAGEMENT ====================

    private static final String WORKSPACES_DIR = "workspaces";
    private static String currentWorkspacePath = null;

    /**
     * Generates a safe workspace folder name from the root directory path.
     * Uses the last folder name plus a short MD5 hash of the full path to avoid collisions.
     * Format: LastFolderName_8f7b3a2e
     * 
     * @param rootDir The absolute path of the library root directory
     * @return A safe folder name for the workspace
     */
    public static String generateWorkspaceFolderName(String rootDir) {
        if (rootDir == null || rootDir.isEmpty()) {
            return "default_workspace";
        }
        
        // Normalize path separators
        String normalizedPath = rootDir.replace("\\", "/");
        
        // Extract the last folder name
        String lastFolderName = "library";
        int lastSlash = normalizedPath.lastIndexOf('/');
        int lastBackslash = normalizedPath.lastIndexOf('\\');
        int lastSep = Math.max(lastSlash, lastBackslash);
        if (lastSep >= 0 && lastSep + 1 < normalizedPath.length()) {
            lastFolderName = normalizedPath.substring(lastSep + 1);
        }
        
        // Clean up the folder name (remove invalid chars)
        lastFolderName = lastFolderName.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (lastFolderName.isEmpty()) {
            lastFolderName = "library";
        }
        
        // Generate short MD5 hash of full path
        String hash = generateShortHash(normalizedPath);
        
        return lastFolderName + "_" + hash;
    }

    /**
     * Generates a short (8 character) MD5 hash of the input string.
     * @param input The string to hash
     * @return 8-character hexadecimal hash
     */
    private static String generateShortHash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(input.getBytes());
            // Convert to hex and take first 8 characters
            BigInteger bigInt = new BigInteger(1, hashBytes);
            String hex = bigInt.toString(16);
            // Pad with zeros if needed
            while (hex.length() < 8) {
                hex = "0" + hex;
            }
            return hex.substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple hash if MD5 not available
            int hash = input.hashCode();
            return String.format("%08x", hash);
        }
    }

    /**
     * Gets the base workspaces directory path.
     */
    public static String getWorkspacesBasePath() {
        return getUserDataPath() + File.separator + WORKSPACES_DIR;
    }

    /**
     * Gets the active workspace path for the current main directory.
     * Creates the directory if it doesn't exist.
     * @return The absolute path to the workspace directory
     */
    public static String getWorkspacePath() {
        if (currentWorkspacePath == null) {
            String rootDir = getMainDirectoryPath();
            if (rootDir == null || rootDir.isEmpty()) {
                return null;
            }
            
            String workspaceName = generateWorkspaceFolderName(rootDir);
            currentWorkspacePath = getWorkspacesBasePath() + File.separator + workspaceName;
            
            // Ensure the workspace directory exists
            File workspaceDir = new File(currentWorkspacePath);
            if (!workspaceDir.exists()) {
                workspaceDir.mkdirs();
                System.out.println("Created workspace directory: " + workspaceDir.getAbsolutePath());
            }
        }
        return currentWorkspacePath;
    }

    /**
     * Gets the workspace-specific cache directory path.
     * @return The absolute path to the workspace cache directory
     */
    public static String getWorkspaceCachePath() {
        String workspacePath = getWorkspacePath();
        if (workspacePath == null) {
            return null;
        }
        return workspacePath + File.separator + "cache";
    }

    /**
     * Ensures the workspace cache directory exists.
     */
    public static void ensureWorkspaceCacheDirectory() {
        String cachePath = getWorkspaceCachePath();
        if (cachePath != null) {
            File cacheDir = new File(cachePath);
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
                System.out.println("Created workspace cache directory: " + cacheDir.getAbsolutePath());
            }
        }
    }

    /**
     * Clears the cached workspace path (called when switching directories).
     * Forces recalculation on next access.
     */
    public static void clearWorkspaceCache() {
        currentWorkspacePath = null;
    }

    // ==================== THEME CONFIGURATION ====================

    /**
     * Theme configuration class for UI colors.
     * Provides centralized color management with persistence.
     */
    public static class ThemeConfig {
        // Color keys
        public static final String BACKGROUND_DARK = "theme.background.dark";
        public static final String BACKGROUND_MEDIUM = "theme.background.medium";
        public static final String BACKGROUND_LIGHT = "theme.background.light";
        public static final String BORDER_COLOR = "theme.border.color";
        public static final String TEXT_PRIMARY = "theme.text.primary";
        public static final String TEXT_SECONDARY = "theme.text.secondary";
        public static final String ACCENT_COLOR = "theme.accent.color";
        public static final String OVERLAY_COLOR = "theme.overlay.color";
        public static final String INPUT_BACKGROUND = "theme.input.background";
        public static final String COLLECTION_AUTO_BORDER = "theme.collection.auto.border";
        public static final String COLLECTION_MANUAL_BORDER = "theme.collection.manual.border";
        public static final String COLLECTION_AUTO_LABEL = "theme.collection.auto.label";
        public static final String COLLECTION_MANUAL_LABEL = "theme.collection.manual.label";
        public static final String RATING_UNRATED = "theme.rating.unrated";
        public static final String RATING_RATED = "theme.rating.rated";
        public static final String PROGRESS_BAR = "theme.progress.bar";
        public static final String PLACEHOLDER_TEXT = "theme.placeholder.text";
        public static final String SUCCESS_COLOR = "theme.success.color";
        public static final String ERROR_COLOR = "theme.error.color";
        public static final String WARNING_COLOR = "theme.warning.color";
        public static final String HIGHLIGHT_COLOR = "theme.highlight.color";
        
        // Default RGB values (matching current hardcoded colors)
        public static final int[] DEFAULT_BACKGROUND_DARK = {30, 30, 30};
        public static final int[] DEFAULT_BACKGROUND_MEDIUM = {50, 50, 50};
        public static final int[] DEFAULT_BACKGROUND_LIGHT = {60, 60, 60};
        public static final int[] DEFAULT_BORDER_COLOR = {80, 80, 80};
        public static final int[] DEFAULT_TEXT_PRIMARY = {255, 255, 255};
        public static final int[] DEFAULT_TEXT_SECONDARY = {211, 211, 211}; // LIGHT_GRAY
        public static final int[] DEFAULT_ACCENT_COLOR = {0, 255, 255}; // CYAN
        public static final int[] DEFAULT_OVERLAY_COLOR = {0, 0, 0, 180}; // RGBA
        public static final int[] DEFAULT_INPUT_BACKGROUND = {50, 50, 50};
        public static final int[] DEFAULT_COLLECTION_AUTO_BORDER = {0, 100, 200};
        public static final int[] DEFAULT_COLLECTION_MANUAL_BORDER = {60, 120, 60};
        public static final int[] DEFAULT_COLLECTION_AUTO_LABEL = {100, 180, 255};
        public static final int[] DEFAULT_COLLECTION_MANUAL_LABEL = {0, 255, 255}; // CYAN
        public static final int[] DEFAULT_RATING_UNRATED = {128, 128, 128}; // GRAY
        public static final int[] DEFAULT_RATING_RATED = {255, 255, 0}; // YELLOW
        public static final int[] DEFAULT_PROGRESS_BAR = {100, 150, 255};
        public static final int[] DEFAULT_PLACEHOLDER_TEXT = {128, 128, 128}; // GRAY
        public static final int[] DEFAULT_SUCCESS_COLOR = {0, 120, 0}; // Green
        public static final int[] DEFAULT_ERROR_COLOR = {150, 0, 0}; // Red
        public static final int[] DEFAULT_WARNING_COLOR = {150, 100, 0}; // Orange
        public static final int[] DEFAULT_HIGHLIGHT_COLOR = {120, 120, 120}; // Gray
    }
    
    /**
     * Gets a theme color as Color object.
     * @param key The color key (e.g., ThemeConfig.BACKGROUND_DARK)
     * @param defaultRgb The default RGB values [r, g, b] or [r, g, b, a]
     * @return Color object
     */
    public static java.awt.Color getThemeColor(String key, int[] defaultRgb) {
        String value = getConfig(key, "");
        if (value.isEmpty()) {
            // Return default color
            if (defaultRgb.length == 4) {
                return new java.awt.Color(defaultRgb[0], defaultRgb[1], defaultRgb[2], defaultRgb[3]);
            }
            return new java.awt.Color(defaultRgb[0], defaultRgb[1], defaultRgb[2]);
        }
        
        // Parse stored value: "r,g,b" or "r,g,b,a"
        String[] parts = value.split(",");
        try {
            int r = Integer.parseInt(parts[0].trim());
            int g = Integer.parseInt(parts[1].trim());
            int b = Integer.parseInt(parts[2].trim());
            if (parts.length == 4) {
                int a = Integer.parseInt(parts[3].trim());
                return new java.awt.Color(r, g, b, a);
            }
            return new java.awt.Color(r, g, b);
        } catch (Exception e) {
            // Return default on parse error
            if (defaultRgb.length == 4) {
                return new java.awt.Color(defaultRgb[0], defaultRgb[1], defaultRgb[2], defaultRgb[3]);
            }
            return new java.awt.Color(defaultRgb[0], defaultRgb[1], defaultRgb[2]);
        }
    }
    
    /**
     * Sets a theme color and saves to config.
     * @param key The color key
     * @param color The color to save
     */
    public static void setThemeColor(String key, java.awt.Color color) {
        String value;
        if (color.getAlpha() < 255) {
            value = color.getRed() + "," + color.getGreen() + "," + color.getBlue() + "," + color.getAlpha();
        } else {
            value = color.getRed() + "," + color.getGreen() + "," + color.getBlue();
        }
        setConfig(key, value);
    }
    
    // Convenience methods for common colors
    public static java.awt.Color getBackgroundDark() {
        return getThemeColor(ThemeConfig.BACKGROUND_DARK, ThemeConfig.DEFAULT_BACKGROUND_DARK);
    }
    
    public static java.awt.Color getBackgroundMedium() {
        return getThemeColor(ThemeConfig.BACKGROUND_MEDIUM, ThemeConfig.DEFAULT_BACKGROUND_MEDIUM);
    }
    
    public static java.awt.Color getBackgroundLight() {
        return getThemeColor(ThemeConfig.BACKGROUND_LIGHT, ThemeConfig.DEFAULT_BACKGROUND_LIGHT);
    }
    
    public static java.awt.Color getBorderColor() {
        return getThemeColor(ThemeConfig.BORDER_COLOR, ThemeConfig.DEFAULT_BORDER_COLOR);
    }
    
    public static java.awt.Color getTextPrimary() {
        return getThemeColor(ThemeConfig.TEXT_PRIMARY, ThemeConfig.DEFAULT_TEXT_PRIMARY);
    }
    
    public static java.awt.Color getTextSecondary() {
        return getThemeColor(ThemeConfig.TEXT_SECONDARY, ThemeConfig.DEFAULT_TEXT_SECONDARY);
    }
    
    public static java.awt.Color getAccentColor() {
        return getThemeColor(ThemeConfig.ACCENT_COLOR, ThemeConfig.DEFAULT_ACCENT_COLOR);
    }
    
    public static java.awt.Color getOverlayColor() {
        return getThemeColor(ThemeConfig.OVERLAY_COLOR, ThemeConfig.DEFAULT_OVERLAY_COLOR);
    }
    
    public static java.awt.Color getInputBackground() {
        return getThemeColor(ThemeConfig.INPUT_BACKGROUND, ThemeConfig.DEFAULT_INPUT_BACKGROUND);
    }
    
    public static java.awt.Color getCollectionAutoBorder() {
        return getThemeColor(ThemeConfig.COLLECTION_AUTO_BORDER, ThemeConfig.DEFAULT_COLLECTION_AUTO_BORDER);
    }
    
    public static java.awt.Color getCollectionManualBorder() {
        return getThemeColor(ThemeConfig.COLLECTION_MANUAL_BORDER, ThemeConfig.DEFAULT_COLLECTION_MANUAL_BORDER);
    }
    
    public static java.awt.Color getCollectionAutoLabel() {
        return getThemeColor(ThemeConfig.COLLECTION_AUTO_LABEL, ThemeConfig.DEFAULT_COLLECTION_AUTO_LABEL);
    }
    
    public static java.awt.Color getCollectionManualLabel() {
        return getThemeColor(ThemeConfig.COLLECTION_MANUAL_LABEL, ThemeConfig.DEFAULT_COLLECTION_MANUAL_LABEL);
    }
    
    public static java.awt.Color getRatingUnrated() {
        return getThemeColor(ThemeConfig.RATING_UNRATED, ThemeConfig.DEFAULT_RATING_UNRATED);
    }
    
    public static java.awt.Color getRatingRated() {
        return getThemeColor(ThemeConfig.RATING_RATED, ThemeConfig.DEFAULT_RATING_RATED);
    }
    
    public static java.awt.Color getProgressBarColor() {
        return getThemeColor(ThemeConfig.PROGRESS_BAR, ThemeConfig.DEFAULT_PROGRESS_BAR);
    }
    
    public static java.awt.Color getPlaceholderText() {
        return getThemeColor(ThemeConfig.PLACEHOLDER_TEXT, ThemeConfig.DEFAULT_PLACEHOLDER_TEXT);
    }
    
    public static java.awt.Color getSuccessColor() {
        return getThemeColor(ThemeConfig.SUCCESS_COLOR, ThemeConfig.DEFAULT_SUCCESS_COLOR);
    }
    
    public static java.awt.Color getErrorColor() {
        return getThemeColor(ThemeConfig.ERROR_COLOR, ThemeConfig.DEFAULT_ERROR_COLOR);
    }
    
    public static java.awt.Color getWarningColor() {
        return getThemeColor(ThemeConfig.WARNING_COLOR, ThemeConfig.DEFAULT_WARNING_COLOR);
    }
    
    /**
     * Resets all theme colors to defaults.
     */
    public static void resetThemeToDefaults() {
        setThemeColor(ThemeConfig.BACKGROUND_DARK, new java.awt.Color(30, 30, 30));
        setThemeColor(ThemeConfig.BACKGROUND_MEDIUM, new java.awt.Color(50, 50, 50));
        setThemeColor(ThemeConfig.BACKGROUND_LIGHT, new java.awt.Color(60, 60, 60));
        setThemeColor(ThemeConfig.BORDER_COLOR, new java.awt.Color(80, 80, 80));
        setThemeColor(ThemeConfig.TEXT_PRIMARY, java.awt.Color.WHITE);
        setThemeColor(ThemeConfig.TEXT_SECONDARY, java.awt.Color.LIGHT_GRAY);
        setThemeColor(ThemeConfig.ACCENT_COLOR, java.awt.Color.CYAN);
        setThemeColor(ThemeConfig.OVERLAY_COLOR, new java.awt.Color(0, 0, 0, 180));
        setThemeColor(ThemeConfig.INPUT_BACKGROUND, new java.awt.Color(50, 50, 50));
        setThemeColor(ThemeConfig.COLLECTION_AUTO_BORDER, new java.awt.Color(0, 100, 200));
        setThemeColor(ThemeConfig.COLLECTION_MANUAL_BORDER, new java.awt.Color(60, 120, 60));
        setThemeColor(ThemeConfig.COLLECTION_AUTO_LABEL, new java.awt.Color(100, 180, 255));
        setThemeColor(ThemeConfig.COLLECTION_MANUAL_LABEL, new java.awt.Color(0, 255, 255));
        setThemeColor(ThemeConfig.RATING_UNRATED, new java.awt.Color(128, 128, 128));
        setThemeColor(ThemeConfig.RATING_RATED, new java.awt.Color(255, 255, 0));
        setThemeColor(ThemeConfig.PROGRESS_BAR, new java.awt.Color(100, 150, 255));
        setThemeColor(ThemeConfig.PLACEHOLDER_TEXT, new java.awt.Color(128, 128, 128));
        setThemeColor(ThemeConfig.SUCCESS_COLOR, new java.awt.Color(0, 120, 0));
        setThemeColor(ThemeConfig.ERROR_COLOR, new java.awt.Color(150, 0, 0));
        setThemeColor(ThemeConfig.WARNING_COLOR, new java.awt.Color(150, 100, 0));
        setThemeColor(ThemeConfig.HIGHLIGHT_COLOR, new java.awt.Color(120, 120, 120));
    }
    
    /**
     * Saves all theme colors to theme.json file.
     */
    public static void saveThemeConfig() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");

        boolean first = true;
        // Save all theme color keys with their current values
        String[] themeKeys = {
            ThemeConfig.BACKGROUND_DARK,
            ThemeConfig.BACKGROUND_MEDIUM,
            ThemeConfig.BACKGROUND_LIGHT,
            ThemeConfig.BORDER_COLOR,
            ThemeConfig.TEXT_PRIMARY,
            ThemeConfig.TEXT_SECONDARY,
            ThemeConfig.ACCENT_COLOR,
            ThemeConfig.OVERLAY_COLOR,
            ThemeConfig.INPUT_BACKGROUND,
            ThemeConfig.COLLECTION_AUTO_BORDER,
            ThemeConfig.COLLECTION_MANUAL_BORDER,
            ThemeConfig.COLLECTION_AUTO_LABEL,
            ThemeConfig.COLLECTION_MANUAL_LABEL,
            ThemeConfig.RATING_UNRATED,
            ThemeConfig.RATING_RATED,
            ThemeConfig.PROGRESS_BAR,
            ThemeConfig.PLACEHOLDER_TEXT,
            ThemeConfig.SUCCESS_COLOR,
            ThemeConfig.ERROR_COLOR,
            ThemeConfig.WARNING_COLOR
        };

        for (String key : themeKeys) {
            String value = getConfig(key, "");
            // Always save the value, even if empty (will use default on load)
            if (!first) json.append(",\n");
            first = false;
            json.append("  \"").append(escapeJson(key)).append("\": \"").append(escapeJson(value)).append("\"");
        }

        json.append("\n}\n");

        try {
            File themeFile = new File(getThemeFilePath());
            if (!themeFile.getParentFile().exists()) {
                themeFile.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(themeFile)) {
                writer.write(json.toString());
            }
            System.out.println("Theme config saved to: " + getThemeFilePath());
        } catch (IOException e) {
            System.err.println("Failed to save theme config: " + e.getMessage());
        }
    }
    
    /**
     * Loads theme colors from theme.json file.
     * If the file doesn't exist or is corrupted, uses defaults.
     */
    public static void loadThemeConfig() {
        File themeFile = new File(getThemeFilePath());
        
        if (!themeFile.exists()) {
            System.out.println("No theme config found, using defaults");
            return;
        }
        
        try (Scanner scanner = new Scanner(themeFile)) {
            StringBuilder content = new StringBuilder();
            while (scanner.hasNextLine()) {
                content.append(scanner.nextLine());
            }

            String json = content.toString().trim();
            
            if (json.startsWith("{") && json.endsWith("}")) {
                json = json.substring(1, json.length() - 1);
                // Use the improved parser that handles commas in values
                parseThemeJsonPairs(json);
            }
            System.out.println("Loaded theme config from JSON: " + themeFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to load theme config: " + e.getMessage());
        }
    }
    
    /**
     * Parses theme JSON key-value pairs, handling commas within quoted strings.
     */
    private static void parseThemeJsonPairs(String json) {
        int i = 0;
        while (i < json.length()) {
            // Skip whitespace
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
                i++;
            }
            if (i >= json.length()) break;
            
            // Find key (quoted string)
            if (json.charAt(i) != '"') {
                i++;
                continue;
            }
            int keyStart = i;
            String key = extractJsonStringAt(json, i);
            i = findNextUnescapedQuote(json, keyStart) + 1;
            
            // Skip whitespace and find colon
            while (i < json.length() && json.charAt(i) != ':') {
                i++;
            }
            if (i >= json.length()) break;
            i++; // skip colon
            
            // Skip whitespace
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
                i++;
            }
            if (i >= json.length()) break;
            
            // Find value (quoted string)
            if (json.charAt(i) != '"') {
                i++;
                continue;
            }
            String value = extractJsonStringAt(json, i);
            i = findNextUnescapedQuote(json, i) + 1;
            
            if (key != null && value != null) {
                // Validate value format: should be "r,g,b" or "r,g,b,a"
                // The value is now unescaped, so check if it matches the pattern
                if (value.matches("\\d+(,\\d+)*")) {
                    config.put(key, value);
                }
            }
        }
    }
    
    /**
     * Reloads theme config from file and notifies listeners.
     */
    public static void refreshTheme() {
        loadThemeConfig();
    }
    
    /**
     * Deletes the theme.json file to clear corrupted data.
     * Next load will use defaults.
     */
    public static void deleteThemeConfig() {
        File themeFile = new File(getThemeFilePath());
        if (themeFile.exists()) {
            if (themeFile.delete()) {
                System.out.println("Deleted theme config file: " + getThemeFilePath());
            } else {
                System.err.println("Failed to delete theme config file");
            }
        } else {
            System.out.println("Theme config file does not exist");
        }
    }
}
