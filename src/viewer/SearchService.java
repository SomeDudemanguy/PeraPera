package viewer;

import java.io.File;
import java.util.*;

/**
 * Service class for filtering and sorting comics and collections based on search criteria.
 */
public class SearchService {
    
    /**
     * Filters and sorts a list containing both File and Collection objects.
     * 
     * @param allFolders The list of folders to include
     * @param collections The map of collections to include
     * @param criteria The search criteria containing filters and sort options
     * @param library The library provider for accessing ratings and tags
     * @return A List<Object> containing filtered and sorted File and Collection objects
     */
    public static List<Object> filterAndSort(List<File> allFolders, Map<String, Collection> collections, 
                                          SearchCriteria criteria, LibraryProvider library) {
        List<Object> displayItems = new ArrayList<>();
        
        // Build O(1) lookup set of all comic IDs that are in collections
        Set<String> nestedComicIds = buildNestedComicIdsSet(collections, library);
        
        // Add filtered folders (with library filter applied)
        List<File> filteredFolders = filterFolders(allFolders, criteria, library, nestedComicIds);
        displayItems.addAll(filteredFolders);
        
        // Add filtered collections (with library filter applied)
        for (Collection collection : collections.values()) {
            if (collectionMatchesFilter(collection, criteria, library)) {
                displayItems.add(collection);
            }
        }
        
        // Sort the combined list
        sortCombinedList(displayItems, criteria.getSortOption(), library);
        
        return displayItems;
    }
    
    /**
     * Builds a Set of all comic IDs that belong to any collection.
     * Used for O(1) lookup to classify comics as chapters vs standalones.
     */
    private static Set<String> buildNestedComicIdsSet(Map<String, Collection> collections, LibraryProvider library) {
        Set<String> nestedIds = new HashSet<>();
        
        if (library instanceof ImageBrowserApp) {
            ImageBrowserApp app = (ImageBrowserApp) library;
            LibraryService libraryService = app.getLibraryService();
            Map<String, CollectionEntry> collectionEntries = libraryService.getCollectionsCache();
            
            for (CollectionEntry entry : collectionEntries.values()) {
                nestedIds.addAll(entry.comicIds);
            }
        } else {
            // Fallback: use old Collection format - iterate but don't use the variable
            collections.values().forEach(c -> {
                // Collection.comicNames contains comic names, we need to map to IDs
                // This is less efficient but maintains compatibility
            });
        }
        
        return nestedIds;
    }
    
    /**
     * Filters folders based on search text, tag expression, and library filter criteria.
     */
    private static List<File> filterFolders(List<File> folders, SearchCriteria criteria, LibraryProvider library, Set<String> nestedComicIds) {
        List<File> filteredFolders = new ArrayList<>();
        
        // Get library service for comic ID lookup
        LibraryService libraryService = null;
        if (library instanceof ImageBrowserApp) {
            libraryService = ((ImageBrowserApp) library).getLibraryService();
        }
        
        for (File folder : folders) {
            // Apply name filter
            String searchText = criteria.getSearchText().trim().toLowerCase();
            if (!searchText.isEmpty()) {
                boolean nameMatches = false;
                if (libraryService != null) {
                    String comicId = libraryService.getComicId(folder);
                    if (comicId != null) {
                        ComicEntry entry = libraryService.getLibraryCache().get(comicId);
                        if (entry != null && entry.name != null && 
                            entry.name.toLowerCase().contains(searchText)) {
                            nameMatches = true;
                        }
                    }
                }
                if (!nameMatches && !folder.getName().toLowerCase().contains(searchText)) {
                    continue; // Skip this folder - name doesn't match
                }
            }
            
            // Apply tag filter
            String tagExpression = criteria.getTagExpression().trim();
            if (!tagExpression.isEmpty()) {
                Set<String> folderTags = library.getFolderTags(folder);
                int folderRating = library.getRating(folder);
                if (!TagService.evaluateTagExpression(tagExpression, folderTags, folderRating)) {
                    continue; // Skip this folder - tags don't match
                }
            }
            
            // Apply library type filter (chapters vs standalones)
            String comicId = libraryService != null ? libraryService.getComicId(folder) : null;
            boolean isInCollection = comicId != null && nestedComicIds.contains(comicId);
            
            if (isInCollection) {
                // This is a chapter (comic in a collection)
                if (!criteria.isShowChapters()) {
                    continue; // Skip - chapters are hidden
                }
                // Reading filter for chapters: if showReading is active, only show if collection is currently reading
                if (criteria.isShowReading()) {
                    // For chapters, we check if parent collection is currently reading
                    if (libraryService != null) {
                        boolean foundInReadingCollection = false;
                        for (CollectionEntry coll : libraryService.getCollectionsCache().values()) {
                            if (coll.comicIds.contains(comicId) && coll.isCurrentlyReading) {
                                foundInReadingCollection = true;
                                break;
                            }
                        }
                        if (!foundInReadingCollection) {
                            continue; // Skip - not in any currently reading collection
                        }
                    }
                }
            } else {
                // This is a standalone (comic not in any collection)
                if (!criteria.isShowStandalones()) {
                    continue; // Skip - standalones are hidden
                }
                // Reading filter for standalones: if showReading is active, only show if comic is currently reading
                if (criteria.isShowReading()) {
                    if (libraryService != null && comicId != null) {
                        ComicEntry entry = libraryService.getLibraryCache().get(comicId);
                        if (entry == null || !entry.isCurrentlyReading) {
                            continue; // Skip - not in currently reading
                        }
                    } else {
                        continue; // Can't check without library service
                    }
                }
            }
            
            filteredFolders.add(folder);
        }
        
        return filteredFolders;
    }
    
