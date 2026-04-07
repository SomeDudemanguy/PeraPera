package viewer;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple data model representing a comic entry in the JSON library.
 */
class ComicEntry {
    String id;
    String folderPath;
    public String name;
    public int rating = 0;
    public int pageCount = 0;
    public boolean thumbnailProcessed = false;
    public Set<String> tags = new HashSet<>();
    public boolean isCurrentlyReading = false; // Track standalone comics being read
    public String customThumbnailPath = null; // User-selected custom thumbnail path
    
    public ComicEntry(String id, String folderPath) {
        this.id = id;
        this.folderPath = folderPath;
    }

    public ComicEntry() {
    }
    
    /**
     * Returns true if this comic has user-defined metadata that should be persisted.
     */
    public boolean hasUserMetadata() {
        return rating > 0 || !tags.isEmpty() || isCurrentlyReading || customThumbnailPath != null;
    }
}

/**
 * Service class for managing library operations and folder scanning.
 * Handles folder discovery, validation, and library state management using JSON-based indexing.
 */
public class LibraryService {
    
    private Map<String, ComicEntry> libraryCache;  // Key: UUID, Value: ComicEntry
    private Map<String, String> pathToIdMap;     // Key: folderPath, Value: UUID
    private Map<String, CollectionEntry> collectionsCache; // Key: collection name, Value: CollectionEntry
    private Set<String> globalTags; // Global tag registry
    
    private final String libraryFilePath;
    private final String collectionsFilePath;
    private final String tagsFilePath;
    
    /**
     * Constructor - initializes the library service.
     * Performs first-run migration if old files exist in root userData.
     */
    public LibraryService() {
        // Build file paths relative to workspace directory (workspace-specific)
        String workspacePath = ApplicationService.getWorkspacePath();
        
        if (workspacePath == null) {
            // No workspace available yet (first launch, no directory configured)
            // Use placeholder paths - actual initialization will happen after directory selection and restart
            String tempPath = ApplicationService.getUserDataPath() + File.separator + "temp";
            this.libraryFilePath = tempPath + File.separator + "library.json";
            this.collectionsFilePath = tempPath + File.separator + "collections.json";
            this.tagsFilePath = tempPath + File.separator + "tags.json";
            
            System.out.println("No workspace available yet (first launch). LibraryService initialized with temp paths.");
            System.out.println("Please select a library folder. App will restart and use workspace-specific paths.");
        } else {
            this.libraryFilePath = workspacePath + File.separator + "library.json";
            this.collectionsFilePath = workspacePath + File.separator + "collections.json";
            this.tagsFilePath = workspacePath + File.separator + "tags.json";
            
            System.out.println("Using workspace path: " + workspacePath);
            
            // First-run migration: copy existing files from root userData to workspace
            migrateLegacyFilesIfNeeded(workspacePath);
        }
        
        this.libraryCache = new ConcurrentHashMap<>();
        this.pathToIdMap = new ConcurrentHashMap<>();
        this.collectionsCache = new ConcurrentHashMap<>();
        this.globalTags = ConcurrentHashMap.newKeySet();
        
        // Only load if workspace is available
        if (workspacePath != null) {
            // Absolute Path Logging
            File libraryFile = new File(libraryFilePath);
            System.out.println("LIBRARY LOCATION: " + libraryFile.getAbsolutePath());
            System.out.println("FILE EXISTS? " + libraryFile.exists() + " | CAN READ? " + libraryFile.canRead());
            
            loadLibrary();
            performLibrarySanityCheck();
            loadCollections();
            loadGlobalTags();
        }
    }
    
    /**
     * First-run migration: copies legacy JSON files from root userData to workspace.
     * Only runs if workspace files don't exist but root files do.
     * @param workspacePath The target workspace directory path
     */
    private void migrateLegacyFilesIfNeeded(String workspacePath) {
        String userDataPath = ApplicationService.getUserDataPath();
        
        String[] filesToMigrate = {"library.json", "collections.json", "tags.json"};
        boolean anyMigrated = false;
        
        for (String filename : filesToMigrate) {
            File legacyFile = new File(userDataPath, filename);
            File workspaceFile = new File(workspacePath, filename);
            
            if (legacyFile.exists() && !workspaceFile.exists()) {
                try {
                    // Ensure workspace directory exists
                    File workspaceDir = new File(workspacePath);
                    if (!workspaceDir.exists()) {
                        workspaceDir.mkdirs();
                    }
                    
                    // Copy file
                    java.nio.file.Files.copy(
                        legacyFile.toPath(), 
                        workspaceFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    );
                    System.out.println("MIGRATED: " + filename + " from userData root to workspace");
                    anyMigrated = true;
                } catch (IOException e) {
                    System.err.println("Failed to migrate " + filename + ": " + e.getMessage());
                }
            }
        }
        
        if (anyMigrated) {
            System.out.println("First-run migration completed. Legacy files in userData root will be ignored.");
        }
    }
    
