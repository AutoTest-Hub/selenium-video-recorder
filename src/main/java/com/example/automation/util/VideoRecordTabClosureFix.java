package com.example.automation.util;

import com.example.automation.logger.LoggerMechanism;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v137.network.Network;
import org.openqa.selenium.devtools.v137.page.Page;
import org.openqa.selenium.devtools.v137.page.model.ScreencastFrame;
import org.openqa.selenium.devtools.v137.target.Target;
import org.openqa.selenium.devtools.v137.target.model.TargetID;
import org.openqa.selenium.devtools.v137.target.model.TargetInfo;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Video Recording with Tab Closure Fix for Selenium in Headless Mode
 * 
 * This version specifically fixes the issues identified in URLCheck.0050:
 * 1. Flickering after tab closure and return to previous tab
 * 2. Missing video recording for subsequent tests (URLCheck.0060)
 * 
 * Key fixes:
 * - Enhanced tab closure detection and handling
 * - Robust DevTools session lifecycle management
 * - Session state validation and recovery
 * - Improved auto-rebind logic for tab closures
 * 
 * Test scenario this fixes:
 * URLCheck.0050: openNewTabWithURL (3 times) → closeCurrentTab (3 times) → return to original tab
 * URLCheck.0060: Should continue recording without issues
 */
public class VideoRecordTabClosureFix {
    
    private final LoggerMechanism logger;
    private final WebDriver driver;
    private final DevTools mainDevTools;
    
    // Enhanced session management to prevent flickering
    private final Map<String, DevTools> activeDevToolsSessions = new ConcurrentHashMap<>();
    private final Map<String, Boolean> sessionRecordingState = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> sessionFrameCount = new ConcurrentHashMap<>();
    
    // Current recording state
    private String currentRecordingTargetId;
    private boolean isRecording = false;
    private boolean autoRebindEnabled = true;
    
    // Frame management
    private final AtomicInteger frameCounter = new AtomicInteger(0);
    private static final Path FRAME_DIR = Paths.get("frames");
    private static final Path VIDEO_DIR = Paths.get("videos");
    
