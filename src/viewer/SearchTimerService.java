package viewer;

import javax.swing.*;

public class SearchTimerService {
    
    private Timer searchTimer;
    private volatile boolean searchInProgress = false;
    private final ImageBrowserApp app;
    private final JPanel thumbnailsPanel;
    
    public SearchTimerService(ImageBrowserApp app, JPanel thumbnailsPanel) {
        this.app = app;
        this.thumbnailsPanel = thumbnailsPanel;
        initializeTimer();
    }
    
    private void initializeTimer() {
        // Initialize search timer for debounced search
        searchTimer = new Timer(300, e -> {
            if (!searchInProgress) {
                performSearch();
            }
        });
        searchTimer.setRepeats(false);
    }
    
    public void restartSearchTimer() {
        searchTimer.stop();
        searchTimer.start();
    }
    
    public void performSearch() {
        // Stop the search timer
        searchTimer.stop();
        
        // Reset to first page when new search is performed
        app.setCurrentPage(1);
        
        // Clear current thumbnails panel but keep cached data
        thumbnailsPanel.removeAll();
        
        // Display filtered results immediately using SearchService
        SwingUtilities.invokeLater(() -> {
            app.refreshDisplay();
        });
        
        // Load thumbnails for any folders that don't have them yet
        for (java.io.File folder : ThumbnailService.getCachedFolders()) {
            if (ThumbnailService.getFolderThumbnail(folder) == null) {
                ThumbnailService.loadThumbnail(folder, app.getLibraryService(), () -> {
                    app.refreshDisplay();
                    // Update pagination controls
                    // This will be handled by PaginationService
                });
            }
        }
    }
    
    public void clearSearch(HeaderPanel headerPanel, PaginationService paginationService) {
        headerPanel.setSearchText("");
        headerPanel.setTagSearchText("");
        paginationService.resetToFirstPage();
        performSearch();
    }
    
    public void setSearchInProgress(boolean inProgress) {
        this.searchInProgress = inProgress;
    }
    
    public boolean isSearchInProgress() {
        return searchInProgress;
    }
}
