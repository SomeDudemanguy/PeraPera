package viewer;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * Unified service class for managing UI navigation and keyboard interactions.
 * Delegates all data/selection logic to SelectionService and PaginationService.
 * Handles keyboard input routing and UI-only concerns.
 */
public class NavigationService {

    private final ImageBrowserApp app;
    private final JScrollPane scrollPane;
    private final JPanel thumbnailsPanel;
    private final SelectionService selectionService;
    private final PaginationService paginationService;
    private final HeaderPanel headerPanel;
    private final List<Object> currentItems;

    private boolean dispatcherInstalled = false;
    private JPopupMenu activeDropdown = null;

    public NavigationService(ImageBrowserApp app, JScrollPane scrollPane, JPanel thumbnailsPanel,
                             SelectionService selectionService, PaginationService paginationService,
                             HeaderPanel headerPanel, List<Object> currentItems) {
        this.app = app;
        this.scrollPane = scrollPane;
        this.thumbnailsPanel = thumbnailsPanel;
        this.selectionService = selectionService;
        this.paginationService = paginationService;
        this.headerPanel = headerPanel;
        this.currentItems = currentItems;
    }

    /**
     * Installs keyboard navigation for the application.
     * Sets up ActionMap actions and KeyEventDispatcher for input routing.
     */
    public void installKeyboardNavigation() {
        // Disable scrollpane arrow scrolling so arrows can be used for selection
        InputMap spIm = scrollPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        spIm.put(KeyStroke.getKeyStroke("UP"), "none");
        spIm.put(KeyStroke.getKeyStroke("DOWN"), "none");
        spIm.put(KeyStroke.getKeyStroke("LEFT"), "none");
        spIm.put(KeyStroke.getKeyStroke("RIGHT"), "none");

        InputMap im = app.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = app.getRootPane().getActionMap();

        // Bind keys to action names
        im.put(KeyStroke.getKeyStroke("LEFT"), "navLeft");
        im.put(KeyStroke.getKeyStroke("RIGHT"), "navRight");
        im.put(KeyStroke.getKeyStroke("UP"), "navUp");
        im.put(KeyStroke.getKeyStroke("DOWN"), "navDown");
        im.put(KeyStroke.getKeyStroke("ENTER"), "openItem");
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "focusSelector");

