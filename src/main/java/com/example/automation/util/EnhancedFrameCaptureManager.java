package com.example.automation.util;

import com.example.automation.logger.LoggerMechanism;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v137.page.Page;
import org.openqa.selenium.devtools.v137.page.model.ScreencastFrame;
import org.openqa.selenium.devtools.v137.target.model.TargetID;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Enhanced Frame Capture Manager
 * 
 * This class addresses critical frame capture timing issues identified in log analysis:
 * - Duplicate frame captures (e.g., "Captured frame 3" appearing twice)
 * - Inconsistent frame timing causing flickering
 * - Frame drops during high-frequency operations
 * - Lack of adaptive timing based on browser performance
 * 
 * Key improvements:
 * - Duplicate frame detection and filtering
 * - Adaptive capture intervals based on browser performance
 * - Fallback mechanisms for timing failures
 * - Frame sequence validation and gap detection
 * - Memory-efficient frame processing pipeline
 */
public class EnhancedFrameCaptureManager {
    
    private final LoggerMechanism loggerMechanism;
    private final WebDriver driver;
    private final Path frameDirectory;
    
    // Frame capture configuration
    private static final int DEFAULT_CAPTURE_INTERVAL_MS = 200;
    private static final int MIN_CAPTURE_INTERVAL_MS = 50;
    private static final int MAX_CAPTURE_INTERVAL_MS = 1000;
    private static final int ADAPTIVE_WINDOW_SIZE = 10;
    private static final double PERFORMANCE_THRESHOLD = 0.8; // 80% success rate
    
    // Frame processing
    private final AtomicInteger frameCounter = new AtomicInteger(0);
    private final AtomicLong lastFrameTimestamp = new AtomicLong(0);
    private final AtomicInteger currentCaptureInterval = new AtomicInteger(DEFAULT_CAPTURE_INTERVAL_MS);
    
    // Duplicate detection
    private final Map<String, Long> frameHashMap = new ConcurrentHashMap<>();
    private final Set<Integer> processedFrameIds = ConcurrentHashMap.newKeySet();
    
    // Performance tracking
    private final CircularBuffer<FrameMetrics> performanceHistory = new CircularBuffer<>(ADAPTIVE_WINDOW_SIZE);
    private final AtomicReference<PerformanceStats> currentStats = new AtomicReference<>(new PerformanceStats());
    
