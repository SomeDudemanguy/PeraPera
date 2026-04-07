package viewer;

/**
 * Holds search criteria for filtering and sorting comics and collections.
 */
public class SearchCriteria {
    private final String searchText;
    private final String tagExpression;
    private final String sortOption;
    private final int currentPage;
    private final int pageSize;
    private final boolean showCollections;
    private final boolean showSeries;
    private final boolean showChapters;
    private final boolean showStandalones;
    private final boolean showReading;
    
    public SearchCriteria(String searchText, String tagExpression, String sortOption, 
                         int currentPage, int pageSize,
                         boolean showCollections, boolean showSeries, 
                         boolean showChapters, boolean showStandalones,
                         boolean showReading) {
        this.searchText = searchText;
        this.tagExpression = tagExpression;
        this.sortOption = sortOption;
        this.currentPage = currentPage;
        this.pageSize = pageSize;
        this.showCollections = showCollections;
        this.showSeries = showSeries;
        this.showChapters = showChapters;
        this.showStandalones = showStandalones;
        this.showReading = showReading;
    }
    
    public String getSearchText() {
        return searchText;
    }
    
    public String getTagExpression() {
        return tagExpression;
    }
    
    public String getSortOption() {
        return sortOption;
    }
    
    public int getCurrentPage() {
        return currentPage;
    }
    
    public int getPageSize() {
        return pageSize;
    }
    
    public boolean isShowCollections() {
        return showCollections;
    }
    
    public boolean isShowSeries() {
        return showSeries;
    }
    
    public boolean isShowChapters() {
        return showChapters;
    }
    
    public boolean isShowStandalones() {
        return showStandalones;
    }
    
    public boolean isShowReading() {
        return showReading;
    }
}
