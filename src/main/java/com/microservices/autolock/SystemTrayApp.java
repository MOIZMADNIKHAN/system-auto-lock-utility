package com.microservices.autolock;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class SystemTrayApp {

    private final FaceWatchService service;
    private volatile TrayIcon trayIcon;
    private volatile boolean initialized = false;

    public SystemTrayApp(FaceWatchService service) {
        this.service = service;
    }

    public void initialize() {
        // Check if system tray is supported
        if (!SystemTray.isSupported()) {
            System.out.println("⚠️ System tray not supported on this platform");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                SystemTray tray = SystemTray.getSystemTray();

                // Create icon image
                Image image = createTrayIcon();
                if (image == null) {
                    System.out.println("⚠️ Could not create tray icon image");
                    return;
                }

                // Create popup menu
                PopupMenu popup = createPopupMenu();

                // Create tray icon
                trayIcon = new TrayIcon(image, "FaceWatch Service", popup);
                trayIcon.setImageAutoSize(true);

                // Double-click to show stats
                trayIcon.addActionListener(e -> showStatistics());

                // Add to system tray
                tray.add(trayIcon);
                initialized = true;

                // Show startup notification
                showMessage(
                        "FaceWatch Started",
                        "Workstation monitoring is active",
                        TrayIcon.MessageType.INFO
                );

                System.out.println("✅ System tray icon initialized");

            } catch (AWTException e) {
                System.err.println("⚠️ Could not add tray icon: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("⚠️ Failed to create system tray icon: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Create a simple colored icon programmatically
     */
    private Image createTrayIcon() {
        try {
            int size = 16;
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                    size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB
            );

            Graphics2D g = image.createGraphics();
            if (g == null) {
                return null;
            }

            try {
                // Enable anti-aliasing
                g.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                );

                // Draw a green circle
                g.setColor(new Color(76, 175, 80)); // Material Green
                g.fillOval(1, 1, size - 2, size - 2);

                // Draw white border
                g.setColor(Color.WHITE);
                g.drawOval(1, 1, size - 2, size - 2);

                // Draw "F" for FaceWatch
                g.setFont(new Font("Arial", Font.BOLD, 10));
                FontMetrics fm = g.getFontMetrics();
                int textWidth = fm.stringWidth("F");
                int textHeight = fm.getAscent();
                g.drawString("F", (size - textWidth) / 2, (size + textHeight) / 2 - 1);

            } finally {
                g.dispose();
            }

            return image;

        } catch (Exception e) {
            System.err.println("⚠️ Error creating tray icon: " + e.getMessage());
            return null;
        }
    }

    /**
     * Create the popup menu
     */
    private PopupMenu createPopupMenu() {
        PopupMenu popup = new PopupMenu();

        // Status item
        MenuItem statusItem = new MenuItem("FaceWatch - Running");
        statusItem.setEnabled(false);
        try {
            Font boldFont = new Font(
                    statusItem.getFont().getName(),
                    Font.BOLD,
                    statusItem.getFont().getSize()
            );
            statusItem.setFont(boldFont);
        } catch (Exception e) {
            // Ignore font errors
        }
        popup.add(statusItem);

        popup.addSeparator();

        // View logs
        MenuItem logsItem = new MenuItem("📋 View Logs");
        logsItem.addActionListener(e -> openLogs());
        popup.add(logsItem);

        // Statistics
        MenuItem statsItem = new MenuItem("📊 Show Statistics");
        statsItem.addActionListener(e -> showStatistics());
        popup.add(statsItem);

        popup.addSeparator();

        // About
        MenuItem aboutItem = new MenuItem("ℹ️ About");
        aboutItem.addActionListener(e -> showAbout());
        popup.add(aboutItem);

        // Exit
        MenuItem exitItem = new MenuItem("❌ Exit");
        exitItem.addActionListener(e -> exitApplication());
        popup.add(exitItem);

        return popup;
    }

    /**
     * Show a notification message
     */
    private void showMessage(String title, String message, TrayIcon.MessageType type) {
        if (initialized && trayIcon != null) {
            try {
                trayIcon.displayMessage(title, message, type);
            } catch (Exception e) {
                // Silently ignore notification errors
            }
        }
    }

    /**
     * Show lock notification
     */
    public void showLockNotification() {
        showMessage(
                "Workstation Locked",
                "No face detected - system locked for security",
                TrayIcon.MessageType.WARNING
        );
    }

    /**
     * Show unlock notification
     */
    public void showUnlockNotification() {
        showMessage(
                "Monitoring Resumed",
                "System unlocked - face detection active",
                TrayIcon.MessageType.INFO
        );
    }

    /**
     * Open log file
     */
    private void openLogs() {
        try {
            File logFile = new File("logs/service.log");
            if (logFile.exists()) {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(logFile);
                } else {
                    Runtime.getRuntime().exec("notepad " + logFile.getAbsolutePath());
                }
            } else {
                showMessage(
                        "No Logs",
                        "Log file not found at: " + logFile.getAbsolutePath(),
                        TrayIcon.MessageType.WARNING
                );
            }
        } catch (Exception e) {
            System.err.println("⚠️ Could not open logs: " + e.getMessage());
        }
    }

    /**
     * Show statistics
     */
    private void showStatistics() {
        showMessage(
                "FaceWatch Statistics",
                "Service: Running\nStatus: Monitoring\n\nCheck logs for detailed statistics.",
                TrayIcon.MessageType.INFO
        );
    }

    /**
     * Show about dialog
     */
    private void showAbout() {
        showMessage(
                "FaceWatch Auto Lock v1.0",
                "Automatically locks your workstation\nwhen no face is detected.\n\n© 2024",
                TrayIcon.MessageType.INFO
        );
    }

    /**
     * Exit application
     */
    private void exitApplication() {
        new Thread(() -> {
            try {
                int response = JOptionPane.showConfirmDialog(
                        null,
                        "Stop FaceWatch service?\n\nYour workstation will no longer auto-lock.",
                        "Confirm Exit",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );

                if (response == JOptionPane.YES_OPTION) {
                    cleanup();
                    service.stop();
                    System.exit(0);
                }
            } catch (Exception e) {
                // Fallback: just exit
                cleanup();
                service.stop();
                System.exit(0);
            }
        }).start();
    }

    /**
     * Cleanup tray icon
     */
    public void cleanup() {
        if (trayIcon != null) {
            try {
                SystemTray.getSystemTray().remove(trayIcon);
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }
}