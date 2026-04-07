package viewer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class LayoutService {
    
    public static void adjustGridColumns(JScrollPane scrollPane, JPanel thumbnailsPanel, int thumbnailSize) {
        int width = scrollPane.getViewport().getWidth();
        if (width <= 0) return;

        int cols = Math.max(1, width / (thumbnailSize + 20));
        GridLayout layout = (GridLayout) thumbnailsPanel.getLayout();
        if (layout.getColumns() != cols) {
            layout.setColumns(cols);
            thumbnailsPanel.revalidate();
        }
    }
    
    public static void setGridView(JPanel thumbnailsPanel) {
        thumbnailsPanel.setLayout(new GridLayout(0, 5, 0, 0)); // No gaps between thumbnails
        thumbnailsPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
    }
    
    public static int getGridColumns(JPanel thumbnailsPanel) {
        LayoutManager lm = thumbnailsPanel.getLayout();
        if (lm instanceof GridLayout) {
            int cols = ((GridLayout) lm).getColumns();
            if (cols > 0) return cols;
        }
        return 5;
    }
}
