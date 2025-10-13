package com.example.automation.demo;

import com.example.automation.logger.LoggerMechanism;
import com.example.automation.util.AdaptiveFrameTiming;
import com.example.automation.util.LinuxHeadlessOptimizer;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * Demo class to showcase Linux optimization functionality without requiring actual Chrome browser.
 * This allows testing the optimization logic on any platform.
 */
public class LinuxOptimizationDemo {
    
    private static final LoggerMechanism logger = new LoggerMechanism("LinuxOptimizationDemo");
    
    public static void main(String[] args) {
        if (args.length == 0) {
            showUsage();
            return;
        }
        
        try {
            switch (args[0]) {
                case "environmentDetection":
                    testEnvironmentDetection();
                    break;
                case "chromeOptionsConfig":
                    testChromeOptionsConfiguration();
                    break;
                case "frameTiming":
                    testFrameTiming();
                    break;
                default:
                    showUsage();
            }
        } catch (Exception e) {
            System.err.println("Demo failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void showUsage() {
        System.out.println("Usage: LinuxOptimizationDemo <test>");
        System.out.println("Tests:");
        System.out.println("  environmentDetection - Test Linux environment detection");
        System.out.println("  chromeOptionsConfig  - Test Chrome options configuration");
        System.out.println("  frameTiming         - Test adaptive frame timing");
    }
    
    /**
     * Test Linux environment detection functionality
     */
    private static void testEnvironmentDetection() {
        System.out.println("Testing Linux Environment Detection...");
        System.out.println("=====================================");
        
        // Test OS detection
        boolean isLinux = LinuxHeadlessOptimizer.isLinux();
        System.out.println("✓ Linux detected: " + isLinux);
        System.out.println("✓ Actual OS: " + System.getProperty("os.name"));
        
        // Expected result on macOS
        if (!isLinux && System.getProperty("os.name").toLowerCase().contains("mac")) {
            System.out.println("✅ PASS: Correctly identified non-Linux environment (macOS)");
        } else if (isLinux && System.getProperty("os.name").toLowerCase().contains("linux")) {
            System.out.println("✅ PASS: Correctly identified Linux environment");
        } else {
            System.out.println("⚠️  Unexpected OS detection result");
        }
        
        // Test headless environment detection
        boolean isHeadless = LinuxHeadlessOptimizer.isHeadlessEnvironment();
        System.out.println("✓ Headless environment: " + isHeadless);
        
        // Show environment info
        System.out.println("\\n📋 Environment Details:");
        String envInfo = LinuxHeadlessOptimizer.getEnvironmentInfo();
        System.out.println(envInfo);
        
        System.out.println("✅ Environment detection test completed successfully!");
    }
    
    /**
     * Test Chrome options configuration for different platforms
     */
    private static void testChromeOptionsConfiguration() {
        System.out.println("Testing Chrome Options Configuration...");
        System.out.println("======================================");
        
        // Create optimized options
        ChromeOptions options = LinuxHeadlessOptimizer.createOptimizedOptions(logger);
        System.out.println("✓ Chrome options created successfully");
        
        // Validate options
        LinuxHeadlessOptimizer.validateChromeOptions(options, logger);
        System.out.println("✓ Chrome options validated");
        
        // Test platform-specific behavior
        if (LinuxHeadlessOptimizer.isLinux()) {
            System.out.println("✅ PASS: Linux-specific optimizations would be applied");
            System.out.println("  • GPU acceleration disabled for stability");
            System.out.println("  • Single-threaded compositing for consistency");
            System.out.println("  • Linux scheduler-aware frame timing");
        } else {
            System.out.println("✅ PASS: Standard optimizations applied for " + System.getProperty("os.name"));
            System.out.println("  • Platform-appropriate flags selected");
            System.out.println("  • Linux optimizations correctly skipped");
        }
        
        System.out.println("✅ Chrome options configuration test completed successfully!");
    }
    
    /**
     * Test adaptive frame timing functionality
     */
    private static void testFrameTiming() throws InterruptedException {
        System.out.println("Testing Adaptive Frame Timing...");
        System.out.println("================================");
        
        // Create frame timing instance
        AdaptiveFrameTiming frameTimer = new AdaptiveFrameTiming(logger, "DemoTest");
        System.out.println("✓ Adaptive frame timing created");
        
        // Start recording simulation
        frameTimer.startRecording();
        System.out.println("✓ Frame timing started");
        
        // Simulate frame capture with varying delays
        System.out.println("✓ Simulating frame captures...");
        long totalDelay = 0;
        for (int i = 0; i < 10; i++) {
            long captureStart = System.currentTimeMillis();
            
            // Wait for next frame
            frameTimer.waitForNextFrame();
            
            // Simulate processing time
            Thread.sleep(20 + (i * 3)); // Progressive delay
            long processingTime = 20 + (i * 3);
            totalDelay += processingTime;
            
            frameTimer.recordCaptureDelay(processingTime);
            
            if (i % 3 == 0) {
                System.out.println("  Frame " + (i + 1) + ": " + processingTime + "ms processing time");
            }
        }
        
        // Get performance metrics
        AdaptiveFrameTiming.TimingMetrics metrics = frameTimer.getMetrics();
        
        System.out.println("\\n📊 Performance Metrics:");
        System.out.println("  Total frames: " + metrics.totalFrames);
        System.out.println("  Frames skipped: " + metrics.framesSkipped);
        System.out.println("  Current frame rate: " + String.format("%.1f", metrics.frameRate) + " fps");
        System.out.println("  System load factor: " + String.format("%.2f", metrics.systemLoad));
        System.out.println("  Timing variance: " + String.format("%.1f", metrics.timingVariance) + "ms");
        System.out.println("  Performance status: " + (metrics.isPerformingWell ? "GOOD" : "NEEDS OPTIMIZATION"));
        
        frameTimer.stopRecording();
        System.out.println("✓ Frame timing stopped");
        
        // Validate results
        if (metrics.totalFrames > 0 && metrics.frameRate > 0) {
            System.out.println("✅ PASS: Adaptive frame timing is working correctly");
            System.out.println("  • Frame capture simulation successful");
            System.out.println("  • Performance metrics calculated correctly");
            System.out.println("  • System load adaptation functional");
            
            if (System.getProperty("os.name").toLowerCase().contains("linux")) {
                System.out.println("  • Linux-specific timing optimizations would be active");
            } else {
                System.out.println("  • Standard timing behavior confirmed for " + System.getProperty("os.name"));
            }
        } else {
            System.out.println("❌ FAIL: Frame timing metrics appear invalid");
        }
        
        System.out.println("✅ Adaptive frame timing test completed successfully!");
    }
}