    // Thread management for async operations to prevent deadlocks
    private final ExecutorService autoRebindExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "VideoRecord-AutoRebind");
        t.setDaemon(true);
        return t;
    });
    
    private final ExecutorService sessionCleanupExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "VideoRecord-SessionCleanup");
        t.setDaemon(true);
        return t;
    });
    
    public VideoRecordTabClosureFix(LoggerMechanism logger, WebDriver driver) {
        this.logger = logger;
        this.driver = driver;
        this.mainDevTools = ((HasDevTools) driver).getDevTools();
        
        initializeDirectories();
        setupMainDevToolsSession();
        setupTabLifecycleListeners();
        
        logger.info("🔧 VideoRecordTabClosureFix initialized - ready to handle URLCheck.0050 scenario");
    }
    
    private void initializeDirectories() {
        try {
            Files.createDirectories(FRAME_DIR);
            Files.createDirectories(VIDEO_DIR);
        } catch (IOException e) {
            logger.error("Failed to create directories: " + e.getMessage());
        }
    }
    
    private void setupMainDevToolsSession() {
        try {
            mainDevTools.createSession();
            mainDevTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()));
            mainDevTools.send(Page.enable(Optional.empty()));
            
            logger.info("✅ Main DevTools session established successfully");
        } catch (Exception e) {
            logger.error("❌ Failed to setup main DevTools session: " + e.getMessage());
        }
    }
    
    /**
     * CRITICAL FIX: Enhanced tab lifecycle listeners that handle both creation and closure
     * This is the key fix for the URLCheck.0050 scenario
     */
    private void setupTabLifecycleListeners() {
        // Listen for new tab creation (openNewTabWithURL)
        mainDevTools.addListener(Target.targetCreated(), this::handleTargetCreated);
        
        // CRITICAL: Listen for tab closure/destruction (closeCurrentTab) - this was missing in original
        mainDevTools.addListener(Target.targetDestroyed(), this::handleTargetDestroyed);
        
        logger.info("🎯 Enhanced tab lifecycle listeners configured for URLCheck.0050 scenario");
        logger.info("   - Will handle: openNewTabWithURL events");
        logger.info("   - Will handle: closeCurrentTab events (CRITICAL FIX)");
    }
    
    /**
     * Handle new tab creation (openNewTabWithURL in URLCheck.0050)
     */
    private void handleTargetCreated(TargetInfo targetInfo) {
        String targetId = targetInfo.getTargetId().toString();
        String targetType = targetInfo.getType();
        String targetUrl = targetInfo.getUrl();
        
        logger.info("🆕 Target created: " + targetId + " type: " + targetType + " URL: " + targetUrl);
        
        if ("page".equals(targetType) && autoRebindEnabled && isRecording) {
            autoRebindExecutor.submit(() -> {
                try {
                    logger.info("🔄 Auto-rebind triggered for new target: " + targetId);
                    logger.info("   This handles openNewTabWithURL in URLCheck.0050");
                    
                    switchRecordingToTarget(targetId);
                    
                } catch (Exception e) {
                    logger.error("❌ Auto-rebind failed for target " + targetId + ": " + e.getMessage());
                }
            });
        }
    }
    
    /**
     * CRITICAL FIX: Handle tab closure/destruction (closeCurrentTab in URLCheck.0050)
     * This is the main fix for the flickering issue
     */
    private void handleTargetDestroyed(TargetID targetId) {
        String targetIdStr = targetId.toString();
        
        logger.info("🔥 CRITICAL: Target destroyed: " + targetIdStr);
        logger.info("   This handles closeCurrentTab in URLCheck.0050");
        
        // If the currently recording tab is being closed, we need to handle this carefully
        if (targetIdStr.equals(currentRecordingTargetId)) {
            logger.warn("🚨 Currently recording tab is being closed: " + targetIdStr);
            logger.warn("   This is the critical moment that causes flickering in original implementation");
            
            sessionCleanupExecutor.submit(() -> {
                try {
                    logger.info("🧹 Starting cleanup for closed recording tab: " + targetIdStr);
                    
                    // STEP 1: Clean up the session for the closed tab
                    cleanupSessionForTarget(targetIdStr);
                    
                    // STEP 2: Find the next available tab to record
                    String nextTargetId = findNextAvailableTarget();
                    
                    if (nextTargetId != null) {
                        logger.info("✅ Found next target after tab closure: " + nextTargetId);
                        logger.info("   Switching recording from closed tab " + targetIdStr + " to " + nextTargetId);
                        switchRecordingToTarget(nextTargetId);
                    } else {
                        logger.warn("⚠️ No available targets found after tab closure");
                        logger.info("   This happens when all new tabs are closed, returning to original tab");
                        switchRecordingToMainTarget();
                    }
                    
                    logger.info("✅ Tab closure handled successfully - no flickering should occur");
                    
                } catch (Exception e) {
                    logger.error("❌ CRITICAL: Failed to handle target destruction: " + e.getMessage());
                    logger.error("   This could cause flickering or missing recording for URLCheck.0060");
                    
                    // Attempt recovery
                    attemptRecordingRecovery();
                }
            });
        } else {
            // Clean up session for non-recording tab (background cleanup)
            logger.info("🧹 Cleaning up non-recording tab: " + targetIdStr);
            sessionCleanupExecutor.submit(() -> cleanupSessionForTarget(targetIdStr));
        }
    }
    
    /**
     * CRITICAL FIX: Find the next available target to record after a tab closure
     * This ensures continuous recording during URLCheck.0050's multiple closeCurrentTab calls
     */
    private String findNextAvailableTarget() {
        try {
            // Get all current targets
            List<TargetInfo> targets = mainDevTools.send(Target.getTargets(Optional.empty()));
            
            logger.info("🔍 Searching for available targets after tab closure");
            logger.info("   Found " + targets.size() + " total targets");
            
            // Find page targets (excluding the closed one)
            List<String> availableTargets = new ArrayList<>();
            
            for (TargetInfo target : targets) {
                String targetId = target.getTargetId().toString();
                String targetType = target.getType();
                String targetUrl = target.getUrl();
                
                logger.debug("   Target: " + targetId + " type: " + targetType + " URL: " + targetUrl);
                
                if ("page".equals(targetType) && !targetId.equals(currentRecordingTargetId)) {
                    availableTargets.add(targetId);
                    logger.info("✅ Found available target: " + targetId + " URL: " + targetUrl);
                }
            }
            
            if (!availableTargets.isEmpty()) {
                // Return the first available target
                String selectedTarget = availableTargets.get(0);
                logger.info("🎯 Selected target for recording: " + selectedTarget);
                return selectedTarget;
            } else {
                logger.warn("⚠️ No suitable page targets found");
                return null;
            }
            
        } catch (Exception e) {
            logger.error("❌ Failed to find next available target: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * CRITICAL FIX: Switch recording to main target (original window)
     * This handles the return to original tab after all new tabs are closed in URLCheck.0050
     */
    private void switchRecordingToMainTarget() {
        try {
            logger.info("🏠 Switching recording to main target (original window)");
            logger.info("   This handles return to original tab after URLCheck.0050 closes all new tabs");
            
            // Get all window handles
            Set<String> windowHandles = driver.getWindowHandles();
            logger.info("   Available window handles: " + windowHandles.size());
            
            if (!windowHandles.isEmpty()) {
                // Switch to the first (main) window
                String mainWindowHandle = windowHandles.iterator().next();
                driver.switchTo().window(mainWindowHandle);
                
                logger.info("   Switched to main window handle: " + mainWindowHandle);
                
                // Get the target ID for the main window
                String mainTargetId = getCurrentTargetId();
                
                if (mainTargetId != null) {
                    logger.info("✅ Found main target ID: " + mainTargetId);
                    logger.info("   Switching recording to main target - this ensures URLCheck.0060 will be recorded");
                    switchRecordingToTarget(mainTargetId);
                } else {
                    logger.error("❌ Could not determine main target ID");
                    logger.error("   This could cause URLCheck.0060 to not be recorded");
                }
            } else {
                logger.error("❌ No window handles available");
                logger.error("   This is a critical error that will prevent URLCheck.0060 recording");
            }
            
        } catch (Exception e) {
            logger.error("❌ Failed to switch to main target: " + e.getMessage());
            logger.error("   This could prevent URLCheck.0060 from being recorded");
        }
    }
    
    /**
     * Get the current target ID for the active tab
     */
    private String getCurrentTargetId() {
        try {
            List<TargetInfo> targets = mainDevTools.send(Target.getTargets(Optional.empty()));
            String currentUrl = driver.getCurrentUrl();
            
            logger.debug("🔍 Looking for target with current URL: " + currentUrl);
            
            for (TargetInfo target : targets) {
                if ("page".equals(target.getType())) {
                    String targetUrl = target.getUrl();
                    String targetId = target.getTargetId().toString();
                    
                    logger.debug("   Checking target " + targetId + " with URL: " + targetUrl);
                    
                    // Match by URL
                    if (currentUrl.equals(targetUrl)) {
                        logger.info("✅ Found matching target by URL: " + targetId);
                        return targetId;
                    }
                }
            }
            
            // If no exact match, return the first page target
            for (TargetInfo target : targets) {
                if ("page".equals(target.getType())) {
                    String targetId = target.getTargetId().toString();
                    logger.info("🔄 Using first available page target: " + targetId);
                    return targetId;
                }
            }
            
            logger.warn("⚠️ No page targets found");
            
        } catch (Exception e) {
            logger.error("❌ Failed to get current target ID: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Switch recording to a specific target with enhanced error handling
     */
    private void switchRecordingToTarget(String targetId) {
        try {
            logger.info("🔄 Switching recording to target: " + targetId);
            
            // Stop recording on current target if any
            if (currentRecordingTargetId != null && !currentRecordingTargetId.equals(targetId)) {
                logger.info("   Stopping recording on previous target: " + currentRecordingTargetId);
                stopRecordingOnTarget(currentRecordingTargetId);
            }
            
            // Start recording on new target
            logger.info("   Starting recording on new target: " + targetId);
            startRecordingOnTarget(targetId);
            
            currentRecordingTargetId = targetId;
            
            logger.info("✅ Successfully switched recording to target: " + targetId);
            
        } catch (Exception e) {
            logger.error("❌ Failed to switch recording to target " + targetId + ": " + e.getMessage());
            logger.error("   Attempting recovery to prevent recording loss");
            attemptRecordingRecovery();
        }
    }
    
    /**
     * Start recording on a specific target with robust session management
     */
    private void startRecordingOnTarget(String targetId) {
        try {
            logger.info("▶️ Starting recording on target: " + targetId);
            
            DevTools targetDevTools = getOrCreateDevToolsSession(targetId);
            
            if (targetDevTools == null) {
                throw new RuntimeException("Failed to create DevTools session for target: " + targetId);
            }
            
            // Enable page domain
            targetDevTools.send(Page.enable(Optional.empty()));
            
            // Set up screencast frame listener
            targetDevTools.addListener(Page.screencastFrame(), frame -> {
                try {
                    handleScreencastFrame(frame, targetId);
                } catch (Exception e) {
                    logger.error("Error handling screencast frame for target " + targetId + ": " + e.getMessage());
                }
            });
            
            // Start screencast
            targetDevTools.send(Page.startScreencast(
                Optional.of(Page.StartScreencastFormat.PNG),
                Optional.of(90),
                Optional.of(1920),
                Optional.of(1080),
                Optional.empty()
            ));
            
            sessionRecordingState.put(targetId, true);
            sessionFrameCount.put(targetId, new AtomicInteger(0));
            
            logger.info("✅ Started recording on target: " + targetId);
            
        } catch (Exception e) {
            logger.error("❌ Failed to start recording on target " + targetId + ": " + e.getMessage());
            throw new RuntimeException("Recording start failed", e);
        }
    }
    
    /**
     * Stop recording on a specific target with proper cleanup
     */
    private void stopRecordingOnTarget(String targetId) {
        try {
            logger.info("⏹️ Stopping recording on target: " + targetId);
            
            DevTools targetDevTools = activeDevToolsSessions.get(targetId);
            if (targetDevTools != null) {
                try {
                    targetDevTools.send(Page.stopScreencast());
                    logger.info("✅ Stopped screencast for target: " + targetId);
                } catch (Exception e) {
                    logger.warn("⚠️ Failed to stop screencast for target " + targetId + ": " + e.getMessage());
                }
            }
            
            sessionRecordingState.put(targetId, false);
            
            int frameCount = sessionFrameCount.getOrDefault(targetId, new AtomicInteger(0)).get();
            logger.info("✅ Stopped recording on target " + targetId + " (captured " + frameCount + " frames)");
            
        } catch (Exception e) {
            logger.error("❌ Error stopping recording on target " + targetId + ": " + e.getMessage());
        }
    }
    
    /**
     * CRITICAL FIX: Get or create DevTools session for a target with proper session management
     */
    private DevTools getOrCreateDevToolsSession(String targetId) {
        DevTools existingSession = activeDevToolsSessions.get(targetId);
        
        if (existingSession != null && isSessionValid(existingSession)) {
            logger.debug("♻️ Reusing existing DevTools session for target: " + targetId);
            return existingSession;
        }
        
        try {
            logger.info("🆕 Creating new DevTools session for target: " + targetId);
            
            // CRITICAL: Create a new DevTools instance for this specific target
            DevTools newDevTools = ((HasDevTools) driver).getDevTools();
            newDevTools.createSession(targetId);
            
            activeDevToolsSessions.put(targetId, newDevTools);
            
            logger.info("✅ Created new DevTools session for target: " + targetId);
            return newDevTools;
            
        } catch (Exception e) {
            logger.error("❌ Failed to create DevTools session for target " + targetId + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Check if a DevTools session is still valid
     */
    private boolean isSessionValid(DevTools devTools) {
        try {
            // Try a simple command to test session validity
            devTools.send(Page.enable(Optional.empty()));
            return true;
        } catch (Exception e) {
            logger.debug("Session validation failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * CRITICAL FIX: Clean up session for a specific target
     * This prevents the flickering issue by properly cleaning up closed tab sessions
     */
    private void cleanupSessionForTarget(String targetId) {
        try {
            logger.info("🧹 Cleaning up session for target: " + targetId);
            
            // Stop recording if active
            if (Boolean.TRUE.equals(sessionRecordingState.get(targetId))) {
                logger.info("   Stopping active recording on target: " + targetId);
                stopRecordingOnTarget(targetId);
            }
            
            // Close DevTools session
            DevTools devTools = activeDevToolsSessions.remove(targetId);
            if (devTools != null) {
                try {
                    logger.info("   Clearing listeners and closing DevTools session");
                    devTools.clearListeners();
                    devTools.close();
                    logger.debug("✅ Closed DevTools session for target: " + targetId);
                } catch (Exception e) {
                    logger.warn("⚠️ Error closing DevTools session for target " + targetId + ": " + e.getMessage());
                }
            }
            
            // Clean up state
            sessionRecordingState.remove(targetId);
            sessionFrameCount.remove(targetId);
            
            logger.info("✅ Cleaned up session for target: " + targetId);
            logger.info("   This prevents flickering when returning to previous tabs");
            
        } catch (Exception e) {
            logger.error("❌ Error during session cleanup for target " + targetId + ": " + e.getMessage());
        }
    }
    
    /**
     * CRITICAL FIX: Attempt to recover from recording failures
     */
    private void attemptRecordingRecovery() {
        try {
            logger.warn("🚑 Attempting recording recovery...");
            logger.warn("   This recovery ensures URLCheck.0060 will be recorded even if URLCheck.0050 caused issues");
            
            // Find any available target
            String availableTarget = findNextAvailableTarget();
            
            if (availableTarget != null) {
                logger.info("✅ Recovery: switching to available target " + availableTarget);
                switchRecordingToTarget(availableTarget);
            } else {
                logger.warn("⚠️ Recovery: no available targets, switching to main target");
                switchRecordingToMainTarget();
            }
            
            logger.info("✅ Recording recovery completed - URLCheck.0060 should now be recorded");
            
        } catch (Exception e) {
            logger.error("❌ Recording recovery failed: " + e.getMessage());
            logger.error("   URLCheck.0060 may not be recorded properly");
        }
    }
    
    /**
     * Handle screencast frame with enhanced error handling
     */
    private void handleScreencastFrame(ScreencastFrame frame, String targetId) {
        try {
            int frameNumber = frameCounter.incrementAndGet();
            sessionFrameCount.get(targetId).incrementAndGet();
            
            // Save frame
            String frameFileName = String.format("frame_%05d.png", frameNumber);
            Path framePath = FRAME_DIR.resolve(frameFileName);
            
            byte[] imageData = Base64.getDecoder().decode(frame.getData());
            
            try (FileOutputStream fos = new FileOutputStream(framePath.toFile())) {
                fos.write(imageData);
            }
            
            // Acknowledge frame
            DevTools targetDevTools = activeDevToolsSessions.get(targetId);
            if (targetDevTools != null) {
                targetDevTools.send(Page.screencastFrameAck(frame.getSessionId()));
            }
            
            logger.debug("📸 Captured frame " + frameNumber + " from target: " + targetId);
            
        } catch (Exception e) {
            logger.error("❌ Error handling screencast frame for target " + targetId + ": " + e.getMessage());
        }
    }
    
    /**
     * Start recording with enhanced initialization
     */
    public void startRecording() {
        try {
            logger.info("🎬 Starting tab closure fix video recording...");
            logger.info("   Ready to handle URLCheck.0050 scenario: openNewTabWithURL + closeCurrentTab sequence");
            
            // Clear any existing frames
            clearFrameDirectory();
            
            // Reset frame counter
            frameCounter.set(0);
            
            // Get current target and start recording
            String currentTargetId = getCurrentTargetId();
            if (currentTargetId != null) {
                startRecordingOnTarget(currentTargetId);
                currentRecordingTargetId = currentTargetId;
                isRecording = true;
                
                logger.info("✅ Tab closure fix video recording started on target: " + currentTargetId);
                logger.info("   Recording will continue through URLCheck.0050 and into URLCheck.0060");
            } else {
                throw new RuntimeException("Could not determine current target for recording");
            }
            
        } catch (Exception e) {
            logger.error("❌ Failed to start tab closure fix video recording: " + e.getMessage());
            throw new RuntimeException("Recording start failed", e);
        }
    }
    
    /**
     * Stop recording and generate video with enhanced cleanup
     */
    public void stopRecordingAndGenerateVideo() {
        try {
            logger.info("🛑 Stopping tab closure fix video recording and generating video...");
            
            // Stop recording on current target
            if (currentRecordingTargetId != null) {
                stopRecordingOnTarget(currentRecordingTargetId);
            }
            
            isRecording = false;
            
            // Generate video
            generateVideoFromFrames();
            
            // Clean up all sessions
            cleanupAllSessions();
            
            logger.info("✅ Tab closure fix video recording stopped and video generated successfully");
            logger.info("   Video should show URLCheck.0050 without flickering and include URLCheck.0060");
            
        } catch (Exception e) {
            logger.error("❌ Error stopping recording and generating video: " + e.getMessage());
            throw new RuntimeException("Recording stop failed", e);
        }
    }
    
    /**
     * Clean up all active sessions
     */
    private void cleanupAllSessions() {
        logger.info("🧹 Cleaning up all DevTools sessions...");
        
        for (String targetId : new HashSet<>(activeDevToolsSessions.keySet())) {
            cleanupSessionForTarget(targetId);
        }
        
        currentRecordingTargetId = null;
        
        logger.info("✅ All DevTools sessions cleaned up");
    }
    
    /**
     * Clear frame directory
     */
    private void clearFrameDirectory() {
        try {
            Files.list(FRAME_DIR)
                .filter(path -> path.toString().endsWith(".png"))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        logger.warn("Failed to delete frame: " + path);
                    }
                });
        } catch (IOException e) {
            logger.warn("Failed to clear frame directory: " + e.getMessage());
        }
    }
    
    /**
     * Generate video from captured frames
     */
    private void generateVideoFromFrames() {
        try {
            int totalFrames = frameCounter.get();
            
            if (totalFrames == 0) {
                logger.warn("⚠️ No frames captured, cannot generate video");
                return;
            }
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String videoFileName = "test_run_" + timestamp + ".mp4";
            Path videoPath = VIDEO_DIR.resolve(videoFileName);
            
            String ffmpegPath = TestBase.getFFmpegPath();
            String inputPattern = FRAME_DIR.resolve("frame_%05d.png").toAbsolutePath().toString();
            String outputPath = videoPath.toAbsolutePath().toString();
            
            // Normalize paths for cross-platform compatibility
            inputPattern = normalizePath(inputPattern);
            outputPath = normalizePath(outputPath);
            
            List<String> command = Arrays.asList(
                ffmpegPath,
                "-y",
                "-framerate", "5",
                "-i", inputPattern,
                "-c:v", "libx264",
                "-pix_fmt", "yuv420p",
                "-crf", "23",
                outputPath
            );
            
            logger.info("🎬 Generating video with " + totalFrames + " frames...");
            logger.debug("FFmpeg command: " + String.join(" ", command));
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(FRAME_DIR.getParent().toFile());
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                logger.info("✅ Video saved to: " + videoPath.toAbsolutePath());
                
                // Log video file size
                long fileSize = Files.size(videoPath);
                logger.info("📹 Video file size: " + (fileSize / 1024) + " KB");
                logger.info("🎯 Video should show URLCheck.0050 and URLCheck.0060 without flickering");
            } else {
                logger.error("❌ FFmpeg failed with exit code: " + exitCode);
            }
            
        } catch (Exception e) {
            logger.error("❌ Failed to generate video: " + e.getMessage());
        }
    }
    
    /**
     * Normalize file paths for cross-platform compatibility
     */
    private static String normalizePath(String path) {
        String os = System.getProperty("os.name").toLowerCase();
        
        if (os.contains("win")) {
            return path.replace("/", "\\");
        } else {
            return path.replace("\\", "/");
        }
    }
    
    /**
     * Set auto-rebind enabled/disabled
     */
    public void setAutoRebindEnabled(boolean enabled) {
        this.autoRebindEnabled = enabled;
        logger.info("Auto-rebind " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Check if currently recording
     */
    public boolean isRecording() {
        return isRecording;
    }
    
    /**
     * Get current recording target ID
     */
    public String getCurrentRecordingTargetId() {
        return currentRecordingTargetId;
    }
    
    /**
     * Get recording statistics
     */
    public Map<String, Object> getRecordingStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("isRecording", isRecording);
        stats.put("currentTarget", currentRecordingTargetId);
        stats.put("totalFrames", frameCounter.get());
        stats.put("activeSessions", activeDevToolsSessions.size());
        stats.put("autoRebindEnabled", autoRebindEnabled);
        
        return stats;
    }
    
    /**
     * Close and cleanup all resources
     */
    public void close() {
        try {
            logger.info("🔒 Closing tab closure fix video recorder...");
            
            if (isRecording) {
                stopRecordingAndGenerateVideo();
            }
            
            // Shutdown executors
            autoRebindExecutor.shutdown();
            sessionCleanupExecutor.shutdown();
            
            // Clean up main DevTools
            if (mainDevTools != null) {
                try {
                    mainDevTools.clearListeners();
                    mainDevTools.close();
                } catch (Exception e) {
                    logger.warn("⚠️ Error closing main DevTools: " + e.getMessage());
                }
            }
            
            logger.info("✅ Tab closure fix video recorder closed successfully");
            
        } catch (Exception e) {
            logger.error("❌ Error closing tab closure fix video recorder: " + e.getMessage());
        }
    }
}
