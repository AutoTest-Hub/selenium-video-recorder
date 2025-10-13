package com.example.automation.util;

import com.example.automation.logger.LoggerMechanism;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Linux-specific Chrome configuration optimizer for headless mode video recording.
 * 
 * This utility addresses platform-specific flickering issues that occur on Linux
 * but not on macOS/Windows. Key optimizations include:
 * - Compositor and GPU configuration specific to Linux X11/Wayland
 * - Memory management tuned for Linux process scheduling
 * - Frame synchronization optimized for Linux display systems
 * - Buffer management for consistent frame capture timing
 * 
 * Usage:
 * ChromeOptions options = LinuxHeadlessOptimizer.createOptimizedOptions(logger);
 */
public class LinuxHeadlessOptimizer {
    
    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();
    private static final String OS_ARCH = System.getProperty("os.arch").toLowerCase();
    private static final String OS_VERSION = System.getProperty("os.version");
    
    public static boolean isLinux() {
        return OS_NAME.contains("linux");
    }
    
    public static boolean isHeadlessEnvironment() {
        String display = System.getenv("DISPLAY");
        String waylandDisplay = System.getenv("WAYLAND_DISPLAY");
        String xdgSessionType = System.getenv("XDG_SESSION_TYPE");
        
        // Check for CI environments (GitHub Actions, GitLab CI, etc.)
        boolean isCIEnvironment = System.getenv("CI") != null ||
                                System.getenv("GITHUB_ACTIONS") != null ||
                                System.getenv("GITLAB_CI") != null ||
                                System.getenv("JENKINS_URL") != null ||
                                System.getenv("BUILDKITE") != null ||
                                System.getenv("TRAVIS") != null;
        
        // Check for virtual displays (like :99 used in GitHub Actions)
        boolean isVirtualDisplay = display != null && 
                                  (display.matches(":9[0-9]") || display.matches(":1[0-9][0-9]"));
        
        // Check if we're in a headless/remote environment
        return display == null || display.isEmpty() || 
               "headless".equals(xdgSessionType) ||
               System.getenv("SSH_CLIENT") != null ||
               System.getenv("SSH_TTY") != null ||
               isCIEnvironment ||
               isVirtualDisplay;
    }
    
    public static ChromeOptions createOptimizedOptions(LoggerMechanism logger) {
        ChromeOptions options = new ChromeOptions();
        
        if (isLinux()) {
            logger.info("Detected Linux environment, applying Linux-specific optimizations");
            applyLinuxOptimizations(options, logger);
        } else {
            logger.info("Non-Linux environment detected, using standard configuration");
            applyStandardConfiguration(options, logger);
        }
        
        return options;
    }
    
    private static void applyLinuxOptimizations(ChromeOptions options, LoggerMechanism logger) {
        logger.info("OS: " + OS_NAME + ", Arch: " + OS_ARCH + ", Version: " + OS_VERSION);
        logger.info("Headless Environment: " + isHeadlessEnvironment());
        
        // Core headless options
        options.addArguments("--headless=new");  // Use new headless mode for better stability
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        
        // Window and display configuration
        options.addArguments("--window-size=1280,720");  // Standard size to reduce memory usage
        options.addArguments("--force-device-scale-factor=1");  // Prevent scaling issues
        options.addArguments("--disable-background-timer-throttling");
        options.addArguments("--disable-backgrounding-occluded-windows");
        options.addArguments("--disable-renderer-backgrounding");
        
        // Linux-specific GPU and compositor optimizations
        logger.info("Applying Linux-specific GPU and compositor settings...");
        options.addArguments("--disable-gpu");  // Essential for consistent headless behavior on Linux
        options.addArguments("--disable-gpu-sandbox");
        options.addArguments("--disable-software-rasterizer");
        options.addArguments("--disable-background-media-suspend");
        options.addArguments("--disable-backgrounding-occluded-windows");
        options.addArguments("--disable-renderer-backgrounding");
        
        // Frame synchronization and timing (critical for Linux flickering fix)
        options.addArguments("--disable-frame-rate-limit");  // Remove frame rate throttling
        options.addArguments("--disable-background-timer-throttling");
        options.addArguments("--max-gum-fps=30");  // Consistent frame rate
        options.addArguments("--disable-features=TranslateUI,VizDisplayCompositor");
        
        // Memory management optimized for Linux
        options.addArguments("--memory-pressure-off");
        options.addArguments("--disable-low-res-tiling");
        options.addArguments("--disable-partial-raster");
        options.addArguments("--disable-threaded-compositing");  // Single-threaded compositing for consistency
        
        // Process and thread management
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-plugins");
        options.addArguments("--disable-background-networking");
        options.addArguments("--disable-default-apps");
        options.addArguments("--disable-sync");
        
        // Linux display system specific
        if (isHeadlessEnvironment()) {
            logger.info("Detected headless/remote environment, applying additional optimizations");
            options.addArguments("--virtual-time-budget=5000");  // Allow more time for rendering
            options.addArguments("--disable-ipc-flooding-protection");
            options.addArguments("--disable-component-update");
        }
        
        // Security and network optimizations
        options.addArguments("--disable-web-security");
        options.addArguments("--allow-running-insecure-content");
        options.addArguments("--disable-features=VizDisplayCompositor");
        options.addArguments("--ignore-certificate-errors");
        options.addArguments("--ignore-ssl-errors");
        options.addArguments("--ignore-certificate-errors-spki-list");
        
        // Remote debugging configuration
        options.addArguments("--remote-debugging-port=9222");
        options.addArguments("--remote-allow-origins=*");
        
        // Additional Linux stability options
        options.addArguments("--no-first-run");
        options.addArguments("--no-default-browser-check");
        options.addArguments("--disable-default-apps");
        options.addArguments("--disable-popup-blocking");
        
        // Performance preferences for Linux
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("profile.default_content_setting_values.notifications", 2);
        prefs.put("profile.default_content_settings.popups", 0);
        prefs.put("profile.managed_default_content_settings.images", 1);
        options.setExperimentalOption("prefs", prefs);
        
        // Chrome flags for better Linux compatibility
        Map<String, Object> chromeFlags = new HashMap<>();
        chromeFlags.put("enable-automation", false);
        chromeFlags.put("disable-blink-features", "AutomationControlled");
        options.setExperimentalOption("useAutomationExtension", false);
        
        logger.info("Linux-specific Chrome optimizations applied successfully");
    }
    
