package viewer;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class ImageViewerFrame extends JFrame {
    private File folder;
    private final LibraryProvider library;
    
    // Collection binge-reading context (null if not reading from a collection)
    private final List<File> collectionFolders;
    private final String collectionName;
    private int currentComicIndex;
    
    // Current comic's images only (memory-efficient)
    private List<File> currentImages;
    private int currentIndex = 0;

    private final JLabel imageLabel = new JLabel() {
        @Override
        protected void paintComponent(Graphics g) {
            // Get the viewport's visible rectangle for proper background rendering
            JViewport viewport = imageScrollPane.getViewport();
            Rectangle visibleRect = viewport.getViewRect();
            
            // Render viewer background using viewport coordinates (prevents warping during zoom)
            BackgroundService.renderViewerBackground(g, visibleRect, this);
            
            if (originalImage != null) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int imgWidth = originalImage.getWidth();
                int imgHeight = originalImage.getHeight();
                
                // Calculate fit-to-screen scale using actual viewport dimensions
                Dimension viewportSize = viewport.getSize();
                
                // Validate viewport dimensions to prevent zero-size errors
                int maxWidth, maxHeight;
                if (viewportSize.width <= 0 || viewportSize.height <= 0) {
                    // Use label size as fallback during initialization
                    maxWidth = Math.max(getWidth(), 100);
                    maxHeight = Math.max(getHeight(), 100);
                } else {
                    maxWidth = viewportSize.width;
                    maxHeight = viewportSize.height;
                }
                
                double widthRatio = (double) maxWidth / imgWidth;
                double heightRatio = (double) maxHeight / imgHeight;
                double fitScale = Math.min(widthRatio, heightRatio);
                
                // Apply zoom on top of fit scale
                double finalScale = fitScale * zoomLevel;
                int scaledWidth = (int) (imgWidth * finalScale);
                int scaledHeight = (int) (imgHeight * finalScale);
                
                // Center the image in the available space
                int x = (getWidth() - scaledWidth) / 2;
                int y = (getHeight() - scaledHeight) / 2;
                
                g2d.drawImage(originalImage, x, y, scaledWidth, scaledHeight, null);
                g2d.dispose();
            }
        }
    };
    private JButton prevButton = new JButton(" ← ");
    private JButton nextButton = new JButton(" → ");
    private JButton firstPageButton = new JButton("|«");
    private JButton lastPageButton = new JButton("»|");
    private final JTextField pageField = new JTextField(3);
    private final JLabel totalPagesLabel = new JLabel("of 1");
    private JButton goToPageButton = new JButton("Go");
    
    // Zoom functionality
    private double zoomLevel = 1.0;
    private final double MIN_ZOOM = 0.25;
    private final double MAX_ZOOM = 4.0;
    private boolean keepZoomOnPageTurn = false;
    private boolean resetViewToTopOnNextPage = false;
    private JCheckBox keepZoomCheckBox;
    private JScrollPane imageScrollPane;
    private BufferedImage originalImage; // Cache original image
    
    // Rating components
    private JSlider ratingSlider;
    private JTextField ratingField;
    
    // Mouse drag functionality
    private Point lastMousePoint;
    private boolean isDragging = false;
    private long mousePressTime = 0;
    private final int DRAG_THRESHOLD = 5; // pixels
    
    private KeyEventDispatcher viewerKeyDispatcher;

    public ImageViewerFrame(File folder, LibraryProvider library) {
        this(folder, library, null, null);
    }
    
    public ImageViewerFrame(File folder, LibraryProvider library, List<File> collectionFolders, String collectionName) {
        super("PeraPera: " + folder.getName());
        this.folder = folder;
        this.library = library;
        this.collectionFolders = collectionFolders;
        this.collectionName = collectionName;
        this.currentComicIndex = (collectionFolders != null) ? collectionFolders.indexOf(folder) : -1;
        if (this.currentComicIndex < 0 && collectionFolders != null) {
            this.currentComicIndex = 0;
        }

        // Load viewer settings from ApplicationService
        this.zoomLevel = ApplicationService.getViewerDefaultZoom();
        boolean controlPanelVisible = ApplicationService.getControlPanelDefaultVisible();

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setUndecorated(true);
        setVisible(true);
        setLocationRelativeTo(null);

        // Add escape key listener to close fullscreen
        getRootPane().registerKeyboardAction(e -> dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);

        // ENTER toggles control panel (same as right-click behavior)
        getRootPane().registerKeyboardAction(e -> toggleControlPanel(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);

        // PageUp/PageDown zoom centered on the viewport (not mouse cursor)
        getRootPane().registerKeyboardAction(e -> zoomCentered(1.1),
            KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e -> zoomCentered(0.9),
            KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Viewer-scoped dispatcher: ensure LEFT/RIGHT (and A/D) always flip pages,
        // even if focus ends up in a text field.
        viewerKeyDispatcher = ev -> {
            if (ev.getID() != KeyEvent.KEY_PRESSED) return false;
            Window aw = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
            if (aw != ImageViewerFrame.this && !ImageViewerFrame.this.isActive()) return false;

            switch (ev.getKeyCode()) {
                case KeyEvent.VK_SHIFT:
                    if (ev.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT) {
                        keepZoomOnPageTurn = !keepZoomOnPageTurn;
                        if (keepZoomCheckBox != null) {
                            SwingUtilities.invokeLater(() -> keepZoomCheckBox.setSelected(keepZoomOnPageTurn));
                        }
                        System.out.println("keepZoomOnPageTurn=" + keepZoomOnPageTurn);
                        ev.consume();
                        return true;
                    }
                    return false;
                case KeyEvent.VK_PAGE_UP:
                    zoomCentered(1.1);
                    ev.consume();
                    return true;
                case KeyEvent.VK_PAGE_DOWN:
                    zoomCentered(0.9);
                    ev.consume();
                    return true;
                case KeyEvent.VK_LEFT:
                case KeyEvent.VK_A:
                    if (!keepZoomOnPageTurn) {
                        resetZoom();
                    } else {
                        resetViewToTopOnNextPage = true;
                    }
                    navigatePage(-1);
                    ev.consume();
                    return true;
                case KeyEvent.VK_RIGHT:
                case KeyEvent.VK_D:
                    if (!keepZoomOnPageTurn) {
                        resetZoom();
                    } else {
                        resetViewToTopOnNextPage = true;
                    }
                    navigatePage(1);
                    ev.consume();
                    return true;
                case KeyEvent.VK_UP:
                    // Scroll up when zoomed in
                    scrollVertical(-50); // Scroll up 50 pixels
                    ev.consume();
                    return true;
                case KeyEvent.VK_DOWN:
                    // Scroll down when zoomed in
                    scrollVertical(50); // Scroll down 50 pixels
                    ev.consume();
                    return true;
                default:
                    return false;
            }
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(viewerKeyDispatcher);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (viewerKeyDispatcher != null) {
                    KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(viewerKeyDispatcher);
                    viewerKeyDispatcher = null;
                }
            }
        });

        // Ensure page navigation always works regardless of which control has focus
        InputMap viewerIm = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap viewerAm = getRootPane().getActionMap();
        viewerIm.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "viewerPrevImage");
        viewerIm.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "viewerNextImage");
        viewerIm.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0), "viewerPrevImage");
        viewerIm.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0), "viewerNextImage");
        viewerAm.put("viewerPrevImage", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!keepZoomOnPageTurn) {
                    resetZoom();
                } else {
                    resetViewToTopOnNextPage = true;
                }
                navigatePage(-1);
            }
        });
        viewerAm.put("viewerNextImage", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!keepZoomOnPageTurn) {
                    resetZoom();
                } else {
                    resetViewToTopOnNextPage = true;
                }
                navigatePage(1);
            }
        });

        getContentPane().setBackground(ApplicationService.getBackgroundDark());

        currentImages = loadImages(folder);
        if (currentImages.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No images found in folder: " + folder.getName(), "No Images", JOptionPane.WARNING_MESSAGE);
            dispose();
            return;
        }

        setLayout(new BorderLayout());

        imageLabel.setHorizontalAlignment(JLabel.CENTER);
        imageLabel.setVerticalAlignment(JLabel.CENTER);
        imageLabel.setOpaque(false);
        imageLabel.setBackground(ApplicationService.getBackgroundDark());
        
        // Ensure imageLabel can receive mouse events properly
        imageLabel.setFocusable(true);
        imageLabel.setRequestFocusEnabled(true);
        
        // Create scroll pane with anti-ghosting settings
        imageScrollPane = new JScrollPane(imageLabel);
        imageScrollPane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        imageScrollPane.setBackground(ApplicationService.getBackgroundDark());
        imageScrollPane.getViewport().setBackground(ApplicationService.getBackgroundDark());
        imageScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        imageScrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        
        // Add viewport change listener to trigger repaints on scroll
        imageScrollPane.getViewport().addChangeListener(e -> imageLabel.repaint());
        
        add(imageScrollPane, BorderLayout.CENTER);
        
        // Add mouse wheel zoom listener to scroll pane
        imageScrollPane.addMouseWheelListener(e -> {
            // Consume the event to prevent scrolling
            e.consume();
            
            double zoomFactor = e.getWheelRotation() < 0 ? 1.1 : 0.9;
            double newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoomLevel * zoomFactor));
            
            // Get mouse position relative to viewport
            Point mousePos = e.getPoint();
            
            zoomAtPoint(mousePos, newZoom);
        });
        
        // Add mouse listeners for navigation and dragging
        imageLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // Always track mouse press point for click detection
                isDragging = false;
                lastMousePoint = e.getPoint();
                mousePressTime = System.currentTimeMillis();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1 || SwingUtilities.isLeftMouseButton(e)) {
                    // Only process as click if not dragging and within threshold
                    if (!isDragging && lastMousePoint != null) {
                        int deltaX = Math.abs(e.getX() - lastMousePoint.x);
                        int deltaY = Math.abs(e.getY() - lastMousePoint.y);
                        
                        if (deltaX <= DRAG_THRESHOLD && deltaY <= DRAG_THRESHOLD) {
                            // Get viewport width for zone calculations with fallback
                            int totalWidth = Math.max(imageScrollPane.getViewport().getWidth(), getWidth());
                            int x = e.getX(); // Mouse coordinates are already relative to component
                            
                            // Simple 50/50 split: Left half = Previous, Right half = Next
                            if (x < (totalWidth * 0.5)) {
                                // Left half - previous page
                                if (!keepZoomOnPageTurn) {
                                    resetZoom();
                                } else {
                                    resetViewToTopOnNextPage = true;
                                }
                                navigatePage(-1);
                            } else {
                                // Right half - next page
                                if (!keepZoomOnPageTurn) {
                                    resetZoom();
                                } else {
                                    resetViewToTopOnNextPage = true;
                                }
                                navigatePage(1);
                            }
                        }
                    }
                } else if (e.getButton() == MouseEvent.BUTTON3 || SwingUtilities.isRightMouseButton(e)) {
                    toggleControlPanel();
                }
                
                // Reset dragging state
                isDragging = false;
                setCursor(Cursor.getDefaultCursor());
            }
        });

        // Add mouse motion listener for dragging and cursor feedback
        imageLabel.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (zoomLevel > 1.0) {
                    long currentTime = System.currentTimeMillis();
                    
                    // Check if this is a drag (not just a long click)
                    if (!isDragging) {
                        int deltaX = Math.abs(e.getX() - lastMousePoint.x);
                        int deltaY = Math.abs(e.getY() - lastMousePoint.y);
                        long timeDelta = currentTime - mousePressTime;
                        
                        // Consider it a drag if moved beyond threshold OR if enough time has passed with movement
                        if ((deltaX > DRAG_THRESHOLD || deltaY > DRAG_THRESHOLD) || 
                            (timeDelta > 200 && (deltaX > 1 || deltaY > 1))) {
                            isDragging = true;
                            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                        }
                    }
                    
                    if (isDragging) {
                        // Calculate drag delta
                        int deltaX = e.getX() - lastMousePoint.x;
                        int deltaY = e.getY() - lastMousePoint.y;
                        
                        // Get current viewport position
                        JViewport viewport = imageScrollPane.getViewport();
                        Point viewPos = viewport.getViewPosition();
                        
                        // Calculate new position (inverted for natural drag feel)
                        int newX = Math.max(0, viewPos.x - deltaX);
                        int newY = Math.max(0, viewPos.y - deltaY);
                        
                        // Apply boundaries
                        Dimension viewSize = viewport.getSize();
                        Dimension imageSize = imageLabel.getSize();
                        
                        newX = Math.min(newX, Math.max(0, imageSize.width - viewSize.width));
                        newY = Math.min(newY, Math.max(0, imageSize.height - viewSize.height));
                        
                        // Update viewport position
                        viewport.setViewPosition(new Point(newX, newY));
                        
                        // Update last mouse point
                        lastMousePoint = e.getPoint();
                    }
                }
            }
            
            @Override
            public void mouseMoved(MouseEvent e) {
                // Update cursor based on position in viewport
                if (!isDragging) {
                    int totalWidth = Math.max(imageScrollPane.getViewport().getWidth(), getWidth());
                    int x = e.getX(); // Mouse coordinates are already relative to component
                    
                    // Set cursor based on zone - 50/50 split
                    if (x < (totalWidth * 0.5) || x > (totalWidth * 0.5)) {
                        // Left or right half - both are navigation zones
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    }
                }
            }
        });

        // Create control panel with centered layout (all buttons grouped in middle)
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        controlPanel.setBackground(ApplicationService.getBackgroundDark());
        controlPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        
        // === NAVIGATION PANEL (First | Prev | Page X of Y | Go | Next | Last) ===
        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        navPanel.setBackground(ApplicationService.getBackgroundDark());
        
        // Style the existing buttons with createStyledButton
        createStyledButton(firstPageButton);
        createStyledButton(prevButton);
        createStyledButton(nextButton);
        createStyledButton(lastPageButton);
        createStyledButton(goToPageButton);
        
        pageField.setBackground(ApplicationService.getInputBackground());
        pageField.setForeground(ApplicationService.getTextPrimary());
        pageField.setCaretColor(ApplicationService.getTextPrimary());
        pageField.setHorizontalAlignment(JTextField.CENTER);
        pageField.setPreferredSize(new Dimension(50, 25));
        pageField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ApplicationService.getBorderColor()),
            BorderFactory.createEmptyBorder(2, 5, 2, 5)
        ));
        
        totalPagesLabel.setForeground(ApplicationService.getTextPrimary());
        totalPagesLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        
        navPanel.add(firstPageButton);
        navPanel.add(prevButton);
        navPanel.add(pageField);
        navPanel.add(totalPagesLabel);
        navPanel.add(goToPageButton);
        navPanel.add(nextButton);
        navPanel.add(lastPageButton);
        
        controlPanel.add(navPanel);
        
        // === RATING PANEL (Label + Slider + Field) ===
        JPanel ratingPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        ratingPanel.setBackground(ApplicationService.getBackgroundDark());
        
        JLabel ratingLabel = new JLabel("Rating:");
        ratingLabel.setForeground(ApplicationService.getTextPrimary());
        ratingLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        
        ratingSlider = new JSlider(0, 100, this.library.getRating(folder));
        ratingSlider.setMajorTickSpacing(25);
        ratingSlider.setMinorTickSpacing(5);
        ratingSlider.setPaintTicks(true);
        ratingSlider.setPaintLabels(true);
        ratingSlider.setBackground(ApplicationService.getBackgroundDark());
        ratingSlider.setForeground(ApplicationService.getTextPrimary());
        ratingSlider.setPreferredSize(new Dimension(150, 40));
        ratingSlider.setFocusable(false);
        
        ratingField = new JTextField(String.valueOf(this.library.getRating(folder)), 3);
        ratingField.setBackground(ApplicationService.getInputBackground());
        ratingField.setForeground(ApplicationService.getTextPrimary());
        ratingField.setCaretColor(ApplicationService.getTextPrimary());
        ratingField.setHorizontalAlignment(JTextField.CENTER);
        ratingField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        ratingField.setPreferredSize(new Dimension(40, 25));
        ratingField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ApplicationService.getBorderColor()),
            BorderFactory.createEmptyBorder(2, 5, 2, 5)
        ));
        
        // Sync slider and text field
        ratingSlider.addChangeListener(e -> {
            int value = ratingSlider.getValue();
            ratingField.setText(String.valueOf(value));
            // Only save when user releases the slider to avoid lag
            if (!ratingSlider.getValueIsAdjusting()) {
                this.library.setRating(folder, value);
            }
        });
        
        ratingField.addActionListener(e -> {
            try {
                int rating = Integer.parseInt(ratingField.getText().trim());
                if (rating >= 0 && rating <= 100) {
                    ratingSlider.setValue(rating);
                    this.library.setRating(folder, rating);
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
        
        controlPanel.add(ratingPanel);
        
        // === KEEP ZOOM CHECKBOX ===
        keepZoomCheckBox = new JCheckBox("Keep Zoom");
        keepZoomCheckBox.setBackground(ApplicationService.getBackgroundDark());
        keepZoomCheckBox.setForeground(ApplicationService.getTextPrimary());
        keepZoomCheckBox.setFocusable(false);
        keepZoomCheckBox.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        
        controlPanel.add(keepZoomCheckBox);
        
        // === ACTION BUTTONS (+ Collection, Tag) ===
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        actionPanel.setBackground(ApplicationService.getBackgroundDark());
        
        JButton addToCollectionButton = createStyledButton("+ Collection");
        addToCollectionButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        addToCollectionButton.setPreferredSize(new Dimension(90, 28));
        
        JButton addTagButton = createStyledButton("Edit Tags");
        addTagButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        addTagButton.setPreferredSize(new Dimension(80, 28));
        
        actionPanel.add(addToCollectionButton);
        actionPanel.add(addTagButton);
        
        controlPanel.add(actionPanel);
        
        // === COLLECTION SELECTOR (if in collection mode) ===
        JComboBox<String> comicSelector = null;
        JButton editCollectionButton = null;
        
        if (collectionFolders != null && !collectionFolders.isEmpty()) {
            JPanel collectionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
            collectionPanel.setBackground(ApplicationService.getBackgroundDark());
            
            comicSelector = new JComboBox<>();
            for (File f : collectionFolders) {
                comicSelector.addItem(f.getName());
            }
            comicSelector.setSelectedIndex(currentComicIndex);
            comicSelector.setBackground(ApplicationService.getInputBackground());
            comicSelector.setForeground(ApplicationService.getTextPrimary());
            comicSelector.setPreferredSize(new Dimension(120, 25));
            comicSelector.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            
            editCollectionButton = createStyledButton("Edit Coll.");
            editCollectionButton.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            editCollectionButton.setPreferredSize(new Dimension(75, 28));
            editCollectionButton.addActionListener(e -> {
                if (collectionName != null && library instanceof ImageBrowserApp) {
                    Collection collection = library.getCollections().get(collectionName);
                    if (collection != null) {
                        DialogService.showEditCollectionDialog((ImageBrowserApp) library, collection);
                    }
                }
            });
            
            collectionPanel.add(comicSelector);
            collectionPanel.add(editCollectionButton);
            
            controlPanel.add(collectionPanel);
        }
        
        // === CLOSE BUTTON ===
        JButton closeButton = createStyledButton("Close");
        controlPanel.add(closeButton);

        // Add action listeners for buttons
        firstPageButton.addActionListener(e -> {
            if (!keepZoomOnPageTurn) {
                resetZoom();
            } else {
                resetViewToTopOnNextPage = true;
            }
            // In Manga mode, first page button goes to last physical page (start of reading)
            int targetIndex = ApplicationService.isMangaMode() ? currentImages.size() - 1 : 0;
            showImage(targetIndex);
        });
        
        prevButton.addActionListener(e -> {
            if (!keepZoomOnPageTurn) {
                resetZoom();
            } else {
                resetViewToTopOnNextPage = true;
            }
            navigatePage(-1);
        });
        
        nextButton.addActionListener(e -> {
            if (!keepZoomOnPageTurn) {
                resetZoom();
            } else {
                resetViewToTopOnNextPage = true;
            }
            navigatePage(1);
        });
        
        lastPageButton.addActionListener(e -> {
            if (!keepZoomOnPageTurn) {
                resetZoom();
            } else {
                resetViewToTopOnNextPage = true;
            }
            // In Manga mode, last page button goes to first physical page (end of reading)
            int targetIndex = ApplicationService.isMangaMode() ? 0 : currentImages.size() - 1;
            showImage(targetIndex);
            // Mark as read when jumping to last page
            markCurrentComicAsRead();
        });
        
        goToPageButton.addActionListener(e -> goToPage());
        
        addToCollectionButton.addActionListener(e -> showAddToCollectionDialog());
        addTagButton.addActionListener(e -> DialogService.showFolderTagAssignmentDialog(folder, (ImageBrowserApp) library, this));
        closeButton.addActionListener(e -> dispose());

        // Sync comic selector when manually changed
        if (comicSelector != null) {
            final JComboBox<String> selectorRef = comicSelector;
            comicSelector.addActionListener(e -> {
                int selected = selectorRef.getSelectedIndex();
                if (selected != currentComicIndex && selected >= 0) {
                    loadComicFolder(collectionFolders.get(selected));
                    currentComicIndex = selected;
                    currentIndex = 0;
                    showImage(0);
                }
            });
        }

        add(controlPanel, BorderLayout.SOUTH);
        
        // Apply control panel visibility setting from preferences
        controlPanel.setVisible(controlPanelVisible);

        // Add component listener to handle window resize
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                showImage(currentIndex);
            }
        });

        setupKeyboardNavigation();
        showImage(currentIndex);
    }
    
    private void navigatePage(int delta) {
        if (currentImages == null || currentImages.isEmpty()) return;
        
        // Reverse navigation direction if in Manga mode
        if (ApplicationService.isMangaMode()) {
            delta = -delta;
        }
        
        int newIndex = currentIndex + delta;
        
        if (delta > 0 && newIndex >= currentImages.size()) {
            // Next page: need to go to next comic
            // Mark current comic as read since user finished it
            markCurrentComicAsRead();
            
            if (collectionFolders != null && currentComicIndex < collectionFolders.size() - 1) {
                currentComicIndex++;
                loadComicFolder(collectionFolders.get(currentComicIndex));
                currentIndex = 0;
                showImage(0);
            } else {
                // Loop to first page of current comic
                currentIndex = 0;
                showImage(0);
            }
        } else if (delta < 0 && newIndex < 0) {
            // Previous page: need to go to previous comic
            if (collectionFolders != null && currentComicIndex > 0) {
                currentComicIndex--;
                loadComicFolder(collectionFolders.get(currentComicIndex));
                currentIndex = currentImages.size() - 1;
                showImage(currentIndex);
            } else {
                // Loop to last page of current comic
                currentIndex = currentImages.size() - 1;
                showImage(currentIndex);
            }
        } else {
            // Normal page navigation within current comic
            currentIndex = newIndex;
            showImage(currentIndex);
            
            // Auto-mark as read when reaching the last page
            System.out.println("DEBUG: currentIndex=" + currentIndex + ", total=" + currentImages.size());
            if (currentImages != null && currentIndex == currentImages.size() - 1) {
                System.out.println("DEBUG: On last page, calling markCurrentComicAsRead");
                markCurrentComicAsRead();
            }
        }
    }
    
    private void loadComicFolder(File newFolder) {
        // Free memory from previous comic
        originalImage = null;
        System.gc();
        
        // Update state
        this.folder = newFolder;
        this.currentImages = loadImages(newFolder);
        
        // IMMEDIATELY update lastReadComicId when loading a new comic in collection mode
        if (collectionName != null) {
            LibraryService libraryService = ((ImageBrowserApp) library).getLibraryService();
            if (libraryService != null) {
                String comicId = libraryService.getComicId(newFolder);
                if (comicId != null) {
                    libraryService.updateLastReadComic(collectionName, comicId);
                    System.out.println("DEBUG loadComicFolder: Updated lastRead to " + comicId);
                    // Auto-track currently reading when navigating in collection mode
                    autoTrackCurrentlyReading(libraryService, collectionName);
                }
            }
        }
        
        // Auto-track standalone comics (not in collection mode) when loading
        if (collectionName == null && library instanceof ImageBrowserApp) {
            LibraryService libraryService = ((ImageBrowserApp) library).getLibraryService();
            if (libraryService != null) {
                String comicId = libraryService.getComicId(newFolder);
                if (comicId != null) {
                    autoTrackStandaloneComic(libraryService, comicId);
                }
            }
        }
        
        // Update title
        if (collectionName != null) {
            setTitle("PeraPera Collection: " + collectionName + " - " + folder.getName());
        } else {
            setTitle("PeraPera: " + folder.getName());
        }
        
        // Update comic selector if visible
        if (collectionFolders != null && currentComicIndex >= 0) {
            // The selector will be updated via navigatePage calling showImage which triggers updatePageDisplay
        }
    }

    private void createStyledButton(JButton button) {
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
    }
    
    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        createStyledButton(button);
        return button;
    }

    private void setupKeyboardNavigation() {
        InputMap inputMap = imageLabel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = imageLabel.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "prevImage");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0), "prevImage");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "nextImage");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0), "nextImage");

        actionMap.put("prevImage", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!keepZoomOnPageTurn) {
                    resetZoom();
                } else {
                    resetViewToTopOnNextPage = true;
                }
                navigatePage(-1);
            }
        });

        actionMap.put("nextImage", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!keepZoomOnPageTurn) {
                    resetZoom();
                } else {
                    resetViewToTopOnNextPage = true;
                }
                navigatePage(1);
            }
        });
    }

    private List<File> loadImages(File folder) {
        File[] files = folder.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".bmp") || lower.endsWith(".webp");
        });

        if (files == null) return Collections.emptyList();

        List<File> list = Arrays.asList(files);
        list.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    private void showImage(int index) {
        if (currentImages == null || currentImages.isEmpty()) return;

        if (index < 0) index = currentImages.size() - 1;
        else if (index >= currentImages.size()) index = 0;

        currentIndex = index;

        try {
            BufferedImage img = ImageIO.read(currentImages.get(currentIndex));
            if (img == null) {
                JOptionPane.showMessageDialog(this, "Failed to load image: " + currentImages.get(currentIndex).getName(), "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Cache original image for fast zooming
            originalImage = img;
            int imgWidth = img.getWidth();
            int imgHeight = img.getHeight();
            
            // Calculate fit-to-screen size using actual viewport dimensions
            JViewport viewport = imageScrollPane.getViewport();
            Dimension viewportSize = viewport.getSize();
            Point viewPosToUse = viewport.getViewPosition();
            if (resetViewToTopOnNextPage) {
                viewPosToUse = new Point(0, 0);
                resetViewToTopOnNextPage = false;
            }
            final Point oldViewPos = viewPosToUse;
            
            // Validate viewport dimensions to prevent zero-size errors
            int maxWidth, maxHeight;
            if (viewportSize.width <= 0 || viewportSize.height <= 0) {
                // Use window size as fallback during initialization
                Dimension windowSize = getContentPane().getSize();
                maxWidth = Math.max(windowSize.width - 40, 100);
                maxHeight = Math.max(windowSize.height - 120, 100);
            } else {
                maxWidth = viewportSize.width;
                maxHeight = viewportSize.height;
            }
            
            double widthRatio = (double) maxWidth / imgWidth;
            double heightRatio = (double) maxHeight / imgHeight;
            double fitScale = Math.min(widthRatio, heightRatio);
            
            // Apply zoom on top of fit scale
            double finalScale = fitScale * zoomLevel;
            int scaledWidth = (int) (imgWidth * finalScale);
            int scaledHeight = (int) (imgHeight * finalScale);
            
            if (zoomLevel == 1.0) {
                // Use ImageIcon for fit-to-screen (faster for simple case)
                Image scaled = img.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
                imageLabel.setIcon(new ImageIcon(scaled));
                imageLabel.setPreferredSize(new Dimension(scaledWidth, scaledHeight));
            } else {
                // Use custom painting for zoom (much faster)
                imageLabel.setIcon(null);
                imageLabel.setPreferredSize(new Dimension(scaledWidth, scaledHeight));
            }
            
            imageLabel.revalidate();
            imageLabel.repaint();
            
            // Center image when zoom is 1.0 (fit to screen)
            if (zoomLevel == 1.0) {
                int centerX = Math.max(0, (scaledWidth - viewport.getWidth()) / 2);
                int centerY = Math.max(0, (scaledHeight - viewport.getHeight()) / 2);
                viewport.setViewPosition(new Point(centerX, centerY));
            } else {
                // Keep the previous scroll position but clamp to new image bounds.
                // Do this after layout settles so viewport sizes are correct.
                SwingUtilities.invokeLater(() -> {
                    int maxX = Math.max(0, scaledWidth - viewport.getWidth());
                    int maxY = Math.max(0, scaledHeight - viewport.getHeight());
                    int newX = Math.max(0, Math.min(oldViewPos.x, maxX));
                    int newY = Math.max(0, Math.min(oldViewPos.y, maxY));
                    viewport.setViewPosition(new Point(newX, newY));
                    imageLabel.repaint();
                });
            }
            
            // Update page display
            updatePageDisplay();

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load image: " + currentImages.get(currentIndex).getName(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void zoomAtPoint(Point mousePos, double newZoom) {
        if (currentImages == null || currentImages.isEmpty() || originalImage == null) return;
        
        // Store old zoom level for calculations
        double oldZoom = zoomLevel;
        
        // Update zoom level
        zoomLevel = newZoom;
        
        // Get viewport and current view position
        JViewport viewport = imageScrollPane.getViewport();
        Point viewPos = viewport.getViewPosition();
        
        // Calculate mouse position in image coordinates
        int mouseX = viewPos.x + mousePos.x;
        int mouseY = viewPos.y + mousePos.y;
        
        // Update display without reloading image
        int imgWidth = originalImage.getWidth();
        int imgHeight = originalImage.getHeight();
        
        // Calculate fit-to-screen size when zoom is 1.0
        int maxWidth = viewport.getWidth();
        int maxHeight = viewport.getHeight();
        
        double widthRatio = (double) maxWidth / imgWidth;
        double heightRatio = (double) maxHeight / imgHeight;
        double fitScale = Math.min(widthRatio, heightRatio);
        
        // Apply zoom on top of fit scale
        double finalScale = fitScale * zoomLevel;
        int scaledWidth = (int) (imgWidth * finalScale);
        int scaledHeight = (int) (imgHeight * finalScale);
        
        // Always use custom painting for consistency
        imageLabel.setIcon(null);
        imageLabel.setPreferredSize(new Dimension(scaledWidth, scaledHeight));
        imageLabel.revalidate();
        imageLabel.repaint(); // Force repaint for custom painting
        
        // Calculate new scroll position to keep mouse point fixed
        // Account for image centering in the label
        int imageX = (imageLabel.getWidth() - scaledWidth) / 2;
        int imageY = (imageLabel.getHeight() - scaledHeight) / 2;
        
        // Adjust mouse position to account for centering
        int adjustedMouseX = mouseX - imageX;
        int adjustedMouseY = mouseY - imageY;
        
        double zoomRatio = newZoom / oldZoom;
        int newViewX = (int)(adjustedMouseX * zoomRatio) - adjustedMouseX + viewPos.x;
        int newViewY = (int)(adjustedMouseY * zoomRatio) - adjustedMouseY + viewPos.y;
        
        // Ensure we don't scroll beyond image bounds
        Dimension viewportSize = viewport.getSize();
        newViewX = Math.max(0, Math.min(newViewX, scaledWidth - viewportSize.width));
        newViewY = Math.max(0, Math.min(newViewY, scaledHeight - viewportSize.height));
        
        viewport.setViewPosition(new Point(newViewX, newViewY));
        
        // Update page display
        updatePageDisplay();
    }

    private void zoomCentered(double factor) {
        if (imageScrollPane == null) {
            Component sp = SwingUtilities.getAncestorOfClass(JScrollPane.class, imageLabel);
            if (sp instanceof JScrollPane) {
                imageScrollPane = (JScrollPane) sp;
            }
        }
        if (imageScrollPane == null) return;
        JViewport viewport = imageScrollPane.getViewport();
        if (viewport == null) return;

        double newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoomLevel * factor));
        Point center = new Point(Math.max(0, viewport.getWidth() / 2), Math.max(0, viewport.getHeight() / 2));
        zoomAtPoint(center, newZoom);
    }
    
    private void resetZoom() {
        zoomLevel = 1.0;
        if (imageScrollPane != null) {
            JViewport viewport = imageScrollPane.getViewport();
            viewport.setViewPosition(new Point(0, 0));
        }
    }
    
    private void scrollVertical(int pixels) {
        if (imageScrollPane == null) return;
        
        JViewport viewport = imageScrollPane.getViewport();
        Point currentPos = viewport.getViewPosition();
        
        // Calculate new position
        int newY = currentPos.y + pixels;
        
        // Bound the scrolling to valid range
        int maxY = Math.max(0, imageLabel.getHeight() - viewport.getHeight());
        newY = Math.max(0, Math.min(newY, maxY));
        
        // Set new position
        viewport.setViewPosition(new Point(currentPos.x, newY));
    }
    
    private void toggleControlPanel() {
        JPanel controls = (JPanel) getContentPane().getComponent(1); // Bottom controls panel
        controls.setVisible(!controls.isVisible());
        
        // Revalidate to adjust image display
        getContentPane().revalidate();
        getContentPane().repaint();
        
        // Recenter image after panel toggle
        if (originalImage != null) {
            SwingUtilities.invokeLater(() -> showImage(currentIndex));
        }
    }
    
    private void showAddToCollectionDialog() {
        JDialog dialog = new JDialog(this, "PeraPera - Add to Collection", true);
        dialog.setSize(450, 350);
        dialog.setLocationRelativeTo(this);
        
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ApplicationService.getBackgroundDark());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        JLabel label = new JLabel("Add '" + folder.getName() + "' to collection:", SwingConstants.CENTER);
        label.setForeground(ApplicationService.getTextPrimary());
        panel.add(label, BorderLayout.NORTH);
        
        // Collection options panel
        JPanel optionsPanel = new JPanel(new GridLayout(2, 1, 10, 10));
        optionsPanel.setBackground(ApplicationService.getBackgroundDark());
        
        // Existing collections
        JPanel existingPanel = new JPanel(new BorderLayout());
        existingPanel.setBackground(ApplicationService.getBackgroundDark());
        
        JLabel existingLabel = new JLabel("Existing Collections:");
        existingLabel.setForeground(ApplicationService.getTextPrimary());
        existingPanel.add(existingLabel, BorderLayout.NORTH);
        
        // Add search field for collections
        JTextField collectionSearchField = new JTextField();
        collectionSearchField.setBackground(ApplicationService.getInputBackground());
        collectionSearchField.setForeground(ApplicationService.getTextPrimary());
        collectionSearchField.setCaretColor(ApplicationService.getTextPrimary());
        collectionSearchField.setToolTipText("Search collections...");
        existingPanel.add(collectionSearchField, BorderLayout.CENTER);
        
        DefaultListModel<String> existingModel = new DefaultListModel<>();
        JList<String> existingList = new JList<>(existingModel);
        existingList.setBackground(ApplicationService.getBackgroundDark());
        existingList.setForeground(ApplicationService.getTextPrimary());
        existingList.setSelectionBackground(ApplicationService.getBackgroundLight());
        
        // Populate collections
        updateCollectionsList(existingModel, "");
        
        // Add search listener
        collectionSearchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateCollectionsList(existingModel, collectionSearchField.getText()); }
            public void removeUpdate(DocumentEvent e) { updateCollectionsList(existingModel, collectionSearchField.getText()); }
            public void changedUpdate(DocumentEvent e) { updateCollectionsList(existingModel, collectionSearchField.getText()); }
        });
        
        JScrollPane existingScroll = new JScrollPane(existingList);
        existingScroll.setPreferredSize(new Dimension(400, 100));
        existingPanel.add(existingScroll, BorderLayout.SOUTH);
        
        optionsPanel.add(existingPanel);
        
        // New collection
        JPanel newPanel = new JPanel(new BorderLayout());
        newPanel.setBackground(ApplicationService.getBackgroundDark());
        
        JLabel newLabel = new JLabel("Or Create New Collection:");
        newLabel.setForeground(ApplicationService.getTextPrimary());
        newPanel.add(newLabel, BorderLayout.NORTH);
        
        JTextField newCollectionField = new JTextField();
        newCollectionField.setBackground(ApplicationService.getInputBackground());
        newCollectionField.setForeground(ApplicationService.getTextPrimary());
        newCollectionField.setCaretColor(ApplicationService.getTextPrimary());
        newPanel.add(newCollectionField, BorderLayout.CENTER);
        
        optionsPanel.add(newPanel);
        
        panel.add(optionsPanel, BorderLayout.CENTER);
        
        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.setBackground(ApplicationService.getBackgroundDark());
        
        JButton addButton = new JButton("Add to Collection");
        JButton cancelButton = new JButton("Cancel");
        
        addButton.addActionListener(e -> {
            // Check if existing collection is selected
            String selected = existingList.getSelectedValue();
            if (selected != null) {
                String collectionName = selected.split(" \\(")[0];
                
                // Get LibraryService for JSON collections
                LibraryService libraryService = null;
                if (library instanceof ImageBrowserApp) {
                    libraryService = ((ImageBrowserApp) library).getLibraryService();
                }
                
                // Try to get collection from old map first, then from LibraryService
                Collection collection = library.getCollections().get(collectionName);
                CollectionEntry entry = null;
                
                if (collection == null && libraryService != null) {
                    // Try to get from LibraryService and create old Collection
                    entry = libraryService.getCollection(collectionName);
                    if (entry != null) {
                        // Create old Collection for compatibility
                        collection = new Collection(entry.name);
                        collection.rating = entry.rating;
                        collection.tags.addAll(entry.tags);
                        collection.selectedThumbnailComic = entry.selectedThumbnailComicId;
                        // Convert comic IDs to names
                        for (String comicId : entry.comicIds) {
                            ComicEntry comic = libraryService.getLibraryCache().get(comicId);
                            if (comic != null) {
                                collection.comicNames.add(comic.name);
                            }
                        }
                        // Add to old collections map
                        library.getCollections().put(collectionName, collection);
                    }
                } else if (collection != null && libraryService != null) {
                    entry = libraryService.getCollection(collectionName);
                }
                
                if (collection == null) {
                    JOptionPane.showMessageDialog(dialog, 
                        "Collection '" + collectionName + "' not found!", 
                        "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                if (collection.comicNames.contains(folder.getName()) || collection.comicIds.contains(libraryService.getComicId(folder))) {
                    JOptionPane.showMessageDialog(dialog,
                        "'" + folder.getName() + "' is already in collection '" + collectionName + "'",
                        "Already in Collection", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    collection.comicNames.add(folder.getName());
                    
                    // Also add UUID to collection.comicIds to prevent name collisions
                    if (libraryService != null) {
                        String comicId = libraryService.getComicId(folder);
                        if (comicId != null && !collection.comicIds.contains(comicId)) {
                            collection.comicIds.add(comicId);
                        }
                    }
                    
                    // Also add to CollectionEntry in LibraryService (JSON)
                    if (libraryService != null) {
                        if (entry == null) {
                            entry = new CollectionEntry(collectionName);
                        }
                        String comicId = libraryService.getComicId(folder);
                        if (comicId != null && !entry.comicIds.contains(comicId)) {
                            entry.comicIds.add(comicId);
                            libraryService.saveCollection(entry);
                        }
                    }
                    
                    library.saveCollections();
                    library.refreshDisplay();
                    dialog.dispose();
                }
            } else {
                // Check if new collection name is entered
                String newCollectionName = newCollectionField.getText().trim();
                if (!newCollectionName.isEmpty()) {
                    // Get LibraryService for JSON collections
                    LibraryService libraryService = null;
                    if (library instanceof ImageBrowserApp) {
                        libraryService = ((ImageBrowserApp) library).getLibraryService();
                    }
                    
                    if (libraryService != null && libraryService.getCollection(newCollectionName) != null) {
                        JOptionPane.showMessageDialog(dialog, 
                            "Collection '" + newCollectionName + "' already exists!", 
                            "Collection Exists", JOptionPane.ERROR_MESSAGE);
                    } else if (library.getCollections().containsKey(newCollectionName)) {
                        JOptionPane.showMessageDialog(dialog, 
                            "Collection '" + newCollectionName + "' already exists!", 
                            "Collection Exists", JOptionPane.ERROR_MESSAGE);
                    } else {
                        // Create new collection using LibraryService (JSON)
                        if (libraryService != null) {
                            CollectionEntry newCollection = new CollectionEntry(newCollectionName);
                            // Find the comic ID for this folder
                            String comicId = libraryService.getComicId(folder);
                            if (comicId != null) {
                                newCollection.comicIds.add(comicId);
                            }
                            libraryService.saveCollection(newCollection);
                            // IMMEDIATELY update lastReadComicId when opening via Read Collection button
                            libraryService.updateLastReadComic(newCollectionName, comicId);
                            // Auto-track currently reading for new collections
                            autoTrackCurrentlyReading(libraryService, newCollectionName);
                            System.out.println("DEBUG ReadCollection: Updated lastRead to " + comicId);
                        }
                        
                        // Also add to old collection map for compatibility
                        Collection oldCollection = new Collection(newCollectionName);
                        oldCollection.comicNames.add(folder.getName());
                        // Add UUID to prevent name collisions
                        if (libraryService != null) {
                            String comicId = libraryService.getComicId(folder);
                            if (comicId != null && !oldCollection.comicIds.contains(comicId)) {
                                oldCollection.comicIds.add(comicId);
                            }
                        }
                        library.getCollections().put(newCollectionName, oldCollection);
                        
                        library.saveCollections();
                        library.refreshDisplay();
                        dialog.dispose();
                    }
                } else {
                    JOptionPane.showMessageDialog(dialog, 
                        "Please select an existing collection or enter a new collection name", 
                        "No Selection", JOptionPane.WARNING_MESSAGE);
                }
            }
        });
        
        cancelButton.addActionListener(e -> dialog.dispose());
        
        buttonPanel.add(addButton);
        buttonPanel.add(cancelButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        dialog.add(panel);
        dialog.setVisible(true);
    }
    
    private void updateCollectionsList(DefaultListModel<String> model, String searchText) {
        model.clear();
        String searchLower = searchText.toLowerCase();
        
        // Get collections from LibraryService (JSON) if available
        Map<String, CollectionEntry> jsonCollections = null;
        if (library instanceof ImageBrowserApp) {
            LibraryService libraryService = ((ImageBrowserApp) library).getLibraryService();
            if (libraryService != null) {
                jsonCollections = libraryService.getCollectionsCache();
            }
        }
        
        // Use JSON collections if available, otherwise fall back to old collections
        if (jsonCollections != null && !jsonCollections.isEmpty()) {
            for (String collectionName : jsonCollections.keySet()) {
                CollectionEntry entry = jsonCollections.get(collectionName);
                
                // Filter by search text
                if (searchText.isEmpty() || 
                    entry.name.toLowerCase().contains(searchLower) ||
                    String.join(", ", entry.tags).toLowerCase().contains(searchLower)) {
                    
                    String display = entry.name + " (" + entry.comicIds.size() + " Titles)";
                    if (entry.rating > 0) {
                        display += " " + entry.rating + "/100";
                    }
                    model.addElement(display);
                }
            }
        } else {
            // Fallback to old collections
            for (String collectionName : library.getCollections().keySet()) {
                Collection collection = library.getCollections().get(collectionName);
                
                // Filter by search text
                if (searchText.isEmpty() || 
                    collection.name.toLowerCase().contains(searchLower) ||
                    String.join(", ", collection.tags).toLowerCase().contains(searchLower)) {
                    
                    String display = collection.name + " (" + collection.comicNames.size() + " Titles)";
                    if (collection.rating > 0) {
                        display += " " + collection.rating + "/100";
                    }
                    model.addElement(display);
                }
            }
        }
    }
    
    private void updatePageDisplay() {
        int currentPage = currentIndex + 1;
        int totalPages = (currentImages != null) ? currentImages.size() : 0;
        pageField.setText(String.valueOf(currentPage));
        totalPagesLabel.setText("of " + totalPages);
        
        // Update button states
        // In Manga mode, all navigation buttons have reversed functionality
        if (ApplicationService.isMangaMode()) {
            // In Manga: " ← " goes to next page, " → " goes to previous page
            // |« goes to last page (start of manga reading), »| goes to first page (end of manga reading)
            firstPageButton.setEnabled(currentImages != null && currentIndex < currentImages.size() - 1);
            lastPageButton.setEnabled(currentIndex > 0);
            prevButton.setEnabled(currentImages != null && currentIndex < currentImages.size() - 1);
            nextButton.setEnabled(currentIndex > 0);
        } else {
            // Normal mode: " ← " goes to previous page, " → " goes to next page
            // |« goes to first page, »| goes to last page
            firstPageButton.setEnabled(currentIndex > 0);
            lastPageButton.setEnabled(currentImages != null && currentIndex < currentImages.size() - 1);
            prevButton.setEnabled(currentIndex > 0);
            nextButton.setEnabled(currentImages != null && currentIndex < currentImages.size() - 1);
        }
        
        // Update window title with page and zoom info
        if (collectionName != null) {
            setTitle("PeraPera Collection: " + collectionName + " - " + folder.getName() + " - Page " + currentPage + "/" + totalPages + " - " + (int)(zoomLevel * 100) + "%");
        } else {
            setTitle("PeraPera: " + folder.getName() + " - Page " + currentPage + "/" + totalPages + " - " + (int)(zoomLevel * 100) + "%");
        }
    }
    
    private void goToPage() {
        try {
            int page = Integer.parseInt(pageField.getText().trim());
            if (currentImages != null && page >= 1 && page <= currentImages.size()) {
                resetZoom();
                showImage(page - 1);
                // Auto-mark as read when reaching the last page
                System.out.println("DEBUG: currentIndex=" + currentIndex + ", total=" + currentImages.size());
                if (currentImages != null && currentIndex == currentImages.size() - 1) {
                    System.out.println("DEBUG: On last page, calling markCurrentComicAsRead");
                    markCurrentComicAsRead();
                }
            } else {
                JOptionPane.showMessageDialog(this, "Please enter a page number between 1 and " + ((currentImages != null) ? currentImages.size() : 0), "Invalid Page", JOptionPane.WARNING_MESSAGE);
                updatePageDisplay(); // Reset to current page
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Please enter a valid page number", "Invalid Input", JOptionPane.WARNING_MESSAGE);
            updatePageDisplay(); // Reset to current page
        }
    }
    
    /**
     * Marks the current comic as read when user reaches the last page.
     * Also updates the last read progress.
     */
    private void markCurrentComicAsRead() {
        System.out.println("DEBUG markCurrentComicAsRead: collectionName=" + collectionName + ", folder=" + folder);
        if (collectionName != null && library instanceof ImageBrowserApp) {
            LibraryService libraryService = ((ImageBrowserApp) library).getLibraryService();
            if (libraryService != null) {
                String comicId = libraryService.getComicId(folder);
                System.out.println("DEBUG: comicId=" + comicId);
                if (comicId != null) {
                    System.out.println("DEBUG: Marking comic as read: " + comicId);
                    libraryService.markComicAsRead(collectionName, comicId);
                    libraryService.updateLastReadComic(collectionName, comicId);
                    // Auto-track currently reading (will check if collection is not fully read)
                    autoTrackCurrentlyReading(libraryService, collectionName);
                }
            }
        }
    }
    
    /**
     * Auto-tracks the collection as "currently reading" if:
     * 1. Auto-tracking is enabled in settings
     * 2. Collection is not already fully read (prevents completed series from re-tracking)
     */
    private void autoTrackCurrentlyReading(LibraryService libraryService, String collName) {
        if (!ApplicationService.getAutoTrackReading()) {
            return; // Auto-tracking disabled
        }
        
        CollectionEntry entry = libraryService.getCollection(collName);
        if (entry == null) {
            return;
        }
        
        // Skip if already marked as currently reading
        if (entry.isCurrentlyReading) {
            return;
        }
        
        // Skip if collection is fully read (prevent completed series from re-tracking)
        if (entry.readComicIds.size() >= entry.comicIds.size()) {
            System.out.println("DEBUG autoTrack: Collection '" + collName + "' is fully read, skipping auto-track");
            return;
        }
        
        // Mark as currently reading and save
        entry.isCurrentlyReading = true;
        libraryService.saveCollection(entry);
        System.out.println("DEBUG autoTrack: Collection '" + collName + "' added to Currently Reading");
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
        System.out.println("DEBUG autoTrack: Standalone comic '" + entry.name + "' added to Currently Reading");
    }
}
