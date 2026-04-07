package viewer;

import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * Service class for managing all dialog windows in the Image Browser application.
 * Centralizes dialog creation and handling logic.
 */
public class DialogService {
    
    /**
     * Helper method to apply consistent dark theme styling to components.
     */
    private static void styleDark(JComponent component, int fontStyle, float fontSize) {
        component.setBackground(ApplicationService.getInputBackground());
        component.setForeground(ApplicationService.getTextPrimary());
        if (fontSize > 0) {
            component.setFont(component.getFont().deriveFont(fontStyle, fontSize));
        }
    }
    
    /**
     * Generic opacity dialog that can be used for thumbnail, browser, or viewer opacity.
     */
    private static void showOpacityDialog(Frame parent, String title, float currentValue, 
                                           java.util.function.Consumer<Float> onSave, 
                                           Runnable onRefresh) {
        JDialog dialog = new JDialog(parent, "PeraPera - " + title, true);
        dialog.setSize(250, 140);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(parent);
        dialog.setLayout(new BorderLayout());
        
        JPanel panel = new JPanel(new GridLayout(2, 2, 10, 10));
        styleDark(panel, Font.PLAIN, 0);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        JLabel currentLabel = new JLabel("Current:");
        styleDark(currentLabel, Font.BOLD, 11f);
        
        JLabel valueLabel = new JLabel(String.format("%d%%", (int)(currentValue * 100)));
        valueLabel.setForeground(ApplicationService.getAccentColor());
        styleDark(valueLabel, Font.BOLD, 11f);
        
        JTextField inputField = new JTextField(String.valueOf((int)(currentValue * 100)), 3);
        styleDark(inputField, Font.PLAIN, 11f);
        inputField.setCaretColor(ApplicationService.getTextPrimary());
        
        inputField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { validateInput(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { validateInput(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { validateInput(); }
            
            private void validateInput() {
                String text = inputField.getText().trim();
                if (!text.isEmpty()) {
                    try {
                        int value = Integer.parseInt(text);
                        if (value >= 0 && value <= 100) {
                            valueLabel.setText(String.format("%d%%", value));
                        }
                    } catch (NumberFormatException ex) {
                        // Invalid format, ignore
                    }
                } else {
                    valueLabel.setText("0%%");
                }
            }
        });
        
        JPanel buttonPanel = new JPanel(new FlowLayout());
        styleDark(buttonPanel, Font.PLAIN, 0);
        
        JButton okButton = new JButton("OK");
        styleDark(okButton, Font.PLAIN, 11f);
        okButton.addActionListener(e -> {
            String text = inputField.getText().trim();
            try {
                int value = Integer.parseInt(text);
                if (value >= 0 && value <= 100) {
                    onSave.accept(value / 100.0f);
                    dialog.dispose();
                    if (onRefresh != null) {
                        onRefresh.run();
                    }
                } else {
                    JOptionPane.showMessageDialog(dialog, "Please enter a value between 0 and 100", 
                        "Invalid Input", JOptionPane.ERROR_MESSAGE);
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "Please enter a valid number", 
                    "Invalid Input", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        JButton cancelButton = new JButton("Cancel");
        styleDark(cancelButton, Font.PLAIN, 11f);
        cancelButton.addActionListener(e -> dialog.dispose());
        
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        
        panel.add(currentLabel);
        panel.add(valueLabel);
        panel.add(inputField);
        panel.add(buttonPanel);
        
        dialog.add(panel, BorderLayout.CENTER);
        dialog.setVisible(true);
    }
    
    /**
     * Creates and returns the menu bar for the application.
     */
    public static JMenuBar createMenuBar(ImageBrowserApp app) {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBackground(ApplicationService.getInputBackground());
        
        // Set UI defaults for better menu rendering
        UIManager.put("Menu.background", ApplicationService.getInputBackground());
        UIManager.put("Menu.foreground", ApplicationService.getTextPrimary());
        UIManager.put("MenuItem.background", ApplicationService.getInputBackground());
        UIManager.put("MenuItem.foreground", ApplicationService.getTextPrimary());
        UIManager.put("PopupMenu.background", ApplicationService.getInputBackground());
        UIManager.put("PopupMenu.foreground", ApplicationService.getTextPrimary());
        
        JMenu settingsMenu = new JMenu("Settings");
        settingsMenu.setBackground(ApplicationService.getInputBackground());
        settingsMenu.setForeground(ApplicationService.getTextPrimary());
        settingsMenu.setOpaque(true);
        
        JMenuItem changeDirItem = new JMenuItem("Change Main Directory");
        changeDirItem.setBackground(ApplicationService.getInputBackground());
        changeDirItem.setForeground(ApplicationService.getTextPrimary());
        changeDirItem.addActionListener(e -> showChangeDirectoryDialog(app));
        
        // Add separator
        settingsMenu.addSeparator();
        
        // Sorting submenu
        JMenu sortMenu = new JMenu("Sort By");
        sortMenu.setBackground(ApplicationService.getInputBackground());
        sortMenu.setForeground(ApplicationService.getTextPrimary());
        sortMenu.setOpaque(true);
        
        JMenuItem sortRatingDesc = new JMenuItem("Rating ↓");
        sortRatingDesc.setBackground(ApplicationService.getInputBackground());
        sortRatingDesc.setForeground(ApplicationService.getTextPrimary());
        sortRatingDesc.addActionListener(e -> {
            app.setSortOption("Rating ↓");
            app.refreshDisplay();
        });
        
        JMenuItem sortNameAsc = new JMenuItem("Name A-Z");
        sortNameAsc.setBackground(ApplicationService.getInputBackground());
        sortNameAsc.setForeground(ApplicationService.getTextPrimary());
        sortNameAsc.addActionListener(e -> {
            app.setSortOption("Name A-Z");
            app.refreshDisplay();
        });
        
        JMenuItem sortNameDesc = new JMenuItem("Name Z-A");
        sortNameDesc.setBackground(ApplicationService.getInputBackground());
        sortNameDesc.setForeground(ApplicationService.getTextPrimary());
        sortNameDesc.addActionListener(e -> {
            app.setSortOption("Name Z-A");
            app.refreshDisplay();
        });
        
        JMenuItem sortRatingAsc = new JMenuItem("Rating ↑");
        sortRatingAsc.setBackground(ApplicationService.getInputBackground());
        sortRatingAsc.setForeground(ApplicationService.getTextPrimary());
        sortRatingAsc.addActionListener(e -> {
            app.setSortOption("Rating ↑");
            app.refreshDisplay();
        });
        
        sortMenu.add(sortRatingDesc);
        sortMenu.add(sortNameAsc);
        sortMenu.add(sortNameDesc);
        sortMenu.add(sortRatingAsc);
        
        // Page size submenu
        JMenu pageSizeMenu = new JMenu("Page Size");
        pageSizeMenu.setBackground(ApplicationService.getInputBackground());
        pageSizeMenu.setForeground(ApplicationService.getTextPrimary());
        pageSizeMenu.setOpaque(true);
        
        JMenuItem size20 = new JMenuItem("20");
        size20.setBackground(ApplicationService.getInputBackground());
        size20.setForeground(ApplicationService.getTextPrimary());
        size20.addActionListener(e -> {
            app.setPageSize(20);
            app.setCurrentPage(1);
            app.refreshDisplay();
        });
        
        JMenuItem size40 = new JMenuItem("40");
        size40.setBackground(ApplicationService.getInputBackground());
        size40.setForeground(ApplicationService.getTextPrimary());
        size40.addActionListener(e -> {
            app.setPageSize(40);
            app.setCurrentPage(1);
            app.refreshDisplay();
        });
        
        JMenuItem size60 = new JMenuItem("60");
        size60.setBackground(ApplicationService.getInputBackground());
        size60.setForeground(ApplicationService.getTextPrimary());
        size60.addActionListener(e -> {
            app.setPageSize(60);
            app.setCurrentPage(1);
            app.refreshDisplay();
        });
        
        JMenuItem size80 = new JMenuItem("80");
        size80.setBackground(ApplicationService.getInputBackground());
        size80.setForeground(ApplicationService.getTextPrimary());
        size80.addActionListener(e -> {
            app.setPageSize(80);
            app.setCurrentPage(1);
            app.refreshDisplay();
        });
        
        JMenuItem size100 = new JMenuItem("100");
        size100.setBackground(ApplicationService.getInputBackground());
        size100.setForeground(ApplicationService.getTextPrimary());
        size100.addActionListener(e -> {
            app.setPageSize(100);
            app.setCurrentPage(1);
            app.refreshDisplay();
        });
        
        JMenuItem size500 = new JMenuItem("500");
        size500.setBackground(ApplicationService.getInputBackground());
        size500.setForeground(ApplicationService.getTextPrimary());
        size500.addActionListener(e -> {
            app.setPageSize(500);
            app.setCurrentPage(1);
            app.refreshDisplay();
        });
        
        JMenuItem sizeAll = new JMenuItem("All");
        sizeAll.setBackground(ApplicationService.getInputBackground());
        sizeAll.setForeground(ApplicationService.getTextPrimary());
        sizeAll.addActionListener(e -> {
            app.setPageSize(Integer.MAX_VALUE); // Show all items
            app.setCurrentPage(1);
            app.refreshDisplay();
        });
        
        pageSizeMenu.add(size20);
        pageSizeMenu.add(size40);
        pageSizeMenu.add(size60);
        pageSizeMenu.add(size80);
        pageSizeMenu.add(size100);
        pageSizeMenu.add(size500);
        pageSizeMenu.add(sizeAll);
        // Add all menu items to settings menu
        settingsMenu.add(changeDirItem);
        settingsMenu.addSeparator();
        settingsMenu.add(sortMenu);
        settingsMenu.add(pageSizeMenu);
        settingsMenu.addSeparator();
        
        // Background submenu
        JMenu backgroundMenu = new JMenu("Background");
        backgroundMenu.setBackground(ApplicationService.getInputBackground());
        backgroundMenu.setForeground(ApplicationService.getTextPrimary());
        backgroundMenu.setOpaque(true);
        
        JMenuItem setBrowserBackgroundItem = new JMenuItem("Set Browser Background");
        setBrowserBackgroundItem.setBackground(ApplicationService.getInputBackground());
        setBrowserBackgroundItem.setForeground(ApplicationService.getTextPrimary());
        setBrowserBackgroundItem.addActionListener(e -> {
            BackgroundService.showBrowserBackgroundSelector(app);
            app.refreshDisplay();
            // Refresh any open viewer frames
            for (Window window : Window.getWindows()) {
                if (window instanceof ImageViewerFrame) {
                    window.repaint();
                }
            }
        });
        
        JMenuItem setViewerBackgroundItem = new JMenuItem("Set Viewer Background");
        setViewerBackgroundItem.setBackground(ApplicationService.getInputBackground());
        setViewerBackgroundItem.setForeground(ApplicationService.getTextPrimary());
        setViewerBackgroundItem.addActionListener(e -> {
            BackgroundService.showViewerBackgroundSelector(app);
            // Refresh any open viewer frames
            for (Window window : Window.getWindows()) {
                if (window instanceof ImageViewerFrame) {
                    window.repaint();
                }
            }
        });
        
        JMenuItem resetBrowserBackgroundItem = new JMenuItem("Reset Browser Background");
        resetBrowserBackgroundItem.setBackground(ApplicationService.getInputBackground());
        resetBrowserBackgroundItem.setForeground(ApplicationService.getTextPrimary());
        resetBrowserBackgroundItem.addActionListener(e -> {
            BackgroundService.resetBrowserBackground();
            app.refreshDisplay();
        });
        
        JMenuItem resetViewerBackgroundItem = new JMenuItem("Reset Viewer Background");
        resetViewerBackgroundItem.setBackground(ApplicationService.getInputBackground());
        resetViewerBackgroundItem.setForeground(ApplicationService.getTextPrimary());
        resetViewerBackgroundItem.addActionListener(e -> {
            BackgroundService.resetViewerBackground();
            // Refresh any open viewer frames
            for (Window window : Window.getWindows()) {
                if (window instanceof ImageViewerFrame) {
                    window.repaint();
                }
            }
        });
        
        JMenuItem thumbnailTransparencyItem = new JMenuItem("Thumbnail Opacity");
        thumbnailTransparencyItem.setBackground(ApplicationService.getInputBackground());
        thumbnailTransparencyItem.setForeground(ApplicationService.getTextPrimary());
        thumbnailTransparencyItem.addActionListener(e -> showThumbnailTransparencyDialog(app));
        
        JMenuItem browserBackgroundOpacityItem = new JMenuItem("Browser Background Opacity");
        browserBackgroundOpacityItem.setBackground(ApplicationService.getInputBackground());
        browserBackgroundOpacityItem.setForeground(ApplicationService.getTextPrimary());
        browserBackgroundOpacityItem.addActionListener(e -> showBrowserBackgroundOpacityDialog(app));
        
        JMenuItem viewerBackgroundOpacityItem = new JMenuItem("Viewer Background Opacity");
        viewerBackgroundOpacityItem.setBackground(ApplicationService.getInputBackground());
        viewerBackgroundOpacityItem.setForeground(ApplicationService.getTextPrimary());
        viewerBackgroundOpacityItem.addActionListener(e -> showViewerBackgroundOpacityDialog(app));
        
        // Add all background items to background submenu
        backgroundMenu.add(setBrowserBackgroundItem);
        backgroundMenu.add(setViewerBackgroundItem);
        backgroundMenu.addSeparator();
        backgroundMenu.add(thumbnailTransparencyItem);
        backgroundMenu.add(browserBackgroundOpacityItem);
        backgroundMenu.add(viewerBackgroundOpacityItem);
        backgroundMenu.addSeparator();
        backgroundMenu.add(resetBrowserBackgroundItem);
        backgroundMenu.add(resetViewerBackgroundItem);
        
        settingsMenu.add(backgroundMenu);
        settingsMenu.addSeparator();
        
        // Reading Mode submenu
        JMenu readingModeMenu = new JMenu("Reading Mode");
        readingModeMenu.setBackground(ApplicationService.getInputBackground());
        readingModeMenu.setForeground(ApplicationService.getTextPrimary());
        readingModeMenu.setOpaque(true);
        
        ButtonGroup readingModeGroup = new ButtonGroup();
        
        JRadioButtonMenuItem comicModeItem = new JRadioButtonMenuItem("Comic Mode");
        comicModeItem.setBackground(ApplicationService.getInputBackground());
        comicModeItem.setForeground(ApplicationService.getTextPrimary());
        comicModeItem.setSelected("comic".equals(ApplicationService.getReadingMode()));
        comicModeItem.addActionListener(e -> {
            if (comicModeItem.isSelected()) {
                ApplicationService.setReadingMode("comic");
                System.out.println("Reading mode set to: comic");
            }
        });
        
        JRadioButtonMenuItem webtoonModeItem = new JRadioButtonMenuItem("Webtoon Mode");
        webtoonModeItem.setBackground(ApplicationService.getInputBackground());
        webtoonModeItem.setForeground(ApplicationService.getTextPrimary());
        webtoonModeItem.setSelected("webtoon".equals(ApplicationService.getReadingMode()));
        webtoonModeItem.addActionListener(e -> {
            if (webtoonModeItem.isSelected()) {
                ApplicationService.setReadingMode("webtoon");
                System.out.println("Reading mode set to: webtoon");
            }
        });
        
        JRadioButtonMenuItem mangaModeItem = new JRadioButtonMenuItem("Manga Mode");
        mangaModeItem.setBackground(ApplicationService.getInputBackground());
        mangaModeItem.setForeground(ApplicationService.getTextPrimary());
        mangaModeItem.setSelected("manga".equals(ApplicationService.getReadingMode()));
        mangaModeItem.addActionListener(e -> {
            if (mangaModeItem.isSelected()) {
                ApplicationService.setReadingMode("manga");
                System.out.println("Reading mode set to: manga");
            }
        });
        
        readingModeGroup.add(comicModeItem);
        readingModeGroup.add(webtoonModeItem);
        readingModeGroup.add(mangaModeItem);
        
        readingModeMenu.add(comicModeItem);
        readingModeMenu.add(webtoonModeItem);
        readingModeMenu.add(mangaModeItem);
        settingsMenu.add(readingModeMenu);
        settingsMenu.addSeparator();
        
        // Auto-Track Currently Reading toggle
        JCheckBoxMenuItem autoTrackReadingItem = new JCheckBoxMenuItem("Auto-Track Currently Reading");
        autoTrackReadingItem.setBackground(ApplicationService.getInputBackground());
        autoTrackReadingItem.setForeground(ApplicationService.getTextPrimary());
        autoTrackReadingItem.setSelected(ApplicationService.getAutoTrackReading());
        autoTrackReadingItem.addActionListener(e -> {
            ApplicationService.setAutoTrackReading(autoTrackReadingItem.isSelected());
        });
        settingsMenu.add(autoTrackReadingItem);
        
        // Show Titles on Thumbnails toggle
        JCheckBoxMenuItem showTitlesItem = new JCheckBoxMenuItem("Show Titles on Thumbnails");
        showTitlesItem.setBackground(ApplicationService.getInputBackground());
        showTitlesItem.setForeground(ApplicationService.getTextPrimary());
        showTitlesItem.setSelected(ApplicationService.isShowThumbnailTitles());
        showTitlesItem.addActionListener(e -> {
            ApplicationService.setShowThumbnailTitles(showTitlesItem.isSelected());
            app.refreshDisplay();
        });
        settingsMenu.add(showTitlesItem);
        settingsMenu.addSeparator();
        
        // Viewer Settings menu item
        JMenuItem viewerSettingsItem = new JMenuItem("Viewer Settings");
        viewerSettingsItem.setBackground(ApplicationService.getInputBackground());
        viewerSettingsItem.setForeground(ApplicationService.getTextPrimary());
        viewerSettingsItem.addActionListener(e -> showViewerSettingsDialog(app));
        settingsMenu.add(viewerSettingsItem);
        settingsMenu.addSeparator();
        
        // Password Protection toggle
        JCheckBoxMenuItem passwordToggleItem = new JCheckBoxMenuItem("Password Protection");
        passwordToggleItem.setBackground(ApplicationService.getInputBackground());
        passwordToggleItem.setForeground(ApplicationService.getTextPrimary());
        passwordToggleItem.setSelected(ApplicationService.isPasswordEnabled());
        passwordToggleItem.addActionListener(e -> {
            if (passwordToggleItem.isSelected()) {
                // Enabling password - show setup dialog
                showPasswordSetupDialog(app, passwordToggleItem);
            } else {
                // Disabling password - confirm and disable
                int confirm = JOptionPane.showConfirmDialog(app,
                    "Disable password protection? This will allow anyone to open the app.",
                    "Confirm Disable",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
                if (confirm == JOptionPane.YES_OPTION) {
                    ApplicationService.setPasswordEnabled(false);
                    ApplicationService.saveConfig();
                    JOptionPane.showMessageDialog(app, "Password protection disabled.");
                } else {
                    passwordToggleItem.setSelected(true); // Keep it checked
                }
            }
        });
        settingsMenu.add(passwordToggleItem);
        settingsMenu.addSeparator();
        
        // Tag management menu items
        JMenuItem addTagItem = new JMenuItem("Add Tag");
        addTagItem.setBackground(ApplicationService.getInputBackground());
        addTagItem.setForeground(ApplicationService.getTextPrimary());
        addTagItem.addActionListener(e -> showAddTagDialog(app));
        
        JMenuItem manageTagsItem = new JMenuItem("Manage Tags");
        manageTagsItem.setBackground(ApplicationService.getInputBackground());
        manageTagsItem.setForeground(ApplicationService.getTextPrimary());
        manageTagsItem.addActionListener(e -> showManageTagsDialog(app));
        
        JMenuItem refreshItem = new JMenuItem("Refresh Folders");
        refreshItem.setBackground(ApplicationService.getInputBackground());
        refreshItem.setForeground(ApplicationService.getTextPrimary());
        refreshItem.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(
                app,
                "<html><b>Refresh Folders</b><br><br>" +
                "This will:<br>" +
                "• Rescan your library directory for new/removed folders<br>" +
                "• Clear the thumbnail cache (thumbnails will be regenerated)<br>" +
                "• Sync metadata with the file system<br><br>" +
                "Note: This may take a while if you have many Titles.<br>" +
                "(This was a debug feature)<br>" +
                "(Most of this can now be done by just restarting the program)<br>" +
                "(and not having to wait for the thumbnails to load)<br><br>"+
                "Do you want to continue?</html>",
                "PeraPera - Confirm Refresh",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            if (result == JOptionPane.YES_OPTION) {
                app.refreshFolders();
            }
        });
        
        settingsMenu.add(addTagItem);
        settingsMenu.add(manageTagsItem);
        settingsMenu.add(refreshItem);
        settingsMenu.addSeparator();
        
        // Help menu item
        JMenuItem helpItem = new JMenuItem("Help");
        helpItem.setBackground(ApplicationService.getInputBackground());
        helpItem.setForeground(ApplicationService.getTextPrimary());
        helpItem.addActionListener(e -> HelpMenu.showHelpDialog(app));
        
        settingsMenu.add(helpItem);
        settingsMenu.addSeparator();
        
        // Customize menu item for theme colors
        JMenuItem customizeItem = new JMenuItem("Customize Colors");
        customizeItem.setBackground(ApplicationService.getInputBackground());
        customizeItem.setForeground(ApplicationService.getTextPrimary());
        customizeItem.addActionListener(e -> showCustomizeDialog(app));
        settingsMenu.add(customizeItem);
        
        menuBar.add(settingsMenu);
        
        return menuBar;
    }
    
    /**
     * Shows a dialog for changing the main directory.
     */
    public static void showChangeDirectoryDialog(ImageBrowserApp app) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setCurrentDirectory(new File(app.getMainDirectoryPath()));
        chooser.setDialogTitle("Select Main Comic Directory");
        
        int result = chooser.showOpenDialog(app);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedDir = chooser.getSelectedFile();
            if (selectedDir != null && selectedDir.exists() && selectedDir.isDirectory()) {
                String newPath = selectedDir.getAbsolutePath();
                
                // Clear workspace cache before changing directory
                ApplicationService.clearWorkspaceCache();
                ThumbnailService.clearCacheDir();
                
                // Update config
                ApplicationService.setConfig("main.directory", newPath);
                ApplicationService.saveConfig();
                
                JOptionPane.showMessageDialog(app, "Main directory updated to:\n" + newPath + "\n\nThe app will now restart to load the new library.", "Directory Changed", JOptionPane.INFORMATION_MESSAGE);
                
                // Restart the app to use the new workspace
                SwingUtilities.invokeLater(() -> {
                    restartApplication(app);
                });
            }
        }
    }
    
    /**
     * Restarts the application by spawning a new JVM process and exiting the current one.
     */
    private static void restartApplication(ImageBrowserApp app) {
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
            JOptionPane.showMessageDialog(app,
                "Failed to restart automatically. Please restart manually.",
                "Restart Failed", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Shows a dialog for adjusting thumbnail transparency.
     */
    public static void showThumbnailTransparencyDialog(Frame parent) {
        showOpacityDialog(parent, "Thumbnail Opacity", 
            ApplicationService.getThumbnailAlpha(),
            ApplicationService::setThumbnailAlpha,
            () -> {
                if (parent instanceof ImageBrowserApp) {
                    ((ImageBrowserApp) parent).refreshDisplay();
                }
            });
    }
    
    /**
     * Shows a dialog for adding a new tag.
     */
    public static void showAddTagDialog(ImageBrowserApp app) {
        JDialog dialog = new JDialog(app, "PeraPera - Add New Tag", true);
        dialog.setSize(350, 180);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(app);
        
        // Main panel with dark theme
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(ApplicationService.getBackgroundDark());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // Input panel
        JPanel inputPanel = new JPanel(new BorderLayout(10, 0));
        inputPanel.setBackground(ApplicationService.getBackgroundDark());
        
        JLabel tagLabel = new JLabel("Tag Name:");
        tagLabel.setForeground(ApplicationService.getTextPrimary());
        tagLabel.setFont(new Font("Arial", Font.PLAIN, 13));
        
        JTextField tagField = new JTextField(20);
        tagField.setBackground(ApplicationService.getInputBackground());
        tagField.setForeground(ApplicationService.getTextPrimary());
        tagField.setCaretColor(ApplicationService.getTextPrimary());
        tagField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ApplicationService.getBorderColor()),
            BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
        tagField.setFont(new Font("Arial", Font.PLAIN, 13));
        
        inputPanel.add(tagLabel, BorderLayout.WEST);
        inputPanel.add(tagField, BorderLayout.CENTER);
        
        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonPanel.setBackground(ApplicationService.getBackgroundDark());
        
        JButton okButton = new JButton("OK");
        okButton.setBackground(ApplicationService.getInputBackground());
        okButton.setForeground(ApplicationService.getTextPrimary());
        okButton.setFocusPainted(false);
        okButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ApplicationService.getBorderColor()),
            BorderFactory.createEmptyBorder(6, 20, 6, 20)
        ));
        okButton.setFont(new Font("Arial", Font.PLAIN, 12));
        okButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        JButton cancelButton = new JButton("Cancel");
        cancelButton.setBackground(ApplicationService.getInputBackground());
        cancelButton.setForeground(ApplicationService.getTextPrimary());
        cancelButton.setFocusPainted(false);
        cancelButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ApplicationService.getBorderColor()),
            BorderFactory.createEmptyBorder(6, 15, 6, 15)
        ));
        cancelButton.setFont(new Font("Arial", Font.PLAIN, 12));
        cancelButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        
        // Button actions
        okButton.addActionListener(e -> {
            String tagName = tagField.getText().trim();
            if (!tagName.isEmpty()) {
                if (app.addTag(tagName)) {
                    dialog.dispose();
                } else {
                    JOptionPane.showMessageDialog(dialog, "Tag already exists!", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        
        cancelButton.addActionListener(e -> dialog.dispose());
        
        // Enter key to submit
        tagField.addActionListener(e -> okButton.doClick());
        
        // Assemble dialog
        mainPanel.add(inputPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        dialog.add(mainPanel);
        dialog.setVisible(true);
    }
    
    /**
     * Shows a dialog for managing existing tags.
     */
    public static void showManageTagsDialog(ImageBrowserApp app) {
        JDialog tagDialog = new JDialog(app, "PeraPera - Manage Tags", true);
        tagDialog.setSize(400, 550);
        tagDialog.setLocationRelativeTo(app);
        
        // Main panel with dark theme
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(ApplicationService.getBackgroundDark());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Search panel at top
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBackground(ApplicationService.getBackgroundDark());
        searchPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        
        JLabel searchLabel = new JLabel("Search: ");
        searchLabel.setForeground(ApplicationService.getTextPrimary());
        searchPanel.add(searchLabel, BorderLayout.WEST);
        
        JTextField searchField = new JTextField();
        searchField.setBackground(ApplicationService.getInputBackground());
        searchField.setForeground(ApplicationService.getTextPrimary());
        searchField.setCaretColor(ApplicationService.getTextPrimary());
        searchField.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        searchPanel.add(searchField, BorderLayout.CENTER);
        
        // Get all tags and sort alphabetically (case-insensitive)
        List<String> allTags = new ArrayList<>(app.getAllTags());
        Collections.sort(allTags, String.CASE_INSENSITIVE_ORDER);
        
        // DefaultListModel for dynamic filtering
        DefaultListModel<String> tagListModel = new DefaultListModel<>();
        for (String tag : allTags) {
            tagListModel.addElement(tag);
        }
        
        // JList inside JScrollPane with performance settings
        JList<String> tagList = new JList<>(tagListModel);
        tagList.setBackground(ApplicationService.getBackgroundDark());
        tagList.setForeground(ApplicationService.getTextPrimary());
        tagList.setSelectionBackground(ApplicationService.getInputBackground());
        tagList.setSelectionForeground(ApplicationService.getTextPrimary());
        tagList.setFont(new Font("Arial", Font.PLAIN, 12));
        
        JScrollPane scrollPane = new JScrollPane(tagList);
        scrollPane.setBackground(ApplicationService.getBackgroundDark());
        scrollPane.getViewport().setBackground(ApplicationService.getBackgroundDark());
        scrollPane.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        
        // Search filtering with DocumentListener
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filterTags(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filterTags(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterTags(); }
            
            private void filterTags() {
                String filter = searchField.getText().trim().toLowerCase();
                tagListModel.clear();
                for (String tag : allTags) {
                    if (filter.isEmpty() || tag.toLowerCase().contains(filter)) {
                        tagListModel.addElement(tag);
                    }
                }
            }
        });
        
        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.setBackground(ApplicationService.getBackgroundDark());
        
        JButton deleteButton = new JButton("Delete Tag");
        deleteButton.setBackground(ApplicationService.getInputBackground());
        deleteButton.setForeground(ApplicationService.getTextPrimary());
        deleteButton.setFocusPainted(false);
        deleteButton.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        
        JButton closeButton = new JButton("Close");
        closeButton.setBackground(ApplicationService.getInputBackground());
        closeButton.setForeground(ApplicationService.getTextPrimary());
        closeButton.setFocusPainted(false);
        closeButton.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        
        buttonPanel.add(deleteButton);
        buttonPanel.add(closeButton);
        
        deleteButton.addActionListener(e -> {
            String selectedTag = tagList.getSelectedValue();
            if (selectedTag != null) {
                int confirm = JOptionPane.showConfirmDialog(
                    tagDialog,
                    "Delete tag '" + selectedTag + "'? This will remove it from all folders.",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
                );
                
                if (confirm == JOptionPane.YES_OPTION) {
                    if (app.removeTag(selectedTag)) {
                        allTags.remove(selectedTag);
                        tagListModel.removeElement(selectedTag);
                    }
                }
            }
        });
        
        closeButton.addActionListener(e -> tagDialog.dispose());
        
        // Assemble dialog
        JLabel titleLabel = new JLabel("Existing Tags (" + allTags.size() + "):");
        titleLabel.setForeground(ApplicationService.getTextPrimary());
        titleLabel.setFont(new Font("Arial", Font.BOLD, 12));
        
        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.setBackground(ApplicationService.getBackgroundDark());
        northPanel.add(titleLabel, BorderLayout.NORTH);
        northPanel.add(searchPanel, BorderLayout.SOUTH);
        
        mainPanel.add(northPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        tagDialog.add(mainPanel);
        tagDialog.setVisible(true);
    }
    
    /**
     * Shows a dialog for assigning tags to a folder/comic.
     * Searchable, high-performance UI matching the Manage Tags style.
     */
    public static void showFolderTagAssignmentDialog(File folder, ImageBrowserApp app, Component parent) {
        // Persistent state: pre-populate with folder's current tags
        Set<String> currentlySelected = new HashSet<>(app.getFolderTags(folder));
        
        // Get master tag list, sorted alphabetically (case-insensitive)
        List<String> masterTagList = new ArrayList<>(app.getAllTags());
        Collections.sort(masterTagList, String.CASE_INSENSITIVE_ORDER);
        
        JDialog tagDialog = new JDialog(SwingUtilities.getWindowAncestor(parent), 
            "PeraPera - Assign Tags to " + folder.getName(), Dialog.ModalityType.APPLICATION_MODAL);
        tagDialog.setSize(400, 550);
        tagDialog.setLocationRelativeTo(parent);
        
        // Main panel with dark theme
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(ApplicationService.getBackgroundDark());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Search panel at top
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBackground(ApplicationService.getBackgroundDark());
        searchPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        
        JLabel searchLabel = new JLabel("Search: ");
        searchLabel.setForeground(ApplicationService.getTextPrimary());
        searchPanel.add(searchLabel, BorderLayout.WEST);
        
        JTextField searchField = new JTextField();
        searchField.setBackground(ApplicationService.getInputBackground());
        searchField.setForeground(ApplicationService.getTextPrimary());
        searchField.setCaretColor(ApplicationService.getTextPrimary());
        searchField.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        searchPanel.add(searchField, BorderLayout.CENTER);
        
        // Checkbox panel with vertical BoxLayout
        JPanel tagsPanel = new JPanel();
        tagsPanel.setBackground(ApplicationService.getBackgroundDark());
        tagsPanel.setLayout(new BoxLayout(tagsPanel, BoxLayout.Y_AXIS));
        
        // JScrollPane with performance settings
        JScrollPane scrollPane = new JScrollPane(tagsPanel);
        scrollPane.setBackground(ApplicationService.getBackgroundDark());
        scrollPane.getViewport().setBackground(ApplicationService.getBackgroundDark());
        scrollPane.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        
        // Method to refresh checkboxes based on filter
        Runnable refreshCheckboxes = () -> {
            tagsPanel.removeAll();
            String filter = searchField.getText().trim().toLowerCase();
            
            for (String tag : masterTagList) {
                // Filter: show all if filter is empty, or only matching tags
                if (filter.isEmpty() || tag.toLowerCase().contains(filter)) {
                    int count = app.getFolderTagCount(tag);
                    String label = tag + (count > 0 ? " (" + count + " Titles)" : "");
                    
                    JCheckBox checkBox = new JCheckBox(label);
                    checkBox.setBackground(ApplicationService.getBackgroundDark());
                    checkBox.setForeground(ApplicationService.getTextPrimary());
                    checkBox.setFocusPainted(false);
                    
                    // Check currentlySelected set to determine if selected
                    checkBox.setSelected(currentlySelected.contains(tag));
                    
                    // ActionListener updates currentlySelected set immediately
                    checkBox.addActionListener(e -> {
                        if (checkBox.isSelected()) {
                            currentlySelected.add(tag);
                        } else {
                            currentlySelected.remove(tag);
                        }
                        // Update selected count label
                        updateSelectedCountLabel(mainPanel, currentlySelected.size());
                    });
                    
                    tagsPanel.add(checkBox);
                }
            }
            
            updateSelectedCountLabel(mainPanel, currentlySelected.size());
            
            tagsPanel.revalidate();
            tagsPanel.repaint();
        };
        
        // Search filtering with DocumentListener
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { refreshCheckboxes.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { refreshCheckboxes.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { refreshCheckboxes.run(); }
        });
        
        // Initial checkbox population
        refreshCheckboxes.run();
        
        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.setBackground(ApplicationService.getBackgroundDark());
        
        JButton saveButton = new JButton("Save");
        saveButton.setBackground(ApplicationService.getInputBackground());
        saveButton.setForeground(ApplicationService.getTextPrimary());
        saveButton.setFocusPainted(false);
        saveButton.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        
        JButton cancelButton = new JButton("Cancel");
        cancelButton.setBackground(ApplicationService.getInputBackground());
        cancelButton.setForeground(ApplicationService.getTextPrimary());
        cancelButton.setFocusPainted(false);
        cancelButton.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);
        
        saveButton.addActionListener(e -> {
            // Save tags to the folder
            app.setFolderTags(folder, currentlySelected);
            // Refresh the main UI to reflect changes
            app.refreshDisplay();
            tagDialog.dispose();
        });
        
        cancelButton.addActionListener(e -> tagDialog.dispose());
        
        // Title panel showing counts
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setBackground(ApplicationService.getBackgroundDark());
        
        JLabel titleLabel = new JLabel("Available Tags (" + masterTagList.size() + "):");
        titleLabel.setForeground(ApplicationService.getTextPrimary());
        titleLabel.setFont(new Font("Arial", Font.BOLD, 12));
        
        JLabel selectedLabel = new JLabel("Selected: " + currentlySelected.size());
        selectedLabel.setName("selectedCountLabel");
        selectedLabel.setForeground(ApplicationService.getAccentColor());
        selectedLabel.setFont(new Font("Arial", Font.BOLD, 11));
        
        titlePanel.add(titleLabel, BorderLayout.WEST);
        titlePanel.add(selectedLabel, BorderLayout.EAST);
        
        // North panel with title and search
        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.setBackground(ApplicationService.getBackgroundDark());
        northPanel.add(titlePanel, BorderLayout.NORTH);
        northPanel.add(searchPanel, BorderLayout.SOUTH);
        
        mainPanel.add(northPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        tagDialog.add(mainPanel);
        tagDialog.setVisible(true);
    }
    
    /**
     * Helper to update the selected count label in the folder tag dialog.
     */
    private static void updateSelectedCountLabel(JPanel mainPanel, int count) {
        Component northComponent = ((BorderLayout) mainPanel.getLayout()).getLayoutComponent(BorderLayout.NORTH);
        if (northComponent instanceof JPanel) {
            JPanel northPanel = (JPanel) northComponent;
            Component titleComponent = ((BorderLayout) northPanel.getLayout()).getLayoutComponent(BorderLayout.NORTH);
            if (titleComponent instanceof JPanel) {
                JPanel titlePanel = (JPanel) titleComponent;
                for (Component c : titlePanel.getComponents()) {
                    if (c instanceof JLabel && "selectedCountLabel".equals(c.getName())) {
                        ((JLabel) c).setText("Selected: " + count);
                        break;
                    }
                }
            }
        }
    }
    
    /**
     * Shows a dialog for editing an existing collection.
     */
    public static void showEditCollectionDialog(ImageBrowserApp app, Collection collection) {
        // Get the CollectionEntry from LibraryService (JSON) - this has the real rating
        LibraryService libraryService = app.getLibraryService();
        CollectionEntry collectionEntry = libraryService.getCollection(collection.name);
        
        // Use CollectionEntry rating if available, otherwise fall back to old collection rating
        int actualRating = (collectionEntry != null) ? collectionEntry.rating : collection.rating;
        
        JDialog editDialog = new JDialog(app, "PeraPera - Edit Collection: " + collection.name, true);
        editDialog.setSize(600, 650); // Wider to fit Top/Bottom buttons
        editDialog.setLocationRelativeTo(app);
        
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(ApplicationService.getBackgroundDark());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Content panel - vertical layout
        JPanel contentPanel = new JPanel();
        contentPanel.setBackground(ApplicationService.getBackgroundDark());
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        
        // Name field (read-only since collection.name is final)
        JPanel namePanel = new JPanel(new BorderLayout());
        namePanel.setBackground(ApplicationService.getBackgroundDark());
        namePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        
        JLabel nameLabel = new JLabel("Collection Name:");
        nameLabel.setForeground(ApplicationService.getAccentColor());
        nameLabel.setFont(new Font("Arial", Font.BOLD, 10));
        namePanel.add(nameLabel, BorderLayout.NORTH);
        
        JTextField nameField = new JTextField(collection.name);
        nameField.setBackground(ApplicationService.getInputBackground());
        nameField.setForeground(ApplicationService.getTextPrimary());
        nameField.setCaretColor(ApplicationService.getTextPrimary());
        nameField.setEditable(false); // Cannot edit final name
        nameField.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        namePanel.add(nameField, BorderLayout.CENTER);
        
        // Rating panel - use actualRating from CollectionEntry
        JPanel ratingPanel = new JPanel(new BorderLayout());
        ratingPanel.setBackground(ApplicationService.getBackgroundDark());
        ratingPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        ratingPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        
        JLabel ratingLabel = new JLabel("Rating: " + actualRating);
        ratingLabel.setForeground(ApplicationService.getAccentColor());
        ratingLabel.setFont(new Font("Arial", Font.BOLD, 10));
        ratingLabel.setPreferredSize(new Dimension(80, 25));
        
        JPanel ratingControlPanel = new JPanel(new BorderLayout());
        ratingControlPanel.setBackground(ApplicationService.getBackgroundDark());
        
        JSlider ratingSlider = new JSlider(0, 100, actualRating);
        ratingSlider.setBackground(ApplicationService.getInputBackground());
        ratingSlider.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        ratingSlider.setPreferredSize(new Dimension(250, 30)); // Constrain slider width
        ratingControlPanel.add(ratingSlider, BorderLayout.CENTER);
        
        JTextField ratingField = new JTextField(String.valueOf(actualRating));
        ratingField.setBackground(ApplicationService.getInputBackground());
        ratingField.setForeground(ApplicationService.getRatingRated());
        ratingField.setCaretColor(ApplicationService.getTextPrimary());
        ratingField.setHorizontalAlignment(JTextField.CENTER);
        ratingField.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        ratingField.setPreferredSize(new Dimension(40, 20));
        ratingControlPanel.add(ratingField, BorderLayout.EAST);
        
        ratingPanel.add(ratingControlPanel, BorderLayout.CENTER);
        
        // Sync slider, text field, and rating label in real-time
        ratingSlider.addChangeListener(e -> {
            int value = ratingSlider.getValue();
            ratingField.setText(String.valueOf(value));
            ratingLabel.setText("Rating: " + value);
        });
        
        ratingField.addActionListener(e -> {
            try {
                int rating = Integer.parseInt(ratingField.getText().trim());
                if (rating >= 0 && rating <= 100) {
                    ratingSlider.setValue(rating);
                } else {
                    JOptionPane.showMessageDialog(editDialog, "Rating must be between 0 and 100", "Invalid Rating", JOptionPane.WARNING_MESSAGE);
                    ratingField.setText(String.valueOf(ratingSlider.getValue()));
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(editDialog, "Please enter a valid number", "Invalid Rating", JOptionPane.WARNING_MESSAGE);
                ratingField.setText(String.valueOf(ratingSlider.getValue()));
            }
        });
        
        // Tags field
        JPanel tagsPanel = new JPanel(new BorderLayout());
        tagsPanel.setBackground(ApplicationService.getBackgroundDark());
        tagsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        tagsPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        
        JLabel tagsLabel = new JLabel("Tags:");
        tagsLabel.setForeground(ApplicationService.getAccentColor());
        tagsLabel.setFont(new Font("Arial", Font.BOLD, 10));
        tagsPanel.add(tagsLabel, BorderLayout.NORTH);
        
        JTextField tagsField = new JTextField(String.join(", ", collection.tags));
        tagsField.setBackground(ApplicationService.getInputBackground());
        tagsField.setForeground(ApplicationService.getTextPrimary());
        tagsField.setCaretColor(ApplicationService.getTextPrimary());
        tagsField.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        tagsField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        tagsPanel.add(tagsField, BorderLayout.CENTER);
        
        // Tags selection button
        JButton selectTagsButton = new JButton("Select Tags");
        selectTagsButton.setBackground(ApplicationService.getInputBackground());
        selectTagsButton.setForeground(ApplicationService.getTextPrimary());
        selectTagsButton.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        selectTagsButton.setFont(new Font("Arial", Font.PLAIN, 9));
        selectTagsButton.setPreferredSize(new Dimension(350, 22));
        selectTagsButton.addActionListener(e -> showTagSelectionDialog(app, collection, tagsField, editDialog));
        tagsPanel.add(selectTagsButton, BorderLayout.SOUTH);
        
        // Thumbnail selection
        JPanel thumbnailPanel = new JPanel(new BorderLayout());
        thumbnailPanel.setBackground(ApplicationService.getBackgroundDark());
        thumbnailPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        thumbnailPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        
        JLabel thumbnailLabel = new JLabel("Thumbnail Comic:");
        thumbnailLabel.setForeground(ApplicationService.getAccentColor());
        thumbnailLabel.setFont(new Font("Arial", Font.BOLD, 10));
        thumbnailPanel.add(thumbnailLabel, BorderLayout.NORTH);
        
        DefaultComboBoxModel<String> thumbnailModel = new DefaultComboBoxModel<>();
        thumbnailModel.addElement("(Use first Title)");
        for (String comicName : collection.comicNames) {
            thumbnailModel.addElement(comicName);
        }
        JComboBox<String> thumbnailCombo = new JComboBox<>(thumbnailModel);
        thumbnailCombo.setBackground(ApplicationService.getInputBackground());
        thumbnailCombo.setForeground(ApplicationService.getTextPrimary());
        thumbnailCombo.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        thumbnailCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        
        if (collection.selectedThumbnailComic != null) {
            thumbnailCombo.setSelectedItem(collection.selectedThumbnailComic);
        }
        thumbnailPanel.add(thumbnailCombo, BorderLayout.CENTER);
        
        // Currently Reading checkbox
        JPanel readingPanel = new JPanel(new BorderLayout());
        readingPanel.setBackground(ApplicationService.getBackgroundDark());
        readingPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        readingPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        
        JCheckBox currentlyReadingCheckBox = new JCheckBox("Currently Reading");
        currentlyReadingCheckBox.setBackground(ApplicationService.getBackgroundDark());
        currentlyReadingCheckBox.setForeground(ApplicationService.getAccentColor());
        currentlyReadingCheckBox.setFont(new Font("Arial", Font.BOLD, 12));
        currentlyReadingCheckBox.setSelected(collectionEntry != null ? collectionEntry.isCurrentlyReading : false);
        currentlyReadingCheckBox.setFocusPainted(false);
        currentlyReadingCheckBox.setToolTipText("Add this collection to your 'Currently Reading' shelf");
        
        readingPanel.add(currentlyReadingCheckBox, BorderLayout.CENTER);
        
        // Comics list - takes remaining space
        JPanel comicsPanel = new JPanel(new BorderLayout());
        comicsPanel.setBackground(ApplicationService.getBackgroundDark());
        comicsPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        
        JLabel comicsLabel = new JLabel("Comics in Collection:");
        comicsLabel.setForeground(ApplicationService.getAccentColor());
        comicsLabel.setFont(new Font("Arial", Font.BOLD, 10));
        comicsPanel.add(comicsLabel, BorderLayout.NORTH);
        
        DefaultListModel<String> comicsModel = new DefaultListModel<>();
        for (String comic : collection.comicNames) {
            comicsModel.addElement(comic);
        }
        
        JList<String> comicsList = new JList<>(comicsModel);
        comicsList.setBackground(ApplicationService.getBackgroundDark());
        comicsList.setForeground(ApplicationService.getTextPrimary());
        comicsList.setSelectionBackground(ApplicationService.getInputBackground());
        comicsList.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        comicsList.setVisibleRowCount(6); // Reduced visible rows
        comicsList.setLayoutOrientation(JList.VERTICAL);
        comicsList.setFixedCellHeight(18); // Smaller row height
        
        JScrollPane comicsScroll = new JScrollPane(comicsList);
        comicsScroll.setBackground(ApplicationService.getBackgroundDark());
        comicsScroll.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        comicsScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        comicsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        comicsPanel.add(comicsScroll, BorderLayout.CENTER);
        
        // Direction constants for unified move logic
        final int MOVE_TOP = 0;
        final int MOVE_UP = 1;
        final int MOVE_DOWN = 2;
        final int MOVE_BOTTOM = 3;
        
        // Unified move helper method - handles both single and multi-selection
        java.util.function.Consumer<Integer> moveSelectedItems = (direction) -> {
            int[] selectedIndices = comicsList.getSelectedIndices();
            if (selectedIndices.length == 0) return;
            
            // Sort indices in ascending order
            java.util.Arrays.sort(selectedIndices);
            
            // Get the CollectionEntry from LibraryService (JSON) - this has the real rating
            CollectionEntry entry = libraryService.getCollection(collection.name);
            
            // Extract selected items (paired names and IDs) preserving order
            java.util.List<String> selectedNames = new java.util.ArrayList<>();
            java.util.List<String> selectedIds = new java.util.ArrayList<>();
            
            for (int index : selectedIndices) {
                selectedNames.add(collection.comicNames.get(index));
                if (entry != null && index < entry.comicIds.size()) {
                    selectedIds.add(entry.comicIds.get(index));
                } else {
                    selectedIds.add(null);
                }
            }
            
            // Remove items from highest index to lowest to prevent shifting issues
            for (int i = selectedIndices.length - 1; i >= 0; i--) {
                int index = selectedIndices[i];
                comicsModel.remove(index);
                collection.comicNames.remove(index);
                if (entry != null && index < entry.comicIds.size()) {
                    entry.comicIds.remove(index);
                }
            }
            
            // Calculate target index based on direction (after removal, list is smaller)
            int listSize = collection.comicNames.size();
            int targetIndex;
            int minSelectedIndex = selectedIndices[0];
            
            switch (direction) {
                case MOVE_TOP:
                    targetIndex = 0;
                    break;
                case MOVE_UP:
                    targetIndex = Math.max(0, minSelectedIndex - 1);
                    break;
                case MOVE_DOWN:
                    targetIndex = Math.min(listSize, minSelectedIndex + 1);
                    break;
                case MOVE_BOTTOM:
                    targetIndex = listSize;
                    break;
                default:
                    targetIndex = 0;
            }
            
            // Re-insert items as contiguous block at target index
            for (int i = 0; i < selectedNames.size(); i++) {
                int insertIndex = targetIndex + i;
                String name = selectedNames.get(i);
                String id = selectedIds.get(i);

                comicsModel.add(insertIndex, name);
                collection.comicNames.add(insertIndex, name);
                // Add UUID to collection.comicIds to prevent name collisions
                if (id != null) {
                    collection.comicIds.add(insertIndex, id);
                }
                if (entry != null && id != null) {
                    entry.comicIds.add(insertIndex, id);
                }
            }
            
            // Refresh UI to ensure list displays correctly
            comicsList.repaint();
            
            // Save the updated CollectionEntry
            if (entry != null) {
                libraryService.saveCollection(entry);
            }
            
            // Re-select the moved items
            int[] newSelection = new int[selectedNames.size()];
            for (int i = 0; i < selectedNames.size(); i++) {
                newSelection[i] = targetIndex + i;
            }
            comicsList.setSelectedIndices(newSelection);
            
            // Scroll to make the moved items visible
            comicsList.ensureIndexIsVisible(targetIndex);
        };
        
        // Reorder and remove buttons
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.setBackground(ApplicationService.getBackgroundDark());
        
        JButton moveUpButton = new JButton("▲");
        moveUpButton.setBackground(ApplicationService.getInputBackground());
        moveUpButton.setForeground(ApplicationService.getTextPrimary());
        moveUpButton.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        moveUpButton.setFont(new Font("Arial", Font.PLAIN, 9));
        moveUpButton.setPreferredSize(new Dimension(35, 22));
        moveUpButton.setToolTipText("Move selected comics up");
        moveUpButton.addActionListener(e -> moveSelectedItems.accept(MOVE_UP));
        
        JButton moveDownButton = new JButton("▼");
        moveDownButton.setBackground(ApplicationService.getInputBackground());
        moveDownButton.setForeground(ApplicationService.getTextPrimary());
        moveDownButton.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        moveDownButton.setFont(new Font("Arial", Font.PLAIN, 9));
        moveDownButton.setPreferredSize(new Dimension(35, 22));
        moveDownButton.setToolTipText("Move selected comics down");
        moveDownButton.addActionListener(e -> moveSelectedItems.accept(MOVE_DOWN));
        
        JButton moveTopButton = new JButton("Top");
        moveTopButton.setBackground(ApplicationService.getInputBackground());
        moveTopButton.setForeground(ApplicationService.getTextPrimary());
        moveTopButton.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        moveTopButton.setFont(new Font("Arial", Font.PLAIN, 9));
        moveTopButton.setPreferredSize(new Dimension(50, 22));
        moveTopButton.setToolTipText("Move selected comics to top");
        moveTopButton.addActionListener(e -> moveSelectedItems.accept(MOVE_TOP));
        
        JButton moveBottomButton = new JButton("Bottom");
        moveBottomButton.setBackground(ApplicationService.getInputBackground());
        moveBottomButton.setForeground(ApplicationService.getTextPrimary());
        moveBottomButton.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        moveBottomButton.setFont(new Font("Arial", Font.PLAIN, 9));
        moveBottomButton.setPreferredSize(new Dimension(50, 22));
        moveBottomButton.setToolTipText("Move selected comics to bottom");
        moveBottomButton.addActionListener(e -> moveSelectedItems.accept(MOVE_BOTTOM));
        
        JButton removeButton = new JButton("Remove Selected");
        removeButton.setBackground(ApplicationService.getInputBackground());
        removeButton.setForeground(ApplicationService.getTextPrimary());
        removeButton.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        removeButton.setFont(new Font("Arial", Font.PLAIN, 9));
        removeButton.setPreferredSize(new Dimension(120, 22));
        removeButton.addActionListener(e -> {
            CollectionEntry entry = libraryService.getCollection(collection.name);
            
            for (String comic : comicsList.getSelectedValuesList()) {
                int index = comicsModel.indexOf(comic);
                if (index >= 0) {
                    comicsModel.removeElement(comic);
                    collection.comicNames.remove(comic);
                    
                    // Also remove from CollectionEntry comicIds by index
                    if (entry != null && index < entry.comicIds.size()) {
                        entry.comicIds.remove(index);
                    }
                }
            }
            
            // Save the updated CollectionEntry
            if (entry != null) {
                libraryService.saveCollection(entry);
            }
        });
        
        // Sort A-Z button
        JButton sortAZButton = new JButton("Sort A-Z");
        sortAZButton.setBackground(ApplicationService.getInputBackground());
        sortAZButton.setForeground(ApplicationService.getTextPrimary());
        sortAZButton.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        sortAZButton.setFont(new Font("Arial", Font.PLAIN, 9));
        sortAZButton.setPreferredSize(new Dimension(70, 22));
        sortAZButton.setToolTipText("Sort alphabetically (A-Z)");
        sortAZButton.addActionListener(e -> {
            CollectionEntry entry = libraryService.getCollection(collection.name);
            if (entry != null) {
                // Create list of pairs (name, id) to sort together
                List<String[]> pairs = new ArrayList<>();
                for (int i = 0; i < collection.comicNames.size(); i++) {
                    String name = collection.comicNames.get(i);
                    String id = (i < entry.comicIds.size()) ? entry.comicIds.get(i) : null;
                    pairs.add(new String[]{name, id});
                }
                
                // Sort by name using default comparator (lexicographical)
                pairs.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));

                // Update models
                comicsModel.clear();
                collection.comicNames.clear();
                collection.comicIds.clear();
                entry.comicIds.clear();

                for (String[] pair : pairs) {
                    comicsModel.addElement(pair[0]);
                    collection.comicNames.add(pair[0]);
                    if (pair[1] != null) {
                        collection.comicIds.add(pair[1]);
                        entry.comicIds.add(pair[1]);
                    }
                }
                entry.isManuallySorted = true;
                libraryService.saveCollection(entry);
                // Force immediate save to disk
                libraryService.saveCollections();
            }
        });

        // Natural Sort button
        JButton sortNaturalButton = new JButton("Natural");
        sortNaturalButton.setBackground(ApplicationService.getInputBackground());
        sortNaturalButton.setForeground(ApplicationService.getTextPrimary());
        sortNaturalButton.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        sortNaturalButton.setFont(new Font("Arial", Font.PLAIN, 9));
        sortNaturalButton.setPreferredSize(new Dimension(70, 22));
        sortNaturalButton.setToolTipText("Sort naturally (Chapter 2 before Chapter 10)");
        sortNaturalButton.addActionListener(e -> {
            CollectionEntry entry = libraryService.getCollection(collection.name);
            if (entry != null) {
                // Create list of pairs (name, id) to sort together
                List<String[]> pairs = new ArrayList<>();
                for (int i = 0; i < collection.comicNames.size(); i++) {
                    String name = collection.comicNames.get(i);
                    String id = (i < entry.comicIds.size()) ? entry.comicIds.get(i) : null;
                    pairs.add(new String[]{name, id});
                }
                
                // Sort by name using natural (alphanumeric) comparator
                AlphanumericComparator comparator = new AlphanumericComparator();
                pairs.sort((a, b) -> comparator.compare(a[0], b[0]));

                // Update models
                comicsModel.clear();
                collection.comicNames.clear();
                collection.comicIds.clear();
                entry.comicIds.clear();

                for (String[] pair : pairs) {
                    comicsModel.addElement(pair[0]);
                    collection.comicNames.add(pair[0]);
                    if (pair[1] != null) {
                        collection.comicIds.add(pair[1]);
                        entry.comicIds.add(pair[1]);
                    }
                }

                entry.isManuallySorted = true;
                libraryService.saveCollection(entry);
                // Force immediate save to disk
                libraryService.saveCollections();
            }
        });
        
        buttonPanel.add(moveUpButton);
        buttonPanel.add(moveDownButton);
        buttonPanel.add(moveTopButton);
        buttonPanel.add(moveBottomButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(sortAZButton);
        buttonPanel.add(sortNaturalButton);
        comicsPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        // Add all panels to content panel in vertical order
        contentPanel.add(namePanel);
        contentPanel.add(ratingPanel);
        contentPanel.add(tagsPanel);
        contentPanel.add(thumbnailPanel);
        contentPanel.add(readingPanel);
        contentPanel.add(comicsPanel);
        
        mainPanel.add(contentPanel, BorderLayout.CENTER);
        
        // Buttons
        JPanel bottomButtonPanel = new JPanel(new FlowLayout());
        bottomButtonPanel.setBackground(ApplicationService.getBackgroundDark());
        bottomButtonPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        
        JButton saveButton = new JButton("Save");
        saveButton.setBackground(ApplicationService.getSuccessColor());
        saveButton.setForeground(ApplicationService.getTextPrimary());
        saveButton.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        saveButton.setPreferredSize(new Dimension(70, 28));
        saveButton.setFont(new Font("Arial", Font.PLAIN, 11));
        
        JButton deleteButton = new JButton("Delete");
        deleteButton.setBackground(ApplicationService.getErrorColor());
        deleteButton.setForeground(ApplicationService.getTextPrimary());
        deleteButton.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        deleteButton.setPreferredSize(new Dimension(70, 28));
        deleteButton.setFont(new Font("Arial", Font.PLAIN, 11));
        
        JButton cancelButton = new JButton("Cancel");
        cancelButton.setBackground(ApplicationService.getInputBackground());
        cancelButton.setForeground(ApplicationService.getTextPrimary());
        cancelButton.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        cancelButton.setPreferredSize(new Dimension(70, 28));
        cancelButton.setFont(new Font("Arial", Font.PLAIN, 11));
        
        bottomButtonPanel.add(saveButton);
        bottomButtonPanel.add(deleteButton);
        bottomButtonPanel.add(cancelButton);
        
        saveButton.addActionListener(evt -> {
            // Update the CollectionEntry in LibraryService (JSON)
            CollectionEntry entryToSave = libraryService.getCollection(collection.name);
            if (entryToSave == null) {
                entryToSave = new CollectionEntry(collection.name);
                // Copy comic IDs from the old collection
                for (String comicName : collection.comicNames) {
                    String comicId;
                    // Use path-based lookup if collection has a folderPath (auto-generated collections)
                    if (collection.folderPath != null && !collection.folderPath.isEmpty()) {
                        comicId = findComicIdByNameAndPath(comicName, collection.folderPath, libraryService);
                    } else {
                        // Fallback to name-only lookup for manual collections
                        comicId = findComicIdByName(comicName, libraryService);
                    }
                    if (comicId != null) {
                        entryToSave.comicIds.add(comicId);
                    }
                }
            }
            
            // Update rating
            entryToSave.rating = ratingSlider.getValue();
            
            // Update tags
            String[] tags = tagsField.getText().split(",");
            entryToSave.tags.clear();
            for (String tag : tags) {
                String trimmed = tag.trim();
                if (!trimmed.isEmpty()) {
                    entryToSave.tags.add(trimmed);
                }
            }
            
            // Update thumbnail selection - convert name to ID
            String selected = (String) thumbnailCombo.getSelectedItem();
            if (selected != null && !selected.equals("(Use first Title)")) {
                String selectedId;
                // Use path-based lookup if collection has a folderPath (auto-generated collections)
                if (collection.folderPath != null && !collection.folderPath.isEmpty()) {
                    selectedId = findComicIdByNameAndPath(selected, collection.folderPath, libraryService);
                } else {
                    // Fallback to name-only lookup for manual collections
                    selectedId = findComicIdByName(selected, libraryService);
                }
                entryToSave.selectedThumbnailComicId = selectedId;
            } else {
                entryToSave.selectedThumbnailComicId = null;
            }
            
            // Update Currently Reading status
            entryToSave.isCurrentlyReading = currentlyReadingCheckBox.isSelected();
            
            // Save to LibraryService (writes to collections.json)
            libraryService.saveCollection(entryToSave);
            
            // Also update the old collection object for compatibility
            collection.rating = ratingSlider.getValue();
            collection.tags.clear();
            collection.tags.addAll(entryToSave.tags);
            collection.selectedThumbnailComic = selected;
            
            app.refreshDisplay();
            editDialog.dispose();
        });
        
        deleteButton.addActionListener(evt -> {
            // Confirm deletion
            int result = JOptionPane.showConfirmDialog(editDialog,
                "Are you sure you want to delete collection '" + collection.name + "'?\n\n" +
                "This will permanently remove the collection and cannot be undone.",
                "Delete Collection",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
                
            if (result == JOptionPane.YES_OPTION) {
                // Remove from LibraryService (JSON)
                CollectionEntry entryToDelete = libraryService.getCollection(collection.name);
                if (entryToDelete != null) {
                    libraryService.deleteCollection(collection.name);
                }
                
                // Remove from old collections map
                app.getCollections().remove(collection.name);
                
                // Save old collections (for compatibility)
                app.saveCollections();
                
                app.refreshDisplay();
                editDialog.dispose();
            }
        });
        
        cancelButton.addActionListener(evt -> editDialog.dispose());
        
        mainPanel.add(bottomButtonPanel, BorderLayout.SOUTH);
        
        editDialog.add(mainPanel);
        editDialog.setVisible(true);
    }
    
    /**
     * Shows a dialog for selecting tags for a collection.
     */
    public static void showTagSelectionDialog(ImageBrowserApp app, Collection collection, JTextField tagsField, Dialog parentDialog) {
        // Persistent state: pre-populate with collection's current tags
        Set<String> selectedTags = new HashSet<>(collection.tags);
        
        // Get master tag list, sorted alphabetically (case-insensitive)
        List<String> masterTagList = new ArrayList<>(app.getAllTags());
        Collections.sort(masterTagList, String.CASE_INSENSITIVE_ORDER);
        
        JDialog tagDialog = new JDialog(parentDialog, "PeraPera - Select Tags for Collection", true);
        tagDialog.setSize(400, 550);
        tagDialog.setLocationRelativeTo(parentDialog);
        
        // Main panel with dark theme
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(ApplicationService.getBackgroundDark());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Search panel at top
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBackground(ApplicationService.getBackgroundDark());
        searchPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        
        JLabel searchLabel = new JLabel("Search: ");
        searchLabel.setForeground(ApplicationService.getTextPrimary());
        searchPanel.add(searchLabel, BorderLayout.WEST);
        
        JTextField searchField = new JTextField();
        searchField.setBackground(ApplicationService.getInputBackground());
        searchField.setForeground(ApplicationService.getTextPrimary());
        searchField.setCaretColor(ApplicationService.getTextPrimary());
        searchField.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        searchPanel.add(searchField, BorderLayout.CENTER);
        
        // Checkbox panel with vertical BoxLayout
        JPanel tagsPanel = new JPanel();
        tagsPanel.setBackground(ApplicationService.getBackgroundDark());
        tagsPanel.setLayout(new BoxLayout(tagsPanel, BoxLayout.Y_AXIS));
        
        // JScrollPane with performance settings
        JScrollPane scrollPane = new JScrollPane(tagsPanel);
        scrollPane.setBackground(ApplicationService.getBackgroundDark());
        scrollPane.getViewport().setBackground(ApplicationService.getBackgroundDark());
        scrollPane.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        
        // Method to refresh checkboxes based on filter
        Runnable refreshCheckboxes = () -> {
            tagsPanel.removeAll();
            String filter = searchField.getText().trim().toLowerCase();
            
            for (String tag : masterTagList) {
                // Filter: show all if filter is empty, or only matching tags
                if (filter.isEmpty() || tag.toLowerCase().contains(filter)) {
                    int count = app.getFolderTagCount(tag);
                    String label = tag + (count > 0 ? " (" + count + " Titles)" : "");
                    
                    JCheckBox checkBox = new JCheckBox(label);
                    checkBox.setBackground(ApplicationService.getBackgroundDark());
                    checkBox.setForeground(ApplicationService.getTextPrimary());
                    checkBox.setFocusPainted(false);
                    
                    // Check selectedTags set to determine if selected
                    checkBox.setSelected(selectedTags.contains(tag));
                    
                    // ActionListener updates selectedTags set immediately
                    checkBox.addActionListener(e -> {
                        if (checkBox.isSelected()) {
                            selectedTags.add(tag);
                        } else {
                            selectedTags.remove(tag);
                        }
                    });
                    
                    tagsPanel.add(checkBox);
                }
            }
            
            // Update selected count label
            JLabel titleLabel = (JLabel) ((BorderLayout) mainPanel.getLayout()).getLayoutComponent(BorderLayout.NORTH);
            if (titleLabel != null) {
                Component[] comps = ((JPanel) titleLabel.getParent()).getComponents();
                for (Component c : comps) {
                    if (c instanceof JLabel && c != titleLabel) {
                        ((JLabel) c).setText("Selected: " + selectedTags.size());
                        break;
                    }
                }
            }
            
            tagsPanel.revalidate();
            tagsPanel.repaint();
        };
        
        // Search filtering with DocumentListener
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { refreshCheckboxes.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { refreshCheckboxes.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { refreshCheckboxes.run(); }
        });
        
        // Initial checkbox population
        refreshCheckboxes.run();
        
        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.setBackground(ApplicationService.getBackgroundDark());
        
        JButton okButton = new JButton("OK");
        okButton.setBackground(ApplicationService.getInputBackground());
        okButton.setForeground(ApplicationService.getTextPrimary());
        okButton.setFocusPainted(false);
        okButton.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        
        JButton cancelButton = new JButton("Cancel");
        cancelButton.setBackground(ApplicationService.getInputBackground());
        cancelButton.setForeground(ApplicationService.getTextPrimary());
        cancelButton.setFocusPainted(false);
        cancelButton.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        
        okButton.addActionListener(e -> {
            // Update collection with selectedTags set
            collection.tags.clear();
            collection.tags.addAll(selectedTags);
            tagsField.setText(String.join(", ", collection.tags));
            tagDialog.dispose();
        });
        
        cancelButton.addActionListener(e -> tagDialog.dispose());
        
        // Title panel showing counts
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setBackground(ApplicationService.getBackgroundDark());
        
        JLabel titleLabel = new JLabel("Available Tags (" + masterTagList.size() + "):");
        titleLabel.setForeground(ApplicationService.getTextPrimary());
        titleLabel.setFont(new Font("Arial", Font.BOLD, 12));
        
        JLabel selectedLabel = new JLabel("Selected: " + selectedTags.size());
        selectedLabel.setForeground(ApplicationService.getAccentColor());
        selectedLabel.setFont(new Font("Arial", Font.BOLD, 11));
        
        titlePanel.add(titleLabel, BorderLayout.WEST);
        titlePanel.add(selectedLabel, BorderLayout.EAST);
        
        // North panel with title and search
        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.setBackground(ApplicationService.getBackgroundDark());
        northPanel.add(titlePanel, BorderLayout.NORTH);
        northPanel.add(searchPanel, BorderLayout.SOUTH);
        
        mainPanel.add(northPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        tagDialog.add(mainPanel);
        tagDialog.setVisible(true);
    }
    
    /**
     * Shows a dialog for adjusting browser background opacity.
     */
    public static void showBrowserBackgroundOpacityDialog(Frame parent) {
        showOpacityDialog(parent, "Browser Background Opacity",
            ApplicationService.getBrowserBackgroundOpacity(),
            ApplicationService::setBrowserBackgroundOpacity,
            () -> {
                if (parent instanceof ImageBrowserApp) {
                    ((ImageBrowserApp) parent).refreshDisplay();
                }
            });
    }
    
    /**
     * Shows a dialog for adjusting viewer background opacity.
     */
    public static void showViewerBackgroundOpacityDialog(Frame parent) {
        showOpacityDialog(parent, "Viewer Background Opacity",
            ApplicationService.getViewerBackgroundOpacity(),
            ApplicationService::setViewerBackgroundOpacity,
            null); // No refresh needed - applies when viewer is opened
    }
    
    /**
     * Shows the viewer settings dialog for customizing reading experience.
     */
    public static void showViewerSettingsDialog(Frame parent) {
        JDialog dialog = new JDialog(parent, "PeraPera - Viewer Settings", true);
        dialog.setSize(450, 500);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(parent);
        
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(ApplicationService.getBackgroundDark());
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // === SECTION 1: General Viewer Settings ===
        JPanel generalSection = createSectionPanel("General Viewer Settings");
        
        // Control Panel Default
        JPanel controlPanelToggle = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controlPanelToggle.setBackground(ApplicationService.getBackgroundDark());
        
        JCheckBox controlPanelCheckBox = new JCheckBox("Show Control Panel by Default");
        controlPanelCheckBox.setBackground(ApplicationService.getBackgroundDark());
        controlPanelCheckBox.setForeground(ApplicationService.getTextPrimary());
        controlPanelCheckBox.setFont(new Font("Arial", Font.PLAIN, 12));
        controlPanelCheckBox.setSelected(ApplicationService.getControlPanelDefaultVisible());
        controlPanelCheckBox.setFocusPainted(false);
        
        controlPanelToggle.add(controlPanelCheckBox);
        generalSection.add(controlPanelToggle);
        
        mainPanel.add(generalSection);
        mainPanel.add(Box.createVerticalStrut(15));
        
        // === SECTION 2: Webtoon Scroll Settings ===
        JPanel webtoonSection = createSectionPanel("Webtoon Scroll Settings");
        
        // Add note label
        JLabel webtoonNote = new JLabel("(These settings only apply to Webtoon Mode)");
        webtoonNote.setForeground(ApplicationService.getTextSecondary());
        webtoonNote.setFont(new Font("Arial", Font.ITALIC, 11));
        webtoonSection.add(webtoonNote);
        webtoonSection.add(Box.createVerticalStrut(10));
        
        // Scroll Animation Speed (percentage-based, inverted: low % = fast, high % = slow/smooth)
        JPanel speedPanel = new JPanel(new BorderLayout(10, 0));
        speedPanel.setBackground(ApplicationService.getBackgroundDark());
        
        JLabel speedLabel = new JLabel("Scroll Smoothness:");
        speedLabel.setForeground(ApplicationService.getTextPrimary());
        speedLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        speedLabel.setPreferredSize(new Dimension(120, 25));
        speedLabel.setToolTipText("Lower = faster/snappy, Higher = slower/smoother");
        
        int currentSpeed = ApplicationService.getWebtoonScrollSpeed();
        // Convert old 5-50ms to 1-100% (inverted: 5ms fast -> 1%, 50ms slow -> 100%)
        int currentPercent = (int) ((currentSpeed - 5) / 45.0 * 99 + 1);
        JTextField speedField = new JTextField(String.valueOf(currentPercent), 3);
        speedField.setBackground(ApplicationService.getInputBackground());
        speedField.setForeground(ApplicationService.getTextPrimary());
        speedField.setCaretColor(ApplicationService.getTextPrimary());
        speedField.setHorizontalAlignment(JTextField.CENTER);
        speedField.setFont(new Font("Arial", Font.BOLD, 12));
        speedField.setPreferredSize(new Dimension(45, 25));
        speedField.setToolTipText("Click and type to set value (1-100%)");
        
        JLabel speedUnitLabel = new JLabel("%");
        speedUnitLabel.setForeground(ApplicationService.getAccentColor());
        speedUnitLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        
        JSlider speedSlider = new JSlider(1, 100, currentPercent);
        speedSlider.setBackground(ApplicationService.getBackgroundDark());
        speedSlider.setForeground(ApplicationService.getTextPrimary());
        speedSlider.setMajorTickSpacing(20);
        speedSlider.setPaintTicks(true);
        speedSlider.addChangeListener(e -> {
            int value = speedSlider.getValue();
            speedField.setText(String.valueOf(value));
        });
        
        speedField.addActionListener(e -> {
            try {
                int value = Integer.parseInt(speedField.getText().trim());
                if (value >= 1 && value <= 100) {
                    speedSlider.setValue(value);
                } else {
                    speedField.setText(String.valueOf(speedSlider.getValue()));
                }
            } catch (NumberFormatException ex) {
                speedField.setText(String.valueOf(speedSlider.getValue()));
            }
        });
        
        speedPanel.add(speedLabel, BorderLayout.WEST);
        speedPanel.add(speedSlider, BorderLayout.CENTER);
        JPanel speedValuePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        speedValuePanel.setBackground(ApplicationService.getBackgroundDark());
        speedValuePanel.add(speedField);
        speedValuePanel.add(speedUnitLabel);
        speedPanel.add(speedValuePanel, BorderLayout.EAST);
        
        webtoonSection.add(speedPanel);
        webtoonSection.add(Box.createVerticalStrut(10));
        
        // Smooth Scroll Distance
        JPanel distancePanel = new JPanel(new BorderLayout(10, 0));
        distancePanel.setBackground(ApplicationService.getBackgroundDark());
        
        JLabel distanceLabel = new JLabel("Scroll Distance:");
        distanceLabel.setForeground(ApplicationService.getTextPrimary());
        distanceLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        distanceLabel.setPreferredSize(new Dimension(120, 25));
        distanceLabel.setToolTipText("How many pixels to scroll per key press");
        
        int currentDistance = ApplicationService.getWebtoonScrollDistance();
        JTextField distanceField = new JTextField(String.valueOf(currentDistance), 4);
        distanceField.setBackground(ApplicationService.getInputBackground());
        distanceField.setForeground(ApplicationService.getTextPrimary());
        distanceField.setCaretColor(ApplicationService.getTextPrimary());
        distanceField.setHorizontalAlignment(JTextField.CENTER);
        distanceField.setFont(new Font("Arial", Font.BOLD, 12));
        distanceField.setPreferredSize(new Dimension(50, 25));
        distanceField.setToolTipText("Click and type to set value (50-500px)");
        
        JLabel distanceUnitLabel = new JLabel("px");
        distanceUnitLabel.setForeground(ApplicationService.getAccentColor());
        distanceUnitLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        
        JSlider distanceSlider = new JSlider(50, 500, currentDistance);
        distanceSlider.setBackground(ApplicationService.getBackgroundDark());
        distanceSlider.setForeground(ApplicationService.getTextPrimary());
        distanceSlider.setMajorTickSpacing(100);
        distanceSlider.setPaintTicks(true);
        distanceSlider.addChangeListener(e -> {
            int value = distanceSlider.getValue();
            distanceField.setText(String.valueOf(value));
        });
        
        distanceField.addActionListener(e -> {
            try {
                int value = Integer.parseInt(distanceField.getText().trim());
                if (value >= 50 && value <= 500) {
                    distanceSlider.setValue(value);
                } else {
                    distanceField.setText(String.valueOf(distanceSlider.getValue()));
                }
            } catch (NumberFormatException ex) {
                distanceField.setText(String.valueOf(distanceSlider.getValue()));
            }
        });
        
        distancePanel.add(distanceLabel, BorderLayout.WEST);
        distancePanel.add(distanceSlider, BorderLayout.CENTER);
        JPanel distanceValuePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        distanceValuePanel.setBackground(ApplicationService.getBackgroundDark());
        distanceValuePanel.add(distanceField);
        distanceValuePanel.add(distanceUnitLabel);
        distancePanel.add(distanceValuePanel, BorderLayout.EAST);
        
        webtoonSection.add(distancePanel);
        
        mainPanel.add(webtoonSection);
        mainPanel.add(Box.createVerticalStrut(20));
        
        // === BUTTONS ===
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonPanel.setBackground(ApplicationService.getBackgroundDark());
        
        JButton saveButton = new JButton("Save");
        saveButton.setBackground(ApplicationService.getSuccessColor());
        saveButton.setForeground(ApplicationService.getTextPrimary());
        saveButton.setFocusPainted(false);
        saveButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ApplicationService.getBorderColor()),
            BorderFactory.createEmptyBorder(8, 25, 8, 25)
        ));
        saveButton.setFont(new Font("Arial", Font.PLAIN, 12));
        saveButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        JButton cancelButton = new JButton("Cancel");
        cancelButton.setBackground(ApplicationService.getInputBackground());
        cancelButton.setForeground(ApplicationService.getTextPrimary());
        cancelButton.setFocusPainted(false);
        cancelButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ApplicationService.getBorderColor()),
            BorderFactory.createEmptyBorder(8, 20, 8, 20)
        ));
        cancelButton.setFont(new Font("Arial", Font.PLAIN, 12));
        cancelButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        saveButton.addActionListener(e -> {
            // Save all settings
            ApplicationService.setControlPanelDefaultVisible(controlPanelCheckBox.isSelected());
            // Convert percentage (1-100%) back to storage format (5-50ms) for compatibility
            int percentValue = speedSlider.getValue();
            int storageValue = (int) ((percentValue - 1) / 99.0 * 45 + 5);
            ApplicationService.setWebtoonScrollSpeed(storageValue);
            ApplicationService.setWebtoonScrollDistance(distanceSlider.getValue());
            
            dialog.dispose();
        });
        
        cancelButton.addActionListener(e -> dialog.dispose());
        
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);
        
        mainPanel.add(buttonPanel);
        
        dialog.add(mainPanel);
        dialog.setVisible(true);
    }
    