    /**
     * Loads library.json from app's root directory into memory cache.
     */
    private void loadLibrary() {
        File libraryFile = new File(libraryFilePath);
        if (!libraryFile.exists()) {
            System.out.println("Library file not found, initializing empty cache");
            return;
        }
        
        try (Scanner scanner = new Scanner(libraryFile)) {
            System.out.println(">>> STARTING LIBRARY LOAD FROM: " + libraryFile.getAbsolutePath());
            int lineCount = 0;
            int entriesParsed = 0;
            String line;
            String currentId = null;
            String currentIdValue = null;
            String currentPath = null;
            ComicEntry currentEntry = null;
            boolean inEntry = false;
            
            while (scanner.hasNextLine()) {
                line = scanner.nextLine().trim();
                lineCount++;
                
                // Header/Footer Check - only skip braces if not in an entry
                if (!inEntry && (line.equals("{") || line.equals("}"))) {
                    continue;
                }
                
                // Skip empty lines
                if (line.isEmpty()) continue;
                
                try {
                    // Entry start - look for UUID key pattern
                    if (line.matches("\"[0-9a-fA-F-]{36}\"\\s*:\\s*\\{")) {
                        currentId = line.substring(1, line.indexOf('"', 1)); // Find second quote
                        currentEntry = new ComicEntry();
                        inEntry = true;
                        entriesParsed++;
                        continue;
                    }
                    
                    // Entry end - ensure final entry is saved
                    if (inEntry && (line.startsWith("}") || line.equals("}"))) {
                        if (currentEntry != null && currentEntry.id != null && !currentEntry.id.isEmpty() && currentEntry.folderPath != null && !currentEntry.folderPath.isEmpty()) {
                            if (currentEntry.name == null || currentEntry.name.trim().isEmpty()) {
                                String fp = currentEntry.folderPath;
                                int slash = fp.lastIndexOf('/');
                                int backslash = fp.lastIndexOf('\\');
                                int idx = Math.max(slash, backslash);
                                currentEntry.name = (idx >= 0 && idx + 1 < fp.length()) ? fp.substring(idx + 1) : fp;
                            }
                            libraryCache.put(currentEntry.id, currentEntry);
                            pathToIdMap.put(currentEntry.folderPath, currentEntry.id);
                        } else {
                            System.err.println("DEBUG: Skipping entry - ID: " + currentId + ", idValue: " + currentIdValue + ", path: " + currentPath);
                        }
                        currentId = null;
                        currentIdValue = null;
                        currentPath = null;
                        currentEntry = null;
                        inEntry = false;
                        continue;
                    }
                    
                    // Parse key-value pairs within entry
                    if (inEntry) {
                        if (line.trim().startsWith("\"id\":")) {
                            currentIdValue = extractStringValue(line);
                            if (currentEntry != null) currentEntry.id = currentIdValue;
                        } else if (line.trim().startsWith("\"folderPath\":")) {
                            currentPath = extractStringValue(line).replace("\\", "/");
                            if (currentEntry != null) currentEntry.folderPath = currentPath;
                        }

                        if (line.contains("\"name\":")) {
                            if (currentEntry != null) currentEntry.name = extractStringValue(line);
                        }

                        if (line.contains("\"rating\":")) {
                            if (currentEntry != null) currentEntry.rating = extractInt(line);
                        }

                        if (line.contains("\"pageCount\":")) {
                            if (currentEntry != null) currentEntry.pageCount = extractInt(line);
                        }

                        if (line.contains("\"thumbnailProcessed\":") && currentEntry != null) {
                            currentEntry.thumbnailProcessed = line.contains("true");
                        }
                        
                        // Parse isCurrentlyReading flag for comics
                        if (line.contains("\"isCurrentlyReading\":") && currentEntry != null) {
                            String isCurrentlyReadingStr = extractNumericValue(line, "isCurrentlyReading");
                            if (isCurrentlyReadingStr != null) {
                                currentEntry.isCurrentlyReading = isCurrentlyReadingStr.contains("true");
                            }
                        }
                        
                        // Parse customThumbnailPath for comics
                        if (line.contains("\"customThumbnailPath\":") && currentEntry != null) {
                            currentEntry.customThumbnailPath = extractStringValue(line);
                        }
                        
                        // Parse tags array: "tags": ["tag1", "tag2"] or "tags": []
                        if (line.contains("\"tags\":") && currentEntry != null) {
                            Set<String> parsedTags = parseTagsArray(line);
                            if (parsedTags != null) {
                                currentEntry.tags = parsedTags;
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("PARSING ERROR on line " + lineCount + ": " + line + " | Error: " + e.getMessage());
                    // Reset state to prevent cascade failures
                    currentId = null;
                    currentIdValue = null;
                    currentPath = null;
                    currentEntry = null;
                    inEntry = false;
                }
            }
            System.out.println(">>> LIBRARY LOAD COMPLETE:");
            System.out.println("    Lines read: " + lineCount);
            System.out.println("    Entries parsed: " + entriesParsed);
            System.out.println("    Successfully loaded: " + libraryCache.size() + " comics into memory.");
        } catch (IOException e) {
            System.err.println(">>> CRITICAL: Failed to load library: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Normalizes path for consistent comparison (handles case sensitivity and slash differences).
     */
    private String normalizePath(String path) {
        return path.toLowerCase().replace('\\', '/');
    }
    private String extractStringValue(String line) {
        // Find the first quote after the field name (e.g., after "folderPath":)
        int colonPos = line.indexOf(':');
        if (colonPos == -1) return "";
        
        int firstQuote = line.indexOf('"', colonPos);
        if (firstQuote == -1) return "";
        
        // Find the matching closing quote, handling escaped quotes
        int secondQuote = firstQuote + 1;
        boolean escaped = false;
        
        while (secondQuote < line.length()) {
            char c = line.charAt(secondQuote);
            if (c == '\\' && !escaped) {
                escaped = true;
            } else if (c == '"' && !escaped) {
                break;
            } else {
                escaped = false;
            }
            secondQuote++;
        }
        
        if (secondQuote >= line.length()) return "";
        
        return line.substring(firstQuote + 1, secondQuote);
    }
    
    /**
     * Extracts string value from JSON data by key.
     */
    private String extractStringValue(String data, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(data);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
    
    /**
     * Extracts numeric value from JSON data by key (for non-string values like rating).
     */
    private String extractNumericValue(String data, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*([^,\\}\\]]+)";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(data);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private int extractInt(String line) {
        try {
            return Integer.parseInt(line.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * Parses a JSON tags array from a line like: "tags": ["action", "romance"] or "tags": []
     */
    private Set<String> parseTagsArray(String line) {
        Set<String> tags = new HashSet<>();
        try {
            // Find the opening bracket
            int openBracket = line.indexOf('[');
            int closeBracket = line.indexOf(']');
            if (openBracket == -1 || closeBracket == -1 || closeBracket <= openBracket) {
                return tags;
            }
            
            String content = line.substring(openBracket + 1, closeBracket).trim();
            if (content.isEmpty()) {
                return tags;
            }
            
            // Parse comma-separated quoted strings
            boolean inQuote = false;
            StringBuilder currentTag = new StringBuilder();
            
            for (int i = 0; i < content.length(); i++) {
                char c = content.charAt(i);
                if (c == '"' && (i == 0 || content.charAt(i - 1) != '\\')) {
                    if (inQuote) {
                        // End of quoted string
                        String tag = currentTag.toString().trim();
                        if (!tag.isEmpty()) {
                            tags.add(tag);
                        }
                        currentTag.setLength(0);
                    }
                    inQuote = !inQuote;
                } else if (inQuote) {
                    currentTag.append(c);
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing tags array: " + e.getMessage());
        }
        return tags;
    }
    
    /**
     * Saves the library cache to library.json file with atomic write.
     * Thread-safe to prevent concurrent modification issues.
     */
    public synchronized void saveLibrary() {
        Path targetPath = Paths.get(libraryFilePath);
        // Use unique temp file per call to avoid conflicts between concurrent saves
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        Path tempPath = Paths.get(libraryFilePath + ".tmp." + uniqueId);

        try {
            // Ensure parent directories exist before writing
            Files.createDirectories(targetPath.getParent());
            
            // Write to temp file
            try (BufferedWriter writer = Files.newBufferedWriter(tempPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                writer.write("{\n");
                boolean first = true;
                for (Map.Entry<String, ComicEntry> entry : libraryCache.entrySet()) {
                    if (!first) {
                        writer.write(",\n");
                    }
                    writer.write(String.format("  \"%s\": {\n", entry.getKey()));
                    writer.write(String.format("    \"id\": \"%s\",\n", escapeJson(entry.getValue().id)));
                    writer.write(String.format("    \"folderPath\": \"%s\",\n", escapeJson(entry.getValue().folderPath.replace("\\", "/"))));
                    writer.write(String.format("    \"name\": \"%s\",\n", escapeJson(entry.getValue().name == null ? "" : entry.getValue().name)));
                    writer.write(String.format("    \"rating\": %d,\n", entry.getValue().rating));
                    writer.write(String.format("    \"pageCount\": %d,\n", entry.getValue().pageCount));
                    writer.write(String.format("    \"thumbnailProcessed\": %s,\n", entry.getValue().thumbnailProcessed ? "true" : "false"));
                    writer.write(String.format("    \"isCurrentlyReading\": %s,\n", entry.getValue().isCurrentlyReading ? "true" : "false"));
                    writer.write(String.format("    \"customThumbnailPath\": \"%s\",\n", escapeJson(entry.getValue().customThumbnailPath != null ? entry.getValue().customThumbnailPath : "")));
                    // Write tags array
                    Set<String> tags = entry.getValue().tags;
                    if (tags == null || tags.isEmpty()) {
                        writer.write("    \"tags\": []\n");
                    } else {
                        writer.write("    \"tags\": [");
                        boolean firstTag = true;
                        for (String tag : tags) {
                            if (!firstTag) writer.write(", ");
                            writer.write("\"" + escapeJson(tag) + "\"");
                            firstTag = false;
                        }
                        writer.write("]\n");
                    }
                    writer.write("  }");
                    first = false;
                }
                writer.write("\n}\n");
            }
        } catch (IOException e) {
            System.err.println("Failed to write temp library file: " + e.toString());
            return;
        }

        IOException lastException = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Library saved successfully with " + libraryCache.size() + " entries");
                return;
            } catch (IOException e) {
                lastException = e;
                if (attempt < 3) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        System.err.println("Failed to save library after retries: " + (lastException == null ? "unknown" : lastException.toString()));
        try {
            Files.deleteIfExists(tempPath);
        } catch (IOException e) {
            System.err.println("Failed to delete temp library file: " + e.toString());
        }
    }

    /**
     * Performs a sanity check on the library cache to detect UUID collisions.
     * Checks if multiple entries point to the same path or if there are other inconsistencies.
     */
    private void performLibrarySanityCheck() {
        Map<String, List<String>> pathToIdsMap = new HashMap<>();
        int collisionCount = 0;

        for (Map.Entry<String, ComicEntry> entry : libraryCache.entrySet()) {
            String id = entry.getKey();
            String path = normalizePath(entry.getValue().folderPath);

            pathToIdsMap.computeIfAbsent(path, k -> new ArrayList<>()).add(id);
        }

        // Check for collisions (multiple IDs pointing to same path)
        for (Map.Entry<String, List<String>> entry : pathToIdsMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                collisionCount++;
                System.err.println("WARNING: UUID COLLISION DETECTED - Path '" + entry.getKey() + "' has " + entry.getValue().size() + " different IDs: " + entry.getValue());
            }
        }

        if (collisionCount > 0) {
            System.err.println("CRITICAL: Found " + collisionCount + " UUID collision(s) in library cache. This may cause incorrect comics to appear in collections.");
            System.err.println("Consider deleting the userData folder and re-scanning the library to fix this issue.");
        } else {
            System.out.println("Library sanity check passed: No UUID collisions detected.");
        }
    }

    /**
     * Escapes special characters in JSON strings.
     */
    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                  .replace("\"", "\\\\\"")
                  .replace("\b", "\\b")
                  .replace("\t", "\\t")
                  .replace("\n", "\\n")
                  .replace("\f", "\\f")
                  .replace("\r", "\\r");
    }
    
    /**
     * Checks if a folder is a comic folder (contains 2+ valid image files).
     * Uses FileSystemService logic for consistency.
     */
    private boolean isComicFolder(File folder) {
        File[] contents = folder.listFiles();
        if (contents == null) return false;
        
        int imageCount = 0;
        for (File file : contents) {
            if (FileSystemService.isValidImageFile(file)) {
                imageCount++;
                if (imageCount >= 2) return true; // Need at least 2 images to be a comic
            }
        }
        return false;
    }
    
    /**
     * Efficient startup sync - performs shallow scan and updates cache.
     * Also detects auto-collections (parent folders containing 2+ comic sub-folders).
     * 
     * @param rootDir The root directory to sync
     */
    public void syncLibrary(File rootDir) {
        System.out.println("=================================================================");
        System.out.println("LIBRARY SYNC STARTING");
        System.out.println("  Cache status: " + libraryCache.size() + " entries loaded from JSON");
        System.out.println("  pathToIdMap: " + pathToIdMap.size() + " entries");
        System.out.println("  Root directory: " + rootDir.getAbsolutePath());
        System.out.println("=================================================================");
        
        // Maps for single-pass auto-collection detection
        Map<String, List<String>> parentToComicIds = new HashMap<>();  // parent path -> comic UUIDs
        Set<String> foldersWithImages = new HashSet<>();  // folders that are comics themselves
        
        // Step A: Get disk state (immediate sub-folders AND nested comic sub-folders for series)
        File[] diskFolders = rootDir.listFiles(File::isDirectory);
        if (diskFolders == null) {
            System.err.println("Cannot list directories in: " + rootDir.getAbsolutePath());
            return;
        }
        
        Set<String> diskPaths = new HashSet<>();
        for (File folder : diskFolders) {
            diskPaths.add(folder.getAbsolutePath());
            
            // Also add nested sub-folders that are comics (for series detection)
            File[] subFolders = folder.listFiles(File::isDirectory);
            if (subFolders != null) {
                for (File subFolder : subFolders) {
                    if (isComicFolder(subFolder)) {
                        diskPaths.add(subFolder.getAbsolutePath());
                    }
                }
            }
        }
        
        // Track if any deletions occurred for performance optimization
        boolean didDeleteOccur = false;
        
        // Step B: Detect deletions (remove entries no longer on disk)
        Iterator<Map.Entry<String, ComicEntry>> iterator = libraryCache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ComicEntry> entry = iterator.next();
            String cachedPath = normalizePath(entry.getValue().folderPath);
            boolean foundOnDisk = false;
            for (String diskPath : diskPaths) {
                if (cachedPath.equals(normalizePath(diskPath))) {
                    foundOnDisk = true;
                    break;
                }
            }
            if (!foundOnDisk) {
                iterator.remove();
                // Also remove from reverse lookup map
                pathToIdMap.remove(entry.getValue().folderPath);
                didDeleteOccur = true;  // Mark that a deletion occurred
                System.out.println("Removed deleted folder from cache: " + entry.getValue().folderPath);
            }
        }
        
        // Step B.5: Clean up orphaned entries in collections and thumbnail cache (only if deletions occurred)
        if (didDeleteOccur) {
            cleanupOrphanedCollectionEntries();
            cleanupOrphanedThumbnails();
        }
        
        // Step C: Handle new/moved folders (single-pass with auto-collection detection)
        for (File folder : diskFolders) {
            String folderPath = folder.getAbsolutePath();
            String normalizedFolderPath = normalizePath(folderPath);
            
            // Check if folder has direct images -> it's a Comic (Conflict Resolution: Comic > Collection)
            boolean hasDirectImages = isComicFolder(folder);
            if (hasDirectImages) {
                foldersWithImages.add(folderPath);
            }
            
            // Check for sub-folders that are comics (only if this folder isn't a comic itself)
            if (!hasDirectImages) {
                File[] subFolders = folder.listFiles(File::isDirectory);
                if (subFolders != null) {
                    for (File subFolder : subFolders) {
                        if (isComicFolder(subFolder)) {
                            // This is a parent folder with comic sub-folders
                            // Get or create UUID for the sub-folder comic
                            String subFolderPath = subFolder.getAbsolutePath();
                            String comicId = pathToIdMap.get(subFolderPath);
                            if (comicId == null) {
                                // New comic sub-folder, process it
                                handleNewFolder(subFolder);
                                comicId = pathToIdMap.get(subFolderPath);
                            }
                            if (comicId != null) {
                                parentToComicIds.computeIfAbsent(folderPath, k -> new ArrayList<>()).add(comicId);
                            }
                        }
                    }
                }
            }
            
            // Check if folder is already in cache (using normalized comparison)
            boolean alreadyCached = false;
            for (Map.Entry<String, ComicEntry> entry : libraryCache.entrySet()) {
                String cachedNormalizedPath = normalizePath(entry.getValue().folderPath);  // Normalize stored path
                if (normalizedFolderPath.equals(cachedNormalizedPath)) {
                    alreadyCached = true;
                    // Update pathToIdMap if needed (in case of case/slash differences)
                    if (!pathToIdMap.containsKey(folderPath)) {
                        pathToIdMap.put(folderPath, entry.getKey());
                    }
                    break;
                }
            }
            
            if (!alreadyCached && hasDirectImages) {
                // Perform deep check only for new folders that are comics
                System.out.println("New folder detected, performing deep check: " + folder.getName());
                handleNewFolder(folder);
            }
        }
        
        // Step D: Create/update auto-collections from parent folders with 2+ comics
        detectAndCreateAutoCollections(parentToComicIds);
        
        // Step 4: Save updated cache
        System.out.println("Saving library with " + libraryCache.size() + " entries...");
        saveLibrary();
        System.out.println("Library sync completed");
    }
    
    /**
     * Handles a new folder - checks for .per_id and creates if missing.
     * Preserves existing metadata (thumbnailProcessed, rating, tags, etc.) if entry already exists.
     * 
     * @param folder The new folder to process
     */
    private void handleNewFolder(File folder) {
        File perIdFile = new File(folder, ".per_id");
        String id;
        
        if (perIdFile.exists()) {
            // Read existing ID
            try {
                id = new String(Files.readAllBytes(perIdFile.toPath())).trim();
                System.out.println("Found existing ID for " + folder.getName() + ": " + id);
            } catch (IOException e) {
                System.err.println("Failed to read .per_id for " + folder.getName() + ": " + e.getMessage());
                id = generateNewId(folder);
            }
        } else {
            // Generate new ID and create .per_id file
            id = generateNewId(folder);
        }
        
        // Check if entry already exists to preserve metadata
        ComicEntry existingEntry = libraryCache.get(id);
        if (existingEntry != null) {
            // Entry exists - just update path maps (preserve all metadata)
            pathToIdMap.put(folder.getAbsolutePath(), id);
            System.out.println("Preserved existing entry for " + folder.getName() + " (thumbnailProcessed=" + existingEntry.thumbnailProcessed + ")");
        } else {
            // New entry - create fresh
            ComicEntry entry = new ComicEntry(id, folder.getAbsolutePath());
            entry.name = folder.getName(); // Set name from folder name
            libraryCache.put(id, entry);
            pathToIdMap.put(folder.getAbsolutePath(), id);
        }
    }
    
    /**
     * Generates a new UUID and creates .per_id file with hidden attribute.
     * 
     * @param folder The folder to create ID for
     * @return Generated UUID string
     */
    private String generateNewId(File folder) {
        String id = UUID.randomUUID().toString();
        File perIdFile = new File(folder, ".per_id");
        
        try {
            Files.write(perIdFile.toPath(), id.getBytes());
            
            // Set hidden attribute on Windows
            try {
                if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                    Files.setAttribute(perIdFile.toPath(), "dos:hidden", true);
                }
            } catch (Exception e) {
                System.out.println("Warning: Could not set hidden attribute on .per_id file");
            }
            
            System.out.println("Created new ID for " + folder.getName() + ": " + id);
        } catch (IOException e) {
            System.err.println("Failed to create .per_id for " + folder.getName() + ": " + e.getMessage());
        }
        
        return id;
    }
    
    /**
     * Gets comic ID from memory cache instantly (O(1) lookup).
     * 
     * @param folder The folder to get ID for
     * @return UUID string or null if not found
     */
    public String getComicId(File folder) {
        return pathToIdMap.get(folder.getAbsolutePath());
    }
    
    /**
     * Persists the current library cache to disk.
     * Called by ThumbnailService after updating ComicEntry metadata.
     */
    public void persistLibrary() {
        saveLibrary();
    }
    
    /**
     * Gets the entire library cache for accessing ComicEntry objects.
     * 
     * @return The library cache map (UUID -> ComicEntry)
     */
    public Map<String, ComicEntry> getLibraryCache() {
        return libraryCache;
    }
    
    /**
     * Gets the number of entries in the library.
     * 
     * @return Number of cached entries
     */
    public int getLibrarySize() {
        return libraryCache.size();
    }
    
    /**
     * Creates or updates auto-collections based on parent folder structure.
     * JSON is the SOURCE OF TRUTH - disk scan only performs delta updates.
     * 
     * If collection exists in JSON:
     *   - Keep existing comicIds order (preserve user sorting)
     *   - Add new comics from disk (append to end)
     *   - Remove comics that no longer exist on disk
     * 
     * If collection doesn't exist:
     *   - Create new auto-collection with disk order
     */
    private void detectAndCreateAutoCollections(Map<String, List<String>> parentToComicIds) {
        int autoCollectionsCreated = 0;
        int autoCollectionsUpdated = 0;
        
        for (Map.Entry<String, List<String>> entry : parentToComicIds.entrySet()) {
            String folderPath = entry.getKey().replace("\\", "/");
            List<String> diskComicIds = entry.getValue();
            
            // Only create collection if 2+ comics
            if (diskComicIds.size() < 2) continue;
            
            // Get folder name as collection name
            File folder = new File(folderPath);
            String collectionName = folder.getName();
            
            // Check if collection already exists in JSON (SOURCE OF TRUTH)
            CollectionEntry existing = collectionsCache.get(collectionName);
            
            if (existing != null) {
                // === JSON EXISTS: Perform Delta Update (JSON is source of truth) ===
                
                // Create set of valid IDs from disk for quick lookup
                Set<String> diskIdsSet = new HashSet<>(diskComicIds);
                
                // Step 1: Remove IDs from JSON that no longer exist on disk
                List<String> removedIds = new ArrayList<>();
                Iterator<String> jsonIterator = existing.comicIds.iterator();
                while (jsonIterator.hasNext()) {
                    String jsonId = jsonIterator.next();
                    if (!diskIdsSet.contains(jsonId)) {
                        jsonIterator.remove();
                        removedIds.add(jsonId);
                    }
                }
                
                // Step 2: Add new IDs from disk that aren't in JSON (append to end)
                List<String> addedIds = new ArrayList<>();
                for (String diskId : diskComicIds) {
                    if (!existing.comicIds.contains(diskId)) {
                        existing.comicIds.add(diskId);
                        addedIds.add(diskId);
                    }
                }
                
                // Update metadata
                existing.isAutoGenerated = true;
                existing.folderPath = folderPath;
                
                if (!removedIds.isEmpty() || !addedIds.isEmpty()) {
                    autoCollectionsUpdated++;
                }
                
            } else {
                // === JSON DOESN'T EXIST: Create new auto-collection from disk ===
                CollectionEntry autoCollection = new CollectionEntry(collectionName);
                autoCollection.isAutoGenerated = true;
                autoCollection.folderPath = folderPath;
                autoCollection.comicIds.addAll(diskComicIds);
                
                collectionsCache.put(collectionName, autoCollection);
                autoCollectionsCreated++;
            }
        }
        
        if (autoCollectionsCreated > 0 || autoCollectionsUpdated > 0) {
            saveCollections();
        }
    }
    
    /**
     * Removes orphaned comic IDs from ALL collections (both auto-generated and user-created).
     * Called after library deletion detection to keep collections in sync with library.
     */
    private void cleanupOrphanedCollectionEntries() {
        int totalRemoved = 0;
        
        for (CollectionEntry collection : collectionsCache.values()) {
            Iterator<String> iterator = collection.comicIds.iterator();
            while (iterator.hasNext()) {
                String comicId = iterator.next();
                if (!libraryCache.containsKey(comicId)) {
                    iterator.remove();
                    totalRemoved++;
                    // Also clean up related metadata
                    collection.readComicIds.remove(comicId);
                    if (collection.selectedThumbnailComicId != null && 
                        collection.selectedThumbnailComicId.equals(comicId)) {
                        collection.selectedThumbnailComicId = collection.getFirstComicId();
                    }
                    if (collection.lastReadComicId != null && 
                        collection.lastReadComicId.equals(comicId)) {
                        collection.lastReadComicId = null;
                    }
                }
            }
        }
        
        if (totalRemoved > 0) {
            System.out.println("Cleaned up " + totalRemoved + " orphaned comic IDs from collections");
            saveCollections();
        }
    }
    
    /**
     * Deletes thumbnail cache files for comics that no longer exist in the library.
     */
    private void cleanupOrphanedThumbnails() {
        File cacheDir = new File(ApplicationService.getWorkspaceCachePath());
        if (cacheDir == null || !cacheDir.exists()) {
            return;
        }
        
        int deletedCount = 0;
        File[] cacheFiles = cacheDir.listFiles((dir, name) -> name.endsWith(".jpg"));
        
        if (cacheFiles != null) {
            for (File cacheFile : cacheFiles) {
                // Extract comicId from filename (remove .jpg extension)
                String comicId = cacheFile.getName().substring(0, cacheFile.getName().length() - 4);
                
                if (!libraryCache.containsKey(comicId)) {
                    if (cacheFile.delete()) {
                        deletedCount++;
                        System.out.println("Deleted orphaned thumbnail cache: " + cacheFile.getName());
                    } else {
                        System.err.println("Failed to delete orphaned thumbnail: " + cacheFile.getName());
                    }
                }
            }
        }
        
        if (deletedCount > 0) {
            System.out.println("Cleaned up " + deletedCount + " orphaned thumbnail cache files");
        }
    }
    
    // ========== COLLECTION MANAGEMENT ==========
    
    /**
     * Loads collections.json from app's root directory into memory cache.
     */
    private void loadCollections() {
        File collectionsFile = new File(collectionsFilePath);
        if (!collectionsFile.exists()) {
            System.out.println("Collections file not found, initializing empty cache");
            return;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(collectionsFile))) {
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
            
            // Parse collections JSON manually
            parseCollectionsJSON(jsonContent.toString());
            System.out.println("Loaded " + collectionsCache.size() + " collections from JSON");
            
        } catch (IOException e) {
            System.err.println("Error loading collections: " + e.getMessage());
        }
    }
    
    /**
     * Manually parses collections JSON content.
     */
    private void parseCollectionsJSON(String jsonContent) {
        collectionsCache.clear();
        
        // Remove outer braces
        jsonContent = jsonContent.trim();
        if (jsonContent.startsWith("{") && jsonContent.endsWith("}")) {
            jsonContent = jsonContent.substring(1, jsonContent.length() - 1);
        }
        
        // Split by collection entries (simplified parsing)
        String[] entries = jsonContent.split("\"[^\"]*\"\\s*:\\s*\\{");
        
        for (int i = 1; i < entries.length; i++) {
            String entry = entries[i];
            if (entry.contains("\"name\"")) {
                // Extract collection name and data
                String[] parts = entry.split("\\}", 2);
                if (parts.length > 0) {
                    String collectionData = parts[0];
                    
                    // Extract name
                    String name = extractStringValue(collectionData, "name");
                    if (name != null) {
                        CollectionEntry collection = new CollectionEntry(name);
                        
                        // Extract rating (numeric value, not string)
                        String ratingStr = extractNumericValue(collectionData, "rating");
                        if (ratingStr != null) {
                            try {
                                collection.rating = Integer.parseInt(ratingStr);
                            } catch (NumberFormatException e) {
                                collection.rating = 0;
                            }
                        }
                        
                        // Extract selected thumbnail
                        collection.selectedThumbnailComicId = extractStringValue(collectionData, "selectedThumbnailComicId");
                        
                        // Extract series cover path (user-selected custom thumbnail) - defensive null/trim check
                        String seriesCoverPath = extractStringValue(collectionData, "seriesCoverPath");
                        collection.seriesCoverPath = (seriesCoverPath != null && !seriesCoverPath.trim().isEmpty()) ? seriesCoverPath.trim() : null;
                        
                        // Extract comic IDs (array)
                        String comicIdsStr = extractArrayValue(collectionData, "comicIds");
                        if (comicIdsStr != null) {
                            String[] ids = comicIdsStr.split(",");
                            for (String id : ids) {
                                id = id.trim().replaceAll("\"", "");
                                if (!id.isEmpty()) {
                                    collection.comicIds.add(id);
                                }
                            }
                        }
                        
                        // Extract tags (array)
                        String tagsStr = extractArrayValue(collectionData, "tags");
                        if (tagsStr != null) {
                            String[] tags = tagsStr.split(",");
                            for (String tag : tags) {
                                tag = tag.trim().replaceAll("\"", "");
                                if (!tag.isEmpty()) {
                                    collection.tags.add(tag);
                                }
                            }
                        }
                        
                        // Extract isAutoGenerated (boolean, optional, defaults false)
                        String isAutoGenStr = extractNumericValue(collectionData, "isAutoGenerated");
                        if (isAutoGenStr != null) {
                            collection.isAutoGenerated = isAutoGenStr.contains("true");
                        }
                        
                        // Extract folderPath (String, optional) - normalize to forward slashes
                        collection.folderPath = extractStringValue(collectionData, "folderPath");
                        if (collection.folderPath != null) {
                            collection.folderPath = collection.folderPath.replace("\\", "/");
                        }
                        
                        // Extract lastReadComicId (String, optional)
                        collection.lastReadComicId = extractStringValue(collectionData, "lastReadComicId");
                        
                        // Extract isManuallySorted (boolean, optional, defaults false)
                        String isManuallySortedStr = extractNumericValue(collectionData, "isManuallySorted");
                        if (isManuallySortedStr != null) {
                            collection.isManuallySorted = isManuallySortedStr.contains("true");
                        }
                        
                        // Extract readComicIds (array of comic IDs marked as read)
                        String readComicIdsStr = extractArrayValue(collectionData, "readComicIds");
                        if (readComicIdsStr != null) {
                            String[] readIds = readComicIdsStr.split(",");
                            for (String id : readIds) {
                                id = id.trim().replaceAll("\"", "");
                                if (!id.isEmpty()) {
                                    collection.readComicIds.add(id);
                                }
                            }
                        }
                        
                        // Extract isCurrentlyReading (boolean, optional, defaults false)
                        String isCurrentlyReadingStr = extractNumericValue(collectionData, "isCurrentlyReading");
                        if (isCurrentlyReadingStr != null) {
                            collection.isCurrentlyReading = isCurrentlyReadingStr.contains("true");
                        }
                        
                        collectionsCache.put(name, collection);
                    }
                }
            }
        }
    }
    
    /**
     * Extracts array value from JSON data.
     */
    private String extractArrayValue(String data, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\\[([^\\]]*)\\]";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(data);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
    
    /**
     * Saves collections to collections.json file.
     * All collections (both auto-generated and user-created) are persisted to ensure data integrity.
     */
    public void saveCollections() {
        try {
            // Build JSON content manually
            StringBuilder json = new StringBuilder();
            json.append("{\n");

            boolean first = true;
            int savedCount = 0;
            for (Map.Entry<String, CollectionEntry> entry : collectionsCache.entrySet()) {
                CollectionEntry collection = entry.getValue();

                if (!first) json.append(",\n");
                first = false;
                savedCount++;
                
                json.append("  \"").append(escapeJson(collection.name)).append("\": {\n");
                json.append("    \"name\": \"").append(escapeJson(collection.name)).append("\",\n");
                json.append("    \"rating\": ").append(collection.rating).append(",\n");
                json.append("    \"selectedThumbnailComicId\": \"").append(collection.selectedThumbnailComicId != null ? escapeJson(collection.selectedThumbnailComicId) : "").append("\",\n");
                json.append("    \"seriesCoverPath\": \"").append(collection.seriesCoverPath != null && !collection.seriesCoverPath.trim().isEmpty() ? escapeJson(collection.seriesCoverPath.trim()) : "").append("\",\n");
                
                // Comic IDs array
                json.append("    \"comicIds\": [");
                boolean firstId = true;
                for (String id : collection.comicIds) {
                    if (!firstId) json.append(", ");
                    firstId = false;
                    json.append("\"").append(id).append("\"");
                }
                json.append("],\n");
                
                // Tags array
                json.append("    \"tags\": [");
                boolean firstTag = true;
                for (String tag : collection.tags) {
                    if (!firstTag) json.append(", ");
                    firstTag = false;
                    json.append("\"").append(escapeJson(tag)).append("\"");
                }
                json.append("],\n");
                
                // New fields for auto-collections
                json.append("    \"isAutoGenerated\": ").append(collection.isAutoGenerated).append(",\n");
                // folderPath: normalize to forward slashes, NO escapeJson to avoid double-escaping
                String normalizedPath = collection.folderPath != null ? collection.folderPath.replace("\\", "/") : "";
                json.append("    \"folderPath\": \"").append(normalizedPath).append("\",\n");
                json.append("    \"lastReadComicId\": \"").append(collection.lastReadComicId != null ? escapeJson(collection.lastReadComicId) : "").append("\",\n");
                json.append("    \"isManuallySorted\": ").append(collection.isManuallySorted).append(",\n");
                
                // Read comic IDs array (comics marked as fully read)
                json.append("    \"readComicIds\": [");
                boolean firstReadId = true;
                for (String id : collection.readComicIds) {
                    if (!firstReadId) json.append(", ");
                    firstReadId = false;
                    json.append("\"").append(id).append("\"");
                }
                json.append("],\n");
                
                // Currently reading flag
                json.append("    \"isCurrentlyReading\": ").append(collection.isCurrentlyReading).append("\n");
                
                json.append("  }");
            }
            
            json.append("\n}\n");
            
            // Ensure parent directories exist before writing
            java.nio.file.Path path = java.nio.file.Paths.get(collectionsFilePath);
            java.nio.file.Files.createDirectories(path.getParent());
            
            // Write to file
            try (FileWriter writer = new FileWriter(collectionsFilePath)) {
                writer.write(json.toString());
            }

            System.out.println("Saved " + savedCount + " collections to JSON (total in cache: " + collectionsCache.size() + ")");
            
        } catch (IOException e) {
            System.err.println("Error saving collections: " + e.getMessage());
        }
    }
    
    /**
     * Gets the collections cache.
     */
    public Map<String, CollectionEntry> getCollectionsCache() {
        return collectionsCache;
    }
    
    /**
     * Gets a specific collection by name.
     */
    public CollectionEntry getCollection(String name) {
        return collectionsCache.get(name);
    }
    
    /**
     * Adds or updates a collection.
     */
    public void saveCollection(CollectionEntry collection) {
        collectionsCache.put(collection.name, collection);
        saveCollections();
    }
    
    /**
     * Removes a collection by name.
     */
    public void removeCollection(String name) {
        collectionsCache.remove(name);
        saveCollections();
    }
    
    /**
     * Deletes a collection by name (alias for removeCollection).
     */
    public void deleteCollection(String name) {
        removeCollection(name);
    }
    
    /**
     * Updates the last read comic for a collection.
     * @param collectionName The name of the collection
     * @param comicId The ID of the comic that was last read (can be null to clear)
     */
    public void updateLastReadComic(String collectionName, String comicId) {
        CollectionEntry collection = collectionsCache.get(collectionName);
        if (collection != null) {
            collection.lastReadComicId = comicId;
            saveCollections();
        }
    }
    
    /**
     * Marks a comic as read in a collection. Auto-clears isCurrentlyReading if all comics are read.
     * @param collectionName The name of the collection
     * @param comicId The ID of the comic to mark as read
     */
    public void markComicAsRead(String collectionName, String comicId) {
        CollectionEntry collection = collectionsCache.get(collectionName);
        if (collection != null && comicId != null) {
            collection.readComicIds.add(comicId);
            // Auto-cleanup: if all comics are now read, remove from currently reading
            if (collection.isCurrentlyReading && collection.readComicIds.size() >= collection.comicIds.size()) {
                collection.isCurrentlyReading = false;
            }
            saveCollections();
        }
    }
    
    /**
     * Marks a comic as unread in a collection.
     * @param collectionName The name of the collection
     * @param comicId The ID of the comic to mark as unread
     */
    public void markComicAsUnread(String collectionName, String comicId) {
        CollectionEntry collection = collectionsCache.get(collectionName);
        if (collection != null && comicId != null) {
            collection.readComicIds.remove(comicId);
            saveCollections();
        }
    }
    
    /**
     * Checks if a comic is marked as read in a collection.
     * @param collectionName The name of the collection
     * @param comicId The ID of the comic to check
     * @return true if the comic is marked as read
     */
    public boolean isComicRead(String collectionName, String comicId) {
        CollectionEntry collection = collectionsCache.get(collectionName);
        if (collection != null && comicId != null) {
            return collection.readComicIds.contains(comicId);
        }
        return false;
    }
    
    /**
     * Toggles the read status of a comic in a collection. Auto-clears isCurrentlyReading if all comics are read.
     * @param collectionName The name of the collection
     * @param comicId The ID of the comic to toggle
     * @return true if the comic is now marked as read, false if unread
     */
    public boolean toggleComicReadStatus(String collectionName, String comicId) {
        CollectionEntry collection = collectionsCache.get(collectionName);
        if (collection != null && comicId != null) {
            if (collection.readComicIds.contains(comicId)) {
                collection.readComicIds.remove(comicId);
                saveCollections();
                return false;
            } else {
                collection.readComicIds.add(comicId);
                // Auto-cleanup: if all comics are now read, remove from currently reading
                if (collection.isCurrentlyReading && collection.readComicIds.size() >= collection.comicIds.size()) {
                    collection.isCurrentlyReading = false;
                }
                saveCollections();
                return true;
            }
        }
        return false;
    }
    
    /**
     * Marks all comics in a collection as read.
     * @param collectionName The name of the collection
     */
    public void markAllAsRead(String collectionName) {
        CollectionEntry collection = collectionsCache.get(collectionName);
        if (collection != null) {
            collection.readComicIds.addAll(collection.comicIds);
            saveCollections();
        }
    }
    
    /**
     * Clears all progress for a collection (both read status and last read).
     * @param collectionName The name of the collection
     */
    public void clearAllProgress(String collectionName) {
        CollectionEntry collection = collectionsCache.get(collectionName);
        if (collection != null) {
            collection.readComicIds.clear();
            collection.lastReadComicId = null;
            saveCollections();
        }
    }
    
    /**
     * Loads global tags from tags.json file.
     */
    private void loadGlobalTags() {
        File tagsFile = new File(tagsFilePath);
        if (!tagsFile.exists()) {
            System.out.println("Global tags file not found, starting with empty registry");
            return;
        }
        
        try (Scanner scanner = new Scanner(tagsFile)) {
            StringBuilder content = new StringBuilder();
            while (scanner.hasNextLine()) {
                content.append(scanner.nextLine());
            }
            
            String json = content.toString().trim();
            // Parse simple JSON array: ["tag1", "tag2"]
            if (json.startsWith("[") && json.endsWith("]")) {
                json = json.substring(1, json.length() - 1);
                String[] parts = json.split(",");
                for (String part : parts) {
                    part = part.trim();
                    if (part.startsWith("\"") && part.endsWith("\"")) {
                        globalTags.add(part.substring(1, part.length() - 1));
                    }
                }
            }
            System.out.println("Loaded " + globalTags.size() + " global tags");
        } catch (IOException e) {
            System.err.println("Failed to load global tags: " + e.getMessage());
        }
    }
    
    /**
     * Saves global tags to tags.json file.
     */
    public void saveGlobalTags() {
        File tagsFile = new File(tagsFilePath);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tagsFile))) {
            writer.write("[\n");
            boolean first = true;
            for (String tag : globalTags) {
                if (!first) {
                    writer.write(",\n");
                }
                writer.write("  \"" + escapeJson(tag) + "\"");
                first = false;
            }
            writer.write("\n]\n");
        } catch (IOException e) {
            System.err.println("Failed to save global tags: " + e.getMessage());
        }
    }
    
    /**
     * Gets the global tags registry.
     */
    public Set<String> getGlobalTags() {
        return new HashSet<>(globalTags);
    }
    
    /**
     * Adds a tag to the global registry.
     */
    public void addGlobalTag(String tag) {
        if (tag != null && !tag.trim().isEmpty()) {
            globalTags.add(tag.trim());
            saveGlobalTags();
        }
    }
    
    /**
     * Removes a tag from the global registry.
     */
    public void removeGlobalTag(String tag) {
        globalTags.remove(tag);
        saveGlobalTags();
    }
    
    /**
     * Gets tags for a comic by folder file.
     */
    public Set<String> getComicTagsByFolder(File folder) {
        String comicId = getComicId(folder);
        if (comicId != null) {
            ComicEntry entry = libraryCache.get(comicId);
            if (entry != null && entry.tags != null) {
                return new HashSet<>(entry.tags);
            }
        }
        return new HashSet<>();
    }
    
    /**
     * Sets tags for a comic by folder file.
     */
    public void setComicTagsByFolder(File folder, Set<String> tags) {
        String comicId = getComicId(folder);
        if (comicId != null) {
            ComicEntry entry = libraryCache.get(comicId);
            if (entry != null) {
                entry.tags = new HashSet<>(tags);
                saveLibrary();
            }
        }
    }
    
    /**
     * Gets a comic entry by its folder name.
     */
    public ComicEntry getComicEntryByFolderName(String folderName) {
        for (ComicEntry entry : libraryCache.values()) {
            File f = new File(entry.folderPath);
            if (f.getName().equals(folderName)) {
                return entry;
            }
        }
        return null;
    }
    
    /**
     * Gets all comic folders from the library cache.
     * This includes sub-folders inside auto-collections.
     * 
     * @return List of File objects representing all comics in the library
     */
    public List<File> getAllComicFolders() {
        List<File> folders = new ArrayList<>();
        for (ComicEntry entry : libraryCache.values()) {
            if (entry.folderPath != null && !entry.folderPath.isEmpty()) {
                File folder = new File(entry.folderPath);
                if (folder.exists()) {
                    folders.add(folder);
                }
            }
        }
        return folders;
    }
    
    /**
     * Gets a comic folder by its name (searches all comics in library).
     * 
     * @param folderName The name of the folder to find
     * @return File object or null if not found
     */
    public File getComicFolderByName(String folderName) {
        for (ComicEntry entry : libraryCache.values()) {
            File folder = new File(entry.folderPath);
            if (folder.getName().equals(folderName)) {
                return folder;
            }
        }
        return null;
    }
    
    /**
     * Gets all comics in the library.
     */
    public List<ComicEntry> getAllComics() {
        return new ArrayList<>(libraryCache.values());
    }
    
    /**
     * Gets all collection names that contain a specific comic ID.
     * Used for ghost tag generation in TagService.
     * 
     * @param comicId The comic UUID to search for
     * @return List of collection names containing this comic
     */
    public List<String> getCollectionsContainingComic(String comicId) {
        List<String> containingCollections = new ArrayList<>();
        if (comicId == null) {
            return containingCollections;
        }
        
        for (CollectionEntry collection : collectionsCache.values()) {
            if (collection.comicIds != null && collection.comicIds.contains(comicId)) {
                containingCollections.add(collection.name);
            }
        }
        return containingCollections;
    }
}
