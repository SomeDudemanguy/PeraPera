package viewer;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service class for thumbnail generation and caching.
 * Provides asynchronous thumbnail loading with caching support.
 */
public class ThumbnailService {
    
    // Progress listener interface for UI updates
    public interface ProgressListener {
        void onProgress(int current, int total, String message);
        void onComplete();
    }
    
    // Static fields for thumbnail management
    public static final Map<File, ImageIcon> folderThumbnails = new ConcurrentHashMap<>();
    public static final Map<String, ImageIcon> collectionThumbnails = new ConcurrentHashMap<>(); // Cache for series/collection thumbnails
    private static File cacheDir = null; // Lazily initialized to workspace-specific path
    public static int thumbnailSize = 250;
    public static final ExecutorService thumbnailLoader = Executors.newFixedThreadPool(4);
    
    /**
     * Gets the cache directory for thumbnails, initializing it lazily to the workspace-specific path.
     * Creates the directory if it doesn't exist.
     * @return The File object representing the cache directory
     */
    public static File getCacheDir() {
        if (cacheDir == null) {
            String workspaceCachePath = ApplicationService.getWorkspaceCachePath();
            if (workspaceCachePath == null) {
                // No workspace available yet (first launch, no directory configured)
                // Return a temp cache location - will be replaced after directory selection and restart
                String tempPath = ApplicationService.getUserDataPath() + File.separator + "temp" + File.separator + "cache";
                cacheDir = new File(tempPath);
                System.out.println("No workspace available yet (first launch). Using temp cache: " + tempPath);
            } else {
                cacheDir = new File(workspaceCachePath);
            }
            // Ensure directory exists
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
        }
        return cacheDir;
    }

    /**
     * Clears the cached cacheDir reference.
     * Call this when switching directories to force re-initialization on next access.
     */
    public static void clearCacheDir() {
        cacheDir = null;
    }
    
    // Progress tracking (thread-safe)
    private static final AtomicInteger thumbnailsProcessed = new AtomicInteger(0);
    private static final AtomicInteger totalThumbnails = new AtomicInteger(0);
    private static ProgressListener progressListener;
    
    /**
     * Displays thumbnails in the thumbnails panel based on current search criteria and pagination.
     * This method coordinates the display of both comic folders and collections.
     */
    public static void displayThumbnails(JPanel thumbnailsPanel, List<Object> currentItems, 
                                       HeaderPanel headerPanel, Map<String, CollectionEntry> collections,
                                       PaginationService paginationService, LibraryProvider libraryProvider,
                                       java.util.function.Consumer<CollectionEntry> onShowCollection,
                                       Runnable onRefresh, ImageBrowserApp app, SelectionService selectionService) {
        
        thumbnailsPanel.removeAll();
        currentItems.clear();

        // Prepare source folders - use all folders directly
        List<File> sourceFolders = new ArrayList<>(getCachedFolders());
        
        // Create search criteria from current UI state
        boolean[] filterState = app.getFilterState();
        SearchCriteria criteria = new SearchCriteria(
            headerPanel.getSearchText(),
            headerPanel.getTagSearchText(),
            app.getSortOption(),
            paginationService.getCurrentPage(),
            paginationService.getPageSize(),
            filterState[0], // showCollections
            filterState[1], // showSeries
            filterState[2], // showChapters
            filterState[3], // showStandalones
            filterState[4]  // showReading
        );
        
        // Convert CollectionEntry map to old Collection format for SearchService compatibility
        LibraryService searchLibraryService = app.getLibraryService();
        Map<String, Collection> searchOldCollections = convertCollectionsToOld(collections, searchLibraryService);
        
        // Use SearchService to filter and sort both folders and collections
        List<Object> displayItems = SearchService.filterAndSort(sourceFolders, searchOldCollections, criteria, libraryProvider);
        
        // Convert old Collection objects back to CollectionEntry for display
        List<Object> finalDisplayItems = new ArrayList<>();
        LibraryService libraryService = app.getLibraryService();
        Map<String, CollectionEntry> collectionsCache = libraryService.getCollectionsCache();
        
        for (Object item : displayItems) {
            if (item instanceof Collection) {
                Collection oldCollection = (Collection) item;
                CollectionEntry collectionEntry = collectionsCache.get(oldCollection.name);
                if (collectionEntry != null) {
                    finalDisplayItems.add(collectionEntry);
                }
            } else {
                finalDisplayItems.add(item);
            }
        }

        // Note: Don't reset to page 1 here - let the user navigate through pages
        // Page reset only happens when search criteria changes (handled by SearchTimerService)

        // Calculate pagination based on filtered results
        int totalPages = (int) Math.ceil((double) finalDisplayItems.size() / paginationService.getPageSize());
        
        // Ensure currentPage is within valid bounds
        paginationService.ensureValidBounds(totalPages);
        
        int startIndex = (paginationService.getCurrentPage() - 1) * paginationService.getPageSize();
        int endIndex = Math.min(startIndex + paginationService.getPageSize(), finalDisplayItems.size());
        
        // Additional bounds checking
        if (startIndex < 0) startIndex = 0;
        if (startIndex >= finalDisplayItems.size()) {
            // Clear display if page is out of bounds
            thumbnailsPanel.removeAll();
            thumbnailsPanel.revalidate();
            thumbnailsPanel.repaint();
            paginationService.updatePaginationControls(totalPages);
            app.setCurrentFilteredTotalPages(totalPages);
            return;
        }
        
        // Update page label and button states
        paginationService.updatePaginationControls(totalPages);
        
        // Store filtered total pages in the app for pagination button calculations
        app.setCurrentFilteredTotalPages(totalPages);
        
        // Display only current page items
        List<Object> pageItems = finalDisplayItems.subList(startIndex, endIndex);

        for (Object item : pageItems) {
            if (item instanceof File) {
                // Display comic folder
                File folder = (File) item;
                ImageIcon icon = getFolderThumbnail(folder);
                if (icon == null) continue;

                // Get rating and page count from LibraryService (JSON database)
                int rating = 0;
                int pageCount = 0;
                if (libraryProvider instanceof ImageBrowserApp) {
                    ImageBrowserApp browserApp = (ImageBrowserApp) libraryProvider;
                    LibraryService comicLibraryService = browserApp.getLibraryService();
                    String comicId = comicLibraryService.getComicId(folder);
                    if (comicId != null) {
                        ComicEntry entry = comicLibraryService.getLibraryCache().get(comicId);
                        if (entry != null) {
                            rating = entry.rating;
                            pageCount = entry.pageCount;
                        }
                    }
                }
                
                ThumbnailUIHelper.displayComicThumbnail(folder, icon, rating, pageCount, thumbnailsPanel, thumbnailSize, false, libraryProvider, onRefresh);
                currentItems.add(folder);
            } else if (item instanceof CollectionEntry) {
                // Display collection as first-class item
                CollectionEntry collection = (CollectionEntry) item;
                ThumbnailUIHelper.displayCollectionThumbnail(collection, thumbnailsPanel, thumbnailSize, libraryProvider, (CollectionEntry coll) -> {
                    // Show collection dropdown using CollectionService
                    if (libraryProvider instanceof ImageBrowserApp) {
                        ImageBrowserApp browserApp = (ImageBrowserApp) libraryProvider;
                        LibraryService collectionLibraryService = browserApp.getLibraryService();
                        Map<String, CollectionEntry> collectionsMap = collectionLibraryService.getCollectionsCache();
                        // Convert to old Collection format for dropdown (temporary)
                        Collection oldCollection = convertToOldCollection(coll, collectionLibraryService);
                        Map<String, Collection> convertedCollections = convertCollectionsToOld(collectionsMap, collectionLibraryService);
                        new CollectionService(browserApp, convertedCollections).showCollectionDropdown(oldCollection, new JPopupMenu[1]);
                    }
                });
                currentItems.add(collection);
            }
        }

        thumbnailsPanel.revalidate();
        thumbnailsPanel.repaint();

        // Reset selection to first item and ensure it's visible
        if (selectionService != null) {
            // Check if there's a pending selection from page navigation (up/down at page boundaries)
            // If pending selection exists, let applyPendingOrResetSelection handle it
            // Otherwise, reset to top for new views (search, category change, etc.)
            selectionService.applyPendingOrResetSelection(currentItems, thumbnailsPanel);
            
            SwingUtilities.invokeLater(() -> {
                selectionService.updateSelectionHighlight(thumbnailsPanel);
                // Scroll to ensure selected item is visible
                int selectedIdx = selectionService.getSelectedIndex();
                if (selectedIdx >= 0 && selectedIdx < thumbnailsPanel.getComponentCount()) {
                    Component selectedComp = thumbnailsPanel.getComponent(selectedIdx);
                    if (selectedComp != null) {
                        thumbnailsPanel.scrollRectToVisible(selectedComp.getBounds());
                    }
                } else {
                    // Default to top if selection is out of bounds
                    thumbnailsPanel.scrollRectToVisible(new Rectangle(0, 0, 1, 1));
                }
            });
        }

        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (!(focusOwner instanceof JTextComponent)) {
            thumbnailsPanel.requestFocusInWindow();
        }
    }
    
