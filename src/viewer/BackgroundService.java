package viewer;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Service class for managing custom background images.
 * Handles loading, caching, and rendering of background images with fallback support.
 */
public class BackgroundService {
    
    private static String browserBackgroundImagePath = null;
    private static String viewerBackgroundImagePath = null;
    private static BufferedImage cachedBrowserBackgroundImage = null;
    private static BufferedImage cachedViewerBackgroundImage = null;
    private static int cachedBrowserWidth = -1;
    private static int cachedBrowserHeight = -1;
    private static final Color FALLBACK_COLOR = ApplicationService.getBackgroundDark();
    private static final File BACKGROUNDS_DIR = new File("userData/backgrounds");
    private static Runnable browserBackgroundRefreshCallback = null;
    
    static {
        // Ensure backgrounds directory exists
        if (!BACKGROUNDS_DIR.exists()) {
            BACKGROUNDS_DIR.mkdirs();
        }
        loadBackgroundFromConfig();
    }
    
    /**
     * Loads background image paths from configuration.
     */
    private static void loadBackgroundFromConfig() {
        browserBackgroundImagePath = ApplicationService.getBrowserBackgroundImagePath();
        viewerBackgroundImagePath = ApplicationService.getViewerBackgroundImagePath();
        
        if (browserBackgroundImagePath != null && !browserBackgroundImagePath.trim().isEmpty()) {
            File browserFile = new File(browserBackgroundImagePath);
            if (browserFile.exists()) {
                loadBrowserBackgroundImage(browserFile);
            } else {
                browserBackgroundImagePath = null;
                ApplicationService.setBrowserBackgroundImagePath("");
            }
        }
        
        if (viewerBackgroundImagePath != null && !viewerBackgroundImagePath.trim().isEmpty()) {
            File viewerFile = new File(viewerBackgroundImagePath);
            if (viewerFile.exists()) {
                loadViewerBackgroundImage(viewerFile);
            } else {
                viewerBackgroundImagePath = null;
                ApplicationService.setViewerBackgroundImagePath("");
            }
        }
    }
    
    /**
     * Loads browser background image from file and caches it.
     */
    private static void loadBrowserBackgroundImage(File imageFile) {
        try {
            BufferedImage originalImage = javax.imageio.ImageIO.read(imageFile);
            if (originalImage != null) {
                cachedBrowserBackgroundImage = originalImage;
                cachedBrowserWidth = -1;
                cachedBrowserHeight = -1;
            }
        } catch (IOException e) {
            System.err.println("Failed to load browser background image: " + e.getMessage());
            resetBrowserBackground();
        }
    }
    
    /**
     * Loads viewer background image from file and caches it.
     */
    private static void loadViewerBackgroundImage(File imageFile) {
        try {
            BufferedImage originalImage = javax.imageio.ImageIO.read(imageFile);
            if (originalImage != null) {
                cachedViewerBackgroundImage = originalImage;
            }
        } catch (IOException e) {
            System.err.println("Failed to load viewer background image: " + e.getMessage());
            resetViewerBackground();
        }
    }
    
    /**
     * Sets a new browser background image by copying it to backgrounds folder.
     */
    public static void setBrowserBackground(File sourceFile) {
        if (sourceFile == null || !sourceFile.exists()) {
            return;
        }
        
        try {
            String fileName = "browser_background_" + System.currentTimeMillis() + getFileExtension(sourceFile);
            File targetFile = new File(BACKGROUNDS_DIR, fileName);
            
            Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            
            browserBackgroundImagePath = targetFile.getAbsolutePath();
            ApplicationService.setBrowserBackgroundImagePath(browserBackgroundImagePath);
            
            loadBrowserBackgroundImage(targetFile);
            
            // Trigger immediate background refresh
            if (browserBackgroundRefreshCallback != null) {
                browserBackgroundRefreshCallback.run();
            }
            
        } catch (IOException e) {
            System.err.println("Failed to copy browser background image: " + e.getMessage());
        }
    }
    
    /**
     * Sets a new viewer background image by copying it to backgrounds folder.
     */
    public static void setViewerBackground(File sourceFile) {
        if (sourceFile == null || !sourceFile.exists()) {
            return;
        }
        
        try {
            String fileName = "viewer_background_" + System.currentTimeMillis() + getFileExtension(sourceFile);
            File targetFile = new File(BACKGROUNDS_DIR, fileName);
            
            Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            
            viewerBackgroundImagePath = targetFile.getAbsolutePath();
            ApplicationService.setViewerBackgroundImagePath(viewerBackgroundImagePath);
            
            loadViewerBackgroundImage(targetFile);
            
        } catch (IOException e) {
            System.err.println("Failed to copy viewer background image: " + e.getMessage());
        }
    }
    
