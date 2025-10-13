package com.example.automation.util;

import com.example.automation.logger.LoggerMechanism;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Adaptive frame timing system designed to address flickering issues on Linux headless Chrome.
 * 
 * Key features:
 * - Dynamic frame interval adjustment based on system performance
 * - Frame skip detection and compensation
 * - Linux-specific timing optimizations
 * - Load balancing for multi-tab recording scenarios
 * - Memory pressure awareness
 */
public class AdaptiveFrameTiming {
    
    private static final long DEFAULT_FRAME_INTERVAL_MS = 33; // ~30 fps
    private static final long MIN_FRAME_INTERVAL_MS = 16;     // ~60 fps max
    private static final long MAX_FRAME_INTERVAL_MS = 100;    // ~10 fps min
    private static final int TIMING_HISTORY_SIZE = 50;
    
    private final LoggerMechanism logger;
    private final Queue<Long> frameTimings;
    private final Queue<Long> captureDelays;
    private final AtomicLong currentFrameInterval;
    private final AtomicInteger consecutiveSlowFrames;
    private final AtomicInteger totalFramesCaptured;
    private final AtomicInteger framesSkipped;
    private final String instanceId;
    private final boolean isLinuxEnvironment;
    
    private volatile Instant lastFrameTime;
    private volatile boolean isRecording;
    private volatile long averageFrameTime;
    private volatile double systemLoadFactor = 1.0;
    
    public AdaptiveFrameTiming(LoggerMechanism logger, String instanceId) {
        this.logger = logger;
        this.instanceId = instanceId;
        this.frameTimings = new ConcurrentLinkedQueue<>();
        this.captureDelays = new ConcurrentLinkedQueue<>();
        this.currentFrameInterval = new AtomicLong(DEFAULT_FRAME_INTERVAL_MS);
        this.consecutiveSlowFrames = new AtomicInteger(0);
        this.totalFramesCaptured = new AtomicInteger(0);
        this.framesSkipped = new AtomicInteger(0);
        this.lastFrameTime = Instant.now();
        this.isLinuxEnvironment = System.getProperty("os.name").toLowerCase().contains("linux");
        this.isRecording = false;
        this.averageFrameTime = DEFAULT_FRAME_INTERVAL_MS;
        
        if (isLinuxEnvironment) {
            logger.info(instanceId + ": Adaptive frame timing initialized for Linux environment");
        } else {
            logger.info(instanceId + ": Adaptive frame timing initialized for " + System.getProperty("os.name"));
        }
    }
    
    public void startRecording() {
        isRecording = true;
        lastFrameTime = Instant.now();
        resetMetrics();
        logger.info(instanceId + ": Frame timing started");
    }
    
    public void stopRecording() {
        isRecording = false;
        logger.info(instanceId + ": Frame timing stopped");
        logFinalMetrics();
    }
    