    /**
     * Loads a thumbnail for the given folder asynchronously.
     * If thumbnail is cached and up-to-date, uses cached version.
     * Otherwise generates new thumbnail and caches it.
     * 
     * @param folder The folder to load thumbnail for
     * @param libraryService The library service to access ComicEntry metadata
     * @param onComplete Callback to run when thumbnail is loaded (runs on EDT)
     */
    public static void loadThumbnail(File folder, LibraryService libraryService, Runnable onComplete) {
        thumbnailLoader.submit(() -> {
            // Get comic ID and ComicEntry from LibraryService
            String comicId = libraryService != null ? libraryService.getComicId(folder) : null;
            ComicEntry entry = null;
            if (comicId != null && libraryService != null) {
                Map<String, ComicEntry> cache = libraryService.getLibraryCache();
                entry = cache.get(comicId);
            }
            
            // Check if thumbnail is already processed according to metadata
            if (entry != null && entry.thumbnailProcessed && comicId != null) {
                // Check if custom thumbnail path has changed - if so, we need to regenerate
                boolean needsRegeneration = false;
                File customThumbnailFile = null;
                if (entry.customThumbnailPath != null && !entry.customThumbnailPath.isEmpty()) {
                    customThumbnailFile = new File(entry.customThumbnailPath);
                    // If custom thumbnail file doesn't exist or is different from cache, regenerate
                    if (!customThumbnailFile.exists()) {
                        needsRegeneration = true;
                    }
                }
                
                if (!needsRegeneration) {
                    // Load directly from ID-based cache without opening folder
                    ImageIcon cachedThumbnail = loadThumbnailFromCache(comicId);
                    if (cachedThumbnail != null) {
                        folderThumbnails.put(folder, cachedThumbnail);
                        notifyProgress(); // Track progress for cached thumbnails
                        SwingUtilities.invokeLater(() -> {
                            onComplete.run();
                        });
                        return;
                    }
                }
                // If cache file missing or needs regeneration, fall through to reprocess
            }
            
            // Process thumbnail: open folder, count images, generate thumbnail
            int pageCount = countImagesInFolder(folder);
            
            // Determine which image file to use for thumbnail
            File thumbnailSourceFile = null;
            if (entry != null && entry.customThumbnailPath != null && !entry.customThumbnailPath.isEmpty()) {
                // Use custom thumbnail path if set
                File customFile = new File(entry.customThumbnailPath);
                if (customFile.exists() && customFile.isFile()) {
                    thumbnailSourceFile = customFile;
                }
            }
            
            // Fallback to first image in folder if no custom thumbnail or file doesn't exist
            if (thumbnailSourceFile == null) {
                thumbnailSourceFile = getFirstImageFile(folder);
            }
            
            if (thumbnailSourceFile != null) {
                ImageIcon thumbnail = createThumbnail(thumbnailSourceFile);
                if (thumbnail != null) {
                    // Save to ID-based cache
                    if (comicId != null) {
                        saveThumbnailToCache(comicId, thumbnail);
                    }
                    folderThumbnails.put(folder, thumbnail);
                    
                    // Update ComicEntry with metadata
                    if (entry != null) {
                        entry.pageCount = pageCount;
                        entry.thumbnailProcessed = true;
                        // Persist changes to library.json
                        if (libraryService != null) {
                            libraryService.persistLibrary();
                        }
                    }
                    
                    SwingUtilities.invokeLater(() -> {
                        onComplete.run();
                    });
                }
            }
            
            // Always notify progress, even if thumbnail failed to load
            notifyProgress();
        });
    }
    