    /**
     * Helper method to create a section panel with title.
     */
    private static JPanel createSectionPanel(String title) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(ApplicationService.getBackgroundDark());
        section.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ApplicationService.getBorderColor()),
                title,
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                new Font("Arial", Font.BOLD, 13),
                ApplicationService.getAccentColor()
            ),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        return section;
    }
    
    /**
     * Finds a comic ID by its folder name using LibraryService.
     */
    private static String findComicIdByName(String folderName, LibraryService libraryService) {
        for (ComicEntry entry : libraryService.getLibraryCache().values()) {
            if (entry.name.equals(folderName)) {
                return entry.id;
            }
        }
        return null;
    }

    /**
     * Finds a comic ID by its folder name and parent path using LibraryService.
     * This prevents collisions when comics have the same name in different series.
     */
    private static String findComicIdByNameAndPath(String folderName, String parentPath, LibraryService libraryService) {
        for (ComicEntry entry : libraryService.getLibraryCache().values()) {
            if (entry.name.equals(folderName)) {
                // If parentPath is provided, check if the comic is within that path
                if (parentPath != null && !parentPath.isEmpty()) {
                    File comicFile = new File(entry.folderPath);
                    File parentFile = new File(parentPath);
                    // Check if comic's parent matches the provided parent path
                    if (comicFile.getParentFile() != null && 
                        comicFile.getParentFile().getAbsolutePath().equals(parentFile.getAbsolutePath())) {
                        return entry.id;
                    }
                } else {
                    return entry.id;
                }
            }
        }
        return null;
    }
    
    /**
     * Shows the dialog to add the current folder to a collection.
     */
    public static void showAddToCollectionDialog(Frame parent, File folder, LibraryProvider library) {
        JDialog dialog = new JDialog(parent, "PeraPera - Add to Collection", true);
        dialog.setSize(450, 350);
        dialog.setLocationRelativeTo(parent);
        
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ApplicationService.getInputBackground());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        JLabel label = new JLabel("Add '" + folder.getName() + "' to collection:", SwingConstants.CENTER);
        label.setForeground(ApplicationService.getTextPrimary());
        panel.add(label, BorderLayout.NORTH);
        
        // Collection options panel
        JPanel optionsPanel = new JPanel(new GridLayout(2, 1, 10, 10));
        optionsPanel.setBackground(ApplicationService.getInputBackground());
        
        // Existing collections
        JPanel existingPanel = new JPanel(new BorderLayout());
        existingPanel.setBackground(ApplicationService.getInputBackground());
        
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
        existingList.setBackground(ApplicationService.getInputBackground());
        existingList.setForeground(ApplicationService.getTextPrimary());
        existingList.setSelectionBackground(ApplicationService.getInputBackground());
        
        // Populate collections from library
        updateCollectionsList(existingModel, "", library);
        
        // Add search listener
        collectionSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateCollectionsList(existingModel, collectionSearchField.getText(), library); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateCollectionsList(existingModel, collectionSearchField.getText(), library); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateCollectionsList(existingModel, collectionSearchField.getText(), library); }
        });
        
        JScrollPane existingScroll = new JScrollPane(existingList);
        existingScroll.setPreferredSize(new Dimension(400, 100));
        existingPanel.add(existingScroll, BorderLayout.SOUTH);
        
        optionsPanel.add(existingPanel);
        
        // New collection
        JPanel newPanel = new JPanel(new BorderLayout());
        newPanel.setBackground(ApplicationService.getInputBackground());
        
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
        buttonPanel.setBackground(ApplicationService.getInputBackground());
        
        JButton addButton = new JButton("Add to Collection");
        JButton cancelButton = new JButton("Cancel");
        
        addButton.addActionListener(e -> {
            // Check if existing collection is selected
            String selected = existingList.getSelectedValue();
            if (selected != null) {
                String collectionName = selected.split(" \\(")[0];
                
                // Get collection from library
                Collection collection = library.getCollections().get(collectionName);
                
                if (collection == null) {
                    JOptionPane.showMessageDialog(dialog,
                        "Collection '" + collectionName + "' not found!",
                        "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // Get LibraryService for UUID lookup
                LibraryService libraryService = null;
                if (library instanceof ImageBrowserApp) {
                    libraryService = ((ImageBrowserApp) library).getLibraryService();
                }
                String comicId = (libraryService != null) ? libraryService.getComicId(folder) : null;

                if (collection.comicNames.contains(folder.getName()) || (comicId != null && collection.comicIds.contains(comicId))) {
                    JOptionPane.showMessageDialog(dialog,
                        "'" + folder.getName() + "' is already in collection '" + collectionName + "'",
                        "Already in Collection", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    collection.comicNames.add(folder.getName());
                    // Add UUID to prevent name collisions
                    if (libraryService != null) {
                        if (comicId != null && !collection.comicIds.contains(comicId)) {
                            collection.comicIds.add(comicId);
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
                    if (library.getCollections().containsKey(newCollectionName)) {
                        JOptionPane.showMessageDialog(dialog, 
                            "Collection '" + newCollectionName + "' already exists!", 
                            "Collection Exists", JOptionPane.ERROR_MESSAGE);
                    } else {
                        Collection newCollection = new Collection(newCollectionName);
                        newCollection.comicNames.add(folder.getName());
                        // Add UUID to prevent name collisions
                        LibraryService libraryService = null;
                        if (library instanceof ImageBrowserApp) {
                            libraryService = ((ImageBrowserApp) library).getLibraryService();
                        }
                        if (libraryService != null) {
                            String comicId = libraryService.getComicId(folder);
                            if (comicId != null && !newCollection.comicIds.contains(comicId)) {
                                newCollection.comicIds.add(comicId);
                            }
                        }
                        library.getCollections().put(newCollectionName, newCollection);
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
    
    /**
     * Updates the collections list based on search filter.
     */
    private static void updateCollectionsList(DefaultListModel<String> model, String searchText, LibraryProvider library) {
        model.clear();
        String searchLower = searchText.toLowerCase();
        
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
    
    /**
     * Shows a dialog for setting up password protection.
     * Called when user enables password protection from settings menu.
     */
    public static void showPasswordSetupDialog(ImageBrowserApp app, JCheckBoxMenuItem toggleItem) {
        JDialog dialog = new JDialog(app, "PeraPera - Set Password", true);
        dialog.setSize(350, 200);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(app);
        
        // Main panel with dark theme
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(ApplicationService.getBackgroundDark());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // Input panel
        JPanel inputPanel = new JPanel(new GridLayout(2, 1, 5, 10));
        inputPanel.setBackground(ApplicationService.getBackgroundDark());
        
        // Password field
        JPanel passwordPanel = new JPanel(new BorderLayout(10, 0));
        passwordPanel.setBackground(ApplicationService.getBackgroundDark());
        
        JLabel passwordLabel = new JLabel("Password:");
        passwordLabel.setForeground(ApplicationService.getTextPrimary());
        passwordLabel.setFont(new Font("Arial", Font.PLAIN, 13));
        passwordLabel.setPreferredSize(new Dimension(100, 25));
        
        JPasswordField passwordField = new JPasswordField(20);
        passwordField.setBackground(ApplicationService.getInputBackground());
        passwordField.setForeground(ApplicationService.getTextPrimary());
        passwordField.setCaretColor(ApplicationService.getTextPrimary());
        passwordField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ApplicationService.getBorderColor()),
            BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
        passwordField.setFont(new Font("Arial", Font.PLAIN, 13));
        
        passwordPanel.add(passwordLabel, BorderLayout.WEST);
        passwordPanel.add(passwordField, BorderLayout.CENTER);
        
        // Confirm password field
        JPanel confirmPanel = new JPanel(new BorderLayout(10, 0));
        confirmPanel.setBackground(ApplicationService.getBackgroundDark());
        
        JLabel confirmLabel = new JLabel("Confirm:");
        confirmLabel.setForeground(ApplicationService.getTextPrimary());
        confirmLabel.setFont(new Font("Arial", Font.PLAIN, 13));
        confirmLabel.setPreferredSize(new Dimension(100, 25));
        
        JPasswordField confirmField = new JPasswordField(20);
        confirmField.setBackground(ApplicationService.getInputBackground());
        confirmField.setForeground(ApplicationService.getTextPrimary());
        confirmField.setCaretColor(ApplicationService.getTextPrimary());
        confirmField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ApplicationService.getBorderColor()),
            BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
        confirmField.setFont(new Font("Arial", Font.PLAIN, 13));
        
        confirmPanel.add(confirmLabel, BorderLayout.WEST);
        confirmPanel.add(confirmField, BorderLayout.CENTER);
        
        inputPanel.add(passwordPanel);
        inputPanel.add(confirmPanel);
        
        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonPanel.setBackground(ApplicationService.getBackgroundDark());
        
        JButton okButton = new JButton("OK");
        okButton.setBackground(ApplicationService.getInputBackground());
        okButton.setForeground(ApplicationService.getTextPrimary());
        okButton.setFocusPainted(false);
        okButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ApplicationService.getBorderColor()),
            BorderFactory.createEmptyBorder(6, 20, 6, 20)
        ));
        okButton.setFont(new Font("Arial", Font.PLAIN, 12));
        okButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        JButton cancelButton = new JButton("Cancel");
        cancelButton.setBackground(ApplicationService.getInputBackground());
        cancelButton.setForeground(ApplicationService.getTextPrimary());
        cancelButton.setFocusPainted(false);
        cancelButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ApplicationService.getBorderColor()),
            BorderFactory.createEmptyBorder(6, 15, 6, 15)
        ));
        cancelButton.setFont(new Font("Arial", Font.PLAIN, 12));
        cancelButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        
        // Button actions
        okButton.addActionListener(e -> {
            String password = new String(passwordField.getPassword());
            String confirm = new String(confirmField.getPassword());
            
            if (password.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, 
                    "Password cannot be empty!", 
                    "Error", 
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            if (!password.equals(confirm)) {
                JOptionPane.showMessageDialog(dialog, 
                    "Passwords do not match!", 
                    "Error", 
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // Save password and enable protection
            ApplicationService.setPassword(password);
            ApplicationService.setPasswordEnabled(true);
            ApplicationService.saveConfig();
            
            JOptionPane.showMessageDialog(dialog, 
                "Password protection enabled!", 
                "Success", 
                JOptionPane.INFORMATION_MESSAGE);
            
            dialog.dispose();
        });
        
        cancelButton.addActionListener(e -> {
            // Cancel - uncheck the toggle
            toggleItem.setSelected(false);
            dialog.dispose();
        });
        
        // Enter key to submit
        confirmField.addActionListener(e -> okButton.doClick());
        passwordField.addActionListener(e -> confirmField.requestFocus());
        
        // Assemble dialog
        mainPanel.add(inputPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        dialog.add(mainPanel);
        dialog.setVisible(true);
    }
    
    /**
     * Shows a dialog for customizing UI theme colors with RGB sliders.
     */
    public static void showCustomizeDialog(ImageBrowserApp app) {
        JDialog dialog = new JDialog(app, "PeraPera - Customize Colors", true);
        dialog.setSize(500, 700);
        dialog.setResizable(true);
        dialog.setLocationRelativeTo(app);
        
        // Main panel with dark theme
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(ApplicationService.getBackgroundDark());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Scrollable content panel
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(ApplicationService.getBackgroundDark());
        
        // Color definitions with display names and keys
        Object[][] colorDefs = {
            {"Background (Dark)", ApplicationService.ThemeConfig.BACKGROUND_DARK, ApplicationService.ThemeConfig.DEFAULT_BACKGROUND_DARK, false},
            {"Background (Medium)", ApplicationService.ThemeConfig.BACKGROUND_MEDIUM, ApplicationService.ThemeConfig.DEFAULT_BACKGROUND_MEDIUM, false},
            {"Background (Light)", ApplicationService.ThemeConfig.BACKGROUND_LIGHT, ApplicationService.ThemeConfig.DEFAULT_BACKGROUND_LIGHT, false},
            {"Border Color", ApplicationService.ThemeConfig.BORDER_COLOR, ApplicationService.ThemeConfig.DEFAULT_BORDER_COLOR, false},
            {"Text Primary", ApplicationService.ThemeConfig.TEXT_PRIMARY, ApplicationService.ThemeConfig.DEFAULT_TEXT_PRIMARY, false},
            {"Text Secondary", ApplicationService.ThemeConfig.TEXT_SECONDARY, ApplicationService.ThemeConfig.DEFAULT_TEXT_SECONDARY, false},
            {"Accent Color", ApplicationService.ThemeConfig.ACCENT_COLOR, ApplicationService.ThemeConfig.DEFAULT_ACCENT_COLOR, false},
            {"Input Background", ApplicationService.ThemeConfig.INPUT_BACKGROUND, ApplicationService.ThemeConfig.DEFAULT_INPUT_BACKGROUND, false},
            {"Collection Auto Border", ApplicationService.ThemeConfig.COLLECTION_AUTO_BORDER, ApplicationService.ThemeConfig.DEFAULT_COLLECTION_AUTO_BORDER, false},
            {"Collection Manual Border", ApplicationService.ThemeConfig.COLLECTION_MANUAL_BORDER, ApplicationService.ThemeConfig.DEFAULT_COLLECTION_MANUAL_BORDER, false},
            {"Collection Auto Label", ApplicationService.ThemeConfig.COLLECTION_AUTO_LABEL, ApplicationService.ThemeConfig.DEFAULT_COLLECTION_AUTO_LABEL, false},
            {"Collection Manual Label", ApplicationService.ThemeConfig.COLLECTION_MANUAL_LABEL, ApplicationService.ThemeConfig.DEFAULT_COLLECTION_MANUAL_LABEL, false},
            {"Rating Unrated", ApplicationService.ThemeConfig.RATING_UNRATED, ApplicationService.ThemeConfig.DEFAULT_RATING_UNRATED, false},
            {"Rating Rated", ApplicationService.ThemeConfig.RATING_RATED, ApplicationService.ThemeConfig.DEFAULT_RATING_RATED, false},
            {"Progress Bar", ApplicationService.ThemeConfig.PROGRESS_BAR, ApplicationService.ThemeConfig.DEFAULT_PROGRESS_BAR, false},
            {"Placeholder Text", ApplicationService.ThemeConfig.PLACEHOLDER_TEXT, ApplicationService.ThemeConfig.DEFAULT_PLACEHOLDER_TEXT, false},
            {"Success Color", ApplicationService.ThemeConfig.SUCCESS_COLOR, ApplicationService.ThemeConfig.DEFAULT_SUCCESS_COLOR, false},
            {"Error Color", ApplicationService.ThemeConfig.ERROR_COLOR, ApplicationService.ThemeConfig.DEFAULT_ERROR_COLOR, false},
            {"Warning Color", ApplicationService.ThemeConfig.WARNING_COLOR, ApplicationService.ThemeConfig.DEFAULT_WARNING_COLOR, false},
            {"Overlay Color", ApplicationService.ThemeConfig.OVERLAY_COLOR, ApplicationService.ThemeConfig.DEFAULT_OVERLAY_COLOR, true},
            {"Highlight Color", ApplicationService.ThemeConfig.HIGHLIGHT_COLOR, ApplicationService.ThemeConfig.DEFAULT_HIGHLIGHT_COLOR, false},
        };
        
        // Store color editors for saving
        Map<String, ColorEditor> colorEditors = new LinkedHashMap<>();
        
        for (Object[] def : colorDefs) {
            String displayName = (String) def[0];
            String key = (String) def[1];
            int[] defaultRgb = (int[]) def[2];
            boolean hasAlpha = (boolean) def[3];
            
            ColorEditor editor = new ColorEditor(displayName, key, defaultRgb, hasAlpha);
            colorEditors.put(key, editor);
            contentPanel.add(editor);
            contentPanel.add(Box.createVerticalStrut(5));
        }
        
        // Create separate preview dialogs and store component references
        PreviewComponents browserComponents = new PreviewComponents();
        PreviewComponents collectionsComponents = new PreviewComponents();
        PreviewComponents viewerComponents = new PreviewComponents();
        PreviewComponents collectionBordersComponents = new PreviewComponents();
        PreviewComponents ratingsComponents = new PreviewComponents();
        PreviewComponents statusComponents = new PreviewComponents();
        PreviewComponents highlighterComponents = new PreviewComponents();
        
        JDialog browserPreviewDialog = createBrowserPreviewDialog(dialog, browserComponents);
        JDialog collectionsPreviewDialog = createCollectionsPreviewDialog(dialog, collectionsComponents);
        JDialog viewerPreviewDialog = createViewerPreviewDialog(dialog, viewerComponents);
        JDialog collectionBordersPreviewDialog = createCollectionBordersPreviewDialog(dialog, collectionBordersComponents);
        JDialog ratingsPreviewDialog = createRatingsPreviewDialog(dialog, ratingsComponents);
        JDialog statusPreviewDialog = createStatusPreviewDialog(dialog, statusComponents);
        JDialog highlighterPreviewDialog = createHighlighterPreviewDialog(dialog, highlighterComponents);
        
        // Position preview dialogs next to the main dialog
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                java.awt.Rectangle bounds = dialog.getBounds();
                int previewWidth = 260;
                int previewHeight = 150;
                
                // Column 1
                browserPreviewDialog.setBounds(bounds.x + bounds.width + 20, bounds.y, previewWidth, previewHeight);
                collectionsPreviewDialog.setBounds(bounds.x + bounds.width + 20, bounds.y + previewHeight + 15, previewWidth, previewHeight);
                viewerPreviewDialog.setBounds(bounds.x + bounds.width + 20, bounds.y + (previewHeight + 15) * 2, previewWidth, previewHeight);
                collectionBordersPreviewDialog.setBounds(bounds.x + bounds.width + 20, bounds.y + (previewHeight + 15) * 3, previewWidth, previewHeight);
                
                // Column 2
                ratingsPreviewDialog.setBounds(bounds.x + bounds.width + previewWidth + 35, bounds.y, previewWidth, previewHeight);
                statusPreviewDialog.setBounds(bounds.x + bounds.width + previewWidth + 35, bounds.y + previewHeight + 15, previewWidth, previewHeight);
                highlighterPreviewDialog.setBounds(bounds.x + bounds.width + previewWidth + 35, bounds.y + (previewHeight + 15) * 2, previewWidth, previewHeight);
                
                browserPreviewDialog.setVisible(true);
                collectionsPreviewDialog.setVisible(true);
                viewerPreviewDialog.setVisible(true);
                collectionBordersPreviewDialog.setVisible(true);
                ratingsPreviewDialog.setVisible(true);
                statusPreviewDialog.setVisible(true);
                highlighterPreviewDialog.setVisible(true);
                
                // Initialize preview dialogs with current theme colors from config
                refreshPreviewDialogs(browserPreviewDialog, collectionsPreviewDialog, viewerPreviewDialog,
                                     collectionBordersPreviewDialog, ratingsPreviewDialog, statusPreviewDialog,
                                     highlighterPreviewDialog,
                                     browserComponents, collectionsComponents, viewerComponents,
                                     collectionBordersComponents, ratingsComponents, statusComponents,
                                     highlighterComponents,
                                     colorEditors);
            }
            
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                browserPreviewDialog.dispose();
                collectionsPreviewDialog.dispose();
                viewerPreviewDialog.dispose();
                collectionBordersPreviewDialog.dispose();
                ratingsPreviewDialog.dispose();
                statusPreviewDialog.dispose();
                highlighterPreviewDialog.dispose();
            }
        });
        
        // Add change listeners to color editors to update previews in real-time
        for (ColorEditor editor : colorEditors.values()) {
            // Add listener directly to sliders for immediate feedback
            editor.rSlider.addChangeListener(e -> {
                refreshPreviewDialogs(browserPreviewDialog, collectionsPreviewDialog, viewerPreviewDialog,
                                     collectionBordersPreviewDialog, ratingsPreviewDialog, statusPreviewDialog,
                                     highlighterPreviewDialog,
                                     browserComponents, collectionsComponents, viewerComponents,
                                     collectionBordersComponents, ratingsComponents, statusComponents,
                                     highlighterComponents,
                                     colorEditors);
            });
            editor.gSlider.addChangeListener(e -> {
                refreshPreviewDialogs(browserPreviewDialog, collectionsPreviewDialog, viewerPreviewDialog,
                                     collectionBordersPreviewDialog, ratingsPreviewDialog, statusPreviewDialog,
                                     highlighterPreviewDialog,
                                     browserComponents, collectionsComponents, viewerComponents,
                                     collectionBordersComponents, ratingsComponents, statusComponents,
                                     highlighterComponents,
                                     colorEditors);
            });
            editor.bSlider.addChangeListener(e -> {
                refreshPreviewDialogs(browserPreviewDialog, collectionsPreviewDialog, viewerPreviewDialog,
                                     collectionBordersPreviewDialog, ratingsPreviewDialog, statusPreviewDialog,
                                     highlighterPreviewDialog,
                                     browserComponents, collectionsComponents, viewerComponents,
                                     collectionBordersComponents, ratingsComponents, statusComponents,
                                     highlighterComponents,
                                     colorEditors);
            });
            if (editor.aSlider != null) {
                editor.aSlider.addChangeListener(e -> {
                    refreshPreviewDialogs(browserPreviewDialog, collectionsPreviewDialog, viewerPreviewDialog,
                                         collectionBordersPreviewDialog, ratingsPreviewDialog, statusPreviewDialog,
                                         highlighterPreviewDialog,
                                         browserComponents, collectionsComponents, viewerComponents,
                                         collectionBordersComponents, ratingsComponents, statusComponents,
                                         highlighterComponents,
                                         colorEditors);
                });
            }
        }
        
        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBackground(ApplicationService.getBackgroundDark());
        scrollPane.getViewport().setBackground(ApplicationService.getBackgroundDark());
        scrollPane.setBorder(null);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        
        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        buttonPanel.setBackground(ApplicationService.getBackgroundDark());
        
        JButton saveButton = new JButton("Save & Apply");
        saveButton.setBackground(ApplicationService.getSuccessColor());
        saveButton.setForeground(ApplicationService.getTextPrimary());
        saveButton.setFocusPainted(false);
        saveButton.setFont(new Font("Arial", Font.PLAIN, 12));
        saveButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        JButton resetButton = new JButton("Reset to Defaults");
        resetButton.setBackground(ApplicationService.getWarningColor());
        resetButton.setForeground(ApplicationService.getTextPrimary());
        resetButton.setFocusPainted(false);
        resetButton.setFont(new Font("Arial", Font.PLAIN, 12));
        resetButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        JButton clearButton = new JButton("Clear Theme File");
        clearButton.setBackground(ApplicationService.getErrorColor());
        clearButton.setForeground(ApplicationService.getTextPrimary());
        clearButton.setFocusPainted(false);
        clearButton.setFont(new Font("Arial", Font.PLAIN, 12));
        clearButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        JButton cancelButton = new JButton("Cancel");
        cancelButton.setBackground(ApplicationService.getBackgroundMedium());
        cancelButton.setForeground(ApplicationService.getTextPrimary());
        cancelButton.setFocusPainted(false);
        cancelButton.setFont(new Font("Arial", Font.PLAIN, 12));
        cancelButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        saveButton.addActionListener(e -> {
            // Save all colors to config
            for (Map.Entry<String, ColorEditor> entry : colorEditors.entrySet()) {
                ApplicationService.setThemeColor(entry.getKey(), entry.getValue().getColor());
            }
            // Explicitly save to theme.json
            ApplicationService.saveThemeConfig();
            
            // Reload theme from file to ensure consistency
            ApplicationService.refreshTheme();
            
            String message = "Colors saved to:\n" + ApplicationService.getThemeFilePath() + "\n\nRestart the application to see all changes.";
            JOptionPane.showMessageDialog(dialog, 
                message, 
                "Colors Saved", JOptionPane.INFORMATION_MESSAGE);
            
            dialog.dispose();
        });
        
        resetButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(dialog,
                "Reset all colors to defaults?",
                "Confirm Reset",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                ApplicationService.resetThemeToDefaults();
                ApplicationService.saveThemeConfig();
                // Refresh editors with default values
                for (Map.Entry<String, ColorEditor> entry : colorEditors.entrySet()) {
                    entry.getValue().refreshFromConfig();
                }
                JOptionPane.showMessageDialog(dialog, 
                    "Colors reset to defaults!", 
                    "Reset Complete", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        
        clearButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(dialog,
                "Delete theme.json file? This will clear all custom colors and use defaults on next restart.",
                "Confirm Clear",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                ApplicationService.deleteThemeConfig();
                JOptionPane.showMessageDialog(dialog, 
                    "Theme file deleted. Restart the application to use default colors.", 
                    "Theme File Deleted", JOptionPane.INFORMATION_MESSAGE);
                dialog.dispose();
            }
        });
        
        cancelButton.addActionListener(e -> dialog.dispose());
        
        buttonPanel.add(saveButton);
        buttonPanel.add(resetButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(cancelButton);
        
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        dialog.add(mainPanel);
        dialog.setVisible(true);
    }
    
    /**
     * Helper class to store component references for preview windows.
     */
    private static class PreviewComponents {
        java.util.List<JLabel> labels = new java.util.ArrayList<>();
        java.util.List<JButton> buttons = new java.util.ArrayList<>();
        java.util.List<JPanel> panels = new java.util.ArrayList<>();
        JProgressBar progressBar = null;
        java.util.List<JPanel> statusPanels = new java.util.ArrayList<>(); // Panels with status color backgrounds
        JPanel collectionsMainPanel = null; // The main panel with thick accent border in collections preview
    }
    
    /**
     * Helper class for editing a single color with RGB sliders.
     */
    private static class ColorEditor extends JPanel {
        private final String key;
        private final boolean hasAlpha;
        private final JSlider rSlider, gSlider, bSlider, aSlider;
        private final JTextField rField, gField, bField, aField;
        private final JLabel previewLabel;
        
        public ColorEditor(String displayName, String key, int[] defaultRgb, boolean hasAlpha) {
            this.key = key;
            this.hasAlpha = hasAlpha;
            
            setLayout(new BorderLayout(5, 0));
            setBackground(ApplicationService.getBackgroundDark());
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(ApplicationService.getBorderColor()),
                    displayName,
                    javax.swing.border.TitledBorder.LEFT,
                    javax.swing.border.TitledBorder.TOP,
                    new Font("Arial", Font.BOLD, 11),
                    ApplicationService.getAccentColor()
                ),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
            ));
            
            // Get current color
            Color currentColor = ApplicationService.getThemeColor(key, defaultRgb);
            
            // Sliders panel
            JPanel slidersPanel = new JPanel(new GridLayout(hasAlpha ? 4 : 3, 1, 5, 5));
            slidersPanel.setBackground(ApplicationService.getBackgroundDark());
            
            rSlider = createSlider(0, 255, currentColor.getRed());
            gSlider = createSlider(0, 255, currentColor.getGreen());
            bSlider = createSlider(0, 255, currentColor.getBlue());
            aSlider = hasAlpha ? createSlider(0, 255, currentColor.getAlpha()) : null;
            
            // Text fields panel
            JPanel fieldsPanel = new JPanel(new GridLayout(hasAlpha ? 4 : 3, 3, 5, 5));
            fieldsPanel.setBackground(ApplicationService.getBackgroundDark());
            
            rField = createField(currentColor.getRed(), rSlider);
            gField = createField(currentColor.getGreen(), gSlider);
            bField = createField(currentColor.getBlue(), bSlider);
            aField = hasAlpha ? createField(currentColor.getAlpha(), aSlider) : null;
            
            // R row
            slidersPanel.add(createLabeledSlider("R:", rSlider, new Color(255, 100, 100)));
            fieldsPanel.add(createLabeledField("R:", rField, new Color(255, 100, 100)));
            
            // G row
            slidersPanel.add(createLabeledSlider("G:", gSlider, new Color(100, 255, 100)));
            fieldsPanel.add(createLabeledField("G:", gField, new Color(100, 255, 100)));
            
            // B row
            slidersPanel.add(createLabeledSlider("B:", bSlider, new Color(100, 100, 255)));
            fieldsPanel.add(createLabeledField("B:", bField, new Color(100, 100, 255)));
            
            // A row (if applicable)
            if (hasAlpha) {
                slidersPanel.add(createLabeledSlider("A:", aSlider, ApplicationService.getTextPrimary()));
                fieldsPanel.add(createLabeledField("A:", aField, ApplicationService.getTextPrimary()));
            }
            
            // Preview label
            previewLabel = new JLabel("  Sample  ");
            previewLabel.setOpaque(true);
            previewLabel.setBackground(currentColor);
            previewLabel.setForeground(getContrastColor(currentColor));
            previewLabel.setHorizontalAlignment(SwingConstants.CENTER);
            previewLabel.setFont(new Font("Arial", Font.BOLD, 11));
            
            // Update preview when sliders change
            javax.swing.event.ChangeListener sliderListener = e -> {
                updatePreview();
            };
            rSlider.addChangeListener(sliderListener);
            gSlider.addChangeListener(sliderListener);
            bSlider.addChangeListener(sliderListener);
            if (hasAlpha) aSlider.addChangeListener(sliderListener);
            
            add(slidersPanel, BorderLayout.CENTER);
            add(fieldsPanel, BorderLayout.EAST);
            add(previewLabel, BorderLayout.SOUTH);
        }
        
        private JSlider createSlider(int min, int max, int value) {
            JSlider slider = new JSlider(min, max, value);
            slider.setBackground(ApplicationService.getBackgroundDark());
            slider.setForeground(ApplicationService.getTextPrimary());
            slider.setPaintTicks(true);
            slider.setMajorTickSpacing(50);
            return slider;
        }
        
        private JTextField createField(int value, JSlider slider) {
            JTextField field = new JTextField(String.valueOf(value), 3);
            field.setBackground(ApplicationService.getInputBackground());
            field.setForeground(ApplicationService.getTextPrimary());
            field.setCaretColor(ApplicationService.getTextPrimary());
            field.setHorizontalAlignment(JTextField.CENTER);
            field.setFont(new Font("Arial", Font.PLAIN, 11));
            
            // Sync field to slider
            field.addActionListener(e -> {
                try {
                    int v = Integer.parseInt(field.getText().trim());
                    if (v >= 0 && v <= 255) {
                        slider.setValue(v);
                    } else {
                        field.setText(String.valueOf(slider.getValue()));
                    }
                } catch (NumberFormatException ex) {
                    field.setText(String.valueOf(slider.getValue()));
                }
            });
            
            // Sync slider to field
            slider.addChangeListener(e -> {
                field.setText(String.valueOf(slider.getValue()));
            });
            
            return field;
        }
        
        private JPanel createLabeledSlider(String label, JSlider slider, Color labelColor) {
            JPanel panel = new JPanel(new BorderLayout(5, 0));
            panel.setBackground(ApplicationService.getBackgroundDark());
            
            JLabel lbl = new JLabel(label);
            lbl.setForeground(labelColor);
            lbl.setFont(new Font("Arial", Font.BOLD, 11));
            lbl.setPreferredSize(new Dimension(20, 20));
            
            panel.add(lbl, BorderLayout.WEST);
            panel.add(slider, BorderLayout.CENTER);
            return panel;
        }
        
        private JPanel createLabeledField(String label, JTextField field, Color labelColor) {
            JPanel panel = new JPanel(new BorderLayout(2, 0));
            panel.setBackground(ApplicationService.getBackgroundDark());
            
            JLabel lbl = new JLabel(label);
            lbl.setForeground(labelColor);
            lbl.setFont(new Font("Arial", Font.BOLD, 10));
            
            panel.add(lbl, BorderLayout.WEST);
            panel.add(field, BorderLayout.CENTER);
            return panel;
        }
        
        private void updatePreview() {
            Color c = getColor();
            previewLabel.setBackground(c);
            previewLabel.setForeground(getContrastColor(c));
        }
        
        public Color getColor() {
            int r = rSlider.getValue();
            int g = gSlider.getValue();
            int b = bSlider.getValue();
            if (hasAlpha && aSlider != null) {
                int a = aSlider.getValue();
                return new Color(r, g, b, a);
            }
            return new Color(r, g, b);
        }
        
        public void refreshFromConfig() {
            Color c = ApplicationService.getThemeColor(key, new int[]{rSlider.getValue(), gSlider.getValue(), bSlider.getValue()});
            rSlider.setValue(c.getRed());
            gSlider.setValue(c.getGreen());
            bSlider.setValue(c.getBlue());
            if (hasAlpha && aSlider != null) {
                aSlider.setValue(c.getAlpha());
            }
            updatePreview();
        }
        
        private Color getContrastColor(Color bg) {
            // Calculate luminance and return black or white for contrast
            double luminance = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) / 255;
            return luminance > 0.5 ? new java.awt.Color(0, 0, 0) : ApplicationService.getTextPrimary();
        }
    }
    
    /**
     * Creates a separate dialog for the browser preview.
     */
    private static JDialog createBrowserPreviewDialog(JDialog parent, PreviewComponents components) {
        JDialog dialog = new JDialog(parent, "Browser Preview", false);
        dialog.setResizable(false);
        
        JPanel content = createBrowserPreview(components);
        content.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor(), 2));
        dialog.add(content);
        
        return dialog;
    }
    
    /**
     * Creates a separate dialog for the collections menu preview.
     */
    private static JDialog createCollectionsPreviewDialog(JDialog parent, PreviewComponents components) {
        JDialog dialog = new JDialog(parent, "Collections Preview", false);
        dialog.setResizable(false);
        
        JPanel content = createCollectionsPreview(components);
        content.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor(), 2));
        dialog.add(content);
        
        return dialog;
    }
    
    /**
     * Creates a separate dialog for the viewer controls preview.
     */
    private static JDialog createViewerPreviewDialog(JDialog parent, PreviewComponents components) {
        JDialog dialog = new JDialog(parent, "Viewer Controls Preview", false);
        dialog.setResizable(false);
        
        JPanel content = createViewerPreview(components);
        content.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor(), 2));
        dialog.add(content);
        
        return dialog;
    }
    
    /**
     * Creates a separate dialog for collection borders preview.
     */
    private static JDialog createCollectionBordersPreviewDialog(JDialog parent, PreviewComponents components) {
        JDialog dialog = new JDialog(parent, "Collection Borders Preview", false);
        dialog.setResizable(false);
        
        JPanel content = createCollectionBordersPreview(components);
        content.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor(), 2));
        dialog.add(content);
        
        return dialog;
    }
    
    /**
     * Creates a separate dialog for ratings preview.
     */
    private static JDialog createRatingsPreviewDialog(JDialog parent, PreviewComponents components) {
        JDialog dialog = new JDialog(parent, "Ratings Preview", false);
        dialog.setResizable(false);
        
        JPanel content = createRatingsPreview(components);
        content.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor(), 2));
        dialog.add(content);
        
        return dialog;
    }
    
    /**
     * Creates a separate dialog for status colors preview.
     */
    private static JDialog createStatusPreviewDialog(JDialog parent, PreviewComponents components) {
        JDialog dialog = new JDialog(parent, "Status Colors Preview", false);
        dialog.setResizable(false);
        
        JPanel content = createStatusPreview(components);
        content.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor(), 2));
        dialog.add(content);
        
        return dialog;
    }
    
    /**
     * Creates a separate dialog for highlighter preview.
     */
    private static JDialog createHighlighterPreviewDialog(JDialog parent, PreviewComponents components) {
        JDialog dialog = new JDialog(parent, "Highlighter Preview", false);
        dialog.setResizable(false);
        
        JPanel content = createHighlighterPreview(components);
        content.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor(), 2));
        dialog.add(content);
        
        return dialog;
    }
    
    /**
     * Refreshes all preview dialogs with current theme colors.
     */
    private static void refreshPreviewDialogs(JDialog browserDialog, JDialog collectionsDialog, JDialog viewerDialog,
                                             JDialog collectionBordersDialog, JDialog ratingsDialog, JDialog statusDialog,
                                             JDialog highlighterDialog,
                                             PreviewComponents browserComps, PreviewComponents collectionsComps, PreviewComponents viewerComps,
                                             PreviewComponents collectionBordersComps, PreviewComponents ratingsComps, PreviewComponents statusComps,
                                             PreviewComponents highlighterComps,
                                             Map<String, ColorEditor> colorEditors) {
        updateDialogColors(browserDialog, browserComps, colorEditors);
        updateDialogColors(collectionsDialog, collectionsComps, colorEditors);
        updateDialogColors(viewerDialog, viewerComps, colorEditors);
        updateDialogColors(collectionBordersDialog, collectionBordersComps, colorEditors);
        updateDialogColors(ratingsDialog, ratingsComps, colorEditors);
        updateDialogColors(statusDialog, statusComps, colorEditors);
        updateDialogColors(highlighterDialog, highlighterComps, colorEditors);
    }
    
    /**
     * Updates all components in a dialog with current theme colors using stored references.
     */
    private static void updateDialogColors(JDialog dialog, PreviewComponents components, Map<String, ColorEditor> colorEditors) {
        if (dialog.getContentPane() instanceof JPanel) {
            JPanel content = (JPanel) dialog.getContentPane();
            
            // Read colors directly from slider values (like sample preview does)
            java.awt.Color borderColor = colorEditors.get(ApplicationService.ThemeConfig.BORDER_COLOR).getColor();
            java.awt.Color accentColor = colorEditors.get(ApplicationService.ThemeConfig.ACCENT_COLOR).getColor();
            java.awt.Color bgMedium = colorEditors.get(ApplicationService.ThemeConfig.BACKGROUND_MEDIUM).getColor();
            java.awt.Color textPrimary = colorEditors.get(ApplicationService.ThemeConfig.TEXT_PRIMARY).getColor();
            java.awt.Color textSecondary = colorEditors.get(ApplicationService.ThemeConfig.TEXT_SECONDARY).getColor();
            java.awt.Color inputBg = colorEditors.get(ApplicationService.ThemeConfig.INPUT_BACKGROUND).getColor();
            java.awt.Color progressColor = colorEditors.get(ApplicationService.ThemeConfig.PROGRESS_BAR).getColor();
            java.awt.Color placeholderColor = colorEditors.get(ApplicationService.ThemeConfig.PLACEHOLDER_TEXT).getColor();
            java.awt.Color collectionAutoBorder = colorEditors.get(ApplicationService.ThemeConfig.COLLECTION_AUTO_BORDER).getColor();
            java.awt.Color collectionManualBorder = colorEditors.get(ApplicationService.ThemeConfig.COLLECTION_MANUAL_BORDER).getColor();
            java.awt.Color collectionAutoLabel = colorEditors.get(ApplicationService.ThemeConfig.COLLECTION_AUTO_LABEL).getColor();
            java.awt.Color collectionManualLabel = colorEditors.get(ApplicationService.ThemeConfig.COLLECTION_MANUAL_LABEL).getColor();
            java.awt.Color ratingUnrated = colorEditors.get(ApplicationService.ThemeConfig.RATING_UNRATED).getColor();
            java.awt.Color ratingRated = colorEditors.get(ApplicationService.ThemeConfig.RATING_RATED).getColor();
            java.awt.Color successColor = colorEditors.get(ApplicationService.ThemeConfig.SUCCESS_COLOR).getColor();
            java.awt.Color errorColor = colorEditors.get(ApplicationService.ThemeConfig.ERROR_COLOR).getColor();
            java.awt.Color warningColor = colorEditors.get(ApplicationService.ThemeConfig.WARNING_COLOR).getColor();
            java.awt.Color overlayColor = colorEditors.get(ApplicationService.ThemeConfig.OVERLAY_COLOR).getColor();
            java.awt.Color highlightColor = colorEditors.get(ApplicationService.ThemeConfig.HIGHLIGHT_COLOR).getColor();
            
            content.setBorder(BorderFactory.createLineBorder(borderColor, 2));
            
            // Update all panels
            for (JPanel panel : components.panels) {
                panel.setBackground(bgMedium);
                
                // Update titled border if present
                if (panel.getBorder() instanceof javax.swing.border.TitledBorder) {
                    javax.swing.border.TitledBorder titledBorder = (javax.swing.border.TitledBorder) panel.getBorder();
                    titledBorder.setTitleColor(accentColor);
                    if (titledBorder.getBorder() instanceof javax.swing.border.LineBorder) {
                        panel.setBorder(BorderFactory.createTitledBorder(
                            BorderFactory.createLineBorder(borderColor),
                            titledBorder.getTitle(),
                            titledBorder.getTitleJustification(),
                            titledBorder.getTitlePosition(),
                            titledBorder.getTitleFont(),
                            accentColor
                        ));
                    }
                }
                
                // Update collection borders
                if (panel.getBorder() instanceof javax.swing.border.LineBorder) {
                    javax.swing.border.LineBorder lb = (javax.swing.border.LineBorder) panel.getBorder();
                    // Check if this is a collection border panel by looking at its child labels
                    for (Component c : panel.getComponents()) {
                        if (c instanceof JLabel) {
                            JLabel label = (JLabel) c;
                            String text = label.getText();
                            if (text.equals("Auto Collection")) {
                                panel.setBorder(BorderFactory.createLineBorder(collectionAutoBorder, 2));
                            } else if (text.equals("Manual Collection")) {
                                panel.setBorder(BorderFactory.createLineBorder(collectionManualBorder, 2));
                            }
                            break;
                        }
                    }
                    // Check if this is a highlighted thumbnail (3-pixel border)
                    if (lb.getThickness() == 3) {
                        panel.setBorder(BorderFactory.createLineBorder(highlightColor, 3));
                    }
                }
            }
            
            // Update all labels
            for (JLabel label : components.labels) {
                String text = label.getText();
                if (text.equals("Browser") || text.equals("Collections Menu") || text.equals("Viewer Controls") || text.equals("Collection Borders") || text.equals("Ratings") || text.equals("Status Colors") || text.equals("Navigation Highlighter")) {
                    label.setForeground(accentColor);
                } else if (text.contains("IMG")) {
                    label.setForeground(placeholderColor);
                } else if (text.contains("Comic")) {
                    label.setForeground(textSecondary);
                } else if (text.contains("My Comics")) {
                    label.setForeground(textPrimary);
                } else if (text.contains("Action Comics") || text.contains("85/100")) {
                    label.setForeground(textPrimary);
                } else if (text.equals(">>>")) {
                    label.setForeground(accentColor);
                } else if (text.contains("Loading thumbnails")) {
                    label.setForeground(textSecondary);
                } else if (text.equals("Auto Collection")) {
                    label.setForeground(collectionAutoLabel);
                } else if (text.equals("Manual Collection")) {
                    label.setForeground(collectionManualLabel);
                } else if (text.equals("Unrated")) {
                    label.setForeground(ratingUnrated);
                } else if (text.equals("Rated ★")) {
                    label.setForeground(ratingRated);
                } else if (text.equals("Success") || text.equals("Error") || text.equals("Warning") || text.equals("Overlay")) {
                    label.setForeground(textPrimary);
                } else {
                    label.setForeground(textPrimary);
                }
            }
            
            // Update all buttons
            for (JButton btn : components.buttons) {
                String text = btn.getText();
                if (text.equals("Next ▶") || text.equals("▶")) {
                    btn.setBackground(accentColor);
                } else {
                    btn.setBackground(inputBg);
                }
                btn.setForeground(textPrimary);
                btn.setBorder(BorderFactory.createLineBorder(borderColor, 1));
            }
            
            // Update progress bar if present
            if (components.progressBar != null) {
                components.progressBar.setBackground(inputBg);
                components.progressBar.setForeground(progressColor);
                components.progressBar.setBorder(BorderFactory.createLineBorder(borderColor, 1));
            }
            
            // Update status color panels
            java.awt.Color[] statusColors = {successColor, errorColor, warningColor, overlayColor};
            for (int i = 0; i < components.statusPanels.size() && i < statusColors.length; i++) {
                components.statusPanels.get(i).setBackground(statusColors[i]);
            }
            
            // Update collections main panel border
            if (components.collectionsMainPanel != null) {
                components.collectionsMainPanel.setBorder(BorderFactory.createLineBorder(accentColor, 2));
            }
            
            content.repaint();
            content.revalidate();
            dialog.repaint();
            dialog.revalidate();
        }
    }
    
    /**
     * Creates a simplified browser preview showing header and thumbnail grid.
     */
    private static JPanel createBrowserPreview(PreviewComponents components) {
        JPanel preview = new JPanel(new BorderLayout());
        preview.setPreferredSize(new Dimension(280, 200));
        preview.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ApplicationService.getBorderColor()),
            "Browser",
            javax.swing.border.TitledBorder.LEFT,
            javax.swing.border.TitledBorder.TOP,
            new Font("Arial", Font.BOLD, 10),
            ApplicationService.getAccentColor()
        ));
        components.panels.add(preview);
        
        // Header panel
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ApplicationService.getBackgroundMedium());
        header.setPreferredSize(new Dimension(0, 35));
        header.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        components.panels.add(header);
        
        JLabel title = new JLabel("My Comics");
        title.setForeground(ApplicationService.getTextPrimary());
        title.setFont(new Font("Arial", Font.BOLD, 12));
        components.labels.add(title);
        header.add(title, BorderLayout.WEST);
        
        // Filter buttons
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        filters.setBackground(ApplicationService.getBackgroundMedium());
        components.panels.add(filters);
        String[] filterNames = {"All", "Coll", "Series"};
        for (String name : filterNames) {
            JButton btn = new JButton(name);
            btn.setBackground(ApplicationService.getInputBackground());
            btn.setForeground(ApplicationService.getTextSecondary());
            btn.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor(), 1));
            btn.setFont(new Font("Arial", Font.PLAIN, 9));
            btn.setFocusPainted(false);
            btn.setPreferredSize(new Dimension(35, 22));
            components.buttons.add(btn);
            filters.add(btn);
        }
        header.add(filters, BorderLayout.EAST);
        
        preview.add(header, BorderLayout.NORTH);
        
        // Thumbnail grid
        JPanel grid = new JPanel(new GridLayout(2, 3, 5, 5));
        grid.setBackground(ApplicationService.getBackgroundDark());
        grid.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        components.panels.add(grid);
        
        for (int i = 0; i < 6; i++) {
            JPanel thumb = new JPanel(new BorderLayout());
            thumb.setBackground(ApplicationService.getBackgroundMedium());
            thumb.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor(), 1));
            components.panels.add(thumb);
            
            JLabel placeholder = new JLabel("IMG");
            placeholder.setForeground(ApplicationService.getPlaceholderText());
            placeholder.setHorizontalAlignment(SwingConstants.CENTER);
            placeholder.setFont(new Font("Arial", Font.PLAIN, 10));
            components.labels.add(placeholder);
            
            JLabel label = new JLabel("Comic " + (i + 1));
            label.setForeground(ApplicationService.getTextSecondary());
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setFont(new Font("Arial", Font.PLAIN, 9));
            components.labels.add(label);
            
            thumb.add(placeholder, BorderLayout.CENTER);
            thumb.add(label, BorderLayout.SOUTH);
            grid.add(thumb);
        }
        
        preview.add(grid, BorderLayout.CENTER);
        
        // Progress bar for loading thumbnails
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setValue(25);
        progressBar.setString("Loading thumbnails: 25/100");
        progressBar.setStringPainted(true);
        components.progressBar = progressBar;
        preview.add(progressBar, BorderLayout.SOUTH);
        
        return preview;
    }
    
    /**
     * Creates a simplified collections menu preview.
     */
    private static JPanel createCollectionsPreview(PreviewComponents components) {
        JPanel preview = new JPanel(new BorderLayout());
        preview.setPreferredSize(new Dimension(280, 180));
        preview.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ApplicationService.getBorderColor()),
            "Collections Menu",
            javax.swing.border.TitledBorder.LEFT,
            javax.swing.border.TitledBorder.TOP,
            new Font("Arial", Font.BOLD, 10),
            ApplicationService.getAccentColor()
        ));
        components.panels.add(preview);
        
        // Main panel
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(ApplicationService.getBackgroundMedium());
        main.setBorder(BorderFactory.createLineBorder(ApplicationService.getAccentColor(), 2));
        components.collectionsMainPanel = main; // Store reference for real-time updates
        components.panels.add(main);
        
        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ApplicationService.getBackgroundMedium());
        header.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        components.panels.add(header);
        
        JLabel title = new JLabel("[Series] Action Comics");
        title.setForeground(ApplicationService.getAccentColor());
        title.setFont(new Font("Arial", Font.BOLD, 11));
        components.labels.add(title);
        header.add(title, BorderLayout.NORTH);
        
        JLabel info = new JLabel("85/100 | Tags: action, superhero");
        info.setForeground(ApplicationService.getTextPrimary());
        info.setFont(new Font("Arial", Font.ITALIC, 9));
        components.labels.add(info);
        header.add(info, BorderLayout.CENTER);
        
        // Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 2));
        buttons.setBackground(ApplicationService.getBackgroundMedium());
        components.panels.add(buttons);
        
        JButton markBtn = new JButton("Mark All");
        markBtn.setBackground(ApplicationService.getInputBackground());
        markBtn.setForeground(ApplicationService.getTextPrimary());
        markBtn.setBorder(BorderFactory.createLineBorder(ApplicationService.getAccentColor(), 1));
        markBtn.setFont(new Font("Arial", Font.PLAIN, 9));
        markBtn.setFocusPainted(false);
        components.buttons.add(markBtn);
        
        JButton clearBtn = new JButton("Clear");
        clearBtn.setBackground(ApplicationService.getInputBackground());
        clearBtn.setForeground(ApplicationService.getTextPrimary());
        clearBtn.setBorder(BorderFactory.createLineBorder(ApplicationService.getAccentColor(), 1));
        clearBtn.setFont(new Font("Arial", Font.PLAIN, 9));
        clearBtn.setFocusPainted(false);
        components.buttons.add(clearBtn);
        
        buttons.add(markBtn);
        buttons.add(clearBtn);
        header.add(buttons, BorderLayout.SOUTH);
        
        main.add(header, BorderLayout.NORTH);
        
        // Comic list
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(ApplicationService.getBackgroundMedium());
        list.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        components.panels.add(list);
        
        String[] comics = {"Chapter 1", "Chapter 2", "Chapter 3"};
        for (String comic : comics) {
            JPanel row = new JPanel(new BorderLayout());
            row.setBackground(ApplicationService.getBackgroundMedium());
            row.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
            components.panels.add(row);
            
            JLabel status = new JLabel(">>>");
            status.setForeground(ApplicationService.getAccentColor());
            status.setFont(new Font("Arial", Font.BOLD, 9));
            status.setPreferredSize(new Dimension(30, 15));
            components.labels.add(status);
            
            JLabel name = new JLabel(comic);
            name.setForeground(ApplicationService.getTextPrimary());
            name.setFont(new Font("Arial", Font.PLAIN, 10));
            components.labels.add(name);
            
            row.add(status, BorderLayout.WEST);
            row.add(name, BorderLayout.CENTER);
            list.add(row);
        }
        
        main.add(list, BorderLayout.CENTER);
        
        preview.add(main, BorderLayout.CENTER);
        
        return preview;
    }
    
    /**
     * Creates a simplified viewer control panel preview.
     */
    private static JPanel createViewerPreview(PreviewComponents components) {
        JPanel preview = new JPanel(new BorderLayout());
        preview.setPreferredSize(new Dimension(280, 80));
        preview.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ApplicationService.getBorderColor()),
            "Viewer Controls",
            javax.swing.border.TitledBorder.LEFT,
            javax.swing.border.TitledBorder.TOP,
            new Font("Arial", Font.BOLD, 10),
            ApplicationService.getAccentColor()
        ));
        components.panels.add(preview);
        
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ApplicationService.getBackgroundMedium());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        components.panels.add(panel);
        
        // Navigation buttons
        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        navPanel.setBackground(ApplicationService.getBackgroundMedium());
        components.panels.add(navPanel);
        
        JButton prevBtn = new JButton("◀");
        prevBtn.setFont(new Font("Arial", Font.BOLD, 14));
        prevBtn.setPreferredSize(new Dimension(50, 30));
        components.buttons.add(prevBtn);
        navPanel.add(prevBtn);
        
        JButton nextBtn = new JButton("▶");
        nextBtn.setFont(new Font("Arial", Font.BOLD, 14));
        nextBtn.setPreferredSize(new Dimension(50, 30));
        components.buttons.add(nextBtn);
        navPanel.add(nextBtn);
        
        panel.add(navPanel, BorderLayout.CENTER);
        
        preview.add(panel, BorderLayout.CENTER);
        
        return preview;
    }
    
    /**
     * Creates a collection borders preview showing auto/manual collection borders and labels.
     */
    private static JPanel createCollectionBordersPreview(PreviewComponents components) {
        JPanel preview = new JPanel(new BorderLayout());
        preview.setPreferredSize(new Dimension(280, 180));
        preview.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ApplicationService.getBorderColor()),
            "Collection Borders",
            javax.swing.border.TitledBorder.LEFT,
            javax.swing.border.TitledBorder.TOP,
            new Font("Arial", Font.BOLD, 10),
            ApplicationService.getAccentColor()
        ));
        components.panels.add(preview);
        
        JPanel panel = new JPanel(new GridLayout(2, 1, 10, 10));
        panel.setBackground(ApplicationService.getBackgroundMedium());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        components.panels.add(panel);
        
        // Auto collection
        JPanel autoPanel = new JPanel(new BorderLayout());
        autoPanel.setBackground(ApplicationService.getBackgroundMedium());
        autoPanel.setBorder(BorderFactory.createLineBorder(ApplicationService.getThemeColor(ApplicationService.ThemeConfig.COLLECTION_AUTO_BORDER, ApplicationService.ThemeConfig.DEFAULT_COLLECTION_AUTO_BORDER), 2));
        components.panels.add(autoPanel);
        
        JLabel autoLabel = new JLabel("Auto Collection");
        autoLabel.setForeground(ApplicationService.getThemeColor(ApplicationService.ThemeConfig.COLLECTION_AUTO_LABEL, ApplicationService.ThemeConfig.DEFAULT_COLLECTION_AUTO_LABEL));
        autoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        autoLabel.setFont(new Font("Arial", Font.BOLD, 11));
        components.labels.add(autoLabel);
        autoPanel.add(autoLabel, BorderLayout.CENTER);
        
        // Manual collection
        JPanel manualPanel = new JPanel(new BorderLayout());
        manualPanel.setBackground(ApplicationService.getBackgroundMedium());
        manualPanel.setBorder(BorderFactory.createLineBorder(ApplicationService.getThemeColor(ApplicationService.ThemeConfig.COLLECTION_MANUAL_BORDER, ApplicationService.ThemeConfig.DEFAULT_COLLECTION_MANUAL_BORDER), 2));
        components.panels.add(manualPanel);
        
        JLabel manualLabel = new JLabel("Manual Collection");
        manualLabel.setForeground(ApplicationService.getThemeColor(ApplicationService.ThemeConfig.COLLECTION_MANUAL_LABEL, ApplicationService.ThemeConfig.DEFAULT_COLLECTION_MANUAL_LABEL));
        manualLabel.setHorizontalAlignment(SwingConstants.CENTER);
        manualLabel.setFont(new Font("Arial", Font.BOLD, 11));
        components.labels.add(manualLabel);
        manualPanel.add(manualLabel, BorderLayout.CENTER);
        
        panel.add(autoPanel);
        panel.add(manualPanel);
        preview.add(panel, BorderLayout.CENTER);
        
        return preview;
    }
    
    /**
     * Creates a ratings preview showing rated and unrated colors.
     */
    private static JPanel createRatingsPreview(PreviewComponents components) {
        JPanel preview = new JPanel(new BorderLayout());
        preview.setPreferredSize(new Dimension(280, 150));
        preview.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ApplicationService.getBorderColor()),
            "Ratings",
            javax.swing.border.TitledBorder.LEFT,
            javax.swing.border.TitledBorder.TOP,
            new Font("Arial", Font.BOLD, 10),
            ApplicationService.getAccentColor()
        ));
        components.panels.add(preview);
        
        JPanel panel = new JPanel(new GridLayout(1, 2, 10, 10));
        panel.setBackground(ApplicationService.getBackgroundMedium());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        components.panels.add(panel);
        
        // Unrated
        JPanel unratedPanel = new JPanel(new BorderLayout());
        unratedPanel.setBackground(ApplicationService.getBackgroundMedium());
        components.panels.add(unratedPanel);
        
        JLabel unratedLabel = new JLabel("Unrated");
        unratedLabel.setForeground(ApplicationService.getThemeColor(ApplicationService.ThemeConfig.RATING_UNRATED, ApplicationService.ThemeConfig.DEFAULT_RATING_UNRATED));
        unratedLabel.setHorizontalAlignment(SwingConstants.CENTER);
        unratedLabel.setFont(new Font("Arial", Font.BOLD, 11));
        components.labels.add(unratedLabel);
        unratedPanel.add(unratedLabel, BorderLayout.CENTER);
        
        // Rated
        JPanel ratedPanel = new JPanel(new BorderLayout());
        ratedPanel.setBackground(ApplicationService.getBackgroundMedium());
        components.panels.add(ratedPanel);
        
        JLabel ratedLabel = new JLabel("Rated ★");
        ratedLabel.setForeground(ApplicationService.getThemeColor(ApplicationService.ThemeConfig.RATING_RATED, ApplicationService.ThemeConfig.DEFAULT_RATING_RATED));
        ratedLabel.setHorizontalAlignment(SwingConstants.CENTER);
        ratedLabel.setFont(new Font("Arial", Font.BOLD, 11));
        components.labels.add(ratedLabel);
        ratedPanel.add(ratedLabel, BorderLayout.CENTER);
        
        panel.add(unratedPanel);
        panel.add(ratedPanel);
        preview.add(panel, BorderLayout.CENTER);
        
        return preview;
    }
    
    /**
     * Creates a status colors preview showing success, error, warning, and overlay colors.
     */
    private static JPanel createStatusPreview(PreviewComponents components) {
        JPanel preview = new JPanel(new BorderLayout());
        preview.setPreferredSize(new Dimension(280, 180));
        preview.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ApplicationService.getBorderColor()),
            "Status Colors",
            javax.swing.border.TitledBorder.LEFT,
            javax.swing.border.TitledBorder.TOP,
            new Font("Arial", Font.BOLD, 10),
            ApplicationService.getAccentColor()
        ));
        components.panels.add(preview);
        
        JPanel panel = new JPanel(new GridLayout(2, 2, 10, 10));
        panel.setBackground(ApplicationService.getBackgroundMedium());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        components.panels.add(panel);
        
        String[] statusNames = {"Success", "Error", "Warning", "Overlay"};
        String[] statusKeys = {
            ApplicationService.ThemeConfig.SUCCESS_COLOR,
            ApplicationService.ThemeConfig.ERROR_COLOR,
            ApplicationService.ThemeConfig.WARNING_COLOR,
            ApplicationService.ThemeConfig.OVERLAY_COLOR
        };
        int[][] defaultColors = {
            ApplicationService.ThemeConfig.DEFAULT_SUCCESS_COLOR,
            ApplicationService.ThemeConfig.DEFAULT_ERROR_COLOR,
            ApplicationService.ThemeConfig.DEFAULT_WARNING_COLOR,
            ApplicationService.ThemeConfig.DEFAULT_OVERLAY_COLOR
        };
        
        for (int i = 0; i < 4; i++) {
            JPanel statusPanel = new JPanel(new BorderLayout());
            statusPanel.setBackground(ApplicationService.getThemeColor(statusKeys[i], defaultColors[i]));
            components.panels.add(statusPanel);
            components.statusPanels.add(statusPanel); // Track status panels separately
            
            JLabel label = new JLabel(statusNames[i]);
            label.setForeground(ApplicationService.getTextPrimary());
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setFont(new Font("Arial", Font.BOLD, 11));
            components.labels.add(label);
            statusPanel.add(label, BorderLayout.CENTER);
            panel.add(statusPanel);
        }
        
        preview.add(panel, BorderLayout.CENTER);
        
        return preview;
    }
    
    /**
     * Creates a highlighter preview showing navigation highlight border.
     */
    private static JPanel createHighlighterPreview(PreviewComponents components) {
        JPanel preview = new JPanel(new BorderLayout());
        preview.setPreferredSize(new Dimension(260, 160));
        preview.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ApplicationService.getBorderColor()),
            "Navigation Highlighter",
            javax.swing.border.TitledBorder.LEFT,
            javax.swing.border.TitledBorder.TOP,
            new Font("Arial", Font.BOLD, 10),
            ApplicationService.getAccentColor()
        ));
        components.panels.add(preview);
        
        JPanel panel = new JPanel(new GridLayout(2, 2, 10, 10));
        panel.setBackground(ApplicationService.getBackgroundMedium());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        components.panels.add(panel);
        
        // Normal thumbnail 1
        JPanel normalPanel1 = new JPanel(new BorderLayout());
        normalPanel1.setBackground(ApplicationService.getBackgroundMedium());
        normalPanel1.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor(), 1));
        components.panels.add(normalPanel1);
        
        JLabel normalLabel1 = new JLabel("IMG_001");
        normalLabel1.setForeground(ApplicationService.getPlaceholderText());
        normalLabel1.setHorizontalAlignment(SwingConstants.CENTER);
        normalLabel1.setFont(new Font("Arial", Font.PLAIN, 10));
        components.labels.add(normalLabel1);
        normalPanel1.add(normalLabel1, BorderLayout.CENTER);
        
        // Highlighted thumbnail
        JPanel highlightedPanel = new JPanel(new BorderLayout());
        highlightedPanel.setBackground(ApplicationService.getBackgroundMedium());
        highlightedPanel.setBorder(BorderFactory.createLineBorder(ApplicationService.getThemeColor(ApplicationService.ThemeConfig.HIGHLIGHT_COLOR, ApplicationService.ThemeConfig.DEFAULT_HIGHLIGHT_COLOR), 3));
        components.panels.add(highlightedPanel);
        
        JLabel highlightedLabel = new JLabel("IMG_002");
        highlightedLabel.setForeground(ApplicationService.getPlaceholderText());
        highlightedLabel.setHorizontalAlignment(SwingConstants.CENTER);
        highlightedLabel.setFont(new Font("Arial", Font.PLAIN, 10));
        components.labels.add(highlightedLabel);
        highlightedPanel.add(highlightedLabel, BorderLayout.CENTER);
        
        // Normal thumbnail 2
        JPanel normalPanel2 = new JPanel(new BorderLayout());
        normalPanel2.setBackground(ApplicationService.getBackgroundMedium());
        normalPanel2.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor(), 1));
        components.panels.add(normalPanel2);
        
        JLabel normalLabel2 = new JLabel("IMG_003");
        normalLabel2.setForeground(ApplicationService.getPlaceholderText());
        normalLabel2.setHorizontalAlignment(SwingConstants.CENTER);
        normalLabel2.setFont(new Font("Arial", Font.PLAIN, 10));
        components.labels.add(normalLabel2);
        normalPanel2.add(normalLabel2, BorderLayout.CENTER);
        
        // Normal thumbnail 3
        JPanel normalPanel3 = new JPanel(new BorderLayout());
        normalPanel3.setBackground(ApplicationService.getBackgroundMedium());
        normalPanel3.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor(), 1));
        components.panels.add(normalPanel3);
        
        JLabel normalLabel3 = new JLabel("IMG_004");
        normalLabel3.setForeground(ApplicationService.getPlaceholderText());
        normalLabel3.setHorizontalAlignment(SwingConstants.CENTER);
        normalLabel3.setFont(new Font("Arial", Font.PLAIN, 10));
        components.labels.add(normalLabel3);
        normalPanel3.add(normalLabel3, BorderLayout.CENTER);
        
        panel.add(normalPanel1);
        panel.add(highlightedPanel);
        panel.add(normalPanel2);
        panel.add(normalPanel3);
        preview.add(panel, BorderLayout.CENTER);
        
        return preview;
    }
    
    /**
     * Alphanumeric comparator that implements natural sort order.
     * Splits strings into numeric and non-numeric chunks for comparison.
     * Example: "Chapter 2" comes before "Chapter 10"
     */
    private static class AlphanumericComparator implements Comparator<String> {
        @Override
        public int compare(String s1, String s2) {
            int i1 = 0, i2 = 0;
            int n1 = s1.length(), n2 = s2.length();
            
            while (i1 < n1 && i2 < n2) {
                char c1 = s1.charAt(i1);
                char c2 = s2.charAt(i2);
                
                // If both are digits, compare as numbers
                if (Character.isDigit(c1) && Character.isDigit(c2)) {
                    // Extract full number from s1
                    int num1 = 0;
                    while (i1 < n1 && Character.isDigit(s1.charAt(i1))) {
                        num1 = num1 * 10 + (s1.charAt(i1) - '0');
                        i1++;
                    }
                    
                    // Extract full number from s2
                    int num2 = 0;
                    while (i2 < n2 && Character.isDigit(s2.charAt(i2))) {
                        num2 = num2 * 10 + (s2.charAt(i2) - '0');
                        i2++;
                    }
                    
                    if (num1 != num2) {
                        return Integer.compare(num1, num2);
                    }
                } else {
                    // Compare as characters (case-insensitive for letters)
                    if (Character.toLowerCase(c1) != Character.toLowerCase(c2)) {
                        return Character.compare(Character.toLowerCase(c1), Character.toLowerCase(c2));
                    }
                    i1++;
                    i2++;
                }
            }
            
            // If one string is a prefix of the other, shorter comes first
            return Integer.compare(n1 - i1, n2 - i2);
        }
    }
}
