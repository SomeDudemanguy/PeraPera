package viewer;



import javax.swing.*;

import java.awt.*;

import java.io.*;

import java.util.*;

import java.util.List;

import java.util.concurrent.*;



public class ImageBrowserApp extends JFrame implements LibraryProvider {



    // Service instances

    private CollectionService collectionService;

    private FileSystemService fileSystemService;

    private PaginationService paginationService;

    private SelectionService selectionService;

    private NavigationService navigationService;

    private SearchTimerService searchTimerService;

    private LibraryService libraryService;

    

    // Collection data structures

    private final Map<String, Collection> collections = new ConcurrentHashMap<>();

    

    /**

     * Gets the current display items including both comics and collections.

     */

    public List<Object> getCurrentItems() {

        List<Object> items = new ArrayList<>();

        

        // Add comics from current search results

        items.addAll(currentItems);

        

        // Add collections from LibraryService

        Map<String, CollectionEntry> collectionEntries = libraryService.getCollectionsCache();

        for (CollectionEntry entry : collectionEntries.values()) {

            items.add(entry); // Add CollectionEntry objects directly

        }

        

        return items;

    }



    // UI components - now managed by services

    private final JPanel thumbnailsPanel;

    private final JScrollPane scrollPane;

    private String sortOption; // Will be loaded from config

    

    // Search and rating components - now handled by LibraryService (JSON)

    private HeaderPanel headerPanel;

    

    // Tag system - now handled by LibraryService

    private List<File> allFolders = new ArrayList<>();

    private final List<Object> currentItems = new ArrayList<>();

    private int currentFilteredTotalPages = 1; // Track total pages for filtered results

    private JPanel welcomePanel; // Welcome screen for empty library state

    // Library filter state - persisted via ApplicationService
    private boolean showCollections = true;
    private boolean showSeries = true;
    private boolean showChapters = true;
    private boolean showStandalones = true;
    private boolean showReading = false;

    public ImageBrowserApp() {

        super("PeraPera");



        // Set application icon

        try {

            ImageIcon logoIcon = new ImageIcon("Insignia/Logo.jpg");

            setIconImage(logoIcon.getImage());

        } catch (Exception e) {

            System.err.println("Could not load application icon: " + e.getMessage());

        }



        // Initialize application services

        ApplicationService.ensureUserDataDirectory();

        ApplicationService.loadConfig();

        

        // Initialize UI components using ComponentSetupService

        ComponentSetupService.setupMainWindow(this);

        thumbnailsPanel = ComponentSetupService.setupThumbnailsPanel();

        scrollPane = ComponentSetupService.setupScrollPane(thumbnailsPanel);

        

        // Initialize service instances

        collectionService = new CollectionService(this, collections);

        fileSystemService = new FileSystemService(this, allFolders, ApplicationService.getMainDirectoryPath());

        libraryService = new LibraryService();  // Initialize Master Controller

        

        // Check if main directory is configured
        boolean hasMainDirectory = ApplicationService.hasMainDirectoryConfigured();
        
        if (!hasMainDirectory) {
            System.out.println("No main directory configured - showing welcome screen");
            setTitle("PeraPera - Welcome");
        }

        

        // Create and add header panel

        headerPanel = new HeaderPanel(this);

        getContentPane().add(headerPanel, BorderLayout.NORTH);



        getContentPane().add(scrollPane, BorderLayout.CENTER);



        // Wrap scroll pane in a background-rendering panel

        JPanel backgroundPanel = new JPanel(new BorderLayout()) {

            @Override

            protected void paintComponent(Graphics g) {

                super.paintComponent(g);

                BackgroundService.renderBrowserBackground(g, getWidth(), getHeight(), this);

            }

        };

        backgroundPanel.setOpaque(false);

        backgroundPanel.add(scrollPane, BorderLayout.CENTER);

        getContentPane().remove(scrollPane);

        getContentPane().add(backgroundPanel, BorderLayout.CENTER);



        // Add menu bar using DialogService

        setJMenuBar(DialogService.createMenuBar(this));



        // Initialize pagination service

        paginationService = new PaginationService(headerPanel, ApplicationService.getPageSize());

        

        // Initialize selection service

        selectionService = new SelectionService();

        

        // Initialize navigation service

        navigationService = new NavigationService(this, scrollPane, thumbnailsPanel,
                                                  selectionService, paginationService,
                                                  headerPanel, currentItems);

        

        // Initialize search timer service

        searchTimerService = new SearchTimerService(this, thumbnailsPanel);
        
        // Setup progress listener for thumbnail loading updates
        setupProgressListener();



        // Setup resize listener

        ComponentSetupService.setupResizeListener(this, scrollPane, thumbnailsPanel, 250);



        // Install keyboard navigation

        navigationService.installKeyboardNavigation();



        // Load data

        loadRatings();

        loadTags();

        loadCollections();

        

        // Load saved user preferences

        sortOption = ApplicationService.getSortOption();

        paginationService.setPageSize(ApplicationService.getPageSize());

        // Load library filter preferences
        showCollections = ApplicationService.getFilterCollections();
        showSeries = ApplicationService.getFilterSeries();
        showChapters = ApplicationService.getFilterChapters();
        showStandalones = ApplicationService.getFilterStandalones();

        // Set initial filter state in header panel
        headerPanel.setFilterState(showCollections, showSeries, showChapters, showStandalones, showReading);



        // Show welcome screen if no library configured, otherwise load library async
        if (!ApplicationService.hasMainDirectoryConfigured()) {
            showWelcomeScreen();
        } else {
            loadLibraryAsync();
        }



        setVisible(true);
        
        // Shift focus to thumbnail grid on startup (mimics ESC behavior)
        thumbnailsPanel.requestFocusInWindow();
    }

    