    /**
     * Resets browser background to default (black) and deletes the current background file.
     */
    public static void resetBrowserBackground() {
        // Delete the current background file if it exists
        if (browserBackgroundImagePath != null && !browserBackgroundImagePath.trim().isEmpty()) {
            File currentBackgroundFile = new File(browserBackgroundImagePath);
            if (currentBackgroundFile.exists()) {
                boolean deleted = currentBackgroundFile.delete();
                if (!deleted) {
                    System.err.println("Failed to delete browser background file: " + browserBackgroundImagePath);
                }
            }
        }
        
        browserBackgroundImagePath = null;
        cachedBrowserBackgroundImage = null;
        cachedBrowserWidth = -1;
        cachedBrowserHeight = -1;
        ApplicationService.setBrowserBackgroundImagePath("");
        
        // Trigger immediate background refresh
        if (browserBackgroundRefreshCallback != null) {
            browserBackgroundRefreshCallback.run();
        }
    }
    
    /**
     * Resets viewer background to default (black) and deletes the current background file.
     */
    public static void resetViewerBackground() {
        // Delete the current background file if it exists
        if (viewerBackgroundImagePath != null && !viewerBackgroundImagePath.trim().isEmpty()) {
            File currentBackgroundFile = new File(viewerBackgroundImagePath);
            if (currentBackgroundFile.exists()) {
                boolean deleted = currentBackgroundFile.delete();
                if (!deleted) {
                    System.err.println("Failed to delete viewer background file: " + viewerBackgroundImagePath);
                }
            }
        }
        
        viewerBackgroundImagePath = null;
        cachedViewerBackgroundImage = null;
        ApplicationService.setViewerBackgroundImagePath("");
        
        // Trigger immediate background refresh
        if (browserBackgroundRefreshCallback != null) {
            browserBackgroundRefreshCallback.run();
        }
    }
    
