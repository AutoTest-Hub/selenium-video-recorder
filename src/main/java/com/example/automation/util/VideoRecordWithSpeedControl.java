package com.example.automation.util;

import com.example.automation.logger.LoggerMechanism;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v137.page.Page;
import org.openqa.selenium.devtools.v137.page.model.ScreencastFrame;
import org.openqa.selenium.devtools.v137.target.Target;
import org.openqa.selenium.devtools.v137.target.model.TargetID;
import org.openqa.selenium.devtools.v137.target.model.TargetInfo;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.Map;

import static com.example.automation.util.TestBase.*;

/**
 * ENHANCED Selenium video recording with configurable playback speed.
 * 
 * This version fixes the "fast-forward video" issue by providing configurable
 * video speeds and better frame timing control.
 * 
 * Key Features:
 * - Multi-tab video recording with automatic tab switching
 * - Configurable video playback speeds (slow motion, real-time, fast)
 * - Deadlock-free implementation for headless Chrome
 * - DevTools v137 compatibility
 * - Cross-platform support (Windows, macOS, Linux)
 * - Timed frame capture for consistent video pacing
 * 
 * Usage:
 * <pre>
 * VideoRecordWithSpeedControl recorder = new VideoRecordWithSpeedControl(logger, driver);
 * recorder.setVideoSpeed(VideoSpeed.SLOW_MOTION); // 2x slower - fixes fast-forward
 * recorder.startRecording();
 * // ... perform test actions with Thread.sleep() ...
 * recorder.stopRecordingAndGenerateVideo();
 * recorder.cleanup();
 * </pre>
 */
public class VideoRecordWithSpeedControl {

    /**
     * Video speed configuration options.
     * Each speed setting controls the final video playback rate.
     */
    public enum VideoSpeed {
        REAL_TIME(5.0, "Real-time playback"),
        SLOW_MOTION(2.0, "2x slower than real-time"), 
        VERY_SLOW(1.0, "4x slower than real-time"),
        FAST(10.0, "2x faster than real-time");
        
        private final double frameRate;
        private final String description;
        
        VideoSpeed(double frameRate, String description) {
            this.frameRate = frameRate;
            this.description = description;
        }
        
        public double getFrameRate() { return frameRate; }
        public String getDescription() { return description; }
    }

    private static final Path FRAME_DIR = Paths.get("frames");
    private static final String FRAME_PATTERN = "frame_*.png";
    private static final Path OUTPUT_DIR = Paths.get("videos");

    private static String FFMPEG_PATH = "";
    
    // Configuration
    private VideoSpeed videoSpeed = VideoSpeed.SLOW_MOTION; // Default to 2x slower
    private int captureIntervalMs = 400; // For timed capture
    
    // Main DevTools connection for target discovery ONLY
    private static DevTools mainDevTools = null;
    
    // Map to store COMPLETELY separate DevTools instances for each target
    private final Map<TargetID, DevTools> targetDevToolsMap = new ConcurrentHashMap<>();
    
    // Map to store frame listeners for each target
    private final Map<TargetID, java.util.function.Consumer<ScreencastFrame>> targetListeners = new ConcurrentHashMap<>();