    public void restartSearchTimer() {

        searchTimerService.restartSearchTimer();

    }

    

    /**

     * Gets the LibraryService instance for accessing comic metadata.

     * 

     * @return The LibraryService instance

     */

    public LibraryService getLibraryService() {
        return libraryService;
    }
    
    public SelectionService getSelectionService() {
        return selectionService;
    }
    
    public void refreshFolders() {
        // Clear selection first to prevent 'ghost' selection on shorter lists
        selectionService.resetSelection();
        
        // Clear current data
        allFolders.clear();

        ThumbnailService.clearCache();

        
        // Clear cache directory
        File cacheDir = ThumbnailService.getCacheDir();
        if (cacheDir != null && cacheDir.exists()) {
            File[] cacheFiles = cacheDir.listFiles();
            if (cacheFiles != null) {
                for (File cacheFile : cacheFiles) {
                    cacheFile.delete();
                }
            }
        }

        
        // Clear thumbnails panel
        thumbnailsPanel.removeAll();
        
        // Explicitly clear any lingering selection state in UI components
        Component[] components = thumbnailsPanel.getComponents();
        for (Component c : components) {
            if (c instanceof JComponent) {
                JComponent jc = (JComponent) c;
                jc.setBorder(null);
            }
        }
        
        thumbnailsPanel.revalidate();
        thumbnailsPanel.repaint();

        
        // Restart library loading async

        loadLibraryAsync();

    }

    

    /**
     * Loads the library asynchronously using SwingWorker.
     * Shows progress bar after 500ms delay if loading takes long.
     */
    private void loadLibraryAsync() {
        // Start delayed progress timer (will show bar after 500ms if still loading)
        headerPanel.startLoadingProgress();
        
        SwingWorker<Integer, Void> libraryLoader = new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                File mainDir = new File(ApplicationService.getMainDirectoryPath());
                if (mainDir.exists() && mainDir.isDirectory()) {
                    libraryService.syncLibrary(mainDir);
                    return libraryService.getLibrarySize();
                }
                return 0;
            }
            