    /**
     * Renders browser background image to fill specified area.
     */
    public static void renderBrowserBackground(Graphics g, int width, int height, Component c) {
        float opacity = ApplicationService.getBrowserBackgroundOpacity();
        
        if (cachedBrowserBackgroundImage != null) {
            if (cachedBrowserWidth != width || cachedBrowserHeight != height) {
                cachedBrowserBackgroundImage = scaleImage(cachedBrowserBackgroundImage, width, height);
                cachedBrowserWidth = width;
                cachedBrowserHeight = height;
            }
            
            // Apply opacity if less than 1.0
            if (opacity < 1.0f) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
                g2d.drawImage(cachedBrowserBackgroundImage, 0, 0, width, height, c);
                g2d.dispose();
            } else {
                g.drawImage(cachedBrowserBackgroundImage, 0, 0, width, height, c);
            }
        } else {
            // Apply opacity to fallback color as well
            if (opacity < 1.0f) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
                g2d.setColor(FALLBACK_COLOR);
                g2d.fillRect(0, 0, width, height);
                g2d.dispose();
            } else {
                g.setColor(FALLBACK_COLOR);
                g.fillRect(0, 0, width, height);
            }
        }
    }
    
    /**
     * Renders viewer background image using cover scaling with absolute screen pinning.
     * Optimized for large coordinate spaces of a JScrollPane.
     * Draws the background exactly within the visible bounds so it never appears to move.
     */
    public static void renderViewerBackground(Graphics g, Rectangle visibleRect, Component c) {
        float opacity = ApplicationService.getViewerBackgroundOpacity();
        
        if (cachedViewerBackgroundImage != null) {
            Graphics2D g2d = (Graphics2D) g.create();
            
            // Set high-quality rendering for background
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            
            // Apply opacity if less than 1.0
            if (opacity < 1.0f) {
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
            }
            
            // Use cover scaling to maintain aspect ratio while filling the viewport
            int bgWidth = cachedViewerBackgroundImage.getWidth();
            int bgHeight = cachedViewerBackgroundImage.getHeight();
            int viewWidth = visibleRect.width;
            int viewHeight = visibleRect.height;
            
            // Calculate cover scaling dimensions
            double widthRatio = (double) viewWidth / bgWidth;
            double heightRatio = (double) viewHeight / bgHeight;
            double coverScale = Math.max(widthRatio, heightRatio);
            
            int scaledWidth = (int) (bgWidth * coverScale);
            int scaledHeight = (int) (bgHeight * coverScale);
            
            // Draw background at fixed screen coordinates following scroll position
            // This keeps the background pinned to screen during scroll
            int x = (viewWidth - scaledWidth) / 2;
            int y = visibleRect.y + (viewHeight - scaledHeight) / 2;
            
            // Clip to visible rectangle to ensure we only draw what's needed
            g2d.setClip(visibleRect.x, visibleRect.y, visibleRect.width, visibleRect.height);
            
            // Draw background at viewport coordinates (stays pinned to screen)
            g2d.drawImage(cachedViewerBackgroundImage, x, y, scaledWidth, scaledHeight, c);
            g2d.dispose();
        } else {
            // Apply opacity to fallback color as well
            Graphics2D g2d = (Graphics2D) g.create();
            if (opacity < 1.0f) {
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
            }
            g2d.setColor(FALLBACK_COLOR);
            g2d.fillRect(visibleRect.x, visibleRect.y, visibleRect.width, visibleRect.height);
            g2d.dispose();
        }
    }
    
    /**
     * Legacy method for backward compatibility - delegates to viewport-based version.
     */
    public static void renderViewerBackground(Graphics g, int width, int height, Component c) {
        Rectangle visibleRect = new Rectangle(0, 0, width, height);
        renderViewerBackground(g, visibleRect, c);
    }
    
    /**
     * Scales an image to cover the specified area while maintaining aspect ratio.
     */
    private static BufferedImage scaleImage(BufferedImage original, int targetWidth, int targetHeight) {
        int originalWidth = original.getWidth();
        int originalHeight = original.getHeight();
        
        // Calculate scale to cover the entire area
        double scaleX = (double) targetWidth / originalWidth;
        double scaleY = (double) targetHeight / originalHeight;
        double scale = Math.max(scaleX, scaleY);
        
        int scaledWidth = (int) (originalWidth * scale);
        int scaledHeight = (int) (originalHeight * scale);
        
        // Create scaled image
        BufferedImage scaledImage = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaledImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(original, 0, 0, scaledWidth, scaledHeight, null);
        g2d.dispose();
        
        return scaledImage;
    }
    
    /**
     * Gets current browser background image path.
     */
    public static String getBrowserBackgroundImagePath() {
        return browserBackgroundImagePath;
    }
    
    /**
     * Gets current viewer background image path.
     */
    public static String getViewerBackgroundImagePath() {
        return viewerBackgroundImagePath;
    }
    
    /**
     * Checks if a custom browser background is currently set.
     */
    public static boolean hasCustomBrowserBackground() {
        return cachedBrowserBackgroundImage != null;
    }
    
    /**
     * Checks if a custom viewer background is currently set.
     */
    public static boolean hasCustomViewerBackground() {
        return cachedViewerBackgroundImage != null;
    }
    
    /**
     * Gets file extension from a file.
     */
    private static String getFileExtension(File file) {
        String name = file.getName();
        int lastDotIndex = name.lastIndexOf('.');
        return lastDotIndex > 0 ? name.substring(lastDotIndex) : "";
    }
    
    /**
     * Opens a file chooser for browser background selection.
     */
    public static void showBrowserBackgroundSelector(Component parent) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Image Files (*.jpg, *.jpeg, *.png, *.gif)", 
            "jpg", "jpeg", "png", "gif"));
        
        int result = fileChooser.showOpenDialog(parent);
        if (result == JFileChooser.APPROVE_OPTION) {
            setBrowserBackground(fileChooser.getSelectedFile());
        }
    }
    
    /**
     * Sets the callback for immediate background refresh when background changes.
     */
    public static void setBrowserBackgroundRefreshCallback(Runnable callback) {
        browserBackgroundRefreshCallback = callback;
    }
    
    /**
     * Opens a file chooser for viewer background selection.
     */
    public static void showViewerBackgroundSelector(Component parent) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Image Files (*.jpg, *.jpeg, *.png, *.gif)", 
            "jpg", "jpeg", "png", "gif"));
        
        int result = fileChooser.showOpenDialog(parent);
        if (result == JFileChooser.APPROVE_OPTION) {
            setViewerBackground(fileChooser.getSelectedFile());
        }
    }
}