    // ExecutorService to handle auto-rebind on proper thread (fixes deadlock)
    private final ExecutorService autoRebindExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AutoRebind-Thread");
        t.setDaemon(true);
        return t;
    });

    // Scheduled executor for timed frame capture
    private ScheduledExecutorService frameTimer;

    private final LoggerMechanism loggerMechanism;
    private final WebDriver driver;
    private final AtomicInteger frameCounter = new AtomicInteger(0);

    // Track the most recently created PAGE target id
    private final AtomicReference<TargetID> lastCreatedPageTargetId = new AtomicReference<>(null);

    // Track which page target we are CURRENTLY recording
    private final AtomicReference<TargetID> currentRecordedTargetId = new AtomicReference<>(null);

    // Auto-rebind toggle
    private volatile boolean autoRebindEnabled = false;

    public VideoRecordWithSpeedControl(LoggerMechanism loggerMechanism, WebDriver driver) {
        this.loggerMechanism = loggerMechanism;
        this.driver = driver;

        // Initialize main DevTools connection ONLY for target discovery
        mainDevTools = ((HasDevTools) driver).getDevTools();
        mainDevTools.createSession();

        // Discover targets so we can see tab creation + opener relations
        mainDevTools.send(Target.setDiscoverTargets(true, Optional.empty()));
        
        // Event listener for target creation
        mainDevTools.addListener(Target.targetCreated(), targetInfo -> {
            loggerMechanism.info("Target created: " + targetInfo.getTargetId() + " type: " + targetInfo.getType());
            
            if (!"page".equalsIgnoreCase(targetInfo.getType())) return;

            TargetID newTargetId = targetInfo.getTargetId();
            lastCreatedPageTargetId.set(newTargetId);
            
            loggerMechanism.info("New page target detected: " + newTargetId);

            // Auto-rebind logic with proper threading (CRITICAL FIX for deadlock)
            if (autoRebindEnabled) {
                TargetID current = currentRecordedTargetId.get();
                loggerMechanism.info("Auto-rebind enabled. Current target: " + current + ", New target: " + newTargetId);
                
                if (current != null && !current.equals(newTargetId)) {
                    loggerMechanism.info("Scheduling auto-rebind from " + current + " to " + newTargetId);
                    
                    // CRITICAL: Execute on separate thread to avoid deadlock
                    autoRebindExecutor.submit(() -> {
                        try {
                            loggerMechanism.info("Executing auto-rebind from " + current + " to " + newTargetId);
                            Thread.sleep(1000); // Allow new tab to stabilize
                            stopRecordingOnTarget(current);
                            bindAndStart(newTargetId);
                            loggerMechanism.info("Auto-rebind completed successfully");
                        } catch (Exception e) {
                            loggerMechanism.error("Auto-rebind failed: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
                }
            }
        });

        // Listen for target destruction to cleanup resources
        mainDevTools.addListener(Target.targetDestroyed(), targetId -> {
            loggerMechanism.info("Target destroyed: " + targetId);
            cleanupTarget(targetId);
        });
    }

    // Configuration methods
    public void setVideoSpeed(VideoSpeed speed) {
        this.videoSpeed = speed;
        loggerMechanism.info("Video speed set to: " + speed.getDescription());
    }

    public void setCaptureInterval(int intervalMs) {
        this.captureIntervalMs = intervalMs;
        loggerMechanism.info("Capture interval set to: " + intervalMs + "ms");
    }

    /** Enable/disable automatic rebind when the recorded tab opens a new tab. */
    public void setAutoRebindEnabled(boolean enabled) {
        this.autoRebindEnabled = enabled;
        loggerMechanism.info("Auto-rebind " + (enabled ? "enabled" : "disabled"));
    }

    /** Start recording the CURRENT tab (call once per test). */
    public void startRecording() throws IOException {
        Files.createDirectories(FRAME_DIR);
        TargetID currentTargetId = getAnyTopPageTargetId();
        loggerMechanism.info("Starting recording on initial target: " + currentTargetId);
        loggerMechanism.info("Recording configuration:");
        loggerMechanism.info("  - Video speed: " + videoSpeed.getDescription());
        loggerMechanism.info("  - Target frame rate: " + videoSpeed.getFrameRate() + " FPS");
        loggerMechanism.info("  - Capture interval: " + captureIntervalMs + "ms");
        bindAndStart(currentTargetId);
    }

    /** Manual rebind for the most recently created tab. */
    public void recordNewlyOpenedTab() {
        TargetID targetId = lastCreatedPageTargetId.get();
        loggerMechanism.info("Manual rebind requested. Last created target: " + targetId);
        
        if (targetId == null) {
            throw new IllegalStateException("No newly created page targetId captured. Did you call window.open() first?");
        }
        
        TargetID currentTarget = currentRecordedTargetId.get();
        if (currentTarget != null) {
            loggerMechanism.info("Manually switching recording from " + currentTarget + " to " + targetId);
            stopRecordingOnTarget(currentTarget);
        }
        
        loggerMechanism.info("Starting recording on new target: " + targetId);
        bindAndStart(targetId);
    }

    /**
     * CRITICAL METHOD: Creates truly separate DevTools instances for each tab.
     * This is the core fix for multi-tab recording in headless Chrome.
     */
    private void bindAndStart(TargetID targetId) {
        try {
            loggerMechanism.info("Creating new DevTools instance for target: " + targetId);
            
            // CRITICAL: Create completely separate DevTools instance for this target
            DevTools targetDevTools = ((HasDevTools) driver).getDevTools();
            targetDevTools.createSession(targetId.toString());
            targetDevToolsMap.put(targetId, targetDevTools);
            
            loggerMechanism.info("Created DevTools session for target: " + targetId);
            
            // Enable Page domain for this specific target
            targetDevTools.send(Page.enable(Optional.empty()));
            loggerMechanism.info("Enabled Page domain for target: " + targetId);

            // Create frame listener with simple sequential naming (fixes FFmpeg pattern issue)
            java.util.function.Consumer<ScreencastFrame> frameListener = frame -> {
                try {
                    byte[] decodedBytes = Base64.getDecoder().decode(frame.getData());
                    BufferedImage image = ImageIO.read(new ByteArrayInputStream(decodedBytes));
                    if (image != null) {
                        // Simple sequential naming for FFmpeg compatibility
                        String filename = String.format("frame_%05d.png", frameCounter.incrementAndGet());
                        Path outputFile = FRAME_DIR.resolve(filename);
                        ImageIO.write(image, "png", outputFile.toFile());
                        loggerMechanism.info("Captured frame " + frameCounter.get() + " from target: " + targetId);
                    }
                    
                    // CRITICAL: Acknowledge frame on the correct DevTools session
                    targetDevTools.send(Page.screencastFrameAck(frame.getSessionId()));
                    
                } catch (Exception e) {
                    loggerMechanism.error("Error processing screencast frame for target " + targetId + ": " + e.getMessage());
                    e.printStackTrace();
                }
            };
            
            targetListeners.put(targetId, frameListener);
            targetDevTools.addListener(Page.screencastFrame(), frameListener);
            loggerMechanism.info("Added frame listener for target: " + targetId);

            // Start screencast on this specific target
            targetDevTools.send(Page.startScreencast(
                    Optional.of(Page.StartScreencastFormat.PNG),
                    Optional.empty(),
                    Optional.of(1280),
                    Optional.of(780),
                    Optional.empty()
            ));

            // Set up timed capture for better frame consistency (fixes timing gaps)
            setupTimedCapture(targetId);

            currentRecordedTargetId.set(targetId);
            loggerMechanism.info("Successfully started recording on target: " + targetId);
            
        } catch (Exception e) {
            loggerMechanism.error("Failed to start recording on target " + targetId + ": " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to start recording on target " + targetId, e);
        }
    }

    /**
     * Set up timed frame capture to ensure consistent video pacing.
     * This helps capture frames during Thread.sleep() periods.
     */
    private void setupTimedCapture(TargetID targetId) {
        if (frameTimer != null) {
            frameTimer.shutdown();
        }
        
        frameTimer = Executors.newScheduledThreadPool(1);
        loggerMechanism.info("Setting up timed capture every " + captureIntervalMs + "ms for target: " + targetId);
        
        frameTimer.scheduleAtFixedRate(() -> {
            try {
                // Force a subtle DOM change to trigger frame capture
                if (driver instanceof JavascriptExecutor && targetId.equals(currentRecordedTargetId.get())) {
                    ((JavascriptExecutor) driver).executeScript(
                        "if (typeof window._frameCaptureTrigger === 'undefined') window._frameCaptureTrigger = 0; " +
                        "window._frameCaptureTrigger++; " +
                        "document.body.setAttribute('data-frame-trigger', window._frameCaptureTrigger);"
                    );
                }
            } catch (Exception e) {
                // Ignore errors in timed capture
            }
        }, 0, captureIntervalMs, TimeUnit.MILLISECONDS);
    }

    private void stopRecordingOnTarget(TargetID targetId) {
        try {
            loggerMechanism.info("Stopping recording on target: " + targetId);
            
            DevTools targetDevTools = targetDevToolsMap.get(targetId);
            if (targetDevTools != null) {
                try {
                    targetDevTools.send(Page.stopScreencast());
                    loggerMechanism.info("Stopped screencast for target: " + targetId);
                } catch (Exception e) {
                    loggerMechanism.error("Error stopping screencast for target " + targetId + ": " + e.getMessage());
                }
                
                try {
                    targetDevTools.clearListeners();
                    loggerMechanism.info("Cleared listeners for target: " + targetId);
                } catch (Exception e) {
                    loggerMechanism.error("Error clearing listeners for target " + targetId + ": " + e.getMessage());
                }
            }
            
            if (targetId.equals(currentRecordedTargetId.get())) {
                currentRecordedTargetId.set(null);
                loggerMechanism.info("Cleared current recorded target");
            }
            
        } catch (Exception e) {
            loggerMechanism.error("Error stopping recording on target " + targetId + ": " + e.getMessage());
        }
    }

    private void cleanupTarget(TargetID targetId) {
        try {
            stopRecordingOnTarget(targetId);
            
            DevTools targetDevTools = targetDevToolsMap.remove(targetId);
            targetListeners.remove(targetId);
            
            if (targetDevTools != null) {
                try {
                    targetDevTools.clearListeners();
                } catch (Exception e) {
                    loggerMechanism.error("Error clearing listeners during cleanup for target " + targetId + ": " + e.getMessage());
                }
            }
            
            loggerMechanism.info("Cleaned up resources for target: " + targetId);
            
        } catch (Exception e) {
            loggerMechanism.error("Error cleaning up target " + targetId + ": " + e.getMessage());
        }
    }

    /** Stop recording and build MP4 with configurable video speed. */
    public void stopRecordingAndGenerateVideo() throws Exception {
        try {
            if (mainDevTools != null) {
                mainDevTools.send(Page.stopScreencast());
            }
        } catch (Exception ignored) {
            // Ignore errors when stopping screencast
        }

        // Stop timed capture
        if (frameTimer != null) {
            frameTimer.shutdown();
        }

        FFMPEG_PATH = TestBase.getFFmpegPath();
        System.out.println("🔸 ffmpeg path: " + FFMPEG_PATH);
        System.out.println("🔸 Operating system: " + System.getProperty("os.name"));
        System.out.println("🎬 Video configuration:");
        System.out.println("   - Speed: " + videoSpeed.getDescription());
        System.out.println("   - Target FPS: " + videoSpeed.getFrameRate());

        evenCropFrames(FRAME_DIR, FRAME_PATTERN);

        Path firstFrame = FRAME_DIR.resolve("frame_00001.png");
        for (int i = 0; i < 50; i++) {
            if (Files.exists(firstFrame)) break;
            Thread.sleep(100);
        }
        if (!Files.exists(firstFrame)) {
            System.out.println("❌ No frames saved. Video generation aborted.");
            throw new IllegalStateException("❌ No frames saved. Video generation aborted.");
        }

        Files.createDirectories(OUTPUT_DIR);
        Path videoPath = generateOutputVideoPath(OUTPUT_DIR);

        try {
            String inputPattern = FRAME_DIR.resolve("frame_%05d.png").toAbsolutePath().toString();
            String outputPath = videoPath.toAbsolutePath().toString();
            
            inputPattern = normalizePath(inputPattern);
            outputPath = normalizePath(outputPath);
            
            System.out.println("🔸 Input pattern: " + inputPattern);
            System.out.println("🔸 Output path: " + outputPath);
            System.out.println("🔸 Frame rate: " + videoSpeed.getFrameRate() + " FPS");
            
            ProcessBuilder pb = new ProcessBuilder(
                    FFMPEG_PATH, "-y",
                    "-framerate", String.valueOf(videoSpeed.getFrameRate()),
                    "-i", inputPattern,
                    "-vf", "pad=ceil(iw/2)*2:ceil(ih/2)*2,format=yuv420p",
                    "-c:v", "libx264",
                    "-pix_fmt", "yuv420p",
                    outputPath
            ).redirectErrorStream(true);

            pb.directory(new java.io.File(System.getProperty("user.dir")));
            
            System.out.println("🔸 FFmpeg command: " + String.join(" ", pb.command()));
            
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                reader.lines().forEach(System.out::println);
            }
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IllegalStateException("❌ FFmpeg failed with exit code " + exit + " – check output above.");
            }

            // Cleanup frames
            if (Files.exists(FRAME_DIR) && Files.isDirectory(FRAME_DIR)) {
                try (Stream<Path> paths = Files.list(FRAME_DIR)) {
                    paths.forEach(p -> {
                        try { 
                            Files.deleteIfExists(p); 
                        } catch (IOException e) { 
                            System.err.println("Warning: Could not delete frame file " + p + ": " + e.getMessage());
                        }
                    });
                }
            }
            
            if (Files.exists(videoPath)) {
                long fileSize = Files.size(videoPath);
                System.out.println("✅ Video saved to: " + outputPath);
                System.out.println("📊 Video file size: " + fileSize + " bytes");
                System.out.println("🎬 Video speed: " + videoSpeed.getDescription());
            } else {
                throw new IllegalStateException("❌ Video file was not created: " + outputPath);
            }

        } catch (Exception e) {
            System.out.println("❌ Video not saved due to: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    private static String normalizePath(String path) {
        if (path == null) return null;
        
        String os = System.getProperty("os.name").toLowerCase();
        
        if (os.contains("win")) {
            return path.replace("/", "\\");
        } else {
            return path.replace("\\", "/");
        }
    }

    public static void clearDEVTOOLS() {
        if (mainDevTools != null) {
            mainDevTools.clearListeners();
            mainDevTools = null;
        }
    }

    public void cleanup() {
        loggerMechanism.info("Starting cleanup of all video recording resources");
        
        if (frameTimer != null) {
            frameTimer.shutdown();
        }
        
        for (TargetID targetId : targetDevToolsMap.keySet()) {
            stopRecordingOnTarget(targetId);
        }
        
        for (DevTools devTools : targetDevToolsMap.values()) {
            try {
                devTools.clearListeners();
            } catch (Exception e) {
                loggerMechanism.error("Error clearing DevTools listeners: " + e.getMessage());
            }
        }
        
        targetDevToolsMap.clear();
        targetListeners.clear();
        autoRebindExecutor.shutdown();
        
        loggerMechanism.info("Cleaned up all video recording resources");
    }

    // Helper methods
    private TargetID getAnyTopPageTargetId() {
        var targets = mainDevTools.send(Target.getTargets(Optional.empty()));
        return targets.stream()
                .filter(t -> "page".equalsIgnoreCase(t.getType()))
                .map(t -> t.getTargetId())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No page target found"));
    }

    private static void evenCropFrames(Path dir, String glob) throws IOException {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, glob)) {
            for (Path p : ds) {
                BufferedImage img = ImageIO.read(p.toFile());
                int w = img.getWidth(), h = img.getHeight();
                int evenW = (w & 1) == 0 ? w : w - 1;
                int evenH = (h & 1) == 0 ? h : h - 1;
                if (evenW == w && evenH == h) continue;
                BufferedImage cropped = img.getSubimage(0, 0, evenW, evenH);
                ImageIO.write(cropped, "png", p.toFile());
                System.out.printf("🟢 Cropped %s → %dx%d%n", p.getFileName(), evenW, evenH);
            }
        }
    }

    private static Path generateOutputVideoPath(Path outputDir) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        return outputDir.resolve("test_run_" + ts + ".mp4");
    }

    public TargetID getCurrentRecordedTargetId() {
        return currentRecordedTargetId.get();
    }
    
    public java.util.Set<TargetID> getActiveTargets() {
        return targetDevToolsMap.keySet();
    }
}