            @Override
            protected void done() {
                // Stop the library loading timer first
                headerPanel.hideLoadingProgress();
                
                try {
                    int size = get();
                    if (size > 0) {
                        System.out.println("Library Synced: " + size + " comics loaded");
                        setTitle("PeraPera - Library Loaded: " + size + " comics");
                        // Initialize thumbnail progress tracking
                        ThumbnailService.initializeProgress(size);
                        // Start progress bar for thumbnail loading phase
                        headerPanel.startLoadingProgress();
                        // Trigger folder loading after sync completes
                        loadFoldersFromBackground();
                    } else {
                        System.err.println("Main directory does not exist: " + ApplicationService.getMainDirectoryPath());
                        showWelcomeScreen();
                    }
                } catch (Exception e) {
                    System.err.println("Error loading library: " + e.getMessage());
                    showWelcomeScreen();
                }
            }
        };
        
        libraryLoader.execute();
    }
    
    /**
     * Sets up the progress listener to connect ThumbnailService updates to the UI.
     */
    private void setupProgressListener() {
        ThumbnailService.setProgressListener(new ThumbnailService.ProgressListener() {
            @Override
            public void onProgress(int current, int total, String message) {
                headerPanel.updateProgress(current, total, message);
            }
            
            @Override
            public void onComplete() {
                headerPanel.hideLoadingProgress();
            }
        });
    }
    
    private void loadFoldersFromBackground() {

        // Load folders using existing system (now with UUID data available)

        fileSystemService.loadFoldersFromBackground();

    }



    public void displayThumbnails() {

        ThumbnailService.displayThumbnails(thumbnailsPanel, currentItems, headerPanel, 

                                          libraryService.getCollectionsCache(), paginationService, this, 

                                          this::showCollectionDropdown, this::refreshDisplay, this, selectionService);

    }

    

    public void refreshDisplay() {
        displayThumbnails();
    }
    
    /**
     * Updates the library filter state and refreshes the display.
     * Called by HeaderPanel when filter toggle buttons are clicked.
     * 
     * @param collections Whether to show manual collections
     * @param series Whether to show auto-generated series
     * @param chapters Whether to show individual chapters (comics in collections)
     * @param standalones Whether to show standalone comics
     * @param reading Whether to show only currently reading collections (primary view mode)
     */
    public void updateFilter(boolean collections, boolean series, boolean chapters, boolean standalones, boolean reading) {
        // Update filter state
        this.showCollections = collections;
        this.showSeries = series;
        this.showChapters = chapters;
        this.showStandalones = standalones;
        this.showReading = reading;
        
        // Persist to config (reading filter is not persisted - it's a session view)
        ApplicationService.setFilterCollections(collections);
        ApplicationService.setFilterSeries(series);
        ApplicationService.setFilterChapters(chapters);
        ApplicationService.setFilterStandalones(standalones);
        
        // Reset to first page when filter changes
        paginationService.resetToFirstPage();
        
        // Refresh display
        refreshDisplay();
    }
    
    /**
     * Gets the current filter state.
     * @return boolean array [collections, series, chapters, standalones, reading]
     */
    public boolean[] getFilterState() {
        return new boolean[] { showCollections, showSeries, showChapters, showStandalones, showReading };
    }

    

    public int getTotalPages() {

        // Return filtered total pages if searching, otherwise use all items

        boolean isSearching = !headerPanel.getSearchText().trim().isEmpty() || 

                           !headerPanel.getTagSearchText().trim().isEmpty();

        return isSearching ? currentFilteredTotalPages : 

               paginationService.getTotalPages(ThumbnailService.getCachedFolders().size(), collections.size());

    }

    

    public void setCurrentFilteredTotalPages(int totalPages) {

        this.currentFilteredTotalPages = totalPages;

    }

    

    public void updatePaginationControls(int totalPages) {

        paginationService.updatePaginationControls(totalPages);

    }

    

    public int getCurrentPage() {

        return paginationService.getCurrentPage();

    }

    

    public void setCurrentPage(int currentPage) {

        paginationService.setCurrentPage(currentPage);

    }

    

    public int getPageSize() {

        return paginationService.getPageSize();

    }

    

    public void setPageSize(int pageSize) {

        paginationService.setPageSize(ApplicationService.getPageSize());

        ApplicationService.setPageSize(pageSize); // Save to config

    }

    

    public void setSortOption(String sortOption) {

        // Store the sort option - will be used in displayThumbnails

        this.sortOption = sortOption;

        ApplicationService.setSortOption(sortOption); // Save to config

    }

    

    public String getSortOption() {

        return sortOption;

    }

    

    public String getMainDirectoryPath() {

        return ApplicationService.getMainDirectoryPath();

    }

    

    public void setMainDirectoryPath(String path) {

        ApplicationService.setMainDirectoryPath(path);

    }

    

    public Map<String, String> getConfig() {

        return ApplicationService.getConfig();

    }

    

    public int getFolderTagCount(String tagName) {

        return TagService.getFolderTagCount(tagName);

    }

    

    // Search and rating methods

    private void loadRatings() {

        // Ratings now loaded from JSON via LibraryService - no action needed

    }

    

    public void saveRatings() {

        // Ratings now saved to JSON via LibraryService - no action needed

    }

    

    public int getRating(File folder) {

        // Get rating from LibraryService (JSON database)

        String comicId = libraryService.getComicId(folder);

        if (comicId != null) {

            ComicEntry entry = libraryService.getLibraryCache().get(comicId);

            if (entry != null) {

                return entry.rating;

            }

        }

        return 0;

    }

    

    public void setRating(File folder, int rating) {

        // Update rating in LibraryService (JSON database)

        String comicId = libraryService.getComicId(folder);

        if (comicId != null) {

            ComicEntry entry = libraryService.getLibraryCache().get(comicId);

            if (entry != null) {

                entry.rating = rating;

                libraryService.persistLibrary();

            }

        }

        // Refresh display to show new rating

        refreshDisplay();

    }

    

    // Tag system methods

    private void loadTags() {

        TagService.initialize(libraryService);

    }

    

        

    public Set<String> getFolderTags(File folder) {

        return TagService.getFolderTags(folder);

    }

    

    public void setFolderTags(File folder, Set<String> tagSet) {

        TagService.setFolderTags(folder, tagSet);

        refreshDisplay();

    }

    

    public Set<String> getAllTags() {

        return TagService.getAllTags();

    }

    

    public boolean addTag(String tagName) {

        return TagService.addTag(tagName);

    }

    

    public boolean removeTag(String tagName) {

        return TagService.removeTag(tagName);

    }

    

    public Map<String, Collection> getCollections() {

        // Sync from LibraryService JSON collections to old map

        Map<String, CollectionEntry> jsonCollections = libraryService.getCollectionsCache();

        for (Map.Entry<String, CollectionEntry> entry : jsonCollections.entrySet()) {

            String name = entry.getKey();

            CollectionEntry collectionEntry = entry.getValue();

            

            // Only add if not already in old collections

            if (!collections.containsKey(name)) {

                Collection oldCollection = new Collection(name);

                oldCollection.rating = collectionEntry.rating;

                oldCollection.tags.addAll(collectionEntry.tags);

                oldCollection.selectedThumbnailComic = collectionEntry.selectedThumbnailComicId;

                oldCollection.isAutoGenerated = collectionEntry.isAutoGenerated;

                oldCollection.folderPath = collectionEntry.folderPath;

                

                // Convert comic IDs to names
                for (String comicId : collectionEntry.comicIds) {

                    ComicEntry comic = libraryService.getLibraryCache().get(comicId);

                    if (comic != null) {
                        oldCollection.comicNames.add(comic.name);
                        // Also add the UUID to prevent name collisions
                        oldCollection.comicIds.add(comicId);
                    }

                }

                collections.put(name, oldCollection);

            }

        }

        

        return collections;

    }

    

    private void loadCollections() {

        collectionService.loadCollections();

    }

    

    public void saveCollections() {

        collectionService.saveCollections();

    }

    

    private void showCollectionDropdown(Collection collection) {

        JPopupMenu[] dropdownRef = new JPopupMenu[1];

        collectionService.showCollectionDropdown(collection, dropdownRef);

        navigationService.setActiveDropdown(dropdownRef[0]);

    }

    

    private void showCollectionDropdown(CollectionEntry collectionEntry) {

        // Convert to old Collection format for dropdown (temporary compatibility)

        Collection oldCollection = ThumbnailService.convertToOldCollection(collectionEntry, libraryService);

        JPopupMenu[] dropdownRef = new JPopupMenu[1];

        collectionService.showCollectionDropdown(oldCollection, dropdownRef);

        navigationService.setActiveDropdown(dropdownRef[0]);

    }

    

    public void openCollectionDropdown(Collection collection) {
        showCollectionDropdown(collection);
    }

    

    public void openCollectionDropdown(CollectionEntry collectionEntry) {
        showCollectionDropdown(collectionEntry);
    }

    

    public void performSearch() {

        searchTimerService.performSearch();

    }



    public void openRandomComic() {

        // Build the same filtered list the UI is currently using.

        List<File> sourceFolders = new ArrayList<>(ThumbnailService.getCachedFolders());



        SearchCriteria criteria = new SearchCriteria(

            headerPanel.getSearchText(),

            headerPanel.getTagSearchText(),

            getSortOption(),

            1,

            Integer.MAX_VALUE,

            showCollections,
            showSeries,
            showChapters,
            showStandalones,
            showReading

        );



        List<Object> displayItems = SearchService.filterAndSort(sourceFolders, collections, criteria, this);



        if (displayItems.isEmpty()) {

            JOptionPane.showMessageDialog(this,

                "No comics or collections match the current filters.",

                "No Results",

                JOptionPane.WARNING_MESSAGE);

            return;

        }



        // Select random item from ALL display items (comics AND collections)

        int randomIndex = new Random().nextInt(displayItems.size());

        Object randomItem = displayItems.get(randomIndex);



        // Calculate pagination using the same index (single source of truth)

        int pageSize = paginationService.getPageSize();

        if (pageSize <= 0) pageSize = Integer.MAX_VALUE;



        int targetPage = (randomIndex / pageSize) + 1;

        int indexOnPage = randomIndex - ((targetPage - 1) * pageSize);



        paginationService.setCurrentPage(targetPage);

        refreshDisplay();



        // After refresh, find the actual index of randomItem in the current page's items

        // This ensures 1:1 mapping between data index and UI component index

        int actualIndexOnPage = currentItems.indexOf(randomItem);

        if (actualIndexOnPage < 0) {

            // Fallback: item not found (shouldn't happen), use calculated index

            actualIndexOnPage = indexOnPage;

        }



        final int finalIndex = actualIndexOnPage;

        SwingUtilities.invokeLater(() -> {

            selectionService.setSelectedIndex(finalIndex);

            selectionService.updateSelectionHighlight(thumbnailsPanel);

        });



        // Type routing: handle comics vs collections using standard methods

        if (randomItem instanceof File) {

            // It's a comic - use centralized router

            File randomFolder = (File) randomItem;

            openComicViewer(randomFolder);

        } else if (randomItem instanceof CollectionEntry) {

            // It's a collection - show dropdown

            CollectionEntry collectionEntry = (CollectionEntry) randomItem;

            Collection oldCollection = ThumbnailService.convertToOldCollection(collectionEntry, libraryService);

            JPopupMenu[] dropdownRef = new JPopupMenu[1];

            collectionService.showCollectionDropdown(oldCollection, dropdownRef);

            navigationService.setActiveDropdown(dropdownRef[0]);

        }

    }

    

    public void clearSearch() {

        searchTimerService.clearSearch(headerPanel, paginationService);

    }

    

        

    public void setGridView() {

        LayoutService.setGridView(thumbnailsPanel);

        refreshDisplay();

    }

    /**
     * Opens a comic viewer for the specified folder.
     * Routes to either standard ImageViewerFrame or Webtoon mode based on configuration.
     * 
     * @param folder The comic folder to open
     */
    public void openComicViewer(File folder) {
        if (ApplicationService.isWebtoonMode()) {
            new ImageScrollerFrame(folder, this).setVisible(true);
        } else {
            new ImageViewerFrame(folder, this).setVisible(true);
        }
    }
    
    /**
     * Opens a collection viewer starting from the specified folder.
     * Routes to either standard ImageViewerFrame or Webtoon mode based on configuration.
     * 
     * @param folder The starting comic folder
     * @param collectionFolders The ordered list of folders in the collection
     * @param collectionName The name of the collection
     */
    public void openCollectionViewer(File folder, List<File> collectionFolders, String collectionName) {
        if (ApplicationService.isWebtoonMode()) {
            int currentIndex = collectionFolders.indexOf(folder);
            new ImageScrollerFrame(folder, this, collectionFolders, collectionName, currentIndex, false).setVisible(true);
        } else {
            new ImageViewerFrame(folder, this, collectionFolders, collectionName).setVisible(true);
        }
    }
    
    /**
     * Shows the welcome screen when no library is configured.
     * Displays a "No Comics Found" message with a button to select library folder.
     */
    public void showWelcomeScreen() {
        // Hide the scroll pane (thumbnail grid)
        scrollPane.setVisible(false);
        
        // Create welcome panel if not exists
        if (welcomePanel == null) {
            welcomePanel = new JPanel(new GridBagLayout());
            welcomePanel.setOpaque(false);
            
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.insets = new Insets(10, 10, 10, 10);
            
            // Main message
            JLabel titleLabel = new JLabel("Welcome! Thanks for downloading PeraPera!");
            titleLabel.setFont(new Font("Arial", Font.BOLD, 28));
            titleLabel.setForeground(ApplicationService.getTextPrimary());
            titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
            gbc.gridy = 0;
            welcomePanel.add(titleLabel, gbc);
            
            // Subtitle
            JLabel subtitleLabel = new JLabel("<html><center>No Comics Found</center></html>");
            subtitleLabel.setFont(new Font("Arial", Font.PLAIN, 14));
            subtitleLabel.setForeground(ApplicationService.getTextSecondary());
            subtitleLabel.setHorizontalAlignment(SwingConstants.CENTER);
            gbc.gridy = 1;
            gbc.insets = new Insets(5, 10, 20, 10);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            welcomePanel.add(subtitleLabel, gbc);
            
            // Select folder button
            JButton selectFolderButton = new JButton("Choose Library Folder");
            selectFolderButton.setFont(new Font("Arial", Font.BOLD, 14));
            selectFolderButton.setPreferredSize(new Dimension(220, 40));
            selectFolderButton.addActionListener(e -> showDirectoryChooser());
            gbc.gridy = 2;
            gbc.insets = new Insets(10, 10, 10, 10);
            gbc.fill = GridBagConstraints.NONE;
            welcomePanel.add(selectFolderButton, gbc);
            
            // Note label
            JLabel noteLabel = new JLabel("<html><center>NOTE: If you are launching this for the first time and have a lot of content, the thumbnails will take some time to generate</center></html>");
            noteLabel.setFont(new Font("Arial", Font.PLAIN, 12));
            noteLabel.setForeground(ApplicationService.getTextSecondary());
            noteLabel.setHorizontalAlignment(SwingConstants.CENTER);
            gbc.gridy = 3;
            gbc.insets = new Insets(5, 10, 10, 10);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            welcomePanel.add(noteLabel, gbc);
        }
        
        // Add welcome panel to center of content pane
        getContentPane().add(welcomePanel, BorderLayout.CENTER);
        welcomePanel.setVisible(true);
        
        revalidate();
        repaint();
    }
    
    /**
     * Hides the welcome screen and shows the main thumbnail grid.
     */
    public void hideWelcomeScreen() {
        if (welcomePanel != null) {
            welcomePanel.setVisible(false);
            getContentPane().remove(welcomePanel);
        }
        scrollPane.setVisible(true);
        revalidate();
        repaint();
    }
    
    /**
     * Shows a directory chooser dialog for selecting the library folder.
     * Saves the selected path to config and loads the library.
     */
    public void showDirectoryChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select Library Folder");
        chooser.setApproveButtonText("Select Folder");
        
        // Start at current directory if no main directory is set
        if (!ApplicationService.hasMainDirectoryConfigured()) {
            chooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
        } else {
            File currentDir = new File(ApplicationService.getMainDirectoryPath());
            if (currentDir.exists()) {
                chooser.setCurrentDirectory(currentDir);
            }
        }
        
        int result = chooser.showOpenDialog(this);
        
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedDir = chooser.getSelectedFile();
            if (selectedDir != null && selectedDir.exists() && selectedDir.isDirectory()) {
                // Clear workspace cache before changing directory
                // This ensures the app uses a fresh workspace path on restart
                ApplicationService.clearWorkspaceCache();
                ThumbnailService.clearCacheDir();
                
                // Save the selected path
                ApplicationService.setMainDirectoryPath(selectedDir.getAbsolutePath());
                
                // Hide welcome screen
                hideWelcomeScreen();
                
                // Sync library with new directory
                try {
                    libraryService.syncLibrary(selectedDir);
                    System.out.println("Library synced: " + libraryService.getLibrarySize() + " comics loaded");
                    setTitle("PeraPera - Library Loaded: " + libraryService.getLibrarySize() + " comics");
                } catch (Exception e) {
                    System.err.println("Failed to sync library: " + e.getMessage());
                }
                
                // Refresh folder list
                refreshFolders();
                
                // Save all state and restart app to ensure proper initialization
                System.out.println("Library folder selected - saving state and restarting...");
                ApplicationService.saveConfig();
                libraryService.persistLibrary();
                
                // Restart automatically after a brief delay to ensure saves complete
                SwingUtilities.invokeLater(() -> {
                    restartApplication();
                });
            }
        }
    }
    
    /**
     * Restarts the application by spawning a new JVM process and exiting the current one.
     */
    private void restartApplication() {
        try {
            // Get the Java runtime path
            String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                javaBin += ".exe";
            }
            
            // Get the current classpath
            String classpath = System.getProperty("java.class.path");
            
            // Build the command to restart
            ProcessBuilder builder = new ProcessBuilder(
                javaBin, "-cp", classpath, "viewer.ImageBrowserApp"
            );
            
            // Start the new process detached from parent IO
            builder.start();
            
            // Exit the current app
            System.exit(0);
        } catch (Exception e) {
            System.err.println("Failed to restart: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                "Failed to restart automatically. Please restart manually.",
                "Restart Failed", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    public static void main(String[] args) {
        ApplicationService.startApplication();
    }
}
