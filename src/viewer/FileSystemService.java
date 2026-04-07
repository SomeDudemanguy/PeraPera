package viewer;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Service class for file system operations and folder loading.
 * Handles folder discovery, background loading, and OS-specific file operations.
 */
public class FileSystemService {
    
    private final ImageBrowserApp app;
    private final List<File> allFolders;
    private final String mainDirectoryPath;
    
    public FileSystemService(ImageBrowserApp app, List<File> allFolders, String mainDirectoryPath) {
        this.app = app;
        this.allFolders = allFolders;
        this.mainDirectoryPath = mainDirectoryPath;
    }
    
    /**
     * Loads folders from the background thread with progress updates.
     */
    public void loadFoldersFromBackground() {
        // Check if main directory is configured
        if (mainDirectoryPath == null || mainDirectoryPath.isEmpty()) {
            System.out.println("Main directory not configured - skipping folder load");
            return;
        }
        
        SwingWorker<Void, File> folderLoader = new SwingWorker<Void, File>() {
            @Override
            protected Void doInBackground() throws Exception {
                File mainFolder = new File(mainDirectoryPath);
                if (mainFolder.exists() && mainFolder.isDirectory()) {
                    File[] folders = mainFolder.listFiles(File::isDirectory);
                    if (folders != null) {
                        // Sort folders by name
                        Arrays.sort(folders, Comparator.comparing(File::getName));
                        
                        // Publish folders as they're found
                        for (File folder : folders) {
                            publish(folder);
                            
                            // Also check for sub-folders that are comics (auto-collection contents)
                            File[] subFolders = folder.listFiles(File::isDirectory);
                            if (subFolders != null) {
                                for (File subFolder : subFolders) {
                                    // Check if sub-folder contains images (is a comic)
                                    if (isComicFolder(subFolder)) {
                                        publish(subFolder);
                                    }
                                }
                            }
                            
                            Thread.sleep(1); // Small delay to prevent overwhelming
                        }
                    }
                } else {
                    System.out.println("Main directory not found: " + mainDirectoryPath);
                }
                return null;
            }
            
            /**
             * Checks if a folder is a comic folder (contains 2+ valid image files).
             */
            private boolean isComicFolder(File folder) {
                File[] contents = folder.listFiles();
                if (contents == null) return false;
                
                int imageCount = 0;
                for (File file : contents) {
                    if (isValidImageFile(file)) {
                        imageCount++;
                        if (imageCount >= 2) return true;
                    }
                }
                return false;
            }
            
            @Override
            protected void process(List<File> chunks) {
                // Update UI with newly found folders
                for (File folder : chunks) {
                    if (!allFolders.contains(folder)) {
                        allFolders.add(folder);
                        
                        // Load thumbnail for this folder asynchronously
                        ThumbnailService.loadThumbnail(folder, app.getLibraryService(), () -> {
                            app.refreshDisplay();
                            app.updatePaginationControls(app.getTotalPages());
                        });
                    }
                }
                
                // Refresh display to show new folders
                app.refreshDisplay();
                app.updatePaginationControls(app.getTotalPages());
            }
            
            @Override
            protected void done() {
                System.out.println("Finished loading " + allFolders.size() + " folders");
                app.refreshDisplay();
                app.updatePaginationControls(app.getTotalPages());
            }
        };
        
        folderLoader.execute();
    }
    
    /**
     * Opens folder in system file explorer with OS-specific handling.
     */
    public void openFolderInExplorer(File folder) {
        try {
            // Debug: Print the folder path
            System.out.println("Attempting to open folder: " + folder.getAbsolutePath());
            System.out.println("Folder exists: " + folder.exists());
            System.out.println("Folder is directory: " + folder.isDirectory());
            
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                // Windows: open the folder directly - this works!
                ProcessBuilder pb1 = new ProcessBuilder("explorer.exe", folder.getAbsolutePath());
                System.out.println("Trying: " + pb1.command());
                pb1.start();
                
                // No fallback needed - Method 1 works perfectly!
                // The Timer was causing the infinite loop
                
            } else {
                // macOS/Linux: use desktop
                Desktop.getDesktop().open(folder);
            }
        } catch (Exception ex) {
            System.out.println("Failed to open folder: " + ex.getMessage());
            JOptionPane.showMessageDialog(app, "Failed to open folder: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Opens the parent folder in system file explorer with the specified folder selected.
     */
    public void openFolderInExplorerWithSelection(File folder) {
        try {
            // Debug: Print the folder path
            System.out.println("Attempting to open parent folder with selection: " + folder.getAbsolutePath());
            System.out.println("Folder exists: " + folder.exists());
            System.out.println("Folder is directory: " + folder.isDirectory());
            
            File parentFolder = folder.getParentFile();
            if (parentFolder == null || !parentFolder.exists()) {
                // If parent doesn't exist, fall back to opening the folder itself
                openFolderInExplorer(folder);
                return;
            }
            
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                // Windows: open parent folder with folder selected
                ProcessBuilder pb = new ProcessBuilder("explorer.exe", "/select,", folder.getAbsolutePath());
                System.out.println("Trying: " + pb.command());
                pb.start();
                
            } else {
                // macOS/Linux: fall back to opening the folder itself
                // (selecting specific folders is more complex on these platforms)
                Desktop.getDesktop().open(folder);
            }
        } catch (Exception ex) {
            System.out.println("Failed to open folder with selection: " + ex.getMessage());
            JOptionPane.showMessageDialog(app, "Failed to open folder: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Loads all valid image files from the folder and sorts them naturally.
     */
    public static List<File> loadImageFiles(File folder) {
        List<File> files = new ArrayList<>();
        
        File[] contents = folder.listFiles();
        if (contents != null) {
            for (File file : contents) {
                if (isValidImageFile(file)) {
                    files.add(file);
                }
            }
        }
        
        // Sort files naturally (handles numbers correctly: 1, 2, 10 instead of 1, 10, 2)
        files.sort((a, b) -> naturalCompare(a.getName(), b.getName()));
        
        System.out.println("Webtoon mode: Loaded " + files.size() + " images from " + folder.getName());
        return files;
    }
    
    /**
     * Natural string comparison for proper numeric sorting.
     * Compares strings with numbers correctly (e.g., "page2" < "page10").
     */
    public static int naturalCompare(String a, String b) {
        int lengthA = a.length();
        int lengthB = b.length();
        int indexA = 0;
        int indexB = 0;
        
        while (indexA < lengthA && indexB < lengthB) {
            char charA = a.charAt(indexA);
            char charB = b.charAt(indexB);
            
            if (Character.isDigit(charA) && Character.isDigit(charB)) {
                // Extract full numbers
                int numA = 0;
                int numB = 0;
                
                while (indexA < lengthA && Character.isDigit(a.charAt(indexA))) {
                    numA = numA * 10 + (a.charAt(indexA) - '0');
                    indexA++;
                }
                
                while (indexB < lengthB && Character.isDigit(b.charAt(indexB))) {
                    numB = numB * 10 + (b.charAt(indexB) - '0');
                    indexB++;
                }
                
                if (numA != numB) {
                    return Integer.compare(numA, numB);
                }
            } else {
                if (charA != charB) {
                    return Character.compare(charA, charB);
                }
                indexA++;
                indexB++;
            }
        }
        
        return Integer.compare(lengthA, lengthB);
    }
    
    /**
     * Checks if a file is a valid image file (JPG, PNG, WebP).
     */
    public static boolean isValidImageFile(File file) {
        if (!file.isFile()) return false;
        
        String name = file.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || 
               name.endsWith(".png") || name.endsWith(".webp");
    }
}
