package viewer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ComponentSetupService {
    
    public static JScrollPane setupScrollPane(JPanel thumbnailsPanel) {
        // Create wrapper panel to prevent vertical stretching
        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.setBackground(new Color(0, 0, 0, 0)); // Fully transparent
        wrapperPanel.setOpaque(false);
        wrapperPanel.add(thumbnailsPanel, BorderLayout.NORTH);
        
        JScrollPane scrollPane = new JScrollPane(wrapperPanel);
        scrollPane.setBackground(new Color(0, 0, 0, 0)); // Fully transparent
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setBackground(new Color(0, 0, 0, 0)); // Fully transparent
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        scrollPane.getViewport().addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                thumbnailsPanel.requestFocusInWindow();
            }
        });
        scrollPane.getVerticalScrollBar().setUnitIncrement(24); // smoother scroll speed
        return scrollPane;
    }
    
    public static JPanel setupThumbnailsPanel() {
        JPanel thumbnailsPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                // Don't fill background - let it be fully transparent
                super.paintComponent(g);
            }
        };
        thumbnailsPanel.setLayout(new GridLayout(0, 5, 0, 0)); // No gaps between thumbnails
        thumbnailsPanel.setBackground(new Color(0, 0, 0, 0)); // Fully transparent
        thumbnailsPanel.setOpaque(false);
        thumbnailsPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        thumbnailsPanel.setFocusable(true);
        thumbnailsPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                thumbnailsPanel.requestFocusInWindow();
            }
        });
        return thumbnailsPanel;
    }
    
    public static void setupResizeListener(JFrame frame, JScrollPane scrollPane, 
                                         JPanel thumbnailsPanel, int thumbnailSize) {
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                LayoutService.adjustGridColumns(scrollPane, thumbnailsPanel, thumbnailSize);
            }
        });
    }
    
    public static void setupMainWindow(JFrame frame) {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setSize(1400, 700);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(ApplicationService.getBackgroundDark());
    }
}
