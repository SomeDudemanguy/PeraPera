package viewer;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;

public class HeaderPanel extends JPanel {

    // Search and rating components
    private final JTextField searchField = new JTextField(20);
    private final JButton searchButton = new JButton("Search");
    private final JButton clearButton = new JButton("Clear");
    private final JButton randomComicButton = new JButton("🎲 Random Comic");
    private final JButton prevPageButton = new JButton("← Prev");
    private final JButton nextPageButton = new JButton("Next →");
    private final JLabel pageLabel = new JLabel("Page 1 of 1");
    
    // Tag system components
    private final JTextField tagSearchField = new JTextField(20);
    private final JLabel tagSearchLabel = new JLabel("Tags:");
    
    // Library filter dropdown with checkboxes
    private final JButton filterDropdownBtn = new JButton("Filter ▼");
    private final JCheckBoxMenuItem collectionsCheck = new JCheckBoxMenuItem("📚 Collections");
    private final JCheckBoxMenuItem seriesCheck = new JCheckBoxMenuItem("📁 Series");
    private final JCheckBoxMenuItem chaptersCheck = new JCheckBoxMenuItem("📖 Chapters");
    private final JCheckBoxMenuItem standalonesCheck = new JCheckBoxMenuItem("🎴 Standalones");
    private final JCheckBoxMenuItem readingCheck = new JCheckBoxMenuItem("📖 Currently Reading");
    
    // Loading progress components
    private JProgressBar loadingProgressBar;
    private JLabel loadingLabel;
    private Timer delayedShowTimer;
    private static final int DELAY_MS = 500;