    /**
     * Wait for the next frame capture based on adaptive timing
     */
    public void waitForNextFrame() {
        if (!isRecording) return;
        
        Instant now = Instant.now();
        long elapsedMs = Duration.between(lastFrameTime, now).toMillis();
        long targetInterval = currentFrameInterval.get();
        
        // Record actual frame timing
        recordFrameTiming(elapsedMs);
        
        // Calculate how long to wait
        long waitTime = Math.max(0, targetInterval - elapsedMs);
        
        if (waitTime > 0) {
            try {
                if (isLinuxEnvironment) {
                    // Linux-specific sleep optimization
                    linuxOptimizedSleep(waitTime);
                } else {
                    Thread.sleep(waitTime);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn(instanceId + ": Frame timing interrupted");
                return;
            }
        }
        
        // Update timing metrics
        lastFrameTime = Instant.now();
        totalFramesCaptured.incrementAndGet();
        
        // Adjust frame interval based on performance
        adjustFrameInterval(elapsedMs);
    }
    
    /**
     * Linux-optimized sleep that accounts for Linux scheduler behavior
     */
    private void linuxOptimizedSleep(long waitTimeMs) throws InterruptedException {
        if (waitTimeMs <= 1) {
            // For very short waits, use yield to avoid scheduler overhead
            Thread.yield();
            return;
        }
        
        // For Linux, break up longer sleeps to account for scheduler granularity
        if (waitTimeMs > 10) {
            // Sleep most of the time
            Thread.sleep(waitTimeMs - 2);
            // Use busy wait for the last few milliseconds for precision
            long endTime = System.currentTimeMillis() + 2;
            while (System.currentTimeMillis() < endTime) {
                Thread.yield();
            }
        } else {
            Thread.sleep(waitTimeMs);
        }
    }
    
    /**
     * Record frame capture delay for analysis
     */
    public void recordCaptureDelay(long delayMs) {
        captureDelays.offer(delayMs);
        if (captureDelays.size() > TIMING_HISTORY_SIZE) {
            captureDelays.poll();
        }
        
        // If capture is taking too long, we might need to skip frames
        if (delayMs > currentFrameInterval.get() * 2) {
            framesSkipped.incrementAndGet();
            logger.debug(instanceId + ": Long capture delay detected: " + delayMs + "ms");
        }
    }
    
    private void recordFrameTiming(long actualIntervalMs) {
        frameTimings.offer(actualIntervalMs);
        if (frameTimings.size() > TIMING_HISTORY_SIZE) {
            frameTimings.poll();
        }
        
        // Calculate rolling average
        OptionalDouble avg = frameTimings.stream().mapToLong(Long::longValue).average();
        if (avg.isPresent()) {
            averageFrameTime = Math.round(avg.getAsDouble());
        }
    }
    
    private void adjustFrameInterval(long actualIntervalMs) {
        long currentInterval = currentFrameInterval.get();
        long targetInterval = currentInterval;
        
        // Check if we're consistently running slow
        if (actualIntervalMs > currentInterval * 1.2) {
            int slowFrames = consecutiveSlowFrames.incrementAndGet();
            if (slowFrames > 5) {
                // Increase frame interval (reduce frame rate) to maintain stability
                targetInterval = Math.min(MAX_FRAME_INTERVAL_MS, currentInterval + 5);
                logger.debug(instanceId + ": Increasing frame interval to " + targetInterval + "ms due to slow performance");
            }
        } else {
            consecutiveSlowFrames.set(0);
            
            // Check if we can improve frame rate
            if (actualIntervalMs < currentInterval * 0.8 && getCurrentSystemLoad() < 0.7) {
                targetInterval = Math.max(MIN_FRAME_INTERVAL_MS, currentInterval - 2);
                logger.debug(instanceId + ": Decreasing frame interval to " + targetInterval + "ms due to good performance");
            }
        }
        
        // Apply Linux-specific adjustments
        if (isLinuxEnvironment) {
            targetInterval = applyLinuxTimingAdjustments(targetInterval);
        }
        
        currentFrameInterval.set(targetInterval);
    }
    
    private long applyLinuxTimingAdjustments(long baseInterval) {
        // Linux scheduler typically has 4ms granularity for sleeps
        // Round to multiples of 4ms for better consistency
        long adjustedInterval = ((baseInterval + 2) / 4) * 4;
        
        // Account for system load on Linux
        if (systemLoadFactor > 1.5) {
            adjustedInterval = Math.min(MAX_FRAME_INTERVAL_MS, adjustedInterval + 8);
        }
        
        return Math.max(MIN_FRAME_INTERVAL_MS, adjustedInterval);
    }
    
    private double getCurrentSystemLoad() {
        try {
            // Simple heuristic based on capture delays and frame timing consistency
            double avgCaptureDelay = captureDelays.stream().mapToLong(Long::longValue).average().orElse(0.0);
            double timingVariance = calculateTimingVariance();
            
            // Normalize load factor between 0.0 and 2.0
            systemLoadFactor = Math.min(2.0, (avgCaptureDelay / 50.0) + (timingVariance / 20.0));
            
            return systemLoadFactor;
        } catch (Exception e) {
            logger.warn(instanceId + ": Error calculating system load: " + e.getMessage());
            return 1.0; // Default moderate load
        }
    }
    
    private double calculateTimingVariance() {
        if (frameTimings.size() < 5) return 0.0;
        
        double mean = frameTimings.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double variance = frameTimings.stream()
            .mapToDouble(timing -> Math.pow(timing - mean, 2))
            .average().orElse(0.0);
        
        return Math.sqrt(variance); // Standard deviation
    }
    
    /**
     * Get current frame rate in FPS
     */
    public double getCurrentFrameRate() {
        return 1000.0 / currentFrameInterval.get();
    }
    
    /**
     * Get the recommended frame interval for external components
     */
    public long getRecommendedFrameInterval() {
        return currentFrameInterval.get();
    }
    
    /**
     * Check if the timing system is performing well
     */
    public boolean isPerformingWell() {
        if (totalFramesCaptured.get() < 10) return true; // Not enough data
        
        double skipRate = (double) framesSkipped.get() / totalFramesCaptured.get();
        double avgDelay = captureDelays.stream().mapToLong(Long::longValue).average().orElse(0.0);
        
        return skipRate < 0.05 && avgDelay < currentFrameInterval.get() * 1.5;
    }
    
    /**
     * Get comprehensive timing metrics
     */
    public TimingMetrics getMetrics() {
        return new TimingMetrics(
            totalFramesCaptured.get(),
            framesSkipped.get(),
            getCurrentFrameRate(),
            averageFrameTime,
            systemLoadFactor,
            captureDelays.stream().mapToLong(Long::longValue).average().orElse(0.0),
            calculateTimingVariance(),
            isPerformingWell()
        );
    }
    
    private void resetMetrics() {
        frameTimings.clear();
        captureDelays.clear();
        totalFramesCaptured.set(0);
        framesSkipped.set(0);
        consecutiveSlowFrames.set(0);
        currentFrameInterval.set(DEFAULT_FRAME_INTERVAL_MS);
        systemLoadFactor = 1.0;
        averageFrameTime = DEFAULT_FRAME_INTERVAL_MS;
    }
    
    private void logFinalMetrics() {
        TimingMetrics metrics = getMetrics();
        logger.info(instanceId + " Final Timing Metrics:");
        logger.info("  Total Frames: " + metrics.totalFrames);
        logger.info("  Frames Skipped: " + metrics.framesSkipped + " (" + 
                   String.format("%.2f%%", (double) metrics.framesSkipped / metrics.totalFrames * 100) + ")");
        logger.info("  Final Frame Rate: " + String.format("%.1f", metrics.frameRate) + " fps");
        logger.info("  Average Frame Time: " + metrics.averageFrameTime + "ms");
        logger.info("  System Load Factor: " + String.format("%.2f", metrics.systemLoad));
        logger.info("  Average Capture Delay: " + String.format("%.1f", metrics.averageCaptureDelay) + "ms");
        logger.info("  Timing Variance: " + String.format("%.1f", metrics.timingVariance) + "ms");
        logger.info("  Performance Status: " + (metrics.isPerformingWell ? "GOOD" : "DEGRADED"));
    }
    
    /**
     * Data class for timing metrics
     */
    public static class TimingMetrics {
        public final int totalFrames;
        public final int framesSkipped;
        public final double frameRate;
        public final long averageFrameTime;
        public final double systemLoad;
        public final double averageCaptureDelay;
        public final double timingVariance;
        public final boolean isPerformingWell;
        
        public TimingMetrics(int totalFrames, int framesSkipped, double frameRate, 
                           long averageFrameTime, double systemLoad, double averageCaptureDelay,
                           double timingVariance, boolean isPerformingWell) {
            this.totalFrames = totalFrames;
            this.framesSkipped = framesSkipped;
            this.frameRate = frameRate;
            this.averageFrameTime = averageFrameTime;
            this.systemLoad = systemLoad;
            this.averageCaptureDelay = averageCaptureDelay;
            this.timingVariance = timingVariance;
            this.isPerformingWell = isPerformingWell;
        }
    }
}