    /**
     * Gets the cached thumbnail for a folder.
     * 
     * @param folder The folder to get thumbnail for
     * @return Cached thumbnail or null if not available
     */
    public static ImageIcon getFolderThumbnail(File folder) {
        return folderThumbnails.get(folder);
    }
    
    /**
     * Clears all cached thumbnails and resets the service.
     */
    public static void clearCache() {
        folderThumbnails.clear();
    }
    
    /**
     * Sets the progress listener for thumbnail loading updates.
     * @param listener The listener to receive progress callbacks
     */
    public static void setProgressListener(ProgressListener listener) {
        progressListener = listener;
    }
    
    /**
     * Initializes progress tracking for thumbnail loading.
     * @param total Total number of thumbnails to process
     */
    public static void initializeProgress(int total) {
        totalThumbnails.set(total);
        thumbnailsProcessed.set(0);
    }
    
    /**
     * Notifies the progress listener of thumbnail loading progress.
     * Thread-safe - uses AtomicInteger counters.
     */
    private static void notifyProgress() {
        int current = thumbnailsProcessed.incrementAndGet();
        int total = totalThumbnails.get();
        if (progressListener != null) {
            SwingUtilities.invokeLater(() -> {
                progressListener.onProgress(current, total, 
                    "Loading thumbnails: " + current + "/" + total);
                if (current >= total) {
                    progressListener.onComplete();
                }
            });
        }
    }
    
        
    /**
     * Shows a context menu for folder thumbnails.
     */
    private static void showContextMenu(MouseEvent e, File folder, LibraryProvider libraryProvider) {
        JPopupMenu contextMenu = new JPopupMenu();
        contextMenu.setBackground(ApplicationService.getInputBackground());
        contextMenu.setForeground(ApplicationService.getTextPrimary());
        
        // Get library service and comic ID
        LibraryService libraryService = null;
        String comicId = null;
        ComicEntry comicEntry = null;
        if (libraryProvider instanceof ImageBrowserApp) {
            ImageBrowserApp app = (ImageBrowserApp) libraryProvider;
            libraryService = app.getLibraryService();
            if (libraryService != null) {
                comicId = libraryService.getComicId(folder);
                if (comicId != null) {
                    comicEntry = libraryService.getLibraryCache().get(comicId);
                }
            }
        }
        
        // Toggle Currently Reading (only for standalones not in collections)
        if (libraryService != null && comicEntry != null) {
            // Check if this comic is in any collection
            boolean isInCollection = false;
            for (CollectionEntry coll : libraryService.getCollectionsCache().values()) {
                if (coll.comicIds.contains(comicId)) {
                    isInCollection = true;
                    break;
                }
            }
            
            // Only show toggle for standalone comics (not in collections)
            if (!isInCollection) {
                final ComicEntry finalEntry = comicEntry;
                final LibraryService finalLibraryService = libraryService;
                final ImageBrowserApp finalApp = (ImageBrowserApp) libraryProvider;
                
                JMenuItem toggleReadingItem = new JMenuItem(
                    finalEntry.isCurrentlyReading ? "📖 Remove from Currently Reading" : "📖 Add to Currently Reading"
                );
                toggleReadingItem.setBackground(ApplicationService.getInputBackground());
                toggleReadingItem.setForeground(ApplicationService.getTextPrimary());
                toggleReadingItem.addActionListener(event -> {
                    finalEntry.isCurrentlyReading = !finalEntry.isCurrentlyReading;
                    finalLibraryService.persistLibrary();
                    finalApp.refreshDisplay();
                });
                contextMenu.add(toggleReadingItem);
                contextMenu.addSeparator();
            }
        }
        
        JMenuItem openInExplorerItem = new JMenuItem("Open in File Explorer");
        openInExplorerItem.setBackground(ApplicationService.getInputBackground());
        openInExplorerItem.setForeground(ApplicationService.getTextPrimary());
        openInExplorerItem.addActionListener(event -> {
            if (libraryProvider instanceof ImageBrowserApp) {
                ImageBrowserApp app = (ImageBrowserApp) libraryProvider;
                FileSystemService fileSystemService = new FileSystemService(app, new ArrayList<>(), "");
                fileSystemService.openFolderInExplorerWithSelection(folder);
            }
        });
        
        contextMenu.add(openInExplorerItem);
        
        // Add to Collection option
        if (libraryProvider instanceof ImageBrowserApp) {
            final ImageBrowserApp finalApp = (ImageBrowserApp) libraryProvider;
            
            JMenuItem addToCollectionItem = new JMenuItem("Add to Collection...");
            addToCollectionItem.setBackground(ApplicationService.getInputBackground());
            addToCollectionItem.setForeground(ApplicationService.getTextPrimary());
            addToCollectionItem.addActionListener(event -> {
                DialogService.showAddToCollectionDialog(finalApp, folder, libraryProvider);
            });
            contextMenu.add(addToCollectionItem);
        }
        
        // Change Thumbnail option (only if we have library data)
        if (libraryService != null && comicEntry != null && comicId != null) {
            final LibraryService finalLibraryService = libraryService;
            final ComicEntry finalEntry = comicEntry;
            final String finalComicId = comicId;
            final ImageBrowserApp finalApp = (ImageBrowserApp) libraryProvider;
            
            JMenuItem changeThumbnailItem = new JMenuItem("Change Thumbnail...");
            changeThumbnailItem.setBackground(ApplicationService.getInputBackground());
            changeThumbnailItem.setForeground(ApplicationService.getTextPrimary());
            changeThumbnailItem.addActionListener(event -> {
                changeComicThumbnail(folder, finalComicId, finalEntry, finalLibraryService, finalApp);
            });
            contextMenu.add(changeThumbnailItem);
        }
        
        contextMenu.show(e.getComponent(), e.getX(), e.getY());
    }
    
