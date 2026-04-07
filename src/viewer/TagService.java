package viewer;

import java.io.File;
import java.util.*;

/**
 * Service class for tag-related operations and expression evaluation.
 * Provides advanced tag search functionality with boolean logic operators.
 * Now uses LibraryService for JSON-based tag storage instead of properties files.
 */
public class TagService {
    
    private static LibraryService libraryService;
    
    /**
     * Sets the LibraryService instance for tag operations.
     */
    public static void setLibraryService(LibraryService service) {
        libraryService = service;
    }
    
    /**
     * Initializes the tag service with the LibraryService.
     */
    public static void initialize(LibraryService service) {
        libraryService = service;
    }
    
    /**
     * Adds a new tag to the system.
     */
    public static boolean addTag(String tagName) {
        if (tagName == null || tagName.trim().isEmpty()) {
            return false;
        }
        
        if (libraryService == null) {
            System.err.println("LibraryService not initialized");
            return false;
        }
        
        tagName = tagName.trim();
        Set<String> globalTags = libraryService.getGlobalTags();
        boolean isNew = !globalTags.contains(tagName);
        
        if (isNew) {
            libraryService.addGlobalTag(tagName);
        }
        
        return isNew;
    }
    
    /**
     * Removes a tag from the system.
     */
    public static boolean removeTag(String tagName) {
        if (libraryService == null) {
            System.err.println("LibraryService not initialized");
            return false;
        }
        
        Set<String> globalTags = libraryService.getGlobalTags();
        boolean removed = globalTags.contains(tagName);
        
        if (removed) {
            // Remove tag from global registry
            libraryService.removeGlobalTag(tagName);
            
            // Remove tag from all comics
            for (ComicEntry entry : libraryService.getAllComics()) {
                if (entry.tags != null && entry.tags.contains(tagName)) {
                    entry.tags.remove(tagName);
                }
            }
            // Save library after removing tags from all comics
            libraryService.saveLibrary();
        }
        
        return removed;
    }
    
    /**
     * Gets all available tags.
     */
    public static Set<String> getAllTags() {
        if (libraryService == null) {
            return new HashSet<>();
        }
        return libraryService.getGlobalTags();
    }
    
    /**
     * Gets tags for a specific folder.
     * Includes ghost tags for collections the comic belongs to (series:name).
     */
    public static Set<String> getFolderTags(File folder) {
        if (libraryService == null) {
            return new HashSet<>();
        }
        
        // Get actual tags from ComicEntry
        Set<String> baseTags = libraryService.getComicTagsByFolder(folder);
        
        // Create a new set to avoid modifying the cached entry
        Set<String> combinedTags = new HashSet<>(baseTags);
        
        // Get comic ID for ghost tag generation
        String comicId = libraryService.getComicId(folder);
        if (comicId != null) {
            // Find all collections containing this comic
            List<String> collectionNames = libraryService.getCollectionsContainingComic(comicId);
            
            // Generate ghost tags: series:sanitizedname
            for (String collectionName : collectionNames) {
                String ghostTag = generateGhostTag(collectionName);
                combinedTags.add(ghostTag);
            }
        }
        
        return combinedTags;
    }
    
    /**
     * Generates a ghost tag from a collection name.
     * Format: series:sanitizedname (lowercase, no spaces/special chars)
     * Example: "My Hero Academia" → "series:myheroacademia"
     * 
     * @param collectionName The collection name to sanitize
     * @return The ghost tag string
     */
    private static String generateGhostTag(String collectionName) {
        if (collectionName == null) {
            return "series:unknown";
        }
        
        // Remove all non-alphanumeric characters and convert to lowercase
        String sanitized = collectionName.toLowerCase().replaceAll("[^a-z0-9]", "");
        
        if (sanitized.isEmpty()) {
            sanitized = "unnamed";
        }
        
        return "series:" + sanitized;
    }
    
    /**
     * Sets tags for a specific folder.
     */
    public static void setFolderTags(File folder, Set<String> tagSet) {
        if (libraryService == null) {
            System.err.println("LibraryService not initialized");
            return;
        }
        
        // Add new tags to global registry
        for (String tag : tagSet) {
            if (tag != null && !tag.trim().isEmpty()) {
                libraryService.addGlobalTag(tag.trim());
            }
        }
        
        // Set tags for the comic
        libraryService.setComicTagsByFolder(folder, tagSet);
    }
    
