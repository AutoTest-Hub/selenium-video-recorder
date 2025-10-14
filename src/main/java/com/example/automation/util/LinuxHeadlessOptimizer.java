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
    
    // Amazon Linux detection
    private static final boolean IS_AMAZON_LINUX = isAmazonLinux();
    private static final String AMAZON_LINUX_VERSION = getAmazonLinuxVersion();
    
    public static boolean isLinux() {
        return OS_NAME.contains("linux");
    }
    
    /**
     * Get Amazon Linux version (AL2 vs AL2023)
     */
    private static String getAmazonLinuxVersion() {
        if (!isLinux()) return "not-amazon-linux";
        
        try {
            // Check for Amazon Linux version indicators
            java.nio.file.Path osRelease = java.nio.file.Paths.get("/etc/os-release");
            java.nio.file.Path systemRelease = java.nio.file.Paths.get("/etc/system-release");
            
            if (java.nio.file.Files.exists(osRelease)) {
                try {
                    String content = java.nio.file.Files.readString(osRelease).toLowerCase();
                    if (content.contains("amazon linux release 2") || content.contains("karoo")) {
                        return "amazon-linux-2";
                    } else if (content.contains("amazon linux 2023") || content.contains("2023")) {
                        return "amazon-linux-2023";
                    } else if (content.contains("amazon") || content.contains("amzn")) {
                        return "amazon-linux-unknown";
                    }
                } catch (Exception ignored) {}
            }
            
            if (java.nio.file.Files.exists(systemRelease)) {
                try {
                    String content = java.nio.file.Files.readString(systemRelease).toLowerCase();
                    if (content.contains("amazon linux release 2")) {
                        return "amazon-linux-2";
                    } else if (content.contains("amazon linux 2023")) {
                        return "amazon-linux-2023";
                    }
                } catch (Exception ignored) {}
            }
            
            return "not-amazon-linux";
            
        } catch (Exception e) {
            return "detection-failed";
        }
    }
    
    /**
     * Detect Amazon Linux EC2 instances
     */
    public static boolean isAmazonLinux() {
        if (!isLinux()) return false;
        
        try {
            // Check for Amazon Linux identifiers
            boolean isAmazonEC2 = false;
            
            // Method 1: Check /etc/os-release or /etc/system-release
            java.nio.file.Path osRelease = java.nio.file.Paths.get("/etc/os-release");
            java.nio.file.Path systemRelease = java.nio.file.Paths.get("/etc/system-release");
            
            if (java.nio.file.Files.exists(osRelease)) {
                try {
                    String content = java.nio.file.Files.readString(osRelease).toLowerCase();
                    isAmazonEC2 = content.contains("amazon") || content.contains("amzn");
                } catch (Exception ignored) {}
            }
            
            if (!isAmazonEC2 && java.nio.file.Files.exists(systemRelease)) {
                try {
                    String content = java.nio.file.Files.readString(systemRelease).toLowerCase();
                    isAmazonEC2 = content.contains("amazon");
                } catch (Exception ignored) {}
            }
            
            // Method 2: Check EC2 metadata service (if accessible)
            if (!isAmazonEC2) {
                try {
                    // Check for EC2 environment variables
                    String awsRegion = System.getenv("AWS_REGION");
                    String awsDefaultRegion = System.getenv("AWS_DEFAULT_REGION");
                    String ec2InstanceId = System.getenv("EC2_INSTANCE_ID");
                    
                    isAmazonEC2 = (awsRegion != null || awsDefaultRegion != null || ec2InstanceId != null);
                } catch (Exception ignored) {}
            }
            
            // Method 3: Check for AWS CLI presence (common on EC2)
            if (!isAmazonEC2) {
                try {
                    java.nio.file.Path awsCli = java.nio.file.Paths.get("/usr/bin/aws");
                    java.nio.file.Path awsCli2 = java.nio.file.Paths.get("/usr/local/bin/aws");
                    isAmazonEC2 = java.nio.file.Files.exists(awsCli) || java.nio.file.Files.exists(awsCli2);
                } catch (Exception ignored) {}
            }
            
            return isAmazonEC2;
            
        } catch (Exception e) {
            // If any detection method fails, assume it's not Amazon Linux
            return false;
        }
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
        logger.info("Amazon Linux EC2: " + IS_AMAZON_LINUX + " (" + AMAZON_LINUX_VERSION + ")");
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
        
        // Amazon Linux EC2-specific optimizations
        if (IS_AMAZON_LINUX) {
            logger.info("Applying Amazon Linux EC2-specific optimizations...");
            
            // EC2 instances often have limited memory, optimize accordingly
            options.addArguments("--max_old_space_size=2048");  // Limit V8 memory usage
            options.addArguments("--disable-background-media-suspend");
            options.addArguments("--disable-backgrounding-occluded-windows");
            
            // EC2 network optimizations
            options.addArguments("--disable-background-networking");
            options.addArguments("--disable-sync");
            options.addArguments("--disable-default-apps");
            
            // EC2 storage optimizations (instances may use EBS)
            options.addArguments("--disk-cache-size=50000000");  // 50MB cache limit
            options.addArguments("--media-cache-size=50000000");  // 50MB media cache
            
            // EC2 CPU optimizations (burstable instances like t2/t3)
            options.addArguments("--max-threads=4");  // Limit thread usage
            options.addArguments("--renderer-process-limit=2");  // Limit processes
            
            logger.info("Amazon Linux EC2 optimizations applied successfully");
        }
        
        // Linux display system specific - conservative CI-friendly options
        if (isHeadlessEnvironment()) {
            logger.info("Detected headless/remote environment, applying additional optimizations");
            // Use more conservative options for CI environments to avoid conflicts
            options.addArguments("--disable-component-update");
            options.addArguments("--disable-background-networking");
            
            // Check specifically for CI environments for more aggressive options
            boolean isCIEnvironment = System.getenv("CI") != null || 
                                    System.getenv("GITHUB_ACTIONS") != null ||
                                    System.getenv("GITLAB_CI") != null;
            if (isCIEnvironment) {
                logger.info("CI environment detected, applying CI-specific optimizations");
                options.addArguments("--disable-logging");
                options.addArguments("--disable-gpu-logging");
                options.addArguments("--silent");
                options.addArguments("--log-level=3");
            }
        }
        
        // Security and network optimizations
        options.addArguments("--disable-web-security");
        options.addArguments("--allow-running-insecure-content");
        options.addArguments("--ignore-certificate-errors");
        options.addArguments("--ignore-ssl-errors");
        options.addArguments("--ignore-certificate-errors-spki-list");
        
        // Remote debugging configuration - use dynamic port to avoid conflicts
        options.addArguments("--remote-debugging-port=0");  // Use dynamic port
        options.addArguments("--remote-allow-origins=*");
        
        // Additional Linux stability options (removing duplicates)
        options.addArguments("--no-first-run");
        options.addArguments("--no-default-browser-check");
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
        info.append("Amazon Linux EC2: ").append(IS_AMAZON_LINUX).append(" (").append(AMAZON_LINUX_VERSION).append(")\n");
        
        // EC2-specific information
        if (IS_AMAZON_LINUX) {
            info.append("AWS Region: ").append(System.getenv("AWS_REGION")).append("\n");
            info.append("AWS Default Region: ").append(System.getenv("AWS_DEFAULT_REGION")).append("\n");
            info.append("EC2 Instance ID: ").append(System.getenv("EC2_INSTANCE_ID")).append("\n");
            
            // Check for common EC2 instance metadata
            try {
                Runtime runtime = Runtime.getRuntime();
                int processors = runtime.availableProcessors();
                long maxMemory = runtime.maxMemory() / 1024 / 1024; // MB
                info.append("Available Processors: ").append(processors).append("\n");
                info.append("Max JVM Memory: ").append(maxMemory).append(" MB\n");
            } catch (Exception e) {
                info.append("System Info: Unable to retrieve\n");
            }
        }
        
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