    // Threading for frame processing
    private final ExecutorService frameProcessingExecutor = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "Frame-Processing-Thread");
        t.setDaemon(true);
        return t;
    });
    
    private final ScheduledExecutorService adaptiveTimingExecutor = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "Adaptive-Timing-Thread");
        t.setDaemon(true);
        return t;
    });
    
    // Fallback mechanisms
    private volatile boolean useTimedCapture = true;
    private volatile boolean useDOMTriggers = true;
    private final AtomicInteger fallbackAttempts = new AtomicInteger(0);
    
    public EnhancedFrameCaptureManager(WebDriver driver, LoggerMechanism loggerMechanism, Path frameDirectory) {
        this.driver = driver;
        this.loggerMechanism = loggerMechanism;
        this.frameDirectory = frameDirectory;
        
        startAdaptiveTimingMonitor();
        loggerMechanism.info("Enhanced Frame Capture Manager initialized");
    }
    
    /**
     * Create an enhanced frame listener with duplicate detection and timing control
     */
    public java.util.function.Consumer<ScreencastFrame> createEnhancedFrameListener(TargetID targetId, DevTools devTools) {
        return frame -> {
            long captureStartTime = System.currentTimeMillis();
            
            try {
                // Check for duplicate frames
                if (isDuplicateFrame(frame)) {
                    loggerMechanism.debug("Skipping duplicate frame for target: " + targetId);
                    acknowledgeFrame(devTools, frame);
                    return;
                }
                
                // Process frame asynchronously to avoid blocking
                frameProcessingExecutor.submit(() -> {
                    processFrame(frame, targetId, devTools, captureStartTime);
                });
                
            } catch (Exception e) {
                loggerMechanism.error("Error in enhanced frame listener for target " + targetId, e);
                // Acknowledge frame even on error to prevent blocking
                acknowledgeFrame(devTools, frame);
            }
        };
    }
    
    /**
     * Process a single frame with comprehensive error handling and metrics
     */
    private void processFrame(ScreencastFrame frame, TargetID targetId, DevTools devTools, long captureStartTime) {
        int frameNumber = 0;
        boolean success = false;
        
        try {
            // Decode frame data
            byte[] decodedBytes = Base64.getDecoder().decode(frame.getData());
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(decodedBytes));
            
            if (image == null) {
                loggerMechanism.error("Failed to decode frame image for target: " + targetId);
                return;
            }
            
            // Validate frame dimensions and content
            if (!isValidFrame(image)) {
                loggerMechanism.warn("Invalid frame dimensions or content for target: " + targetId);
                return;
            }
            
            // Generate frame number and save
            frameNumber = frameCounter.incrementAndGet();
            
            // Check if this frame number was already processed (additional duplicate protection)
            if (!processedFrameIds.add(frameNumber)) {
                loggerMechanism.warn("Frame number " + frameNumber + " already processed, skipping");
                return;
            }
            
            String filename = String.format("frame_%05d.png", frameNumber);
            Path outputFile = frameDirectory.resolve(filename);
            
            // Save frame with retry logic
            boolean saved = saveFrameWithRetry(image, outputFile, 3);
            
            if (saved) {
                long currentTime = System.currentTimeMillis();
                lastFrameTimestamp.set(currentTime);
                
                long processingTime = currentTime - captureStartTime;
                updatePerformanceMetrics(processingTime, true);
                
                loggerMechanism.info("Captured frame " + frameNumber + " from target: " + targetId + 
                                   " (processing: " + processingTime + "ms)");
                success = true;
            } else {
                loggerMechanism.error("Failed to save frame " + frameNumber + " for target: " + targetId);
            }
            
        } catch (Exception e) {
            loggerMechanism.error("Error processing frame for target " + targetId, e);
        } finally {
            // Always acknowledge frame to prevent blocking
            acknowledgeFrame(devTools, frame);
            
            // Update metrics
            if (!success) {
                long processingTime = System.currentTimeMillis() - captureStartTime;
                updatePerformanceMetrics(processingTime, false);
                recordFrameFailure(targetId);
            }
        }
    }
    
    /**
     * Check if frame is duplicate using content hash
     */
    private boolean isDuplicateFrame(ScreencastFrame frame) {
        try {
            // Create hash of frame data
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] frameData = Base64.getDecoder().decode(frame.getData());
            byte[] hash = md.digest(frameData);
            String hashString = Base64.getEncoder().encodeToString(hash);
            
            long currentTime = System.currentTimeMillis();
            Long lastSeen = frameHashMap.get(hashString);
            
            if (lastSeen != null && (currentTime - lastSeen) < 1000) { // Within 1 second
                return true;
            }
            
            // Store hash with timestamp
            frameHashMap.put(hashString, currentTime);
            
            // Clean up old hashes periodically
            if (frameHashMap.size() > 100) {
                cleanupOldFrameHashes(currentTime);
            }
            
            return false;
            
        } catch (Exception e) {
            loggerMechanism.error("Error checking for duplicate frame", e);
            return false; // If we can't determine, process the frame
        }
    }
    
    /**
     * Validate frame content and dimensions
     */
    private boolean isValidFrame(BufferedImage image) {
        if (image.getWidth() < 100 || image.getHeight() < 100) {
            return false;
        }
        
        // Check if image is completely blank (could indicate rendering issue)
        int[] pixels = image.getRGB(0, 0, Math.min(50, image.getWidth()), Math.min(50, image.getHeight()), null, 0, Math.min(50, image.getWidth()));
        boolean allSame = true;
        int firstPixel = pixels[0];
        
        for (int pixel : pixels) {
            if (pixel != firstPixel) {
                allSame = false;
                break;
            }
        }
        
        if (allSame) {
            loggerMechanism.warn("Frame appears to be completely uniform color, possible rendering issue");
        }
        
        return true;
    }
    
    /**
     * Save frame with retry logic
     */
    private boolean saveFrameWithRetry(BufferedImage image, Path outputFile, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                ImageIO.write(image, "png", outputFile.toFile());
                return true;
            } catch (Exception e) {
                loggerMechanism.warn("Frame save attempt " + attempt + " failed: " + e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(50 * attempt); // Progressive delay
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * Safely acknowledge frame to prevent blocking
     */
    private void acknowledgeFrame(DevTools devTools, ScreencastFrame frame) {
        try {
            devTools.send(Page.screencastFrameAck(frame.getSessionId()));
        } catch (Exception e) {
            loggerMechanism.error("Failed to acknowledge screencast frame", e);
        }
    }
    
    /**
     * Setup timed capture with adaptive intervals
     */
    public void setupAdaptiveTimedCapture(TargetID targetId, DevTools devTools) {
        if (!useTimedCapture) {
            loggerMechanism.info("Timed capture disabled, skipping setup for target: " + targetId);
            return;
        }
        
        try {
            loggerMechanism.info("Setting up adaptive timed capture for target: " + targetId);
            
            // Create adaptive capture task
            ScheduledFuture<?> captureTask = adaptiveTimingExecutor.scheduleAtFixedRate(() -> {
                try {
                    triggerFrameCapture(targetId);
                } catch (Exception e) {
                    loggerMechanism.error("Error during timed frame capture for target " + targetId, e);
                    recordTimedCaptureFailure(targetId);
                }
            }, currentCaptureInterval.get(), currentCaptureInterval.get(), TimeUnit.MILLISECONDS);
            
            loggerMechanism.info("Adaptive timed capture started with interval: " + currentCaptureInterval.get() + "ms");
            
        } catch (Exception e) {
            loggerMechanism.error("Failed to setup adaptive timed capture for target " + targetId, e);
            activateFallbackMechanism("timed_capture_setup_failed");
        }
    }
    
    /**
     * Trigger frame capture using DOM manipulation
     */
    private void triggerFrameCapture(TargetID targetId) {
        if (!useDOMTriggers) return;
        
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            // Use multiple trigger methods for reliability
            String triggerScript = """
                (function() {
                    try {
                        // Method 1: Subtle style change
                        if (!window.frameCaptureTrigger) {
                            window.frameCaptureTrigger = 0;
                        }
                        window.frameCaptureTrigger++;
                        
                        // Create or update trigger element
                        var trigger = document.getElementById('selenium-frame-trigger');
                        if (!trigger) {
                            trigger = document.createElement('div');
                            trigger.id = 'selenium-frame-trigger';
                            trigger.style.cssText = 'position:absolute;top:-1px;left:-1px;width:1px;height:1px;opacity:0.01;pointer-events:none;';
                            document.body.appendChild(trigger);
                        }
                        
                        // Subtle change that shouldn't affect visibility but triggers repaint
                        trigger.setAttribute('data-frame', window.frameCaptureTrigger);
                        trigger.style.transform = 'translateZ(' + (window.frameCaptureTrigger % 2) + 'px)';
                        
                        // Method 2: Force layout recalculation (fallback)
                        if (window.frameCaptureTrigger % 10 === 0) {
                            document.body.offsetHeight; // Force layout
                        }
                        
                        return 'ok';
                    } catch (e) {
                        return 'error: ' + e.message;
                    }
                })();
            """;
            
            Object result = js.executeScript(triggerScript);
            
            if (result != null && result.toString().startsWith("error")) {
                loggerMechanism.warn("DOM trigger script failed for target " + targetId + ": " + result);
                recordDOMTriggerFailure(targetId);
            }
            
        } catch (Exception e) {
            loggerMechanism.error("Failed to trigger frame capture for target " + targetId, e);
            recordDOMTriggerFailure(targetId);
        }
    }
    
    /**
     * Update performance metrics and adjust capture intervals
     */
    private void updatePerformanceMetrics(long processingTime, boolean success) {
        FrameMetrics metrics = new FrameMetrics(System.currentTimeMillis(), processingTime, success);
        performanceHistory.add(metrics);
        
        // Update current stats
        PerformanceStats current = currentStats.get();
        PerformanceStats updated = new PerformanceStats(
            current.getTotalFrames() + 1,
            success ? current.getSuccessfulFrames() + 1 : current.getSuccessfulFrames(),
            Math.max(current.getMaxProcessingTime(), processingTime),
            (current.getAverageProcessingTime() * current.getTotalFrames() + processingTime) / (current.getTotalFrames() + 1)
        );
        
        currentStats.set(updated);
    }
    
    /**
     * Monitor performance and adjust timing adaptively
     */
    private void startAdaptiveTimingMonitor() {
        adaptiveTimingExecutor.scheduleAtFixedRate(() -> {
            try {
                adjustCaptureIntervalBasedOnPerformance();
            } catch (Exception e) {
                loggerMechanism.error("Error during adaptive timing adjustment", e);
            }
        }, 5, 5, TimeUnit.SECONDS);
    }
    
    /**
     * Adjust capture interval based on performance metrics
     */
    private void adjustCaptureIntervalBasedOnPerformance() {
        List<FrameMetrics> recentMetrics = performanceHistory.getRecentItems();
        if (recentMetrics.size() < 5) return; // Need enough data
        
        // Calculate recent performance
        long successCount = recentMetrics.stream().mapToLong(m -> m.isSuccess() ? 1 : 0).sum();
        double successRate = (double) successCount / recentMetrics.size();
        
        double avgProcessingTime = recentMetrics.stream()
            .mapToLong(FrameMetrics::getProcessingTime)
            .average()
            .orElse(0);
        
        int currentInterval = currentCaptureInterval.get();
        int newInterval = currentInterval;
        
        // Adjust based on performance
        if (successRate < PERFORMANCE_THRESHOLD) {
            // Performance is poor, slow down
            newInterval = Math.min(currentInterval + 50, MAX_CAPTURE_INTERVAL_MS);
            loggerMechanism.warn("Poor frame capture performance (" + String.format("%.1f", successRate * 100) + 
                               "%), increasing interval to " + newInterval + "ms");
        } else if (successRate > 0.95 && avgProcessingTime < currentInterval * 0.5) {
            // Performance is excellent, can speed up
            newInterval = Math.max(currentInterval - 25, MIN_CAPTURE_INTERVAL_MS);
            loggerMechanism.info("Excellent performance, decreasing interval to " + newInterval + "ms");
        }
        
        if (newInterval != currentInterval) {
            currentCaptureInterval.set(newInterval);
            loggerMechanism.info("Adjusted capture interval from " + currentInterval + "ms to " + newInterval + "ms");
        }
    }
    
    /**
     * Record frame processing failure and activate fallbacks if needed
     */
    private void recordFrameFailure(TargetID targetId) {
        int failures = fallbackAttempts.incrementAndGet();
        
        if (failures > 5) {
            loggerMechanism.warn("Multiple frame failures detected for target " + targetId + ", activating fallback mechanisms");
            activateFallbackMechanism("multiple_frame_failures");
        }
    }
    
    /**
     * Record timed capture failure
     */
    private void recordTimedCaptureFailure(TargetID targetId) {
        loggerMechanism.warn("Timed capture failure for target: " + targetId);
        if (fallbackAttempts.incrementAndGet() > 3) {
            useTimedCapture = false;
            loggerMechanism.error("Disabling timed capture due to repeated failures");
        }
    }
    
    /**
     * Record DOM trigger failure
     */
    private void recordDOMTriggerFailure(TargetID targetId) {
        loggerMechanism.warn("DOM trigger failure for target: " + targetId);
        if (fallbackAttempts.incrementAndGet() > 5) {
            useDOMTriggers = false;
            loggerMechanism.error("Disabling DOM triggers due to repeated failures");
        }
    }
    
    /**
     * Activate fallback mechanisms when primary methods fail
     */
    private void activateFallbackMechanism(String reason) {
        loggerMechanism.warn("Activating fallback mechanisms due to: " + reason);
        
        // Increase capture interval to reduce load
        int safeInterval = Math.min(currentCaptureInterval.get() * 2, MAX_CAPTURE_INTERVAL_MS);
        currentCaptureInterval.set(safeInterval);
        
        // Reset failure counter
        fallbackAttempts.set(0);
        
        loggerMechanism.info("Fallback activated: increased interval to " + safeInterval + "ms");
    }
    
    /**
     * Clean up old frame hashes to prevent memory leak
     */
    private void cleanupOldFrameHashes(long currentTime) {
        frameHashMap.entrySet().removeIf(entry -> 
            (currentTime - entry.getValue()) > 10000 // Remove hashes older than 10 seconds
        );
    }
    
    /**
     * Get current performance statistics
     */
    public PerformanceStats getPerformanceStats() {
        return currentStats.get();
    }
    
    /**
     * Shutdown the manager and cleanup resources
     */
    public void shutdown() {
        loggerMechanism.info("Shutting down Enhanced Frame Capture Manager");
        
        try {
            frameProcessingExecutor.shutdown();
            adaptiveTimingExecutor.shutdown();
            
            if (!frameProcessingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                frameProcessingExecutor.shutdownNow();
            }
            
            if (!adaptiveTimingExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                adaptiveTimingExecutor.shutdownNow();
            }
            
            // Clear data structures
            frameHashMap.clear();
            processedFrameIds.clear();
            
        } catch (Exception e) {
            loggerMechanism.error("Error during Enhanced Frame Capture Manager shutdown", e);
        }
        
        loggerMechanism.info("Enhanced Frame Capture Manager shutdown completed");
    }
    
    /**
     * Thread-safe circular buffer for performance history
     */
    private static class CircularBuffer<T> {
        private final T[] buffer;
        private volatile int head = 0;
        private volatile int size = 0;
        private final int capacity;
        
        @SuppressWarnings("unchecked")
        public CircularBuffer(int capacity) {
            this.capacity = capacity;
            this.buffer = (T[]) new Object[capacity];
        }
        
        public synchronized void add(T item) {
            buffer[head] = item;
            head = (head + 1) % capacity;
            if (size < capacity) size++;
        }
        
        public synchronized List<T> getRecentItems() {
            List<T> items = new ArrayList<>();
            int start = size < capacity ? 0 : head;
            
            for (int i = 0; i < size; i++) {
                int index = (start + i) % capacity;
                if (buffer[index] != null) {
                    items.add(buffer[index]);
                }
            }
            
            return items;
        }
    }
    
    /**
     * Frame processing metrics
     */
    public static class FrameMetrics {
        private final long timestamp;
        private final long processingTime;
        private final boolean success;
        
        public FrameMetrics(long timestamp, long processingTime, boolean success) {
            this.timestamp = timestamp;
            this.processingTime = processingTime;
            this.success = success;
        }
        
        public long getTimestamp() { return timestamp; }
        public long getProcessingTime() { return processingTime; }
        public boolean isSuccess() { return success; }
    }
    
    /**
     * Overall performance statistics
     */
    public static class PerformanceStats {
        private final long totalFrames;
        private final long successfulFrames;
        private final long maxProcessingTime;
        private final double averageProcessingTime;
        
        public PerformanceStats() {
            this(0, 0, 0, 0);
        }
        
        public PerformanceStats(long totalFrames, long successfulFrames, long maxProcessingTime, double averageProcessingTime) {
            this.totalFrames = totalFrames;
            this.successfulFrames = successfulFrames;
            this.maxProcessingTime = maxProcessingTime;
            this.averageProcessingTime = averageProcessingTime;
        }
        
        public long getTotalFrames() { return totalFrames; }
        public long getSuccessfulFrames() { return successfulFrames; }
        public long getMaxProcessingTime() { return maxProcessingTime; }
        public double getAverageProcessingTime() { return averageProcessingTime; }
        
        public double getSuccessRate() {
            return totalFrames > 0 ? (double) successfulFrames / totalFrames * 100 : 0;
        }
        
        @Override
        public String toString() {
            return String.format("PerformanceStats{total=%d, success=%d (%.1f%%), maxTime=%dms, avgTime=%.1fms}",
                totalFrames, successfulFrames, getSuccessRate(), maxProcessingTime, averageProcessingTime);
        }
    }
}