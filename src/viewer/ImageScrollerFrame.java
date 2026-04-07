package viewer;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;



/**

 * Webtoon-style image scroller with lazy loading and memory management.

 * Displays all images in a folder as a continuous vertical scroll.

 */

public class ImageScrollerFrame extends JFrame {

    

    // Configuration constants

    private static final int SCROLL_UNIT_INCREMENT = 50;

    private static final int VIEWPORT_BUFFER_SCREENS = 2; // Load images within 2 screens of viewport

    private static final int UNLOAD_DISTANCE_SCREENS = 3; // Unload images 3+ screens away

    private static final int MAX_CONCURRENT_LOADS = 4;

    

    // UI Components

    private JPanel containerPanel;

    private JScrollPane scrollPane;

    private JButton closeButton;

    private JPanel controlPanel; // Top navigation bar reference

    

    // Rating components

    private JSlider ratingSlider;

    private JTextField ratingField;

    

    // Reading progress slider

    private JSlider progressSlider;

    private boolean isUpdatingProgress = false; // Prevent recursive updates

    

    // Data

    private final File folder;

    private final LibraryProvider library;

    private final List<File> imageFiles;

    private final List<PagePanel> pagePanels;

    

    // Lazy loading

    private final ExecutorService imageLoaderExecutor;

    private final Set<Integer> loadingIndices = Collections.synchronizedSet(new HashSet<>());

    private final Set<Integer> loadedIndices = Collections.synchronizedSet(new HashSet<>());

    

    // Collection context (optional)

    private final List<File> collectionFolders;

    private final String collectionName;

    private final int currentComicIndex;

    private int currentFolderIndex = 0;

    

    // Zoom engine

    private double zoomScale = 1.0;

    private static final double MIN_ZOOM = 0.3;

    private static final double MAX_ZOOM = 1.0; // Maximum is fit-to-screen (initial zoom)

    private static final double ZOOM_STEP = 0.1;

    private JLabel zoomLabel;

    

    // Smooth animated scrolling

    private javax.swing.Timer smoothScrollTimer;

    private int targetScrollValue;

    private double currentScrollValue; // Use double for sub-pixel precision

    private boolean isSystemScrolling = false;

    // Butter-smooth animation constants
    private static final int ANIMATION_FPS = 16; // ~60fps for consistent smoothness

    // Configurable scroll settings (loaded from ApplicationService)
    private final int smoothScrollDelay;
    private final int scrollAmount;

    // 'Hold to Navigate' feature

    private JProgressBar navigationProgress;

    private javax.swing.Timer holdTimer;

    private boolean isAtTopEdge = false;

    private boolean isAtBottomEdge = false;

    private boolean isHoldingUp = false;

    private boolean isHoldingDown = false;

    

    // Strip-integrated navigation buttons

    private JButton prevComicStripButton;

    private JButton nextComicStripButton;

    

    /**

     * Opens a single comic in Webtoon mode.

     */

    public ImageScrollerFrame(File folder, LibraryProvider library) {

        this(folder, library, null, null, -1, false);

    }

    

    /**

     * Opens a comic within a collection context in Webtoon mode.

     */

    public ImageScrollerFrame(File folder, LibraryProvider library, 

                             List<File> collectionFolders, String collectionName, int currentComicIndex, boolean startAtBottom) {

        super(folder.getName() + (collectionName != null ? " [" + collectionName + "]" : "") + " - Webtoon Mode");

        // Initialize pagePanels FIRST - before any methods use it

        this.pagePanels = new ArrayList<>();

        // Load global webtoon zoom and other viewer settings
        this.zoomScale = ApplicationService.getWebtoonZoom();
        boolean controlPanelDefaultVisible = ApplicationService.getControlPanelDefaultVisible();

        // Load smooth scroll settings into instance fields
        this.smoothScrollDelay = ApplicationService.getWebtoonScrollSpeed();
        this.scrollAmount = ApplicationService.getWebtoonScrollDistance();

        this.folder = folder;

        this.library = library;

        this.collectionFolders = collectionFolders;

        this.collectionName = collectionName;

        this.currentComicIndex = currentComicIndex;

        this.currentFolderIndex = currentComicIndex; // Set the navigation index

        // IMMEDIATELY update lastReadComicId when opening a comic in collection mode
        if (collectionName != null && library instanceof ImageBrowserApp) {
            LibraryService libraryService = ((ImageBrowserApp) library).getLibraryService();
            if (libraryService != null) {
                String comicId = libraryService.getComicId(folder);
                if (comicId != null) {
                    libraryService.updateLastReadComic(collectionName, comicId);
                    System.out.println("DEBUG ImageScrollerFrame constructor: Updated lastRead to " + comicId);
                    // Auto-track currently reading when navigating in collection mode
                    autoTrackCurrentlyReading(libraryService, collectionName);
                }
            }
        }

        // Auto-track standalone comics (not in collection mode) when opening

        if (collectionName == null && library instanceof ImageBrowserApp) {

            LibraryService libraryService = ((ImageBrowserApp) library).getLibraryService();

            if (libraryService != null) {

                String comicId = libraryService.getComicId(folder);

                if (comicId != null) {

                    autoTrackStandaloneComic(libraryService, comicId);

                }

            }

        }

        

        // Set application icon

        try {

            ImageIcon logoIcon = new ImageIcon("Insignia/Logo.jpg");

            setIconImage(logoIcon.getImage());

        } catch (Exception e) {

            System.err.println("Could not load application icon: " + e.getMessage());

        }

        

        // Initialize thread pool for image loading

        this.imageLoaderExecutor = Executors.newFixedThreadPool(MAX_CONCURRENT_LOADS);

        

        // Read and sort all image files from the folder

        this.imageFiles = FileSystemService.loadImageFiles(folder);

        

        if (imageFiles.isEmpty()) {

            JOptionPane.showMessageDialog(this, 

                "No valid image files found in: " + folder.getName(),

                "No Images", JOptionPane.WARNING_MESSAGE);

            dispose();

            return;

        }

        

        // Setup UI - Fullscreen mode

        setUndecorated(true);

        setExtendedState(JFrame.MAXIMIZED_BOTH);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        setLocationRelativeTo(null);

        

        // Main layout

        setLayout(new BorderLayout());

        

        // Top navigation bar

        controlPanel = createNavigationBar();
        add(controlPanel, BorderLayout.NORTH);
        
        // Apply control panel visibility setting from preferences
        controlPanel.setVisible(controlPanelDefaultVisible);

        

        // Container panel with vertical BoxLayout and center alignment

        containerPanel = new JPanel() {

            @Override

            protected void paintComponent(Graphics g) {

                // Get the current visible rectangle from the scroll pane viewport

                Rectangle visibleRect = scrollPane.getViewport().getViewRect();

                // Render the stationary background using the same background as standard viewer

                BackgroundService.renderViewerBackground(g, visibleRect, this);

                // Call super to paint children (images) over the background

                super.paintComponent(g);

            }

        };

        containerPanel.setLayout(new BoxLayout(containerPanel, BoxLayout.Y_AXIS));

        containerPanel.setOpaque(false); // Make transparent so background is visible

        containerPanel.setBorder(null); // No border on container

        containerPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        

        // Create PagePanel placeholders for each image

        initializePagePanels();

        

        // Scroll pane with fast scrolling - set black background for proper opacity behavior

        scrollPane = new JScrollPane(containerPanel);

        scrollPane.setBackground(ApplicationService.getBackgroundDark());

        scrollPane.getViewport().setBackground(ApplicationService.getBackgroundDark());

        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        scrollPane.getVerticalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);

