package com.microservices.autolock;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.RectVector;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
import org.bytedeco.opencv.opencv_videoio.VideoCapture;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * FaceWatch Auto Lock Service
 *
 * Automatically locks Windows workstation when:
 * - User is idle for configured time
 * - No face is detected by webcam
 *
 * Features:
 * - Pauses when system is locked (saves battery/privacy)
 * - Scoring system to prevent false positives
 * - Configurable thresholds
 * - Comprehensive logging
 * - System tray integration (optional)
 *
 * @version 1.0.0
 */
public class FaceWatchService {

    // ═══════════════════════════════════════════════════════════════
    // CONFIGURATION - Adjust these values as needed
    // ═══════════════════════════════════════════════════════════════

    /** How often to check user activity (milliseconds) */
    private static final long CHECK_INTERVAL_MS = 5000;

    /** User must be idle this long before camera activates (seconds) */
    private static final int IDLE_TIMEOUT_SEC = 7;

    /** Minimum face size to detect (pixels) */
    private static final int MIN_FACE_SIZE = 40;

    /** Lock when score drops below this threshold */
    private static final int DETECTION_THRESHOLD = 20;

    /** Maximum detection score */
    private static final int DETECTION_SCORE_MAX = 100;

    /** Minimum detection score */
    private static final int DETECTION_SCORE_MIN = 0;

    /** Time between camera activations (milliseconds) */
    private static final long CAMERA_COOLDOWN_MS = 8000;

    /** Minimum brightness to attempt face detection */
    private static final int MIN_BRIGHTNESS = 30;

    /** Camera warmup time (milliseconds) */
    private static final long CAMERA_WARMUP_MS = 1000;

    /** How often to log heartbeat statistics (milliseconds) */
    private static final long HEARTBEAT_INTERVAL_MS = 60000;

    /** Points added when face is detected */
    private static final int SCORE_INCREMENT = 8;

    /** Points removed when no face is detected */
    private static final int SCORE_DECREMENT = 12;

    // ═══════════════════════════════════════════════════════════════
    // FACE DETECTION PARAMETERS
    // ═══════════════════════════════════════════════════════════════

    /** Haar Cascade scale factor (1.1 = 10% scale steps) */
    private final double scaleFactor = 1.1;

    /** Minimum neighbors for valid detection (reduces false positives) */
    private final int minNeighbors = 5;

    // ═══════════════════════════════════════════════════════════════
    // RUNTIME STATE (Thread-Safe)
    // ═══════════════════════════════════════════════════════════════

    /** Service running flag */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Whether we have locked the workstation */
    private final AtomicBoolean locked = new AtomicBoolean(false);

    /** Current detection score (0-100) */
    private final AtomicInteger detectionScore = new AtomicInteger(50);

    /** Last time camera was used */
    private final AtomicLong lastCameraCheckTime = new AtomicLong(0);

    /** Last time heartbeat was logged */
    private final AtomicLong lastHeartbeatTime = new AtomicLong(0);

    /** Last time activity was logged */
    private final AtomicLong lastActivityLogTime = new AtomicLong(0);

    /** Whether user was idle on last check */
    private final AtomicBoolean wasIdleLastCheck = new AtomicBoolean(false);

    /** Whether system is currently locked */
    private final AtomicBoolean systemLocked = new AtomicBoolean(false);

    /** Whether system was locked by us */
    private final AtomicBoolean wasSystemLockedByUs = new AtomicBoolean(false);

    // ═══════════════════════════════════════════════════════════════
    // STATISTICS
    // ═══════════════════════════════════════════════════════════════

    private final AtomicLong totalChecks = new AtomicLong(0);
    private final AtomicLong totalFaceDetections = new AtomicLong(0);
    private final AtomicLong totalLockEvents = new AtomicLong(0);
    private final AtomicLong cameraActivations = new AtomicLong(0);
    private final AtomicLong skippedDueToSystemLock = new AtomicLong(0);

    // ═══════════════════════════════════════════════════════════════
    // COMPONENTS
    // ═══════════════════════════════════════════════════════════════

