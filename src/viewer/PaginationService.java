package viewer;

public class PaginationService {
    
    private int currentPage = 1;
    private int pageSize; // Initialized via constructor
    private HeaderPanel headerPanel;
    
    public PaginationService(HeaderPanel headerPanel, int initialPageSize) {
        this.headerPanel = headerPanel;
        this.pageSize = initialPageSize;
    }
    
    public int getTotalPages(int totalFolders, int totalCollections) {
        int totalItems = totalFolders + totalCollections;
        return (int) Math.ceil((double) totalItems / pageSize);
    }
    
    public void updatePaginationControls(int totalPages) {
        headerPanel.updatePaginationDisplay(currentPage, totalPages);
    }
    
    public int getCurrentPage() {
        return currentPage;
    }
    
    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }
    
    public int getPageSize() {
        return pageSize;
    }
    
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
    
    public void resetToFirstPage() {
        this.currentPage = 1;
    }
    
    public boolean isValidPage(int page, int totalPages) {
        return page >= 1 && page <= totalPages;
    }
    
    public void ensureValidBounds(int totalPages) {
        if (currentPage < 1) currentPage = 1;
        if (currentPage > totalPages) currentPage = totalPages;
    }
}