        scrollPane.getVerticalScrollBar().setBlockIncrement(SCROLL_UNIT_INCREMENT * 10);

        scrollPane.setBorder(null);

        

        // Add AdjustmentListener to sync smooth scroll state when user manually scrolls

        scrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {

            if (!isSystemScrolling) {

                syncSmoothScrollState();

            }

        });

        

        scrollPane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);

        

        // Add viewport change listener for lazy loading and progress sync

        scrollPane.getViewport().addChangeListener(e -> {

            onViewportChanged();

            syncScrollToProgressSlider();

        });

        

        // === HIJACK SCROLLPANE ACTIONS FOR HOLD-TO-NAVIGATE ===

        // Override the default scroll actions to intercept UP/DOWN for navigation

        scrollPane.getActionMap().put("unitScrollUp", new AbstractAction() {

            public void actionPerformed(ActionEvent e) {

                JScrollBar vBar = scrollPane.getVerticalScrollBar();

                int oldValue = vBar.getValue();

                checkAndStartHoldNavigation(-1);

                // Perform smooth scroll if not at edge (hold-to-navigate takes priority at edge)

                if (collectionFolders == null || oldValue >= 120) {

                    startSmoothScroll(-scrollAmount);

                }

            }

        });

        

        scrollPane.getActionMap().put("unitScrollDown", new AbstractAction() {

            public void actionPerformed(ActionEvent e) {

                JScrollBar vBar = scrollPane.getVerticalScrollBar();

                int maxValue = vBar.getMaximum() - vBar.getVisibleAmount();

                int oldValue = vBar.getValue();

                checkAndStartHoldNavigation(1);

                // Perform smooth scroll if not at edge

                if (collectionFolders == null || oldValue <= maxValue - 10) {

                    startSmoothScroll(scrollAmount);

                }

            }

        });

        

        // Add resize listener to recalculate image sizes

        scrollPane.getViewport().addComponentListener(new ComponentAdapter() {

            @Override

            public void componentResized(ComponentEvent e) {

                onViewportResized();

            }

        });

        

        add(scrollPane, BorderLayout.CENTER);

        

        // Add mouse wheel listener for zoom (Ctrl + scroll)

        setupZoomControl();

        

        // Setup 'Hold to Navigate' progress bar

        setupNavigationProgressBar();

        

        // Add input bindings for control panel toggle (right-click and Enter)

        setupControlPanelToggle();

        

        // Initial load of visible images

        final boolean shouldStartAtBottom = startAtBottom;

        SwingUtilities.invokeLater(() -> {

            // Apply loaded zoom to all panels before first render

            if (zoomScale != 1.0) {

                int viewportWidth = scrollPane.getViewport().getWidth() - 20;

                for (PagePanel panel : pagePanels) {

                    panel.setZoomScale(zoomScale);

                    panel.recalculateSize(viewportWidth);

                }

                containerPanel.revalidate();

                zoomLabel.setText((int)(zoomScale * 100) + "%");

            }

            onViewportChanged();

            updateCollectionButtons();

            

            // === INITIAL SCROLL POSITION ===

            if (shouldStartAtBottom) {

                // Start at bottom for backward navigation

                scrollToBottom();

            } else if (prevComicStripButton != null) {

                // Skip the Previous button for forward navigation

                containerPanel.revalidate();

                containerPanel.doLayout();

                // Double revalidate to ensure height is calculated

                containerPanel.revalidate();

                containerPanel.doLayout();

                

                // Safety buffer: ensure UI has actually painted before scrolling

                try {

                    Thread.sleep(50);

                } catch (InterruptedException ignored) {}

                

                int buttonHeight = prevComicStripButton.getPreferredSize().height;

                // Add a small margin to ensure the first image is fully visible

                scrollPane.getVerticalScrollBar().setValue(buttonHeight + 10);

            }

        });

        

        // Cleanup on close

        addWindowListener(new WindowAdapter() {

            @Override

            public void windowClosing(WindowEvent e) {

                shutdownLoader();

            }

            

            @Override

            public void windowClosed(WindowEvent e) {

                unloadAllImages();

            }

        });

        

        // Keyboard shortcuts

        setupKeyboardShortcuts();

    }

    

    /**

     * Creates the top control panel with rating, tags, navigation, and progress slider.

     * Uses a two-row layout: Top row for controls, bottom row for progress.

     */

    private JPanel createNavigationBar() {

        // Main control panel with vertical BoxLayout for two rows

        JPanel mainPanel = new JPanel();

        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        mainPanel.setBackground(ApplicationService.getBackgroundDark());

        mainPanel.setBorder(new EmptyBorder(10, 15, 10, 15));

        

        // === TOP ROW: All controls centered in the middle ===

        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));

        topRow.setBackground(ApplicationService.getBackgroundDark());

        

        // Collection navigation dropdown (replaces prev/next buttons)

        if (collectionFolders != null && collectionFolders.size() > 1) {

            JLabel collectionLabel = new JLabel(collectionName + " (" + (currentComicIndex + 1) + "/" + collectionFolders.size() + ")");

            collectionLabel.setForeground(ApplicationService.getTextSecondary());

            collectionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));

            collectionLabel.setBorder(new EmptyBorder(0, 5, 0, 10));

            topRow.add(collectionLabel);

            

            // Create dropdown with folder names

            String[] folderNames = new String[collectionFolders.size()];

            for (int i = 0; i < collectionFolders.size(); i++) {

                folderNames[i] = collectionFolders.get(i).getName();

            }

            JComboBox<String> comicDropdown = new JComboBox<>(folderNames);
            

            comicDropdown.setFocusable(false); // Prevent focus trap - arrow keys go to scroll pane

            

            // Action listener: navigate to selected comic

            comicDropdown.addActionListener(e -> {

                int selectedIndex = comicDropdown.getSelectedIndex();

                if (selectedIndex != currentFolderIndex) {

                    navigateToComicByIndex(selectedIndex);

                }

            });

            

            topRow.add(comicDropdown);

            topRow.add(Box.createHorizontalStrut(15));

        }

        

        // Rating panel

        JPanel ratingPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));

        ratingPanel.setBackground(ApplicationService.getBackgroundDark());

        ratingPanel.setOpaque(true);

        

        JLabel ratingLabel = new JLabel("Rating:");

        ratingLabel.setForeground(ApplicationService.getTextPrimary());

        ratingLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        

        ratingSlider = new JSlider(0, 100, library.getRating(folder));

        ratingSlider.setMajorTickSpacing(25);

        ratingSlider.setMinorTickSpacing(5);

        ratingSlider.setPaintTicks(true);

        ratingSlider.setPaintLabels(true);

        ratingSlider.setBackground(ApplicationService.getBackgroundDark());

        ratingSlider.setForeground(ApplicationService.getTextPrimary());

        ratingSlider.setPreferredSize(new Dimension(150, 40));

        ratingSlider.setFocusable(false);

        

        ratingField = new JTextField(String.valueOf(library.getRating(folder)), 3);

        ratingField.setBackground(ApplicationService.getInputBackground());

        ratingField.setForeground(ApplicationService.getTextPrimary());

        ratingField.setCaretColor(ApplicationService.getTextPrimary());

        ratingField.setHorizontalAlignment(JTextField.CENTER);

        ratingField.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        ratingField.setPreferredSize(new Dimension(40, 25));

        ratingField.setFocusable(false);

        

        ratingSlider.addChangeListener(e -> {

            int value = ratingSlider.getValue();

            ratingField.setText(String.valueOf(value));

            // Only save when user releases the slider to avoid lag

            if (!ratingSlider.getValueIsAdjusting()) {

                library.setRating(folder, value);

            }

        });

        

        ratingField.addActionListener(e -> {

            try {

                int rating = Integer.parseInt(ratingField.getText().trim());

                if (rating >= 0 && rating <= 100) {

                    ratingSlider.setValue(rating);

                    library.setRating(folder, rating);

                } else {

                    ratingField.setText(String.valueOf(ratingSlider.getValue()));

                }

            } catch (NumberFormatException ex) {

                ratingField.setText(String.valueOf(ratingSlider.getValue()));

            }

        });

        

        ratingPanel.add(ratingLabel);

        ratingPanel.add(ratingSlider);

        ratingPanel.add(ratingField);

        

        topRow.add(ratingPanel);

        

        // Action buttons (Edit Tags, + Collection)

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));

        actionPanel.setBackground(ApplicationService.getBackgroundDark());

        

        JButton tagButton = createStyledButton("Edit Tags");

        tagButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        tagButton.setPreferredSize(new Dimension(80, 28));

        tagButton.addActionListener(e -> DialogService.showFolderTagAssignmentDialog(folder, (ImageBrowserApp) library, this));

        

        JButton addToCollectionButton = createStyledButton("+ Collection");

        addToCollectionButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        addToCollectionButton.setPreferredSize(new Dimension(90, 28));

        addToCollectionButton.addActionListener(e -> DialogService.showAddToCollectionDialog(this, folder, library));

        

        actionPanel.add(tagButton);

        actionPanel.add(addToCollectionButton);

        

        topRow.add(actionPanel);

        

        // Close button

        closeButton = createStyledButton("Close");

        closeButton.addActionListener(e -> dispose());

        topRow.add(closeButton);

        

        mainPanel.add(topRow);

        mainPanel.add(Box.createVerticalStrut(10)); // Spacer between rows

        

        // === BOTTOM ROW: Reading Progress Slider (full width) ===

        JPanel bottomRow = new JPanel(new BorderLayout(10, 0));

        bottomRow.setBackground(ApplicationService.getBackgroundDark());

        

        JLabel progressLabel = new JLabel("Progress:");

        progressLabel.setForeground(ApplicationService.getTextPrimary());

        progressLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        progressLabel.setBorder(new EmptyBorder(0, 0, 0, 10));

        

        // Progress slider (0-10000 representing scroll percentage with high resolution)

        progressSlider = new JSlider(0, 10000, 0);

        progressSlider.setMajorTickSpacing(2500);

        progressSlider.setMinorTickSpacing(500);

        progressSlider.setPaintTicks(false);

        progressSlider.setPaintLabels(false);

        progressSlider.setBackground(ApplicationService.getBackgroundDark());
        progressSlider.setForeground(ApplicationService.getTextPrimary());

        progressSlider.setFocusable(false);

        // ...

        // ...
        // ChangeListener: Slider → Scroll (LIVE - no getValueIsAdjusting check)

        progressSlider.addChangeListener(e -> {

            if (!isUpdatingProgress) {

                int value = progressSlider.getValue();

                double percentage = value / 100.0;

                updateProgressLabel(percentage);

                scrollToProgress(value);

                // Trigger lazy loading during live scroll

                onViewportChanged();

            }

        });

        

        JLabel progressPercentLabel = new JLabel("0%");

        progressPercentLabel.setForeground(ApplicationService.getTextSecondary());

        progressPercentLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        progressPercentLabel.setBorder(new EmptyBorder(0, 10, 0, 0));

        progressPercentLabel.setName("progressPercentLabel");

        

        bottomRow.add(progressLabel, BorderLayout.WEST);

        bottomRow.add(progressSlider, BorderLayout.CENTER);

        bottomRow.add(progressPercentLabel, BorderLayout.EAST);

        

        mainPanel.add(bottomRow);

        

        return mainPanel;
    }

    /**
     * Helper method to create a consistently styled button.
     */
    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);

        button.setBackground(ApplicationService.getBackgroundLight());
        button.setForeground(ApplicationService.getTextPrimary());
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ApplicationService.getBorderColor(), 1),
            BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));

        button.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setFocusable(false);

        return button;
    }

    /**
     * Sets the visibility of the control panel.
     * Used to persist visibility state across comic navigation.
     */
    public void setControlPanelVisible(boolean visible) {
        if (controlPanel != null) {
            controlPanel.setVisible(visible);
        }
    }

    

    /**
     * Scrolls to the bottom of the comic (last page).
     * Used when navigating backward to start at the end.
     */
    public void scrollToBottom() {
        // Use Timer to wait for content to fully load and layout
        // Delay increased to 300ms to ensure images have loaded and reported true sizes
        javax.swing.Timer scrollTimer = new javax.swing.Timer(300, e -> {
            containerPanel.revalidate();
            containerPanel.doLayout();
            JScrollBar vBar = scrollPane.getVerticalScrollBar();
            vBar.setValue(vBar.getMaximum());
            ((javax.swing.Timer)e.getSource()).stop(); // Stop after one execution
        });
        scrollTimer.setRepeats(false);
        scrollTimer.start();
    }

    

    /**

     * Syncs the scroll position to the progress slider value.

     * Called when the user scrolls (AdjustmentListener on scroll pane).

     */

    private void syncScrollToProgressSlider() {

        if (isUpdatingProgress || progressSlider == null) return;

        

        JScrollBar verticalBar = scrollPane.getVerticalScrollBar();

        int maxValue = verticalBar.getMaximum() - verticalBar.getVisibleAmount();

        

        if (maxValue > 0) {

            double ratio = (double) verticalBar.getValue() / maxValue;

            int sliderValue = (int) (ratio * 10000);

            double percentage = sliderValue / 100.0;

            isUpdatingProgress = true;

            progressSlider.setValue(sliderValue);

            updateProgressLabel(percentage);

            isUpdatingProgress = false;

        }

    }

    

    /**

     * Updates the progress percentage label with one decimal precision.

     */

    private void updateProgressLabel(double percentage) {

        if (controlPanel == null) return;

        

        // Find the progress label in the bottom row

        Component[] components = ((JPanel) ((JPanel) controlPanel).getComponent(2)).getComponents();

        for (Component c : components) {

            if (c instanceof JLabel && "progressPercentLabel".equals(c.getName())) {

                ((JLabel) c).setText(String.format("%.1f%%", percentage));

                break;

            }

        }

    }

    

    /**

     * Scrolls the view to the specified slider value (0-10000).

     * Called when the user moves the progress slider.

     */

    private void scrollToProgress(int sliderValue) {

        JScrollBar verticalBar = scrollPane.getVerticalScrollBar();

        int maxValue = verticalBar.getMaximum() - verticalBar.getVisibleAmount();

        

        double percentage = (double) sliderValue / 10000.0;

        int newValue = (int) (percentage * maxValue);

        

        verticalBar.setValue(newValue);

        

        // Trigger lazy loading for the new viewport position

        SwingUtilities.invokeLater(() -> {

            onViewportChanged();

        });

    }

    

    /**

     * Initializes PagePanel placeholders for all images with pre-calculated dimensions.

     * Uses ImageReader to read only the image header (not pixels) for accurate scrollbar sizing.

     * Also adds strip-integrated Previous/Next navigation buttons at top and bottom.

     */

    private void initializePagePanels() {

        int initialViewportWidth = getWidth() - 40; // Estimate viewport width

        

        // === PREVIOUS COMIC BUTTON (at the very top, index 0) ===

        if (collectionFolders != null && collectionFolders.size() > 1 && currentFolderIndex > 0) {

            String prevComicName = collectionFolders.get(currentFolderIndex - 1).getName();

            prevComicStripButton = createStripNavigationButton("↑ Previous Title: " + prevComicName);

            prevComicStripButton.addActionListener(e -> navigateToComicByIndex(currentFolderIndex - 1));

            containerPanel.add(prevComicStripButton, 0);

            // Force layout manager to give the button physical space
            prevComicStripButton.setMinimumSize(new Dimension(800, 100));
            prevComicStripButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
            prevComicStripButton.setPreferredSize(new Dimension(800, 100));

            // Add spacing after the button
            containerPanel.add(Box.createRigidArea(new Dimension(1, 10)), 1);

        }

        

        // Create PagePanel placeholders for each image

        for (int i = 0; i < imageFiles.size(); i++) {

            File imageFile = imageFiles.get(i);

            Dimension imageSize = readImageDimensions(imageFile);

            

            PagePanel pagePanel = new PagePanel(imageFile, i + 1, imageFiles.size(), imageSize, initialViewportWidth);

            pagePanels.add(pagePanel);

            containerPanel.add(pagePanel);

        }

        

        // === NEXT COMIC BUTTON (at the very bottom) ===

        if (collectionFolders != null && collectionFolders.size() > 1 && currentFolderIndex < collectionFolders.size() - 1) {

            String nextComicName = collectionFolders.get(currentFolderIndex + 1).getName();

            nextComicStripButton = createStripNavigationButton("↓ Next Title: " + nextComicName);

            nextComicStripButton.addActionListener(e -> navigateToComicByIndex(currentFolderIndex + 1));

            containerPanel.add(nextComicStripButton);

        }

        

        // No glue added - pages should touch each other

    }

    

    /**

     * Creates a large, styled navigation button for the comic strip.

     * These buttons feel like part of the webtoon 'border' rather than standard Windows buttons.

     */

    private JButton createStripNavigationButton(String text) {

        JButton button = new JButton(text);

        button.setBackground(ApplicationService.getBackgroundDark()); // Dark background
        button.setForeground(ApplicationService.getTextSecondary()); // Light gray text

        button.setFocusPainted(false);

        button.setBorder(BorderFactory.createCompoundBorder(

            BorderFactory.createLineBorder(ApplicationService.getBorderColor(), 2),

            BorderFactory.createEmptyBorder(30, 20, 30, 20)

        ));

        button.setFont(new Font("Segoe UI", Font.BOLD, 18));

        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        button.setFocusable(false);

        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

        button.setPreferredSize(new Dimension(0, 100));

        button.setAlignmentX(Component.CENTER_ALIGNMENT);

        

        // Hover effect

        button.addMouseListener(new MouseAdapter() {

            @Override

            public void mouseEntered(MouseEvent e) {

                button.setBackground(ApplicationService.getBackgroundMedium());
                button.setForeground(ApplicationService.getTextPrimary());

            }

            

            @Override

            public void mouseExited(MouseEvent e) {

                button.setBackground(ApplicationService.getBackgroundDark());
                button.setForeground(ApplicationService.getTextSecondary());

            }

        });

        

        return button;

    }

    

    private Dimension readImageDimensions(File imageFile) {

        try (ImageInputStream input = ImageIO.createImageInputStream(imageFile)) {

            ImageReader reader = ImageIO.getImageReaders(input).next();

            reader.setInput(input, true);

            int width = reader.getWidth(0);

            int height = reader.getHeight(0);

            reader.dispose();

            return new Dimension(width, height);

        } catch (Exception e) {

            // Fallback: return default size, will be corrected when image loads

            System.err.println("Could not read dimensions for: " + imageFile.getName());

            return new Dimension(800, 1200);

        }

    }

    

    /**

     * Called when the viewport changes (scrolling). Triggers lazy loading/unloading.

     */

    private void onViewportChanged() {

        Rectangle viewportBounds = scrollPane.getViewport().getViewRect();

        int viewportHeight = viewportBounds.height;

        int viewportY = viewportBounds.y;

        

        // Calculate visible range with buffer

        int loadStartY = viewportY - (viewportHeight * VIEWPORT_BUFFER_SCREENS);

        int loadEndY = viewportY + viewportHeight + (viewportHeight * VIEWPORT_BUFFER_SCREENS);

        

        int unloadBeforeY = viewportY - (viewportHeight * UNLOAD_DISTANCE_SCREENS);

        int unloadAfterY = viewportY + viewportHeight + (viewportHeight * UNLOAD_DISTANCE_SCREENS);

        

        // Find which panels need loading/unloading

        for (int i = 0; i < pagePanels.size(); i++) {

            PagePanel panel = pagePanels.get(i);

            Rectangle panelBounds = panel.getBounds();

            

            int panelTop = panelBounds.y;

            int panelBottom = panelTop + panelBounds.height;

            

            // Check if panel is in load zone

            boolean inLoadZone = panelBottom >= loadStartY && panelTop <= loadEndY;

            

            // Check if panel is far outside viewport (unload zone)

            boolean inUnloadZone = panelBottom < unloadBeforeY || panelTop > unloadAfterY;

            

            if (inLoadZone && !panel.isLoaded() && !loadingIndices.contains(i)) {

                loadImageAsync(i);

            } else if (inUnloadZone && panel.isLoaded()) {

                panel.unloadImage();

                loadedIndices.remove(i);

            }

        }

    }

    

    /**

     * Called when the viewport is resized. Updates all panel sizes.

     */

    private void onViewportResized() {

        int viewportWidth = scrollPane.getViewport().getWidth() - 20; // Account for scrollbar

        

        for (PagePanel panel : pagePanels) {

            panel.updateViewportWidth(viewportWidth);

        }

        

        containerPanel.revalidate();

        containerPanel.repaint();

        

        // Re-check visible images after resize

        onViewportChanged();

    }

    

    /**

     * Loads an image asynchronously in the background.

     */

    private void loadImageAsync(int index) {

        if (index < 0 || index >= imageFiles.size()) return;

        

        loadingIndices.add(index);

        

        imageLoaderExecutor.submit(() -> {

            try {

                File imageFile = imageFiles.get(index);

                BufferedImage image = ImageIO.read(imageFile);

                

                if (image != null) {

                    SwingUtilities.invokeLater(() -> {

                        PagePanel panel = pagePanels.get(index);

                        int viewportWidth = scrollPane.getViewport().getWidth() - 20;
                        panel.setImage(image, viewportWidth);
                        containerPanel.revalidate();
                        loadedIndices.add(index);

                        loadingIndices.remove(index);

                    });

                } else {

                    loadingIndices.remove(index);

                }

            } catch (Exception e) {

                System.err.println("Error loading image: " + imageFiles.get(index).getName() + " - " + e.getMessage());

                loadingIndices.remove(index);

            }

        });

    }

    

    /**

     * Unloads all images to free memory.

     */

    private void unloadAllImages() {

        for (PagePanel panel : pagePanels) {

            panel.unloadImage();

        }

        loadedIndices.clear();

        loadingIndices.clear();

    }

    

    /**

     * Shuts down the image loader executor.

     */

    private void shutdownLoader() {

        imageLoaderExecutor.shutdownNow();

    }

    

    /**

     * Navigates to a specific comic by its index in the collection.

     * This is the main navigation method used by both dropdown and strip buttons.

     */

    private void navigateToComicByIndex(int targetIndex) {
        if (collectionFolders == null || targetIndex < 0 || targetIndex >= collectionFolders.size()) {
            return;
        }
        
        File targetFolder = collectionFolders.get(targetIndex);
        
        // Remember control panel visibility state
        boolean wasNavBarVisible = (controlPanel != null && controlPanel.isVisible());
        
        // Detect if moving backward (to previous comic)
        boolean movingBackward = targetIndex < currentFolderIndex;
        
        // MARK AS READ: If moving forward to next comic, mark current as read
        if (!movingBackward && targetIndex > currentFolderIndex) {
            markCurrentComicAsRead();
        }
        
        // Open new scroller frame
        ImageScrollerFrame nextFrame;
        if (collectionName != null) {
            nextFrame = new ImageScrollerFrame(targetFolder, library, collectionFolders, collectionName, targetIndex, movingBackward);
        } else {
            nextFrame = new ImageScrollerFrame(targetFolder, library);
        }
        
        // Apply previous control panel visibility
        nextFrame.setControlPanelVisible(wasNavBarVisible);
        
        // Show window
        nextFrame.setVisible(true);
        
        // Request focus immediately for keyboard shortcuts
        SwingUtilities.invokeLater(() -> {
            nextFrame.scrollPane.requestFocusInWindow();
            nextFrame.requestFocus();
        });
        
        // If moving backward, scroll to bottom of the new comic
        if (movingBackward) {
            nextFrame.scrollToBottom();
        }
        
        nextFrame.currentFolderIndex = targetIndex;
        
        // Close current frame
        dispose();
    }

    

    /**

     * Navigates to another comic in the collection by direction (-1 or +1).

     * Kept for backward compatibility with any existing callers.

     */

    private void navigateToComic(int direction) {
        if (collectionFolders == null) return;
        
        int newIndex = currentFolderIndex + direction;
        navigateToComicByIndex(newIndex);
    }

    

    /**

     * Updates the enabled state and visibility of collection navigation buttons.

     */

    private void updateCollectionButtons() {

        // Strip-integrated buttons are created with visibility based on index

        // so no runtime update needed for those

    }

    

    /**

     * Sets up the 'Hold to Navigate' progress bar.

     * This bar appears at the top or bottom when holding arrow keys at the edge.

     */

    private void setupNavigationProgressBar() {

        // Create sleek progress bar with transparency (cyan pulse effect)

        navigationProgress = new JProgressBar(0, 100);

        navigationProgress.setVisible(false);

        navigationProgress.setBorderPainted(false);

        navigationProgress.setBorder(null);

        navigationProgress.setPreferredSize(new Dimension(0, 10));

        navigationProgress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));

        navigationProgress.setBackground(new Color(ApplicationService.getBackgroundDark().getRed(), ApplicationService.getBackgroundDark().getGreen(), ApplicationService.getBackgroundDark().getBlue(), 180));
        navigationProgress.setForeground(ApplicationService.getAccentColor());

        navigationProgress.setOpaque(true);

        navigationProgress.setValue(0);


        // Add to the JLayeredPane for proper layering OVER the scroll pane

        JLayeredPane layeredPane = getLayeredPane();

        navigationProgress.setBounds(0, 0, getWidth(), 10); // Sleek 10px thickness

        layeredPane.add(navigationProgress, JLayeredPane.DRAG_LAYER);


        // Initialize hold timer (1000ms = 1 second)

        holdTimer = new javax.swing.Timer(50, e -> updateHoldProgress());

        // Add resize listener to keep progress bar at correct position

        addComponentListener(new ComponentAdapter() {

            @Override

            public void componentResized(ComponentEvent e) {

                if (navigationProgress.isVisible()) {

                    updateProgressBarPosition();

                }

            }

        });

    }

    

    /**

     * Updates the position of the navigation progress bar based on direction.

     * Uses absolute positioning on the GlassPane for precise placement.

     */

    private void updateProgressBarPosition() {
        int width = getWidth();
        
        // Calculate position relative to content pane for accurate placement
        Rectangle contentBounds = getContentPane().getBounds();
        
        if (isHoldingUp) {
            // Position at top of content area
            navigationProgress.setBounds(0, contentBounds.y, width, 10);
        } else if (isHoldingDown) {
            // Position at bottom of content area
            int bottomY = contentBounds.y + contentBounds.height - 10;
            navigationProgress.setBounds(0, bottomY, width, 10);
        }
        
        // Ensure the progress bar is at the front of the popup layer
        navigationProgress.getParent().setComponentZOrder(navigationProgress, 0);
    }

    

    /**

     * Updates the hold progress bar during the timer tick.

     */

    private void updateHoldProgress() {

        if (!isHoldingUp && !isHoldingDown) {

            resetHoldNavigation();

            return;

        }

        

        // Check if still at the edge (with threshold for top to account for Previous button area)

        JScrollBar verticalBar = scrollPane.getVerticalScrollBar();

        int currentValue = verticalBar.getValue();

        int maxValue = verticalBar.getMaximum() - verticalBar.getVisibleAmount();

        

        // BROADENED: Top edge detection threshold increased to < 120px

        isAtTopEdge = (currentValue < 120);

        isAtBottomEdge = (currentValue >= maxValue - 10);

        

        boolean shouldNavigate = false;

        int direction = 0;

        

        if (isHoldingUp && isAtTopEdge && currentFolderIndex > 0) {

            // Calculate progress based on timer ticks

            int currentProgress = navigationProgress.getValue();

            currentProgress += 5; // 5% per 50ms tick = 1000ms total

            navigationProgress.setValue(Math.min(100, currentProgress));

            

            if (currentProgress >= 100) {

                shouldNavigate = true;

                direction = -1;

            }

        } else if (isHoldingDown && isAtBottomEdge && currentFolderIndex < collectionFolders.size() - 1) {

            int currentProgress = navigationProgress.getValue();

            currentProgress += 5;

            navigationProgress.setValue(Math.min(100, currentProgress));

            

            if (currentProgress >= 100) {

                shouldNavigate = true;

                direction = 1;

            }

        } else {

            // Not at edge anymore, reset

            resetHoldNavigation();

            return;

        }

        

        if (shouldNavigate) {

            resetHoldNavigation();

            // If navigating backward (direction -1), scroll to bottom of next comic
            if (direction < 0 && collectionFolders != null) {
                int newIndex = currentFolderIndex + direction;
                if (newIndex >= 0) {
                    // Navigate and then scroll to bottom
                    navigateToComic(direction);
                }
            } else {
                navigateToComic(direction);
            }

        }

    }

    

    /**

     * Resets the hold navigation state and hides the progress bar.

     */

    private void resetHoldNavigation() {

        holdTimer.stop();

        navigationProgress.setValue(0);

        navigationProgress.setVisible(false);

        isHoldingUp = false;

        isHoldingDown = false;

    }

    

    /**

     * Starts the hold-to-navigate sequence for the given direction.

     * @param direction -1 for previous comic, 1 for next comic

     */

    private void startHoldNavigation(int direction) {

        if (collectionFolders == null || collectionFolders.size() <= 1) {

            return;

        }

        

        if (direction < 0 && currentFolderIndex <= 0) {

            return; // Can't go to previous

        }

        if (direction > 0 && currentFolderIndex >= collectionFolders.size() - 1) {

            return; // Can't go to next

        }

        

        // Check if already holding in the same direction - don't reset progress

        if (direction < 0 && isHoldingUp) {

            return; // Already holding up, don't reset

        }

        if (direction > 0 && isHoldingDown) {

            return; // Already holding down, don't reset

        }

        

        if (direction < 0) {

            isHoldingUp = true;

            isHoldingDown = false;

        } else {

            isHoldingUp = false;

            isHoldingDown = true;

        }

        

        updateProgressBarPosition();

        navigationProgress.setValue(0);

        navigationProgress.setVisible(true);

        

        if (!holdTimer.isRunning()) {

            holdTimer.start();

        }

    }

    

    /**

     * Checks if we should trigger hold-to-navigate based on scroll position and key.

     * Called when a key is pressed.

     * Uses dynamic thresholds that scale with the configured scroll distance to ensure

     * hold-to-navigate works even with very large or small scroll distances.

     */

    private void checkAndStartHoldNavigation(int direction) {

        JScrollBar verticalBar = scrollPane.getVerticalScrollBar();

        int currentValue = verticalBar.getValue();

        int maxValue = verticalBar.getMaximum() - verticalBar.getVisibleAmount();

        

        // Dynamic threshold: use scroll distance + buffer (minimum 120px for top, 10px for bottom)

        // This ensures hold-to-navigate works even with extreme scroll distances

        int topThreshold = Math.max(120, scrollAmount + 20);

        int bottomThreshold = Math.max(10, scrollAmount / 10); // Proportional buffer

        

        boolean atTopEdge = (direction < 0 && currentValue < topThreshold);

        boolean atBottomEdge = (direction > 0 && currentValue >= maxValue - bottomThreshold);

        

        if (atTopEdge || atBottomEdge) {

            startHoldNavigation(direction);

        }

    }

    

    /**

     * Sets up mouse wheel listener for zoom control (Ctrl + scroll).

     */

    private void setupZoomControl() {

        // Add zoom label to title bar

        zoomLabel = new JLabel("100%");

        zoomLabel.setForeground(ApplicationService.getTextSecondary());

        zoomLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        

        // Add mouse wheel listener to scroll pane

        scrollPane.addMouseWheelListener(e -> {

            if (e.isControlDown()) {

                e.consume(); // Prevent default scroll behavior

                

                double oldZoom = zoomScale;

                int rotation = e.getWheelRotation();

                

                if (rotation < 0) {

                    // Zoom in

                    zoomScale = Math.min(MAX_ZOOM, zoomScale + ZOOM_STEP);

                } else {

                    // Zoom out

                    zoomScale = Math.max(MIN_ZOOM, zoomScale - ZOOM_STEP);

                }

                

                if (zoomScale != oldZoom) {

                    applyZoom();

                }

                return; // Exit to prevent scroll pane from handling the event

            }

        });

    }

    

    /**

     * Sets up input bindings for toggling the control panel.

     * Right-click on scroll pane and Enter key both toggle the control panel.

     */

    private void setupControlPanelToggle() {

        // Right-click mouse listener on scroll pane

        scrollPane.addMouseListener(new MouseAdapter() {

            @Override

            public void mouseClicked(MouseEvent e) {

                if (e.getButton() == MouseEvent.BUTTON3) {

                    toggleControlPanel();

                }

            }

        });

        

        // Also add to container panel to ensure right-click works on the content

        containerPanel.addMouseListener(new MouseAdapter() {

            @Override

            public void mouseClicked(MouseEvent e) {

                if (e.getButton() == MouseEvent.BUTTON3) {

                    toggleControlPanel();

                }

            }

        });

        

        // Enter key binding to toggle control panel

        KeyStroke enterKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);

        getRootPane().registerKeyboardAction(

            e -> toggleControlPanel(),

            enterKey, JComponent.WHEN_IN_FOCUSED_WINDOW

        );

        

        // Add key binding for 'T' to open tag dialog

        KeyStroke tKey = KeyStroke.getKeyStroke(KeyEvent.VK_T, 0);

        getRootPane().registerKeyboardAction(

            e -> DialogService.showFolderTagAssignmentDialog(folder, (ImageBrowserApp) library, this),

            tKey, JComponent.WHEN_IN_FOCUSED_WINDOW

        );

        

        // Add key binding for 'C' to open add to collection dialog

        KeyStroke cKey = KeyStroke.getKeyStroke(KeyEvent.VK_C, 0);

        getRootPane().registerKeyboardAction(
            e -> DialogService.showAddToCollectionDialog(this, folder, library),
            cKey, JComponent.WHEN_IN_FOCUSED_WINDOW
        );

    }

    

    /**

     * Toggles the visibility of the top control/navigation panel.

     * When hidden, the scroll pane expands to fill the space.

     */

    private void toggleControlPanel() {

        if (controlPanel != null) {

            controlPanel.setVisible(!controlPanel.isVisible());

            revalidate(); // Let the scroll pane expand to fill space

            repaint();

        }

    }

    

    /**

     * Applies the current zoom scale to all page panels without reloading images.

     * Only recalculates sizes and revalidates the layout.

     * Maintains the viewport center point to prevent jumping during zoom.

     */

    private void applyZoom() {

        // Cancel any smooth scroll animation to prevent post-zoom drift

        if (smoothScrollTimer != null && smoothScrollTimer.isRunning()) {

            smoothScrollTimer.stop();

            isSystemScrolling = false; // Reset guard when animation is cancelled

        }

        

        // Get current viewport and calculate the center point (in view coordinates)

        JViewport viewport = scrollPane.getViewport();

        Rectangle viewRect = viewport.getViewRect();

        int viewportCenterY = viewRect.y + (viewRect.height / 2);

        

        // Find which panel is at the center and the relative offset within it

        int panelIndexAtCenter = -1;

        double relativeOffsetInPanel = 0.0; // 0.0 = top, 1.0 = bottom of panel

        int cumulativeHeight = 0;

        

        for (int i = 0; i < pagePanels.size(); i++) {

            PagePanel panel = pagePanels.get(i);

            int panelHeight = panel.getHeight();

            

            if (cumulativeHeight <= viewportCenterY && viewportCenterY < cumulativeHeight + panelHeight) {

                panelIndexAtCenter = i;

                relativeOffsetInPanel = (double)(viewportCenterY - cumulativeHeight) / panelHeight;

                break;

            }

            cumulativeHeight += panelHeight;

        }

        

        // Get current viewport width

        int viewportWidth = viewport.getWidth() - 20;

        

        // Apply zoom to all panels - no image reloading needed

        for (PagePanel panel : pagePanels) {

            panel.setZoomScale(zoomScale);

            panel.recalculateSize(viewportWidth);

        }

        

        // Critical: Revalidate and layout BEFORE setting scroll value

        // This "pins" the layout to the new scaled dimensions

        containerPanel.revalidate();

        containerPanel.doLayout();

        scrollPane.getVerticalScrollBar().revalidate();

        

        // Update zoom label and save global webtoon zoom

        zoomLabel.setText((int)(zoomScale * 100) + "%");

        ApplicationService.setWebtoonZoom(zoomScale);

        

        // Restore the viewport center point after zoom

        if (panelIndexAtCenter >= 0) {

            final int targetPanelIndex = panelIndexAtCenter;

            final double targetRelativeOffset = relativeOffsetInPanel;

            

            SwingUtilities.invokeLater(() -> {

                // Calculate new scroll position to maintain the same center point

                int newCumulativeHeight = 0;

                for (int i = 0; i < targetPanelIndex && i < pagePanels.size(); i++) {

                    newCumulativeHeight += pagePanels.get(i).getHeight();

                }

                

                if (targetPanelIndex < pagePanels.size()) {

                    int newPanelHeight = pagePanels.get(targetPanelIndex).getHeight();

                    int newCenterY = newCumulativeHeight + (int)(newPanelHeight * targetRelativeOffset);

                    

                    // Center this point in the viewport

                    int newScrollValue = newCenterY - (viewport.getHeight() / 2);

                    JScrollBar verticalBar = scrollPane.getVerticalScrollBar();

                    int clampedValue = Math.max(0, Math.min(newScrollValue, verticalBar.getMaximum() - verticalBar.getVisibleAmount()));

                    verticalBar.setValue(clampedValue);

                    

                    // PIN the scrollbar values - critical for preventing drift

                    targetScrollValue = clampedValue;

                    currentScrollValue = clampedValue;

                }

            });

        } else {

            // Even if we didn't center on a panel, pin the current scroll value

            JScrollBar verticalBar = scrollPane.getVerticalScrollBar();

            int currentValue = verticalBar.getValue();

            targetScrollValue = currentValue;

            currentScrollValue = currentValue;

        }

    }

    

    /**

     * Sets up keyboard shortcuts for navigation.

     */

    private void setupKeyboardShortcuts() {

        // Close on Escape

        KeyStroke escapeKey = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);

        getRootPane().registerKeyboardAction(e -> dispose(), escapeKey, JComponent.WHEN_IN_FOCUSED_WINDOW);

        

        // Page Up / Page Down for zoom control (no Ctrl needed)

        KeyStroke pageDownKey = KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0);

        KeyStroke pageUpKey = KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0);

        

        getRootPane().registerKeyboardAction(

            e -> {

                // Zoom in with Page Down

                zoomScale = Math.min(MAX_ZOOM, zoomScale + ZOOM_STEP);

                applyZoom();

            },

            pageDownKey, JComponent.WHEN_IN_FOCUSED_WINDOW

        );

        

        getRootPane().registerKeyboardAction(

            e -> {

                // Zoom out with Page Up

                zoomScale = Math.max(MIN_ZOOM, zoomScale - ZOOM_STEP);

                applyZoom();

            },

            pageUpKey, JComponent.WHEN_IN_FOCUSED_WINDOW

        );

        

        // Home / End for start/end of scroll

        KeyStroke homeKey = KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0);

        KeyStroke endKey = KeyStroke.getKeyStroke(KeyEvent.VK_END, 0);

        

        getRootPane().registerKeyboardAction(

            e -> scrollPane.getVerticalScrollBar().setValue(0),

            homeKey, JComponent.WHEN_IN_FOCUSED_WINDOW

        );

        

        getRootPane().registerKeyboardAction(

            e -> scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getMaximum()),

            endKey, JComponent.WHEN_IN_FOCUSED_WINDOW

        );

        

        // Arrow keys for smooth animated scrolling with 'Hold to Navigate' feature

        KeyStroke upKeyPressed = KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0, false);

        KeyStroke upKeyReleased = KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0, true);

        KeyStroke downKeyPressed = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0, false);

        KeyStroke downKeyReleased = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0, true);

        

        // Up arrow pressed - start scroll or hold navigation

        getRootPane().registerKeyboardAction(

            e -> {

                checkAndStartHoldNavigation(-1);

                startSmoothScroll(-scrollAmount);

            },

            upKeyPressed, JComponent.WHEN_IN_FOCUSED_WINDOW

        );

        

        // Up arrow released - cancel hold navigation

        getRootPane().registerKeyboardAction(

            e -> {

                if (isHoldingUp) {

                    resetHoldNavigation();

                }

            },

            upKeyReleased, JComponent.WHEN_IN_FOCUSED_WINDOW

        );

        

        // Down arrow pressed - start scroll or hold navigation

        getRootPane().registerKeyboardAction(

            e -> {

                checkAndStartHoldNavigation(1);

                startSmoothScroll(scrollAmount);

            },

            downKeyPressed, JComponent.WHEN_IN_FOCUSED_WINDOW

        );

        

        // Down arrow released - cancel hold navigation

        getRootPane().registerKeyboardAction(

            e -> {

                if (isHoldingDown) {

                    resetHoldNavigation();

                }

            },

            downKeyReleased, JComponent.WHEN_IN_FOCUSED_WINDOW

        );

    }

    

    /**

     * Synchronizes smooth scroll state with actual scrollbar position.

     * Called when user manually scrolls to prevent desync.

     */

    private void syncSmoothScrollState() {

        JScrollBar verticalBar = scrollPane.getVerticalScrollBar();

        int currentValue = verticalBar.getValue();

        currentScrollValue = currentValue; // Keep as double for precision

        targetScrollValue = currentValue;

    }

    

    /**

     * Starts smooth animated scrolling to a target position.

     * @param delta The amount to scroll (positive = down, negative = up)

     */

    private void startSmoothScroll(int delta) {

        JScrollBar verticalBar = scrollPane.getVerticalScrollBar();

        int maxValue = verticalBar.getMaximum() - verticalBar.getVisibleAmount();

        

        // Clamp target strictly between 0 and max visible range

        double newTarget = currentScrollValue + delta;

        newTarget = Math.max(0, Math.min(newTarget, maxValue));

        

        // If timer is already running, just update target for fluid "chase" behavior

        if (smoothScrollTimer != null && smoothScrollTimer.isRunning()) {

            targetScrollValue = (int) newTarget;

            return;

        }

        

        // Set system scrolling guard to prevent feedback loop

        isSystemScrolling = true;

        // Starting fresh - initialize values from current scrollbar position

        currentScrollValue = verticalBar.getValue();

        targetScrollValue = (int) newTarget;

        

        // Map speed setting (5-50 stored value) to interpolation factor (0.35 to 0.08)
        // Inverted: low stored value = fast/snappy (high factor), high stored value = slow/smooth (low factor)
        // Percentage 1% (stored 5) -> 0.35 factor (fast)
        // Percentage 100% (stored 50) -> 0.08 factor (smooth)
        double percent = (smoothScrollDelay - 5) / 45.0; // 0.0 to 1.0
        double interpolationFactor = 0.35 - (percent * 0.27); // 0.35 down to 0.08

        

        // Create timer for butter-smooth animation at fixed 60fps

        smoothScrollTimer = new javax.swing.Timer(ANIMATION_FPS, e -> {

            double diff = targetScrollValue - currentScrollValue;

            

            // Apply ease-out interpolation for natural deceleration

            // Multiply by factor to get step size - adapts to remaining distance

            double step = diff * interpolationFactor;

            

            // Minimum step threshold to prevent micro-stalls near target

            if (Math.abs(step) < 0.5 && Math.abs(diff) > 0.5) {

                step = (diff > 0) ? 0.5 : -0.5;

            }

            

            currentScrollValue += step;

            

            // Stop when close enough to target

            if (Math.abs(diff) < 0.5) {

                currentScrollValue = targetScrollValue;

                smoothScrollTimer.stop();

                isSystemScrolling = false;

            }

            

            verticalBar.setValue((int) currentScrollValue);

        });

        

        smoothScrollTimer.start();

    }
    
    /**
     * Marks the current comic as read when user navigates to next comic.
     * Also updates the last read progress.
     */
    private void markCurrentComicAsRead() {
        System.out.println("DEBUG markCurrentComicAsRead (Webtoon): collectionName=" + collectionName + ", folder=" + folder);
        if (collectionName != null && library instanceof ImageBrowserApp) {
            LibraryService libraryService = ((ImageBrowserApp) library).getLibraryService();
            if (libraryService != null) {
                String comicId = libraryService.getComicId(folder);
                System.out.println("DEBUG (Webtoon): comicId=" + comicId);
                if (comicId != null) {
                    System.out.println("DEBUG (Webtoon): Marking comic as read: " + comicId);
                    libraryService.markComicAsRead(collectionName, comicId);
                    libraryService.updateLastReadComic(collectionName, comicId);
                }
            }
        }
    }
    
    /**
     * Auto-tracks a standalone comic as "currently reading" if:
     * 1. Auto-tracking is enabled in settings
     * 2. Comic is not already marked as currently reading
     */
    private void autoTrackStandaloneComic(LibraryService libraryService, String comicId) {
        if (!ApplicationService.getAutoTrackReading()) {
            return; // Auto-tracking disabled
        }
        
        ComicEntry entry = libraryService.getLibraryCache().get(comicId);
        if (entry == null) {
            return;
        }
        
        // Skip if already marked as currently reading
        if (entry.isCurrentlyReading) {
            return;
        }
        
        // Mark as currently reading and save
        entry.isCurrentlyReading = true;
        libraryService.persistLibrary();
        System.out.println("DEBUG autoTrack (Webtoon): Standalone comic '" + entry.name + "' added to Currently Reading");
    }
    
    /**
     * Auto-tracks a collection as "currently reading" when opened in webtoon mode.
     * Only tracks if:
     * 1. Auto-tracking is enabled in settings
     * 2. Collection is not already marked as currently reading
     * 3. Collection is not fully read (prevents completed series from re-tracking)
     */
    private void autoTrackCurrentlyReading(LibraryService libraryService, String collName) {
        if (!ApplicationService.getAutoTrackReading()) {
            return; // Auto-tracking disabled
        }
        
        CollectionEntry collection = libraryService.getCollection(collName);
        if (collection == null) {
            return;
        }
        
        // Skip if already marked as currently reading
        if (collection.isCurrentlyReading) {
            return;
        }
        
        // Skip if collection is fully read (prevents completed series from re-tracking)
        if (collection.readComicIds.size() >= collection.comicIds.size()) {
            return;
        }
        
        // Mark as currently reading and save
        collection.isCurrentlyReading = true;
        libraryService.saveCollection(collection);
        System.out.println("DEBUG autoTrack (Webtoon): Collection '" + collName + "' added to Currently Reading");
    }
}