    /**
     * Gets the count of folders that have a specific tag.
     */
    public static int getFolderTagCount(String tagName) {
        if (libraryService == null) {
            return 0;
        }
        
        int count = 0;
        for (ComicEntry entry : libraryService.getAllComics()) {
            if (entry.tags != null && entry.tags.contains(tagName)) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Evaluates a tag expression against a set of folder tags and rating.
     * Supports boolean logic with operators:
     * - ~ for OR operations (lowest precedence)
     * - , for AND operations (medium precedence)
     * - () for grouping and precedence (highest precedence)
     * - - for NOT operations (high precedence, applies to next operand)
     * - r> for rating greater than, r< for rating less than, r= for rating equal
     * 
     * Examples:
     * "action,adventure" - matches folders with both action AND adventure tags
     * "action~adventure" - matches folders with action OR adventure tags
     * "(action,adventure)~comedy" - matches folders with (action AND adventure) OR comedy
     * "-romance" - excludes folders with romance tag
     * "action,-romance" - matches folders with action but without romance
     * "-(romance,drama)" - excludes folders with romance OR drama tags
     * "action,r>70" - matches folders with action tag AND rating greater than 70
     * "r<50,-romance" - matches folders with rating less than 50 AND without romance
     * "r=0" - matches folders with no rating (rating equals 0)
     * 
     * @param expression The tag expression to evaluate
     * @param folderTags The set of tags to evaluate against
     * @param rating The folder's rating (0 if not rated)
     * @return true if the expression matches the folder tags and rating, false otherwise
     */
    public static boolean evaluateTagExpression(String expression, Set<String> folderTags, int rating) {
        if (expression == null || expression.trim().isEmpty()) {
            return true;
        }
        
        expression = expression.trim();
        return evaluateExpression(expression, folderTags, rating);
    }
    
    /**
     * Legacy method for backward compatibility.
     * @deprecated Use evaluateTagExpression(String, Set<String>, int) instead
     */
    @Deprecated
    public static boolean evaluateTagExpression(String expression, Set<String> folderTags) {
        return evaluateTagExpression(expression, folderTags, 0);
    }
    
    /**
     * Recursively evaluates a parsed tag expression using Top-Level Operator Search.
     */
    private static boolean evaluateExpression(String expr, Set<String> folderTags, int rating) {
        expr = expr.trim();
        if (expr.isEmpty()) {
            return true;
        }
        
        // Step 1: Look for top-level OR (~) operator
        int orIndex = findTopLevelOperator(expr, '~');
        if (orIndex != -1) {
            String leftPart = expr.substring(0, orIndex).trim();
            String rightPart = expr.substring(orIndex + 1).trim();
            return evaluateExpression(leftPart, folderTags, rating) || evaluateExpression(rightPart, folderTags, rating);
        }
        
        // Step 2: Look for top-level AND (,) operator
        int andIndex = findTopLevelOperator(expr, ',');
        if (andIndex != -1) {
            String leftPart = expr.substring(0, andIndex).trim();
            String rightPart = expr.substring(andIndex + 1).trim();
            return evaluateExpression(leftPart, folderTags, rating) && evaluateExpression(rightPart, folderTags, rating);
        }
        
        // Step 3: Check for NOT (-) operator at the beginning
        if (expr.startsWith("-")) {
            String notExpr = expr.substring(1).trim();
            return !evaluateExpression(notExpr, folderTags, rating);
        }
        
        // Step 4: Check if wrapped in parentheses
        if (expr.startsWith("(") && expr.endsWith(")")) {
            // Remove outer parentheses and recurse
            String innerExpr = expr.substring(1, expr.length() - 1).trim();
            return evaluateExpression(innerExpr, folderTags, rating);
        }
        
        // Step 5: Base case - single tag or rating expression
        return hasTagOrRating(expr, folderTags, rating);
    }
    
    /**
     * Finds the index of a top-level operator that is not inside any parentheses.
     * Returns -1 if no such operator exists.
     */
    private static int findTopLevelOperator(String expr, char target) {
        int parenCount = 0;
        
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            
            if (c == '(') {
                parenCount++;
            } else if (c == ')') {
                parenCount--;
            } else if (c == target && parenCount == 0) {
                // Found operator at top level (outside all parentheses)
                return i;
            }
        }
        
        return -1; // No top-level operator found
    }
    
    /**
     * Checks if the folder contains the exact tag or matches a rating expression.
     * Uses exact matching to avoid false positives.
     */
    private static boolean hasTagOrRating(String expression, Set<String> folderTags, int rating) {
        if (expression == null || expression.trim().isEmpty()) {
            return false;
        }
        
        String trimmedExpr = expression.trim();
        
        // Check for rating expressions: r>, r<, r=
        if (trimmedExpr.startsWith("r>") || trimmedExpr.startsWith("r<") || trimmedExpr.startsWith("r=")) {
            return evaluateRatingExpression(trimmedExpr, rating);
        }
        
        // Check for special r? expression (unrated)
        if (trimmedExpr.equals("r?")) {
            return rating == 0;
        }
        
        // Otherwise, treat as a tag
        return folderTags.stream()
            .anyMatch(folderTag -> folderTag.equalsIgnoreCase(trimmedExpr));
    }
    
    /**
     * Evaluates rating expressions.
     * r>80: rating greater than 80
     * r<50: rating less than 50  
     * r=0 or r=100: rating equals value
     */
    private static boolean evaluateRatingExpression(String expr, int rating) {
        if (expr.length() < 3) {
            return false; // Invalid expression
        }
        
        String operator = expr.substring(0, 2);
        String valueStr = expr.substring(2);
        
        try {
            int value = Integer.parseInt(valueStr);
            
            switch (operator) {
                case "r>":
                    return rating > value;
                case "r<":
                    return rating < value;
                case "r=":
                    return rating == value;
                default:
                    return false;
            }
        } catch (NumberFormatException e) {
            return false; // Invalid number
        }
    }
}
