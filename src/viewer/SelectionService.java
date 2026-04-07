package viewer;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class SelectionService {
    
    private int selectedIndex = 0;
    private int pendingSelectColumn = -1;
    private boolean pendingSelectFromBottom = false;
    
    public void applyPendingOrResetSelection(List<Object> currentItems, JPanel thumbnailsPanel) {
        if (currentItems.isEmpty()) {
            selectedIndex = 0;
            pendingSelectColumn = -1;
            return;
        }

        int columns = LayoutService.getGridColumns(thumbnailsPanel);

        if (pendingSelectColumn >= 0) {
            int col = pendingSelectColumn;
            pendingSelectColumn = -1;

            int target;
            if (pendingSelectFromBottom) {
                int lastRowStart = ((currentItems.size() - 1) / columns) * columns;
                target = Math.min(lastRowStart + col, currentItems.size() - 1);
            } else {
                target = Math.min(col, currentItems.size() - 1);
            }
            selectedIndex = target;
            return;
        }

        // Reset selection if out of bounds (e.g., page changed/search changed)
        if (selectedIndex < 0 || selectedIndex >= currentItems.size()) {
            selectedIndex = 0;
        }
    }
    
    public void updateSelectionHighlight(JPanel thumbnailsPanel) {
        Component[] comps = thumbnailsPanel.getComponents();
        for (int i = 0; i < comps.length; i++) {
            if (comps[i] instanceof JComponent) {
                JComponent jc = (JComponent) comps[i];
                Object defaultBorder = jc.getClientProperty("defaultBorder");
                if (defaultBorder == null) {
                    jc.putClientProperty("defaultBorder", jc.getBorder());
                    defaultBorder = jc.getBorder();
                }

                if (i == selectedIndex) {
                    jc.setBorder(BorderFactory.createLineBorder(ApplicationService.getThemeColor(ApplicationService.ThemeConfig.HIGHLIGHT_COLOR, ApplicationService.ThemeConfig.DEFAULT_HIGHLIGHT_COLOR), 3));
                    Rectangle r = jc.getBounds();
                    SwingUtilities.invokeLater(() -> thumbnailsPanel.scrollRectToVisible(r));
                } else {
                    jc.setBorder((javax.swing.border.Border) defaultBorder);
                }
            }
        }
    }
    
    public void navigateLeft(List<Object> currentItems) {
        if (currentItems.isEmpty()) return;
        selectedIndex--;
        if (selectedIndex < 0) selectedIndex = currentItems.size() - 1;
    }
    
    public void navigateRight(List<Object> currentItems) {
        if (currentItems.isEmpty()) return;
        selectedIndex++;
        if (selectedIndex >= currentItems.size()) selectedIndex = 0;
    }
    
    public boolean navigateUp(List<Object> currentItems, JPanel thumbnailsPanel, 
                          HeaderPanel headerPanel, PaginationService paginationService) {
        if (currentItems.isEmpty()) return false;
        int columns = LayoutService.getGridColumns(thumbnailsPanel);
        int col = selectedIndex % columns;

        if (selectedIndex < columns && headerPanel != null && paginationService.getCurrentPage() > 1) {
            pendingSelectColumn = col;
            pendingSelectFromBottom = true;
            paginationService.setCurrentPage(paginationService.getCurrentPage() - 1);
            return true; // Indicates page change occurred
        }

        selectedIndex -= columns;
        if (selectedIndex < 0) selectedIndex = 0;
        return false; // No page change
    }
    
    public boolean navigateDown(List<Object> currentItems, JPanel thumbnailsPanel, 
                               HeaderPanel headerPanel, PaginationService paginationService) {
        if (currentItems.isEmpty()) return false;
        int columns = LayoutService.getGridColumns(thumbnailsPanel);
        int col = selectedIndex % columns;

        if (selectedIndex + columns >= currentItems.size() && headerPanel != null && 
            paginationService.getCurrentPage() < paginationService.getTotalPages(
                ThumbnailService.getCachedFolders().size(), 
                ((ImageBrowserApp) headerPanel.getParent().getParent().getParent().getParent()).getCollections().size())) {
            
            pendingSelectColumn = col;
            pendingSelectFromBottom = false;
            paginationService.setCurrentPage(paginationService.getCurrentPage() + 1);
            return true; // Indicates page change occurred
        }

        selectedIndex += columns;
        if (selectedIndex >= currentItems.size()) selectedIndex = currentItems.size() - 1;
        return false; // No page change
    }
    
    public Object getSelectedItem(List<Object> currentItems) {
        if (currentItems.isEmpty() || selectedIndex < 0 || selectedIndex >= currentItems.size()) {
            return null;
        }
        return currentItems.get(selectedIndex);
    }
    
    public void setSelectedIndex(int index) {
        this.selectedIndex = index;
    }
    
    public int getSelectedIndex() {
        return selectedIndex;
    }
    
    public void setPendingSelection(int column, boolean fromBottom) {
        this.pendingSelectColumn = column;
        this.pendingSelectFromBottom = fromBottom;
    }
    
    public void resetSelection() {
        this.selectedIndex = 0;
        this.pendingSelectColumn = -1;
        this.pendingSelectFromBottom = false;
    }
}
