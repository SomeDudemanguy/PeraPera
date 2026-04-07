package viewer;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.swing.*;

/**
 * Custom panel representing a single page in webtoon scroller mode.
 * Handles lazy loading, zoom, and display of a single image page.
 */
public class PagePanel extends JPanel {
    private final File imageFile;
    private final int pageNumber;
    private final int totalPages;
    private final int originalWidth;
    private final int originalHeight;
    private BufferedImage image;
    private int viewportWidth;
    private double zoomScale = 1.0;
    private int preferredHeight;

    /**
     * Creates a PagePanel with pre-calculated dimensions for accurate scrollbar sizing.
     */
    public PagePanel(File imageFile, int pageNumber, int totalPages, Dimension imageSize, int viewportWidth) {
        this.imageFile = imageFile;
        this.pageNumber = pageNumber;
        this.totalPages = totalPages;
        this.originalWidth = imageSize.width;
        this.originalHeight = imageSize.height;
        this.viewportWidth = viewportWidth;

        // Calculate initial height using aspect ratio
        recalculateSize(viewportWidth);

        setLayout(new BorderLayout());
        // PagePanel is non-opaque so background shows through margins
        setOpaque(false);
        setBackground(ApplicationService.getBackgroundDark());
        setBorder(null); // No border between pages
        setAlignmentX(Component.CENTER_ALIGNMENT);

        // Enable double buffering to prevent jitter during scrolling
        setDoubleBuffered(true);

        // Show page number as placeholder until image loads
        JLabel placeholderLabel = new JLabel("Page " + pageNumber + " / " + totalPages, SwingConstants.CENTER);
        placeholderLabel.setForeground(ApplicationService.getTextSecondary());
        placeholderLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        add(placeholderLabel, BorderLayout.CENTER);
    }

    /**
     * Sets the zoom scale for this panel.
     */
    public void setZoomScale(double zoomScale) {
        this.zoomScale = zoomScale;
    }

    /**
     * Recalculates the panel size based on viewport width and zoom scale.
     * Uses the pre-calculated original dimensions.
     */
    public void recalculateSize(int newViewportWidth) {
        this.viewportWidth = newViewportWidth;

        // Calculate scaled width with zoom applied
        double scaledWidth = newViewportWidth * zoomScale;

        // Calculate height maintaining aspect ratio: H = W * (Horig / Worig)
        double aspectRatio = (double) originalHeight / originalWidth;
        preferredHeight = (int) (scaledWidth * aspectRatio);

        // Set sizes with centering in mind
        int displayWidth = Math.min((int)scaledWidth, newViewportWidth);

        setPreferredSize(new Dimension(newViewportWidth, preferredHeight));
        setMaximumSize(new Dimension(newViewportWidth, preferredHeight));
        setMinimumSize(new Dimension(displayWidth, preferredHeight));
    }

    /**
     * Sets the loaded image. Note: image dimensions are already known from pre-calculation.
     */
    public void setImage(BufferedImage image, int viewportWidth) {
        this.image = image;

        // Recalculate size for the current viewport
        recalculateSize(viewportWidth);

        // Remove placeholder and trigger repaint
        removeAll();
        repaint();

        // Revalidate the container
        // Note: caller must revalidate the container panel after calling this
    }

    /**
     * Unloads the image to free memory.
     */
    public void unloadImage() {
        if (image != null) {
            image = null;

            // Restore placeholder
            removeAll();
            JLabel placeholderLabel = new JLabel("Page " + pageNumber + " / " + totalPages + " (unloaded)", SwingConstants.CENTER);
            placeholderLabel.setForeground(ApplicationService.getTextSecondary());
            placeholderLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            add(placeholderLabel, BorderLayout.CENTER);

            revalidate();
            repaint();
        }
    }

    /**
     * Updates the viewport width and recalculates size.
     * Called on window resize.
     */
    public void updateViewportWidth(int newViewportWidth) {
        if (newViewportWidth != this.viewportWidth) {
            recalculateSize(newViewportWidth);
        }
    }

    /**
     * Checks if this panel has a loaded image.
     */
    public boolean isLoaded() {
        return image != null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (image != null) {
            Graphics2D g2d = (Graphics2D) g.create();

            // High quality rendering to match comic mode
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Calculate display dimensions with zoom
            int panelWidth = getWidth();
            int panelHeight = getHeight();

            double scaledWidth = viewportWidth * zoomScale;
            int displayWidth = Math.min((int)scaledWidth, panelWidth);
            int displayHeight = panelHeight;

            // Center the image horizontally
            int xOffset = (panelWidth - displayWidth) / 2;

            g2d.drawImage(image, xOffset, 0, displayWidth, displayHeight, null);
            g2d.dispose();
        }
    }

    // Getters for accessing internal state
    public File getImageFile() {
        return imageFile;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public int getTotalPages() {
        return totalPages;
    }
}