    private static void applyStandardConfiguration(ChromeOptions options, LoggerMechanism logger) {
        // Standard configuration for non-Linux platforms
        options.addArguments("--headless");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1280,720");
        options.addArguments("--remote-debugging-port=9222");
        options.addArguments("--disable-web-security");
        options.addArguments("--allow-running-insecure-content");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-plugins");
        
        if (OS_NAME.contains("mac")) {
            logger.info("Applying macOS-specific optimizations");
            options.addArguments("--disable-background-timer-throttling");
            options.addArguments("--disable-backgrounding-occluded-windows");
            options.addArguments("--disable-renderer-backgrounding");
        } else if (OS_NAME.contains("win")) {
            logger.info("Applying Windows-specific optimizations");
            options.addArguments("--disable-features=VizDisplayCompositor");
            options.addArguments("--disable-background-timer-throttling");
            options.addArguments("--disable-backgrounding-occluded-windows");
            options.addArguments("--disable-renderer-backgrounding");
        }
        
        logger.info("Standard Chrome configuration applied");
    }
    
    /**
     * Get environment information for debugging purposes
     */
    public static String getEnvironmentInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Operating System: ").append(OS_NAME).append("\n");
        info.append("Architecture: ").append(OS_ARCH).append("\n");
        info.append("OS Version: ").append(OS_VERSION).append("\n");
        info.append("Display: ").append(System.getenv("DISPLAY")).append("\n");
        info.append("Wayland Display: ").append(System.getenv("WAYLAND_DISPLAY")).append("\n");
        info.append("XDG Session Type: ").append(System.getenv("XDG_SESSION_TYPE")).append("\n");
        info.append("SSH Client: ").append(System.getenv("SSH_CLIENT")).append("\n");
        info.append("SSH TTY: ").append(System.getenv("SSH_TTY")).append("\n");
        info.append("Is Headless Environment: ").append(isHeadlessEnvironment()).append("\n");
        return info.toString();
    }
    
    /**
     * Validate Chrome options for common issues
     */
    public static void validateChromeOptions(ChromeOptions options, LoggerMechanism logger) {
        try {
            // Try to get arguments using reflection for compatibility
            java.lang.reflect.Method getArgumentsMethod = options.getClass().getMethod("getArguments");
            @SuppressWarnings("unchecked")
            List<String> arguments = (List<String>) getArgumentsMethod.invoke(options);
            
            logger.info("Validating Chrome options...");
            logger.info("Total arguments: " + arguments.size());
            
            // Check for conflicting options
            if (arguments.contains("--disable-gpu") && arguments.contains("--enable-gpu")) {
                logger.warn("Conflicting GPU options detected");
            }
            
            if (arguments.contains("--headless") && arguments.contains("--headless=new")) {
                logger.warn("Multiple headless options detected");
            }
            
            // Check for essential Linux options
            if (isLinux()) {
                boolean hasNoSandbox = arguments.contains("--no-sandbox");
                boolean hasDisableDevShm = arguments.contains("--disable-dev-shm-usage");
                boolean hasDisableGpu = arguments.contains("--disable-gpu");
                
                if (!hasNoSandbox) logger.warn("Missing --no-sandbox option (recommended for Linux)");
                if (!hasDisableDevShm) logger.warn("Missing --disable-dev-shm-usage option (recommended for Linux)");
                if (!hasDisableGpu) logger.warn("Missing --disable-gpu option (recommended for Linux headless)");
            }
            
            logger.info("Chrome options validation completed");
            
        } catch (Exception e) {
            logger.warn("Could not validate Chrome options directly (method not available): " + e.getMessage());
            logger.info("Chrome options validation skipped - this is expected with some Selenium versions");
        }
    }
}