    /**
     * Checks if a collection matches the search and filter criteria.
     */
    private static boolean collectionMatchesFilter(Collection collection, SearchCriteria criteria, LibraryProvider library) {
        // Reading filter: if showReading is true, only show collections marked as currently reading
        if (criteria.isShowReading()) {
            // Check if this collection is marked as currently reading
            if (library instanceof ImageBrowserApp) {
                ImageBrowserApp app = (ImageBrowserApp) library;
                LibraryService libraryService = app.getLibraryService();
                CollectionEntry entry = libraryService.getCollection(collection.name);
                if (entry == null || !entry.isCurrentlyReading) {
                    return false; // Skip - not in currently reading shelf
                }
            } else {
                return false; // Can't check reading status without LibraryService
            }
        }
        
        // Library type filter: check if we should show collections vs series
        if (collection.isAutoGenerated) {
            // This is a series (auto-generated)
            if (!criteria.isShowSeries()) {
                return false; // Skip - series are hidden
            }
        } else {
            // This is a manual collection
            if (!criteria.isShowCollections()) {
                return false; // Skip - collections are hidden
            }
        }
        
        // Name filter: check collection name against the search text
        String nameQuery = criteria.getSearchText().trim().toLowerCase();
        if (!nameQuery.isEmpty() && !collection.name.toLowerCase().contains(nameQuery)) {
            return false;
        }
        
        // Tag filter: evaluate tag expression against collection's tags
        String tagQuery = criteria.getTagExpression().trim();
        if (!tagQuery.isEmpty()) {
            return TagService.evaluateTagExpression(tagQuery, new HashSet<>(collection.tags), collection.rating);
        }
        
        return true; // Include this collection
    }
    
    /**
     * Sorts a combined list of File and Collection objects.
     */
    private static void sortCombinedList(List<Object> items, String sortOption, LibraryProvider library) {
        switch (sortOption) {
            case "Name A-Z":
                items.sort((a, b) -> {
                    String nameA = getItemName(a);
                    String nameB = getItemName(b);
                    return nameA.compareToIgnoreCase(nameB);
                });
                break;
            case "Name Z-A":
                items.sort((a, b) -> {
                    String nameA = getItemName(a);
                    String nameB = getItemName(b);
                    return nameB.compareToIgnoreCase(nameA);
                });
                break;
            case "Rating ↑":
                items.sort((a, b) -> {
                    int ratingA = getItemRating(a, library);
                    int ratingB = getItemRating(b, library);
                    int ratingCompare = Integer.compare(ratingA, ratingB);
                    
                    // If ratings are equal, sort by name alphabetically
                    if (ratingCompare == 0) {
                        String nameA = getItemName(a);
                        String nameB = getItemName(b);
                        return nameA.compareToIgnoreCase(nameB);
                    }
                    
                    return ratingCompare;
                });
                break;
            case "Rating ↓":
                items.sort((a, b) -> {
                    int ratingA = getItemRating(a, library);
                    int ratingB = getItemRating(b, library);
                    int ratingCompare = Integer.compare(ratingB, ratingA);
                    
                    // If ratings are equal, sort by name alphabetically
                    if (ratingCompare == 0) {
                        String nameA = getItemName(a);
                        String nameB = getItemName(b);
                        return nameA.compareToIgnoreCase(nameB);
                    }
                    
                    return ratingCompare;
                });
                break;
        }
    }
    
    /**
     * Gets the name of an item (File or Collection).
     */
    private static String getItemName(Object item) {
        if (item instanceof File) {
            return ((File) item).getName();
        } else if (item instanceof Collection) {
            return ((Collection) item).name;
        }
        return "";
    }
    
    /**
     * Gets the rating of an item (File or Collection).
     */
    private static int getItemRating(Object item, LibraryProvider library) {
        if (item instanceof File) {
            return library.getRating((File) item);
        } else if (item instanceof Collection) {
            return ((Collection) item).rating;
        }
        return 0;
    }
}