    /**
     * Shows a context menu for collection thumbnails.
     */
    private static void showCollectionContextMenu(MouseEvent e, CollectionEntry collection, 
            LibraryProvider libraryProvider, java.util.function.Consumer<CollectionEntry> onShowDropdown) {
        JPopupMenu contextMenu = new JPopupMenu();
        contextMenu.setBackground(ApplicationService.getInputBackground());
        contextMenu.setForeground(ApplicationService.getTextPrimary());
        
        // Toggle Currently Reading
        JMenuItem toggleReadingItem = new JMenuItem(
            collection.isCurrentlyReading ? "📖 Remove from Currently Reading" : "📖 Add to Currently Reading"
        );
        toggleReadingItem.setBackground(ApplicationService.getInputBackground());
        toggleReadingItem.setForeground(ApplicationService.getTextPrimary());
        toggleReadingItem.addActionListener(event -> {
            if (libraryProvider instanceof ImageBrowserApp) {
                ImageBrowserApp app = (ImageBrowserApp) libraryProvider;
                LibraryService libraryService = app.getLibraryService();
                if (libraryService != null) {
                    CollectionEntry entry = libraryService.getCollection(collection.name);
                    if (entry != null) {
                        entry.isCurrentlyReading = !entry.isCurrentlyReading;
                        libraryService.saveCollection(entry);
                        app.refreshDisplay();
                    }
                }
            }
        });
        
        // Open Collection (same as left click)
        JMenuItem openItem = new JMenuItem("Open Collection");
        openItem.setBackground(ApplicationService.getInputBackground());
        openItem.setForeground(ApplicationService.getTextPrimary());
        openItem.addActionListener(event -> onShowDropdown.accept(collection));
        
        // Edit Collection
        JMenuItem editItem = new JMenuItem("Edit Collection...");
        editItem.setBackground(ApplicationService.getInputBackground());
        editItem.setForeground(ApplicationService.getTextPrimary());
        editItem.addActionListener(event -> {
            if (libraryProvider instanceof ImageBrowserApp) {
                ImageBrowserApp app = (ImageBrowserApp) libraryProvider;
                LibraryService libraryService = app.getLibraryService();
                // Convert to old Collection format for the edit dialog
                Collection oldCollection = convertToOldCollection(collection, libraryService);
                DialogService.showEditCollectionDialog(app, oldCollection);
            }
        });
        
        contextMenu.add(toggleReadingItem);
        contextMenu.addSeparator();
        contextMenu.add(openItem);
        contextMenu.add(editItem);
        
        // Open in File Explorer option (only for auto-generated collections with folder path)
        if (collection.isAutoGenerated && collection.folderPath != null) {
            JMenuItem openInExplorerItem = new JMenuItem("Open in File Explorer");
            openInExplorerItem.setBackground(ApplicationService.getInputBackground());
            openInExplorerItem.setForeground(ApplicationService.getTextPrimary());
            openInExplorerItem.addActionListener(event -> {
                if (libraryProvider instanceof ImageBrowserApp) {
                    ImageBrowserApp app = (ImageBrowserApp) libraryProvider;
                    FileSystemService fileSystemService = new FileSystemService(app, new ArrayList<>(), "");
                    fileSystemService.openFolderInExplorerWithSelection(new File(collection.folderPath));
                }
            });
            contextMenu.add(openInExplorerItem);
        }
        
        // Change Thumbnail option for collections
        if (libraryProvider instanceof ImageBrowserApp) {
            ImageBrowserApp app = (ImageBrowserApp) libraryProvider;
            LibraryService libraryService = app.getLibraryService();
            if (libraryService != null && !collection.comicIds.isEmpty()) {
                JMenuItem changeThumbnailItem = new JMenuItem("Change Thumbnail...");
                changeThumbnailItem.setBackground(ApplicationService.getInputBackground());
                changeThumbnailItem.setForeground(ApplicationService.getTextPrimary());
                changeThumbnailItem.addActionListener(event -> {
                    changeCollectionThumbnail(collection, libraryService, app);
                });
                contextMenu.add(changeThumbnailItem);
            }
        }
        
        contextMenu.show(e.getComponent(), e.getX(), e.getY());
    }
    
    /**
     * Gets all cached folders.
     * 
     * @return Set of folders with cached thumbnails
     */
    public static java.util.Set<File> getCachedFolders() {
        return folderThumbnails.keySet();
    }
    
    /**
     * Loads thumbnail from ID-based cache file.
     * 
     * @param comicId The comic UUID to load thumbnail for
     * @return Cached thumbnail or null if not available
     */
    private static ImageIcon loadThumbnailFromCache(String comicId) {
        File cacheFile = new File(getCacheDir(), comicId + ".jpg");
        
        // Check if cache exists
        if (!cacheFile.exists()) {
            return null;
        }
        
        try {
            BufferedImage cachedImage = ImageIO.read(cacheFile);
            if (cachedImage != null) {
                return new ImageIcon(cachedImage);
            }
        } catch (Exception e) {
            // Cache corrupted, delete it
            cacheFile.delete();
        }
        
        return null;
    }
    
    /**
     * Saves thumbnail to ID-based cache file.
     * 
     * @param comicId The comic UUID to save thumbnail for
     * @param thumbnail The thumbnail image to cache
     */
    private static void saveThumbnailToCache(String comicId, ImageIcon thumbnail) {
        File cacheFile = new File(getCacheDir(), comicId + ".jpg");
        
        try {
            BufferedImage image = new BufferedImage(
                thumbnail.getIconWidth(), 
                thumbnail.getIconHeight(), 
                BufferedImage.TYPE_INT_RGB
            );
            Graphics2D g2d = image.createGraphics();
            g2d.drawImage(thumbnail.getImage(), 0, 0, null);
            g2d.dispose();
            
            ImageIO.write(image, "jpg", cacheFile);
        } catch (Exception e) {
            System.err.println("Failed to save thumbnail cache for " + comicId + ": " + e.getMessage());
        }
    }

    /**
     * Creates a thumbnail from an image file.
     * Memory optimized: original BufferedImage is nulled after scaling for immediate GC.
     */
    private static ImageIcon createThumbnail(File imageFile) {
        try {
            BufferedImage img = ImageIO.read(imageFile);
            if (img == null) return null;

            int width = img.getWidth();
            int height = img.getHeight();
            int newWidth, newHeight;

            if (width > height) {
                newWidth = thumbnailSize;
                newHeight = (height * thumbnailSize) / width;
            } else {
                newHeight = thumbnailSize;
                newWidth = (width * thumbnailSize) / height;
            }

            Image scaled = img.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
            ImageIcon icon = new ImageIcon(scaled);
            
            // Null the original image to allow immediate garbage collection
            img = null;
            
            return icon;
        } catch (IOException e) {
            return null;
        }
    }
    