    public HeaderPanel(ImageBrowserApp app) {
        // Set up the panel layout
        setLayout(new BorderLayout());
        setBackground(new Color(0, 0, 0, 180)); // Semi-transparent black overlay
        setOpaque(false);
        
        // Override paintComponent to draw overlay
        JPanel mainPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(new Color(0, 0, 0, 100)); // Lighter overlay for readability
                g.fillRect(0, 0, getWidth(), getHeight());
                super.paintComponent(g);
            }
        };
        mainPanel.setBackground(new Color(0, 0, 0, 0)); // Fully transparent
        mainPanel.setOpaque(false);
        
        // Left panel for search controls
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftPanel.setBackground(new Color(0, 0, 0, 0)); // Fully transparent
        leftPanel.setOpaque(false);
        
        // Right panel for pagination controls
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightPanel.setBackground(new Color(0, 0, 0, 0)); // Fully transparent
        rightPanel.setOpaque(false);
        
        // Configure search components
        searchField.setBackground(ApplicationService.getInputBackground());
        searchField.setForeground(ApplicationService.getTextPrimary());
        searchField.setCaretColor(ApplicationService.getTextPrimary());
        searchButton.setBackground(ApplicationService.getInputBackground());
        searchButton.setForeground(ApplicationService.getTextPrimary());
        clearButton.setBackground(ApplicationService.getInputBackground());
        clearButton.setForeground(ApplicationService.getTextPrimary());
        randomComicButton.setBackground(ApplicationService.getInputBackground());
        randomComicButton.setForeground(ApplicationService.getTextPrimary());
        
        // Configure pagination components
        prevPageButton.setBackground(ApplicationService.getInputBackground());
        prevPageButton.setForeground(ApplicationService.getTextPrimary());
        nextPageButton.setBackground(ApplicationService.getInputBackground());
        nextPageButton.setForeground(ApplicationService.getTextPrimary());
        pageLabel.setForeground(ApplicationService.getTextPrimary());
        
        // Configure tag search components
        tagSearchLabel.setForeground(ApplicationService.getTextPrimary());
        tagSearchField.setBackground(ApplicationService.getInputBackground());
        tagSearchField.setForeground(ApplicationService.getTextPrimary());
        tagSearchField.setCaretColor(ApplicationService.getTextPrimary());
        
        // Add components to left panel
        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setForeground(ApplicationService.getTextPrimary());
        leftPanel.add(searchLabel);
        leftPanel.add(searchField);
        leftPanel.add(searchButton);
        leftPanel.add(clearButton);
        leftPanel.add(randomComicButton);
        
        leftPanel.add(tagSearchLabel);
        leftPanel.add(tagSearchField);
        
        // Configure and add filter toggle buttons
        setupFilterButtons(leftPanel, app);
        
        // Configure loading progress components
        loadingProgressBar = new JProgressBar();
        loadingProgressBar.setIndeterminate(true);
        loadingProgressBar.setStringPainted(true);
        loadingProgressBar.setString("Loading library...");
        loadingProgressBar.setVisible(false);
        loadingProgressBar.setBackground(ApplicationService.getInputBackground());
        loadingProgressBar.setForeground(ApplicationService.getProgressBarColor());
        loadingProgressBar.setPreferredSize(new Dimension(200, 20));
        loadingProgressBar.setMinimumSize(new Dimension(200, 20));
        
        loadingLabel = new JLabel("Loading...");
        loadingLabel.setForeground(ApplicationService.getTextPrimary());
        loadingLabel.setVisible(false);
        
        // Add pagination to right panel
        rightPanel.add(prevPageButton);
        rightPanel.add(pageLabel);
        rightPanel.add(nextPageButton);
        rightPanel.add(loadingLabel);
        rightPanel.add(loadingProgressBar);
        
        // Add panels to main header
        mainPanel.add(leftPanel, BorderLayout.WEST);
        mainPanel.add(rightPanel, BorderLayout.EAST);
        
        // Add main panel to this
        add(mainPanel, BorderLayout.CENTER);
        
        // Add all the listeners
        setupListeners(app);
    }
    
    private void setupListeners(ImageBrowserApp app) {
        // Search button listener
        searchButton.addActionListener(e -> {
            app.restartSearchTimer();
            app.performSearch();
        });
        
        // Clear button listener
        clearButton.addActionListener(e -> app.clearSearch());

        randomComicButton.addActionListener(e -> app.openRandomComic());
        
        // Document listeners for debounced search
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { app.restartSearchTimer(); }
            @Override public void removeUpdate(DocumentEvent e) { app.restartSearchTimer(); }
            @Override public void changedUpdate(DocumentEvent e) { app.restartSearchTimer(); }
        });
        
        tagSearchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { app.restartSearchTimer(); }
            @Override public void removeUpdate(DocumentEvent e) { app.restartSearchTimer(); }
            @Override public void changedUpdate(DocumentEvent e) { app.restartSearchTimer(); }
        });
        
        // Action listeners for Enter key
        searchField.addActionListener(e -> {
            app.restartSearchTimer();
            app.performSearch();
        });
        
        tagSearchField.addActionListener(e -> {
            app.restartSearchTimer();
            app.performSearch();
        });
        
                
                
                
        // Pagination button listeners
        prevPageButton.addActionListener(e -> {
            if (app.getCurrentPage() > 1) {
                // Reset selection to top when using UI pagination buttons
                app.getSelectionService().resetSelection();
                app.setCurrentPage(app.getCurrentPage() - 1);
                app.refreshDisplay();
                updatePaginationDisplay(app.getCurrentPage(), app.getTotalPages());
            }
        });
        
        nextPageButton.addActionListener(e -> {
            int totalPages = app.getTotalPages();
            if (app.getCurrentPage() < totalPages) {
                // Reset selection to top when using UI pagination buttons
                app.getSelectionService().resetSelection();
                app.setCurrentPage(app.getCurrentPage() + 1);
                app.refreshDisplay();
                updatePaginationDisplay(app.getCurrentPage(), totalPages);
            }
        });
        
    }
    
    /**
     * Sets up the filter dropdown button with checkbox menu items.
     */
    private void setupFilterButtons(JPanel leftPanel, ImageBrowserApp app) {
        // Configure dropdown button
        filterDropdownBtn.setBackground(ApplicationService.getInputBackground());
        filterDropdownBtn.setForeground(ApplicationService.getTextPrimary());
        filterDropdownBtn.setFocusable(false);
        filterDropdownBtn.setPreferredSize(new Dimension(100, 28));
        
        // Create popup menu
        JPopupMenu filterMenu = new JPopupMenu();
        filterMenu.setBackground(ApplicationService.getInputBackground());
        
        // Configure checkbox menu items
        JCheckBoxMenuItem[] checkboxes = {collectionsCheck, seriesCheck, chaptersCheck, standalonesCheck, readingCheck};
        String[] tooltips = {"Show Collections", "Show Series", "Show Chapters", "Show Standalones", "Show Currently Reading"};
        
        for (int i = 0; i < checkboxes.length; i++) {
            JCheckBoxMenuItem check = checkboxes[i];
            check.setToolTipText(tooltips[i]);
            check.setBackground(ApplicationService.getInputBackground());
            check.setForeground(ApplicationService.getTextPrimary());
            
            // Add action listener to trigger filter update when checkbox changes
            check.addActionListener(e -> {
                app.updateFilter(
                    collectionsCheck.isSelected(),
                    seriesCheck.isSelected(),
                    chaptersCheck.isSelected(),
                    standalonesCheck.isSelected(),
                    readingCheck.isSelected()
                );
                // Re-show menu to allow multiple selections without closing
                SwingUtilities.invokeLater(() -> filterMenu.show(filterDropdownBtn, 0, filterDropdownBtn.getHeight()));
            });
            
            filterMenu.add(check);
        }
        
        // Show dropdown when button is clicked
        filterDropdownBtn.addActionListener(e -> {
            filterMenu.show(filterDropdownBtn, 0, filterDropdownBtn.getHeight());
        });
        
        leftPanel.add(filterDropdownBtn);
    }
    
    
    /**
     * Sets the filter checkbox states programmatically.
     */
    public void setFilterState(boolean collections, boolean series, boolean chapters, boolean standalones, boolean reading) {
        collectionsCheck.setSelected(collections);
        seriesCheck.setSelected(series);
        chaptersCheck.setSelected(chapters);
        standalonesCheck.setSelected(standalones);
        readingCheck.setSelected(reading);
    }
    
    /**
     * Gets the current filter state from the checkboxes.
     */
    public boolean[] getFilterState() {
        return new boolean[] {
            collectionsCheck.isSelected(),
            seriesCheck.isSelected(),
            chaptersCheck.isSelected(),
            standalonesCheck.isSelected(),
            readingCheck.isSelected()
        };
    }
    
    // Getter methods for components that the main app needs to access
    public String getSearchText() {
        return searchField.getText();
    }
    
    public String getTagSearchText() {
        return tagSearchField.getText();
    }
    
        
    public void setPageLabel(String text) {
        pageLabel.setText(text);
    }
    
    public void setPaginationButtons(boolean prevEnabled, boolean nextEnabled) {
        prevPageButton.setEnabled(prevEnabled);
        nextPageButton.setEnabled(nextEnabled);
    }
    
    public void updatePaginationDisplay(int currentPage, int totalPages) {
        pageLabel.setText("Page " + currentPage + " of " + totalPages);
        prevPageButton.setEnabled(currentPage > 1);
        nextPageButton.setEnabled(currentPage < totalPages);
    }
    
    public void setSearchText(String text) {
        searchField.setText(text);
    }
    
    public void setTagSearchText(String text) {
        tagSearchField.setText(text);
    }
    
    /**
     * Starts the delayed show timer for loading progress.
     * If loading finishes before 500ms, the progress bar will never appear.
     */
    public void startLoadingProgress() {
        if (delayedShowTimer != null && delayedShowTimer.isRunning()) {
            System.out.println("DEBUG: Progress timer already running, skipping");
            return; // Already started
        }
        System.out.println("DEBUG: Starting progress delay timer (500ms)");
        delayedShowTimer = new Timer(DELAY_MS, e -> {
            System.out.println("DEBUG: Progress timer FIRED - showing progress bar");
            loadingProgressBar.setVisible(true);
            loadingLabel.setVisible(true);
            revalidate();
            repaint();
        });
        delayedShowTimer.setRepeats(false);
        delayedShowTimer.start();
    }
    
    /**
     * Hides the loading progress bar.
     * Safe to call even if the bar was never shown.
     */
    public void hideLoadingProgress() {
        System.out.println("DEBUG: Hiding progress bar");
        if (delayedShowTimer != null) {
            delayedShowTimer.stop();
        }
        loadingProgressBar.setVisible(false);
        loadingLabel.setVisible(false);
        revalidate();
        repaint();
    }
    
    /**
     * Updates the loading progress bar with current progress.
     * @param current Current number of items processed
     * @param total Total number of items to process
     * @param message Progress message to display
     */
    public void updateProgress(int current, int total, String message) {
        System.out.println("DEBUG: Updating progress - " + message);
        loadingProgressBar.setIndeterminate(false);
        loadingProgressBar.setMaximum(total);
        loadingProgressBar.setValue(current);
        loadingProgressBar.setString(message);
        loadingLabel.setText(message);
        if (!loadingProgressBar.isVisible()) {
            loadingProgressBar.setVisible(true);
            loadingLabel.setVisible(true);
            revalidate();
            repaint();
        }
    }
    
    /**
     * Shows the loading progress immediately (for library sync phase).
     * @param message Initial loading message
     */
    public void showLibraryLoading(String message) {
        loadingProgressBar.setIndeterminate(true);
        loadingProgressBar.setString(message);
        loadingLabel.setText(message);
        loadingProgressBar.setVisible(true);
        loadingLabel.setVisible(true);
        revalidate();
    }
}