    private CascadeClassifier faceCascade;
    private ScheduledExecutorService executor;
    private WindowsSessionMonitor sessionMonitor;
    private SystemTrayApp trayApp;

    // ═══════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    // ═══════════════════════════════════════════════════════════════
    // SERVICE LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Start the FaceWatch service
     *
     * @throws Exception if service cannot start
     */
    public void start() throws Exception {
        if (!running.compareAndSet(false, true)) {
            log("Service already running");
            return;
        }

        log("🚀 FaceWatchService starting...");
        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log("📋 Configuration:");
        log("   - Check Interval: " + CHECK_INTERVAL_MS + "ms (" + (CHECK_INTERVAL_MS/1000) + "s)");
        log("   - Idle Timeout: " + IDLE_TIMEOUT_SEC + "s");
        log("   - Camera Cooldown: " + CAMERA_COOLDOWN_MS + "ms (" + (CAMERA_COOLDOWN_MS/1000) + "s)");
        log("   - Detection Threshold: " + DETECTION_THRESHOLD);
        log("   - Score Increment (face): +" + SCORE_INCREMENT);
        log("   - Score Decrement (no face): -" + SCORE_DECREMENT);
        log("   - Min Brightness: " + MIN_BRIGHTNESS);
        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Initialize user activity monitor
        try {
            UserActivityMonitor.start();
        } catch (Exception e) {
            log("Failed to initialize UserActivityMonitor: " + e.getMessage());
            throw e;
        }

        // Initialize session monitor (detects lock/unlock)
        try {
            sessionMonitor = new WindowsSessionMonitor();
            sessionMonitor.start(new WindowsSessionMonitor.SessionChangeListener() {
                @Override
                public void onSessionLocked() {
                    handleSystemLocked();
                }

                @Override
                public void onSessionUnlocked() {
                    handleSystemUnlocked();
                }
            });
        } catch (Exception e) {
            log("Session monitor not available: " + e.getMessage());
            log("   Service will continue but won't detect manual lock/unlock");
        }

        // Load Haar Cascade for face detection
        try (InputStream is = getClass().getResourceAsStream("/haarcascade_frontalface_alt.xml")) {
            if (is == null) {
                throw new IllegalStateException("Cascade file not found in resources");
            }
            Path temp = Files.createTempFile("cascade-", ".xml");
            Files.copy(is, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            faceCascade = new CascadeClassifier(temp.toAbsolutePath().toString());
            temp.toFile().deleteOnExit();

            if (faceCascade.empty()) {
                throw new IllegalStateException("Failed to load cascade classifier");
            }
            log(" Face detection model loaded");
        } catch (Exception e) {
            log("Failed to load face detection model: " + e.getMessage());
            throw e;
        }

        // Test camera availability
        VideoCapture testCapture = null;
        try {
            testCapture = new VideoCapture(0);
            if (!testCapture.isOpened()) {
                throw new IllegalStateException("Webcam not available or access denied");
            }
            log(" Webcam detected and available");
        } catch (Exception e) {
            log(" Webcam initialization failed: " + e.getMessage());
            throw e;
        } finally {
            if (testCapture != null) {
                safeRelease(testCapture);
            }
        }

        // Initialize system tray (optional, won't fail if unsupported)
//        try {
//            trayApp = new SystemTrayApp(this);
//            trayApp.initialize();
//        } catch (Exception e) {
//            log("️ System tray not available: " + e.getMessage());
//            trayApp = null;
//        }
        // System tray disabled - running in background mode
        trayApp = null;
        log(" Running in background mode (no system tray)");

        // Start monitoring executor
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "FaceWatchService-Monitor");
            t.setDaemon(true);
            return t;
        });

        executor.scheduleWithFixedDelay(
                this::checkFrame,
                0,
                CHECK_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );

        log("FaceWatchService started successfully");
        log(" Monitoring user activity...");
        log("");
    }

    /**
     * Stop the FaceWatch service
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        log(" Stopping FaceWatchService...");

        // Print final statistics
        log("");
        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log(" Final Statistics:");
        log("   Total checks: " + totalChecks.get());
        log("   Camera activations: " + cameraActivations.get());
        log("   Faces detected: " + totalFaceDetections.get());
        log("   Lock events: " + totalLockEvents.get());
        log("   Skipped (system locked): " + skippedDueToSystemLock.get());

        long activations = cameraActivations.get();
        if (activations > 0) {
            double detectionRate = (totalFaceDetections.get() * 100.0) / activations;
            log("   Detection rate: " + String.format("%.1f%%", detectionRate));
        }
        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Cleanup tray icon
        if (trayApp != null) {
            try {
                trayApp.cleanup();
            } catch (Exception e) {
                log(" Error cleaning up tray icon: " + e.getMessage());
            }
        }

        // Stop session monitor
        if (sessionMonitor != null) {
            try {
                sessionMonitor.stop();
            } catch (Exception e) {
                log(" Error stopping session monitor: " + e.getMessage());
            }
        }

        // Shutdown executor
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        log("Executor did not terminate");
                    }
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log(" FaceWatchService stopped");
    }

    // ═══════════════════════════════════════════════════════════════
    // CORE MONITORING LOGIC
    // ═══════════════════════════════════════════════════════════════

    /**
     * Main monitoring loop - runs every CHECK_INTERVAL_MS
     */
    private void checkFrame() {
        try {
            totalChecks.incrementAndGet();

            // Skip all checks if system is locked
            if (systemLocked.get()) {
                skippedDueToSystemLock.incrementAndGet();
                // Log occasionally to show we're still alive
                if (skippedDueToSystemLock.get() % 12 == 0) {
                    log(" System locked - monitoring paused (skipped " +
                            skippedDueToSystemLock.get() + " checks)");
                }
                return;
            }

            long idleTime = UserActivityMonitor.getIdleTimeSeconds();
            long now = System.currentTimeMillis();

            // Periodic heartbeat
            if (now - lastHeartbeatTime.get() > HEARTBEAT_INTERVAL_MS) {
                logHeartbeat();
                lastHeartbeatTime.set(now);
            }

            // User is active - clean state
            if (idleTime < IDLE_TIMEOUT_SEC) {
                handleUserActive(idleTime, now);
                return;
            }

            // Log transition from active to idle
            if (!wasIdleLastCheck.get()) {
                log("User went idle (no activity for " + idleTime + "s)");
                wasIdleLastCheck.set(true);
            }

            // User idle but camera in cooldown - wait
            if (isInCooldown()) {
                return;
            }

            // Capture and process frame
            processFrameWithCamera(idleTime);

        } catch (Exception e) {
            log(" Error in checkFrame: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle user becoming active
     */
    private void handleUserActive(long idleTime, long now) {
        boolean shouldLog = false;

        // Log state changes or periodically
        if (wasIdleLastCheck.get()) {
            // Transition: idle → active
            log(" User is now ACTIVE (idle time: " + idleTime + "s) - camera OFF, score reset to 50");
            shouldLog = true;
            wasIdleLastCheck.set(false);
            skippedDueToSystemLock.set(0);
        } else if (now - lastActivityLogTime.get() > 30000) {
            // Log every 30s while active
            log("User ACTIVE (idle: " + idleTime + "s) - camera OFF - monitoring...");
            shouldLog = true;
        }

        if (shouldLog) {
            lastActivityLogTime.set(now);
        }

        // Reset state
        detectionScore.set(50);
        locked.set(false);
        lastCameraCheckTime.set(0);
    }

    /**
     * Process a single frame from camera
     */
    private void processFrameWithCamera(long idleTime) {
        VideoCapture camera = null;
        Mat frame = null;

        try {
            cameraActivations.incrementAndGet();
            log(" Opening camera... (idle: " + idleTime + "s, score: " + detectionScore.get() + ")");

            // Open camera
            camera = new VideoCapture(0);
            if (!camera.isOpened()) {
                log("️ Could not open webcam");
                return;
            }

            // Give MSMF time to initialize
            Thread.sleep(CAMERA_WARMUP_MS);

            // Check if system was locked during warmup
            if (systemLocked.get()) {
                log(" System locked during camera warmup - aborting check");
                return;
            }

            // Capture frame
            frame = new Mat();
            if (!camera.read(frame) || frame.empty()) {
                log(" Empty frame received");
                return;
            }

            // Double-check user is still idle
            long currentIdleTime = UserActivityMonitor.getIdleTimeSeconds();
            if (currentIdleTime < IDLE_TIMEOUT_SEC) {
                log(" User became active during capture (idle: " + currentIdleTime + "s) - aborting check");
                detectionScore.set(50);
                locked.set(false);
                lastCameraCheckTime.set(0);
                wasIdleLastCheck.set(false);
                return;
            }

            // Process frame
            boolean faceDetected = detectFace(frame, currentIdleTime);

            // Update score
            updateDetectionScore(faceDetected);

            // Check if should lock
            if (!systemLocked.get()) {
                checkAndLock();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log(" Frame processing interrupted");
        } catch (Exception e) {
            log(" Error processing frame: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Always cleanup resources
            if (frame != null && !frame.isNull()) {
                frame.release();
            }
            if (camera != null) {
                safeRelease(camera);
            }
            // Start cooldown after release
            startCooldown();
        }
    }

    /**
     * Detect faces in a frame
     *
     * @param frame The captured frame
     * @param idleTime Current idle time
     * @return true if face detected, false otherwise
     */
    private boolean detectFace(Mat frame, long idleTime) {
        Mat gray = null;
        RectVector faces = null;

        try {
            // Convert to grayscale
            gray = new Mat();
            opencv_imgproc.cvtColor(frame, gray, opencv_imgproc.COLOR_BGR2GRAY);

            // Check brightness
            double brightness = opencv_core.mean(gray).get(0);
            if (brightness < MIN_BRIGHTNESS) {
                log(" Low light (brightness: " + String.format("%.1f", brightness) +
                        ") - assuming no face");
                return false;
            }

            // Enhance contrast
            opencv_imgproc.equalizeHist(gray, gray);

            // Detect faces
            faces = new RectVector();
            faceCascade.detectMultiScale(
                    gray,
                    faces,
                    scaleFactor,
                    minNeighbors,
                    0,
                    new Size(MIN_FACE_SIZE, MIN_FACE_SIZE),
                    new Size()
            );

            long faceCount = faces.size();
            boolean detected = faceCount > 0;

            if (detected) {
                totalFaceDetections.incrementAndGet();
            }

            // Log result
            String status = detected ? " FACE DETECTED" : " NO FACE";
            int currentScore = detectionScore.get();
            int newScore = detected
                    ? Math.min(currentScore + SCORE_INCREMENT, DETECTION_SCORE_MAX)
                    : Math.max(currentScore - SCORE_DECREMENT, DETECTION_SCORE_MIN);

            log(String.format(" %s | Faces: %d | Idle: %ds | Score: %d → %d | Brightness: %.1f",
                    status, faceCount, idleTime, currentScore, newScore, brightness));

            return detected;

        } finally {
            if (gray != null && !gray.isNull()) {
                gray.release();
            }
            if (faces != null) {
                faces.close();
            }
        }
    }

    /**
     * Update detection score based on face detection result
     */
    private void updateDetectionScore(boolean faceDetected) {
        int current = detectionScore.get();
        int newScore;

        if (faceDetected) {
            newScore = Math.min(current + SCORE_INCREMENT, DETECTION_SCORE_MAX);
        } else {
            newScore = Math.max(current - SCORE_DECREMENT, DETECTION_SCORE_MIN);
        }

        detectionScore.set(newScore);
    }

    /**
     * Check if workstation should be locked
     */
    private void checkAndLock() {
        int score = detectionScore.get();

        // Lock condition
        if (score < DETECTION_THRESHOLD && locked.compareAndSet(false, true)) {
            totalLockEvents.incrementAndGet();
            log(" LOCKING WORKSTATION (score: " + score + " < " +
                    DETECTION_THRESHOLD + ") ");
            lockWorkstation();
            log(" Workstation lock command executed");

            // Show tray notification (if available)
            if (trayApp != null) {
                try {
                    trayApp.showLockNotification();
                } catch (Exception e) {
                    // Ignore tray notification errors
                }
            }
        }

        // Reset lock flag if score recovers
        if (score > DETECTION_THRESHOLD + 10 && locked.get()) {
            log(" Score recovered (" + score + " > " +
                    (DETECTION_THRESHOLD + 10) + ") - resetting lock flag");
            locked.set(false);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SESSION MANAGEMENT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Handle system lock event
     */
    private void handleSystemLocked() {
        systemLocked.set(true);
        log(" SYSTEM LOCKED DETECTED - Pausing all monitoring");
        log("   Camera will NOT activate until system is unlocked");

        if (locked.get()) {
            wasSystemLockedByUs.set(true);
            log("   (System was locked by FaceWatch)");
        } else {
            wasSystemLockedByUs.set(false);
            log("   (System was locked manually by user)");
        }
    }

    /**
     * Handle system unlock event
     */
    private void handleSystemUnlocked() {
        systemLocked.set(false);
        log(" SYSTEM UNLOCKED DETECTED - Resuming monitoring");

        // Reset state - user authenticated
        detectionScore.set(50);
        locked.set(false);
        wasIdleLastCheck.set(false);
        lastCameraCheckTime.set(0);
        wasSystemLockedByUs.set(false);

        log("   All checks reset - monitoring resumed");

        // Show tray notification (if available)
        if (trayApp != null) {
            try {
                trayApp.showUnlockNotification();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Start camera cooldown period
     */
    private void startCooldown() {
        lastCameraCheckTime.set(System.currentTimeMillis());
    }

    /**
     * Check if camera is in cooldown period
     */
    private boolean isInCooldown() {
        long lastCheck = lastCameraCheckTime.get();
        if (lastCheck == 0) return false;
        return (System.currentTimeMillis() - lastCheck) < CAMERA_COOLDOWN_MS;
    }

    /**
     * Safely release camera with proper cleanup
     */
    private void safeRelease(VideoCapture camera) {
        if (camera == null) return;

        try {
            if (camera.isOpened()) {
                camera.release();
                // Give MSMF time to cleanup
                Thread.sleep(500);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log(" Error releasing camera: " + e.getMessage());
        }
    }

    /**
     * Execute workstation lock command
     */
    private void lockWorkstation() {
        try {
            Runtime.getRuntime().exec("rundll32.exe user32.dll,LockWorkStation");
        } catch (Exception e) {
            log(" Failed to lock workstation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Log periodic heartbeat with statistics
     */
    private void logHeartbeat() {
        long checks = totalChecks.get();
        long faces = totalFaceDetections.get();
        long locks = totalLockEvents.get();
        long activations = cameraActivations.get();
        long skipped = skippedDueToSystemLock.get();
        double detectionRate = activations > 0 ? (faces * 100.0 / activations) : 0;

        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log(" HEARTBEAT - " + getCurrentTime());
        log("   Total checks: " + checks);
        log("   Camera activations: " + activations);
        log("   Faces detected: " + faces + " (" + String.format("%.1f", detectionRate) + "%)");
        log("   Lock events: " + locks);
        log("   Skipped (system locked): " + skipped);
        log("   Current score: " + detectionScore.get());
        log("   System status: " + (systemLocked.get() ? " LOCKED" : " UNLOCKED"));
        log("   Service status: " + (locked.get() ? " LOCKED" : " UNLOCKED"));
        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * Log a message with timestamp
     */
    private void log(String message) {
        System.out.println("[" + getCurrentTime() + "] " + message);
    }

    /**
     * Get current time formatted
     */
    private String getCurrentTime() {
        return LocalDateTime.now().format(TIME_FORMAT);
    }

    // ═══════════════════════════════════════════════════════════════
    // MAIN ENTRY POINT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Main entry point
     */
    public static void main(String[] args) {
        FaceWatchService service = new FaceWatchService();
        try {
            service.start();

            // Add shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n Shutdown signal received");
                service.stop();
            }));

            // Keep main thread alive
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println(" Fatal error: " + e.getMessage());
            e.printStackTrace();
            service.stop();
            System.exit(1);
        }
    }
}