        // Define actions - all delegate to SelectionService
        am.put("focusSelector", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                thumbnailsPanel.requestFocusInWindow();
            }
        });

        am.put("navLeft", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isMenuNavigationActive()) return;
                selectionService.navigateLeft(currentItems);
                selectionService.updateSelectionHighlight(thumbnailsPanel);
            }
        });

        am.put("navRight", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isMenuNavigationActive()) return;
                selectionService.navigateRight(currentItems);
                selectionService.updateSelectionHighlight(thumbnailsPanel);
            }
        });

        am.put("navUp", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isMenuNavigationActive()) return;
                boolean pageChanged = selectionService.navigateUp(currentItems, thumbnailsPanel, headerPanel, paginationService);
                if (pageChanged) {
                    app.refreshDisplay();
                } else {
                    selectionService.updateSelectionHighlight(thumbnailsPanel);
                }
            }
        });

        am.put("navDown", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isMenuNavigationActive()) return;
                boolean pageChanged = selectionService.navigateDown(currentItems, thumbnailsPanel, headerPanel, paginationService);
                if (pageChanged) {
                    app.refreshDisplay();
                } else {
                    selectionService.updateSelectionHighlight(thumbnailsPanel);
                }
            }
        });

        am.put("openItem", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isMenuNavigationActive()) return;
                
                Object item = selectionService.getSelectedItem(currentItems);
                
                if (item instanceof java.io.File) {
                    app.openComicViewer((java.io.File) item);
                } else if (item instanceof Collection) {
                    SwingUtilities.invokeLater(() -> app.openCollectionDropdown((Collection) item));
                } else if (item instanceof CollectionEntry) {
                    SwingUtilities.invokeLater(() -> app.openCollectionDropdown((CollectionEntry) item));
                }
            }
        });

        installKeyEventDispatcher();
    }

    /**
     * Installs the KeyEventDispatcher to handle key routing priorities:
     * 1. Active collection dropdowns
     * 2. Standard Swing popups/menus
     * 3. Main window navigation (when focused and no text component focused)
     *
     * Uses ActionMap actions for navigation to avoid duplicate logic.
     */
    private void installKeyEventDispatcher() {
        if (dispatcherInstalled) return;
        dispatcherInstalled = true;

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(ev -> {
            if (ev.getID() != KeyEvent.KEY_PRESSED) return false;

            // Priority 1: Route to active collection dropdown
            if (activeDropdown != null && activeDropdown.isVisible()) {
                return routeToDropdown(ev);
            }

            // Priority 2: Route to standard Swing popup/menu navigation
            if (isMenuNavigationActive()) {
                return routeToMenu(ev);
            }

            // Priority 3: Handle main window navigation (only when this window is focused)
            if (!isMainWindowFocused()) return false;

            // Don't steal keys from viewer windows
            if (isViewerWindowActive()) return false;

            // Don't steal keys from text components (search bar, etc.)
            if (isTextComponentFocused()) return false;

            // Trigger ActionMap actions (DRY - no duplicate logic)
            return triggerNavigationAction(ev);
        });
    }

    /**
     * Routes keys to the active dropdown's ActionMap.
     */
    private boolean routeToDropdown(KeyEvent ev) {
        if (activeDropdown == null) return false;
        
        ActionMap da = activeDropdown.getActionMap();
        switch (ev.getKeyCode()) {
            case KeyEvent.VK_UP:
                if (da.get("ddUp") != null) da.get("ddUp").actionPerformed(null);
                ev.consume();
                return true;
            case KeyEvent.VK_DOWN:
                if (da.get("ddDown") != null) da.get("ddDown").actionPerformed(null);
                ev.consume();
                return true;
            case KeyEvent.VK_ENTER:
                if (da.get("ddEnter") != null) da.get("ddEnter").actionPerformed(null);
                ev.consume();
                return true;
            case KeyEvent.VK_ESCAPE:
                if (da.get("ddEsc") != null) da.get("ddEsc").actionPerformed(null);
                ev.consume();
                return true;
            default:
                return false;
        }
    }

    /**
     * Routes keys to standard Swing menu navigation.
     */
    private boolean routeToMenu(KeyEvent ev) {
        switch (ev.getKeyCode()) {
            case KeyEvent.VK_UP:
            case KeyEvent.VK_DOWN:
            case KeyEvent.VK_LEFT:
            case KeyEvent.VK_RIGHT:
            case KeyEvent.VK_ENTER:
            case KeyEvent.VK_ESCAPE:
                MenuSelectionManager.defaultManager().processKeyEvent(ev);
                ev.consume();
                return true;
            default:
                return false;
        }
    }

    /**
     * Triggers navigation actions from the KeyEventDispatcher.
     * This ensures DRY principle - navigation logic is only in ActionMap actions.
     */
    private boolean triggerNavigationAction(KeyEvent ev) {
        ActionMap am = app.getRootPane().getActionMap();
        String actionName = null;

        switch (ev.getKeyCode()) {
            case KeyEvent.VK_LEFT:
                actionName = "navLeft";
                break;
            case KeyEvent.VK_RIGHT:
                actionName = "navRight";
                break;
            case KeyEvent.VK_UP:
                actionName = "navUp";
                break;
            case KeyEvent.VK_DOWN:
                actionName = "navDown";
                break;
            case KeyEvent.VK_ENTER:
                actionName = "openItem";
                break;
            default:
                return false;
        }

        Action action = am.get(actionName);
        if (action != null) {
            action.actionPerformed(new ActionEvent(ev.getSource(), ActionEvent.ACTION_PERFORMED, actionName));
            return true;
        }
        return false;
    }

    /**
     * Adjusts the grid columns based on window size.
     */
    public void adjustGridColumns() {
        if (thumbnailsPanel.getLayout() instanceof GridLayout) {
            int width = thumbnailsPanel.getWidth();
            int thumbnailSize = ThumbnailService.thumbnailSize;
            int columns = Math.max(1, width / (thumbnailSize + 10)); // thumbnailSize + spacing
            thumbnailsPanel.setLayout(new GridLayout(0, columns, 10, 10));
        }
    }

    // Helper methods for focus/context checks

    private boolean isMenuNavigationActive() {
        return MenuSelectionManager.defaultManager().getSelectedPath().length > 0;
    }

    private boolean isMainWindowFocused() {
        Window focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
        return focusedWindow == app;
    }

    private boolean isViewerWindowActive() {
        Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        return activeWindow instanceof ImageViewerFrame;
    }

    private boolean isTextComponentFocused() {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return focusOwner instanceof JTextComponent;
    }

    // Public setters for external state management

    public void setActiveDropdown(JPopupMenu dropdown) {
        this.activeDropdown = dropdown;
    }
}
