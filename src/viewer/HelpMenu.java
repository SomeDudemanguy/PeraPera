package viewer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Help menu component that displays application help documentation.
 */
public class HelpMenu {

    /**
     * Shows the help dialog with application documentation.
     */
    public static void showHelpDialog(Component parent) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "PeraPera - Help", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(1000, 600);
        dialog.setLocationRelativeTo(parent);
        dialog.setResizable(true);

        // Main panel with dark theme
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(ApplicationService.getBackgroundDark());
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Title
        JLabel titleLabel = new JLabel("PeraPera Help Guide");
        titleLabel.setForeground(ApplicationService.getAccentColor());
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        titleLabel.setBorder(new EmptyBorder(0, 0, 15, 0));

        // Help text area
        JTextArea helpText = new JTextArea(getHelpText());
        helpText.setBackground(ApplicationService.getInputBackground());
        helpText.setForeground(ApplicationService.getTextPrimary());
        helpText.setFont(new Font("Consolas", Font.PLAIN, 12));
        helpText.setEditable(false);
        helpText.setLineWrap(true);
        helpText.setWrapStyleWord(true);
        helpText.setCaretPosition(0);

        // Scroll pane
        JScrollPane scrollPane = new JScrollPane(helpText);
        scrollPane.setBackground(ApplicationService.getInputBackground());
        scrollPane.getViewport().setBackground(ApplicationService.getInputBackground());
        scrollPane.setBorder(BorderFactory.createLineBorder(ApplicationService.getBorderColor()));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        // Close button
        JButton closeButton = new JButton("Close");
        closeButton.setBackground(ApplicationService.getInputBackground());
        closeButton.setForeground(ApplicationService.getTextPrimary());
        closeButton.setFocusPainted(false);
        closeButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ApplicationService.getBorderColor()),
            new EmptyBorder(8, 25, 8, 25)
        ));
        closeButton.setFont(new Font("Arial", Font.PLAIN, 12));
        closeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeButton.addActionListener(e -> dialog.dispose());

        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(ApplicationService.getBackgroundDark());
        buttonPanel.setBorder(new EmptyBorder(15, 0, 0, 0));
        buttonPanel.add(closeButton);

        // Assemble
        mainPanel.add(titleLabel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.add(mainPanel);
        dialog.setVisible(true);
    }

    /**
     * Returns the help text content.
     */
    private static String getHelpText() {
        return """
0. (NUCLEAR FIX) If anything feels extremely broken or weird, close the program and delete the UserData folder, this will delete all your custom collections, ratings, tags and thumbnail cache(which will have to be re-generated) but should fix most problems.

 1. The Golden Rules of Library Structure=================================

To keep the app running smoothly, your files must follow a strict hierarchy. If you go deeper than these levels, the library will break:

    Standalone Comic: Main Directory → Comic Folder (contains only image files).

    Series/Collection: Main Directory → Series Folder → Chapter Folders (chapters contain image files). No further sub-folders allowed.

    Page Sorting: Pages are sorted alphabetically. To ensure they appear in order, use leading zeros (e.g., 01.jpg, 02.jpg instead of 1.jpg, 2.jpg, otherwise 10.jpg will appear before 2.jpg(Within chapter folders)).

Example:
The app scans the Main Directory and decides on the fly: if a folder contains images, it's a Comic; if it contains other folders, it's a Series.

 Correct Library Architecture

Main Directory (Selected in App)
├── Oneshot/                <-- [COMIC] Contains only images
│   ├── 001.jpg
│   ├── 002.jpg
│   └── cover.jpg
│
├── Doujin/                <-- [COMIC] Contains only images
│   └── 01.webp
│
├── Series/            <-- [SERIES] Contains sub-folders (Chapters)
│   ├── cover.jpg                   <-- (turns this image into a 1-page comic, either ignore or delete the image file)
│   ├── Chapter_01/                 <-- [CHAPTER] Contains images
│   │   └── 001.jpg
│   ├── Chapter_02/                 <-- [CHAPTER] Contains images
│   │   └── 001.jpg
│   └── Volume_01/                  <-- [CHAPTER] Contains images
│       └── 001.jpg
│
└── Series 2/              <-- [SERIES] Contains sub-folders
    ├── chapter1/             <-- [CHAPTER] Contains images
    └── chapter2/             <-- [CHAPTER] Contains images
etc

 2. Keyboard Controls & Navigation========================================

    Library Browser: Use Arrow Keys to navigate, Enter to select. In the Collection menu, Left/Right arrows will highlight the "Continue Reading" button. 

    Comic/Manga Mode: Left/Right to turn pages(click on page or use arrowKeys). Page Up/Down to control zoom. Hold Right Shift to "Keep Zoom" (for works that have miniscule text). Press Enter/right click to open the control bar in Title view.

    Webtoon Mode: Use Up/Down arrows for smooth scrolling, pageUp and pageDown for zoom.

 3. Advanced Search & Tag Syntax==========================================

    The tag search bar supports tag logic and rating filters:

    Operators: Use , for AND, ~ for OR, and - for NOT.

    Parentheses: Group logic like (action, adventure) ~ comedy. (shows results for (action and adventure) or comedy

    Ratings: Filter by quality using r>80, r<50, r=100, or r=0 / r? for unrated.

    Series Search: Type series:name to find everything within a specific collection/series.

    Flexibility: Tags aren't just for genres! Use them for artists, bookmarks or reading status.

 4. Understanding Tabs & Collections======================================

    Browser Tabs: Use the tabs next to the search bar to filter your view: Standalone (works that are not in series or collections), Collection (User created), Series(Auto generated from folder in main directory), or Chapters (works inside collections and series).

    Ghost Comics: If a Series folder contains an image (like cover.jpg), the app creates a "Ghost Comic." This acts as a 1-page comic. Move the image file away into a chapter or other directory to remove this.

    collection/series sorting: Toggle between Natural Sort (alphanumeric) and A-Z Sort depending on your file naming habits.

 5. Downloads & External Tools============================================

    Recommendation: Use Gallery-DL or MangaDex-DL to grab your media.

 6. Workspaces & UserData=================================================

    Your settings, tags, and library data are stored in userData/workspaces. Each main directory you add gets its own isolated subfolder here.

 7. Visual Customization==================================================

    Thumbnails: The app automatically picks the first page as a thumbnail. To change it, right-click any comic/collection/series in the browser and select Change Thumbnail.

    If the focus ever feels "stuck" (like arrow keys arent registering), hit Escape to reset the focus to the main library grid.

    You can also change the .exe icon (The one shown on the taskbar and top left of the programs window) by replacing the Logo.jpg in the Insignia/ folder. Just make sure the name and .jpg are the same (idk about resolution)

""";
    }
}