    /**
     * Gets the first image file from a folder.
     */
    private static File getFirstImageFile(File folder) {
        File[] files = folder.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || 
                   lower.endsWith(".png") || lower.endsWith(".bmp") || lower.endsWith(".webp");
        });
        
        if (files == null || files.length == 0) {
            return null;
        }
        
        // Sort files and return first one
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return files[0];
    }
    
    /**
     * Counts images in a folder.
     */
    private static int countImagesInFolder(File folder) {
        File[] files = folder.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || 
                   lower.endsWith(".png") || lower.endsWith(".bmp") || lower.endsWith(".webp");
        });
        return files != null ? files.length : 0;
    }
    
    /**
     * Changes the thumbnail for a comic by letting the user select a new image.
     * Stores the path in customThumbnailPath, deletes old cached thumbnail, 
     * generates new thumbnail, and saves to library.
     */
    private static void changeComicThumbnail(File folder, String comicId, ComicEntry entry, 
            LibraryService libraryService, ImageBrowserApp app) {
        // Create file chooser filtered for images
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser(folder);
        chooser.setFileSelectionMode(javax.swing.JFileChooser.FILES_ONLY);
        chooser.setDialogTitle("Select Thumbnail Image");
        chooser.setApproveButtonText("Select as Thumbnail");
        
        // Add image file filter
        javax.swing.filechooser.FileNameExtensionFilter filter = new javax.swing.filechooser.FileNameExtensionFilter(
            "Image Files (*.jpg, *.jpeg, *.png, *.bmp, *.webp)", 
            "jpg", "jpeg", "png", "bmp", "webp");
        chooser.setFileFilter(filter);
        
        int result = chooser.showOpenDialog(app);
        if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            if (selectedFile != null && selectedFile.exists()) {
                // Store the custom thumbnail path
                entry.customThumbnailPath = selectedFile.getAbsolutePath();
                
                // Delete old cached thumbnail
                File oldCacheFile = new File(getCacheDir(), comicId + ".jpg");
                if (oldCacheFile.exists()) {
                    oldCacheFile.delete();
                }
                
                // Generate new thumbnail from selected image
                ImageIcon newThumbnail = createThumbnail(selectedFile);
                if (newThumbnail != null) {
                    // Save to cache
                    saveThumbnailToCache(comicId, newThumbnail);
                    // Update in-memory cache
                    folderThumbnails.put(folder, newThumbnail);
                }
                
                // Save library to persist customThumbnailPath
                libraryService.saveLibrary();
                
                // Refresh display
                app.refreshDisplay();
            }
        }
    }
    
    /**
     * Changes the thumbnail for a collection/series by letting the user select any image file.
     * For Series: Opens chooser in the series' root folder.
     * For Collections: Opens chooser in the main library directory.
     */
    private static void changeCollectionThumbnail(CollectionEntry collection, 
            LibraryService libraryService, ImageBrowserApp app) {
        
        // Determine the starting directory for the file chooser
        File startDir;
        if (collection.isAutoGenerated && collection.folderPath != null && !collection.folderPath.isEmpty()) {
            // For Series: Open in the series' root folder
            startDir = new File(collection.folderPath);
        } else {
            // For Collections: Open in main library directory
            startDir = new File(ApplicationService.getMainDirectoryPath());
        }
        
        // Ensure directory exists, fallback to user home if not
        if (!startDir.exists() || !startDir.isDirectory()) {
            startDir = new File(System.getProperty("user.home"));
        }
        
        // Create file chooser filtered for images
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser(startDir);
        chooser.setFileSelectionMode(javax.swing.JFileChooser.FILES_ONLY);
        chooser.setDialogTitle("Select Thumbnail Image");
        chooser.setApproveButtonText("Select as Thumbnail");
        
        // Add image file filter
        javax.swing.filechooser.FileNameExtensionFilter filter = new javax.swing.filechooser.FileNameExtensionFilter(
            "Image Files (*.jpg, *.jpeg, *.png, *.bmp, *.webp)", 
            "jpg", "jpeg", "png", "bmp", "webp");
        chooser.setFileFilter(filter);
        
        int result = chooser.showOpenDialog(app);
        if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            if (selectedFile != null && selectedFile.exists()) {
                // Normalize path to forward slashes for consistency
                String normalizedPath = selectedFile.getAbsolutePath().replace("\\", "/");
                
                // Defensive check: trim and validate path
                if (normalizedPath != null && !normalizedPath.trim().isEmpty()) {
                    // Store the custom cover path (takes priority)
                    collection.seriesCoverPath = normalizedPath.trim();
                    
                    // Clear selectedThumbnailComicId to ensure manual path takes priority
                    collection.selectedThumbnailComicId = null;
                    
                    // Save collections to persist changes
                    libraryService.saveCollection(collection);
                    
                    // Refresh display to show new thumbnail immediately
                    app.refreshDisplay();
                }
            }
        }
    }
    
    /**
     * Custom thumbnail panel with hover support and proper transparency.
     */
    private static class HoverableThumbnailPanel extends javax.swing.JPanel {
        private boolean isHovered = false;
        
        public HoverableThumbnailPanel(java.awt.LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            float alpha = ApplicationService.getThumbnailAlpha();
            if (isHovered) {
                g.setColor(new java.awt.Color(40, 40, 40, (int)(alpha * 255)));
            } else {
                g.setColor(new java.awt.Color(0, 0, 0, (int)(alpha * 255)));
            }
            g.fillRect(0, 0, getWidth(), getHeight());
            super.paintComponent(g);
        }
        
        public void setHovered(boolean hovered) {
            if (isHovered != hovered) {
                isHovered = hovered;
                repaint();
            }
        }
    }
    
    /**
     * Inner helper class for thumbnail UI operations.
     * Provides helper methods for thumbnail display and formatting.
     */
    public static class ThumbnailUIHelper {
        
        /**
         * Enumeration of display modes for thumbnails.
         */
        public enum DisplayMode {
            GRID, COLLECTION
        }
        
        /**
         * Gets the appropriate thumbnail size for the given display mode.
         */
        public static java.awt.Dimension getThumbnailSize(DisplayMode mode, int baseSize) {
            switch (mode) {
                case GRID:
                    return new java.awt.Dimension(baseSize, baseSize + 20);
                case COLLECTION:
                    return new java.awt.Dimension(baseSize, baseSize + 40);
                default:
                    return new java.awt.Dimension(baseSize, baseSize + 20);
            }
        }
        
        /**
         * Formats thumbnail info text (rating and page count).
         */
        public static String formatThumbnailInfo(int rating, Integer pageCount) {
            String infoText = rating > 0 ? rating + "/100" : "Unrated";
            if (pageCount != null && pageCount > 0) {
                infoText += " • " + pageCount;
            }
            return infoText;
        }
        
        /**
         * Resolves the appropriate thumbnail PATH for a collection (NO I/O - safe for EDT).
         * Returns the path string only - actual loading happens asynchronously.
         * Priority: 1) seriesCoverPath (user-selected custom image), 2) selectedThumbnailComicId, 3) first comic
         * 
         * @return The path to use for thumbnail generation, or null if no valid path found
         */
        public static String resolveCollectionThumbnailPath(CollectionEntry collection, LibraryProvider libraryProvider) {
            if (!(libraryProvider instanceof ImageBrowserApp)) {
                return null;
            }
            
            ImageBrowserApp app = (ImageBrowserApp) libraryProvider;
            LibraryService libraryService = app.getLibraryService();
            Map<String, ComicEntry> libraryCache = libraryService.getLibraryCache();
            
            // Priority 1: Use seriesCoverPath if set (user-selected custom thumbnail image)
            if (collection.seriesCoverPath != null && !collection.seriesCoverPath.isEmpty()) {
                return collection.seriesCoverPath;
            }
            
            // Priority 2: Try to use selected thumbnail comic if specified
            if (collection.selectedThumbnailComicId != null && !collection.selectedThumbnailComicId.isEmpty()) {
                ComicEntry selectedEntry = libraryCache.get(collection.selectedThumbnailComicId);
                if (selectedEntry != null) {
                    return selectedEntry.folderPath;
                }
            }
            
            // Priority 3: Fallback to first comic if selected not found
            if (!collection.comicIds.isEmpty()) {
                String firstComicId = collection.getFirstComicId();
                if (firstComicId != null) {
                    ComicEntry firstEntry = libraryCache.get(firstComicId);
                    if (firstEntry != null) {
                        return firstEntry.folderPath;
                    }
                }
            }
            
            return null;
        }
        
        /**
         * Resolves the appropriate thumbnail for a collection.
         * Priority: 1) seriesCoverPath (user-selected custom image), 2) selectedThumbnailComicId, 3) first comic
         * @deprecated Use resolveCollectionThumbnailPath() + async loading instead to avoid EDT blocking
         */
        @Deprecated
        public static ImageIcon resolveCollectionThumbnail(CollectionEntry collection, LibraryProvider libraryProvider) {
            String path = resolveCollectionThumbnailPath(collection, libraryProvider);
            if (path == null) {
                return null;
            }
            
            File source = new File(path);
            if (source.isFile()) {
                return createThumbnail(source);
            } else if (source.isDirectory()) {
                return ThumbnailService.getFolderThumbnail(source);
            }
            return null;
        }
        
        /**
         * Loads a collection thumbnail asynchronously.
         * Distinguishes between File (custom cover) and Directory (comic folder).
         * Includes UI validity check to skip work if user scrolled past.
         * Uses cache for instant loading like regular comics.
         */
        public static void loadCollectionThumbnail(CollectionEntry collection, String path, 
                                                   LibraryProvider libraryProvider, JLabel thumbLabel,
                                                   int thumbnailSize) {
            // Check cache first for instant loading (like regular comics)
            String cacheKey = collection.name + ":" + (path != null ? path : "");
            ImageIcon cachedThumbnail = ThumbnailService.collectionThumbnails.get(cacheKey);
            if (cachedThumbnail != null) {
                // Use cached thumbnail immediately - no async needed
                thumbLabel.setIcon(cachedThumbnail);
                thumbLabel.setText(null);
                thumbLabel.revalidate();
                thumbLabel.repaint();
                return;
            }
            
            // Set loading placeholder
            SwingUtilities.invokeLater(() -> {
                if (thumbLabel != null && thumbLabel.isShowing()) {
                    thumbLabel.setText("Loading...");
                    thumbLabel.setForeground(ApplicationService.getPlaceholderText());
                }
            });
            
            thumbnailLoader.submit(() -> {
                // Early exit check: if label is no longer showing, skip the work
                if (thumbLabel == null || !thumbLabel.isShowing()) {
                    return;
                }
                
                ImageIcon thumbnail = null;
                
                if (path != null && !path.isEmpty()) {
                    File source = new File(path);
                    // Distinguish between File (custom cover) and Directory (comic folder)
                    if (source.isFile()) {
                        // If it's a direct file path (seriesCoverPath), use createThumbnail
                        thumbnail = createThumbnail(source);
                    } else if (source.isDirectory()) {
                        // If it's a directory, use getFolderThumbnail logic
                        thumbnail = ThumbnailService.getFolderThumbnail(source);
                        if (thumbnail == null) {
                            // Generate thumbnail from first image in folder
                            File firstImage = getFirstImageFile(source);
                            if (firstImage != null) {
                                thumbnail = createThumbnail(firstImage);
                            }
                        }
                    }
                }
                
                // Store in cache for instant future loads
                final ImageIcon finalThumbnail = thumbnail;
                if (finalThumbnail != null) {
                    ThumbnailService.collectionThumbnails.put(cacheKey, finalThumbnail);
                }
                
                SwingUtilities.invokeLater(() -> {
                    // Only update UI if label is still valid and showing
                    if (thumbLabel != null && thumbLabel.isShowing()) {
                        if (finalThumbnail != null) {
                            // Use thumbnail as-is without re-scaling to preserve aspect ratio
                            thumbLabel.setIcon(finalThumbnail);
                            thumbLabel.setText(null); // Clear loading text
                        } else {
                            thumbLabel.setText("No Image");
                            thumbLabel.setForeground(ApplicationService.getPlaceholderText());
                        }
                        thumbLabel.revalidate();
                        thumbLabel.repaint();
                    }
                });
            });
        }
        
        /**
         * Creates a thumbnail panel for a comic folder.
         * Handles grid view layout only.
         */
        public static javax.swing.JPanel createComicThumbnailPanel(File folder, ImageIcon icon, int rating, 
                                                                   int thumbnailSize, boolean isListView, String displayName) {
            DisplayMode mode = DisplayMode.GRID; // Always use grid view
            java.awt.Dimension size = getThumbnailSize(mode, thumbnailSize);
            
            HoverableThumbnailPanel thumbPanel = new HoverableThumbnailPanel(new java.awt.BorderLayout());
        thumbPanel.setPreferredSize(size);
        thumbPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(60, 60, 60), 2));
        
        // Create a custom JLabel that draws the overlay on top of the image
        javax.swing.JLabel thumbLabel = new javax.swing.JLabel(icon) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // Draw title overlay if enabled
                if (ApplicationService.isShowThumbnailTitles() && displayName != null && !displayName.isEmpty()) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    
                    int overlayHeight = 28;
                    int width = getWidth();
                    int height = getHeight();
                    
                    // Draw semi-transparent overlay background
                    g2d.setColor(ApplicationService.getOverlayColor());
                    g2d.fillRect(0, height - overlayHeight, width, overlayHeight);
                    
                    // Draw text
                    g2d.setColor(ApplicationService.getTextPrimary());
                    g2d.setFont(new Font("Arial", Font.BOLD, 11));
                    
                    FontMetrics fm = g2d.getFontMetrics();
                    int textWidth = fm.stringWidth(displayName);
                    int maxWidth = width - 10; // 5px padding each side
                    
                    String textToDraw = displayName;
                    if (textWidth > maxWidth) {
                        String truncated = displayName;
                        while (fm.stringWidth(truncated + "...") > maxWidth && truncated.length() > 0) {
                            truncated = truncated.substring(0, truncated.length() - 1);
                        }
                        textToDraw = truncated + "...";
                    }
                    
                    int textX = (width - fm.stringWidth(textToDraw)) / 2;
                    int textY = height - overlayHeight + (overlayHeight + fm.getAscent()) / 2 - 2;
                    g2d.drawString(textToDraw, textX, textY);
                    
                    g2d.dispose();
                }
            }
        };
        thumbLabel.setOpaque(false);
        thumbPanel.add(thumbLabel, java.awt.BorderLayout.CENTER);
            
        return thumbPanel;
        }
        
        /**
         * Creates a thumbnail panel for a collection.
         * Handles collection-specific styling and layout.
         * Auto-collections get blue border, manual collections get green border.
         */
        public static javax.swing.JPanel createCollectionThumbnailPanel(CollectionEntry collection, ImageIcon icon, 
                                                                  int thumbnailSize, boolean isListView) {
            DisplayMode mode = DisplayMode.GRID; // Always use grid view
            java.awt.Dimension size = getThumbnailSize(mode, thumbnailSize);
            
            HoverableThumbnailPanel thumbPanel = new HoverableThumbnailPanel(new java.awt.BorderLayout());
            thumbPanel.setPreferredSize(size);
            
            // Different border colors for auto vs manual collections
            java.awt.Color borderColor = collection.isAutoGenerated 
                ? ApplicationService.getCollectionAutoBorder()
                : ApplicationService.getCollectionManualBorder();
            thumbPanel.setBorder(javax.swing.BorderFactory.createLineBorder(borderColor, 2));
        
            // Create a custom JLabel that draws the overlay on top of the image
            javax.swing.JLabel thumbLabel = new javax.swing.JLabel(icon != null ? icon : new javax.swing.ImageIcon()) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    // Draw title overlay if enabled
                    if (ApplicationService.isShowThumbnailTitles() && collection.name != null && !collection.name.isEmpty()) {
                        Graphics2D g2d = (Graphics2D) g.create();
                        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        
                        int overlayHeight = 28;
                        int width = getWidth();
                        int height = getHeight();
                        
                        // Draw semi-transparent overlay background at bottom
                        g2d.setColor(ApplicationService.getOverlayColor());
                        g2d.fillRect(0, height - overlayHeight, width, overlayHeight);
                        
                        // Draw text
                        g2d.setColor(ApplicationService.getTextPrimary());
                        g2d.setFont(new Font("Arial", Font.BOLD, 11));
                        
                        FontMetrics fm = g2d.getFontMetrics();
                        int textWidth = fm.stringWidth(collection.name);
                        int maxWidth = width - 10; // 5px padding each side
                        
                        String textToDraw = collection.name;
                        if (textWidth > maxWidth) {
                            String truncated = collection.name;
                            while (fm.stringWidth(truncated + "...") > maxWidth && truncated.length() > 0) {
                                truncated = truncated.substring(0, truncated.length() - 1);
                            }
                            textToDraw = truncated + "...";
                        }
                        
                        int textX = (width - fm.stringWidth(textToDraw)) / 2;
                        int textY = height - overlayHeight + (overlayHeight + fm.getAscent()) / 2 - 2;
                        g2d.drawString(textToDraw, textX, textY);
                        
                        g2d.dispose();
                    }
                }
            };
            thumbLabel.setOpaque(false);
        
            // Create overlay panel for thumbnail with Collection label
            javax.swing.JPanel overlayPanel = new javax.swing.JPanel(new java.awt.BorderLayout());
            overlayPanel.setOpaque(false);
        
            // Different label for auto vs manual collections
            String labelText = collection.isAutoGenerated ? "Series" : "Collection";
            javax.swing.JLabel collectionLabel = new javax.swing.JLabel(labelText, javax.swing.SwingConstants.CENTER);
            collectionLabel.setForeground(collection.isAutoGenerated ? ApplicationService.getCollectionAutoLabel() : ApplicationService.getCollectionManualLabel());
            collectionLabel.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 9));
            overlayPanel.add(collectionLabel, java.awt.BorderLayout.NORTH);
            overlayPanel.add(thumbLabel, java.awt.BorderLayout.CENTER);
        
            thumbPanel.add(overlayPanel, java.awt.BorderLayout.CENTER);
        
            return thumbPanel;
        }
        
        /**
         * Creates a rating label for thumbnails.
         */
        public static javax.swing.JLabel createRatingLabel(int rating, Integer pageCount) {
            String infoText = formatThumbnailInfo(rating, pageCount);
            javax.swing.JLabel ratingLabel = new javax.swing.JLabel(infoText, javax.swing.SwingConstants.CENTER);
            ratingLabel.setForeground(rating > 0 ? ApplicationService.getRatingRated() : ApplicationService.getRatingUnrated());
            ratingLabel.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 9));
            return ratingLabel;
        }
        
        /**
         * Displays a comic thumbnail in the thumbnails panel.
         */
        public static void displayComicThumbnail(File folder, ImageIcon icon, int rating, int pageCount,
                JPanel thumbnailsPanel, int thumbnailSize, boolean isListView, 
                LibraryProvider libraryProvider, Runnable onRefresh) {
            
            // Get comic name from LibraryService if available
            String displayName = folder.getName();
            if (libraryProvider instanceof ImageBrowserApp) {
                ImageBrowserApp app = (ImageBrowserApp) libraryProvider;
                LibraryService library = app.getLibraryService();
                String comicId = library.getComicId(folder);
                if (comicId != null) {
                    ComicEntry entry = library.getLibraryCache().get(comicId);
                    if (entry != null && entry.name != null && !entry.name.trim().isEmpty()) {
                        displayName = entry.name;
                    }
                }
            }
            
            // Create thumbnail panel
            JPanel thumbPanel = createComicThumbnailPanel(folder, icon, rating, thumbnailSize, isListView, displayName);
            
            // Add rating and page count display for grid view
            JLabel ratingLabel = createRatingLabel(rating, pageCount);
            thumbPanel.add(ratingLabel, BorderLayout.SOUTH);

            thumbPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            thumbPanel.setToolTipText(displayName + " (Rating: " + rating + "/100)");

            thumbPanel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        if (libraryProvider instanceof ImageBrowserApp) {
                            ((ImageBrowserApp) libraryProvider).openComicViewer(folder);
                        }
                    } else if (SwingUtilities.isRightMouseButton(e)) {
                        // Right-click to show context menu
                        showContextMenu(e, folder, libraryProvider);
                    }
                }
                @Override
                public void mouseEntered(MouseEvent e) {
                    ((HoverableThumbnailPanel)thumbPanel).setHovered(true);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    ((HoverableThumbnailPanel)thumbPanel).setHovered(false);
                }
            });

            thumbnailsPanel.add(thumbPanel);
        }
        
        /**
         * Displays a collection thumbnail in the thumbnails panel.
         * Uses async loading to prevent EDT blocking.
         */
        public static void displayCollectionThumbnail(CollectionEntry collection, JPanel thumbnailsPanel, 
                int thumbnailSize, LibraryProvider libraryProvider, 
                java.util.function.Consumer<CollectionEntry> onShowDropdown) {
            
            // Resolve just the path (NO I/O - safe for EDT)
            final String thumbnailPath = resolveCollectionThumbnailPath(collection, libraryProvider);
            
            // Create placeholder panel initially (will be updated async)
            JPanel thumbPanel = createCollectionThumbnailPanel(collection, null, thumbnailSize, false);
            
            // Store reference to the overlay panel which contains the thumbLabel
            final JPanel overlayPanel = (JPanel) ((JPanel)thumbPanel).getComponent(0);
            // The thumbLabel is at CENTER position in overlayPanel (index 1 after NORTH)
            final JLabel thumbLabel = (JLabel) overlayPanel.getComponent(1);

            // Add collection info at bottom (same format as comics)
            String infoText = "";
            if (collection.rating > 0) {
                infoText += collection.rating + "/100";
            }
            if (!collection.comicIds.isEmpty()) {
                if (!infoText.isEmpty()) infoText += " • ";
                infoText += collection.getComicCount() + " Titles";
            }
            JLabel infoLabel = new JLabel(infoText, SwingConstants.CENTER);
            infoLabel.setForeground(ApplicationService.getAccentColor());
            infoLabel.setFont(new Font("Arial", Font.BOLD, 9));
            thumbPanel.add(infoLabel, BorderLayout.SOUTH);

            thumbPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            thumbPanel.setToolTipText("Collection: " + collection.name + " (Click for dropdown menu)");

            thumbPanel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        onShowDropdown.accept(collection);
                    } else if (SwingUtilities.isRightMouseButton(e)) {
                        showCollectionContextMenu(e, collection, libraryProvider, onShowDropdown);
                    }
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    ((HoverableThumbnailPanel)thumbPanel).setHovered(true);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    ((HoverableThumbnailPanel)thumbPanel).setHovered(false);
                }
            });

            thumbnailsPanel.add(thumbPanel);
            
            // Load thumbnail asynchronously (may involve I/O)
            if (thumbnailPath != null) {
                loadCollectionThumbnail(collection, thumbnailPath, libraryProvider, thumbLabel, thumbnailSize);
            }
        }
    }
    
    /**
     * Converts CollectionEntry to old Collection format for compatibility.
     */
    public static Collection convertToOldCollection(CollectionEntry entry, LibraryService libraryService) {
        Collection oldCollection = new Collection(entry.name);
        oldCollection.rating = entry.rating;
        oldCollection.tags.addAll(entry.tags);
        oldCollection.selectedThumbnailComic = entry.selectedThumbnailComicId;
        oldCollection.isAutoGenerated = entry.isAutoGenerated;
        oldCollection.folderPath = entry.folderPath;
        
        // Convert comic IDs to comic names
        Map<String, ComicEntry> libraryCache = libraryService.getLibraryCache();
        for (String comicId : entry.comicIds) {
            ComicEntry comicEntry = libraryCache.get(comicId);
            if (comicEntry != null) {
                oldCollection.comicNames.add(comicEntry.name);
                // Also add the UUID to prevent name collisions
                oldCollection.comicIds.add(comicId);
            }
        }

        return oldCollection;
    }
    
    /**
     * Converts Map of CollectionEntry to Map of old Collection format.
     */
    private static Map<String, Collection> convertCollectionsToOld(Map<String, CollectionEntry> newCollections, LibraryService libraryService) {
        Map<String, Collection> oldCollections = new HashMap<>();
        for (Map.Entry<String, CollectionEntry> entry : newCollections.entrySet()) {
            oldCollections.put(entry.getKey(), convertToOldCollection(entry.getValue(), libraryService));
        }
        return oldCollections;
    }
}
