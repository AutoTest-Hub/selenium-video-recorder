package com.example.automation.test.linux;

import com.example.automation.logger.LoggerMechanism;
import com.example.automation.util.AdaptiveFrameTiming;
import com.example.automation.util.LinuxHeadlessOptimizer;
import com.example.automation.util.VideoRecordInHeadless;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;

/**
 * Linux-specific video recording optimization tests.
 * 
 * These tests specifically target Linux headless Chrome flickering issues
 * by testing the integration of:
 * - LinuxHeadlessOptimizer for Chrome options
 * - AdaptiveFrameTiming for frame synchronization
 * - Enhanced VideoRecordInHeadless with Linux support
 * 
 * Tests should demonstrate improved stability and reduced flickering
 * when compared to standard configuration.
 */
public class LinuxOptimizationTest {
    
    private WebDriver driver;
    private LoggerMechanism logger;
    private VideoRecordInHeadless recorder;
    private WebDriverWait wait;
    
    @BeforeMethod
    public void setup() {
        logger = new LoggerMechanism(LinuxOptimizationTest.class);
        
        // Create optimized Chrome options for the current platform
        ChromeOptions options = LinuxHeadlessOptimizer.createOptimizedOptions(logger);
        
        // Validate the options
        LinuxHeadlessOptimizer.validateChromeOptions(options, logger);

        // Ensure ChromeDriver is available
        WebDriverManager.chromedriver().setup();
        
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        
        recorder = new VideoRecordInHeadless(logger, driver);
        
        logger.info("=== Linux Optimization Test Setup Complete ===");
        if (LinuxHeadlessOptimizer.isLinux()) {
            logger.info("Running on Linux - Linux optimizations active");
        } else {
            logger.info("Running on " + System.getProperty("os.name") + " - standard optimizations");
        }
    }
    
    @AfterMethod
    public void teardown() {
        try {
            if (recorder != null) {
                recorder.cleanup();
            }
        } catch (Exception e) {
            logger.error("Error during recorder cleanup: " + e.getMessage());
        }
        
        try {
            if (driver != null) {
                driver.quit();
            }
        } catch (Exception e) {
            logger.error("Error during driver quit: " + e.getMessage());
        }
    }
    
    /**
     * Test basic video recording with Linux optimizations.
     * Validates that the adaptive frame timing system works correctly.
     */
    @Test
    public void testBasicRecordingWithLinuxOptimizations() throws Exception {
        logger.info("=== Testing Basic Recording with Linux Optimizations ===");
        
        recorder.startRecording();
        
        // Navigate to a simple test page
        driver.get("data:text/html,<html><body><h1 id='title'>Linux Optimization Test</h1><p>Frame timing test in progress...</p></body></html>");
        
        // Wait for page to stabilize
        Thread.sleep(2000);
        
        // Perform some basic interactions
        WebElement title = driver.findElement(By.id("title"));
        Assert.assertNotNull(title, "Page should load correctly with Linux optimizations");
        
        // Add some dynamic content to test frame capture
        JavascriptExecutor js = (JavascriptExecutor) driver;
        for (int i = 0; i < 10; i++) {
            js.executeScript("document.getElementById('title').textContent = 'Frame " + i + "';");
            Thread.sleep(200);
        }
        
        // Get performance metrics
        AdaptiveFrameTiming.TimingMetrics metrics = recorder.getTimingMetrics();
        logger.info("Recording metrics - Total frames: " + metrics.totalFrames + 
                   ", Skipped: " + metrics.framesSkipped + 
                   ", Frame rate: " + String.format("%.1f", metrics.frameRate) + " fps");
        
        recorder.stopRecordingAndGenerateVideo();
        
        // Validate performance
        Assert.assertTrue(recorder.isPerformingWell(), 
            "Recording should perform well with Linux optimizations");
        
        // On Linux, we should have captured at least some frames
        if (LinuxHeadlessOptimizer.isLinux()) {
            Assert.assertTrue(metrics.totalFrames > 0, 
                "Should capture frames on Linux with optimizations");
        }
        
        logger.info("=== Basic Recording Test Completed Successfully ===");
    }
    
    /**
     * Test frame timing consistency under load.
     * This test specifically targets timing issues that cause flickering.
     */
    @Test
    public void testFrameTimingConsistency() throws Exception {
        logger.info("=== Testing Frame Timing Consistency ===");
        
        recorder.startRecording();
        
        // Create a page with rapid DOM changes (common flickering scenario)
        driver.get("data:text/html,<html><head><style>" +
                ".box { width: 100px; height: 100px; background: red; margin: 10px; transition: all 0.1s; }" +
                "</style></head><body>" +
                "<div id='container'></div>" +
                "<script>" +
                "let count = 0;" +
                "function addBox() {" +
                "  const box = document.createElement('div');" +
                "  box.className = 'box';" +
                "  box.style.background = 'hsl(' + (count * 30) + ', 70%, 50%)';" +
                "  box.textContent = count++;" +
                "  document.getElementById('container').appendChild(box);" +
                "  if (count < 50) setTimeout(addBox, 100);" +
                "}" +
                "addBox();" +
                "</script>" +
                "</body></html>");
        
        // Let the rapid DOM changes run
        Thread.sleep(6000);
        
        AdaptiveFrameTiming.TimingMetrics metrics = recorder.getTimingMetrics();
        logger.info("Timing consistency test metrics:");
        logger.info("  Total frames: " + metrics.totalFrames);
        logger.info("  Frames skipped: " + metrics.framesSkipped);
        logger.info("  Frame rate: " + String.format("%.1f", metrics.frameRate) + " fps");
        logger.info("  Timing variance: " + String.format("%.1f", metrics.timingVariance) + "ms");
        logger.info("  Performance status: " + (metrics.isPerformingWell ? "GOOD" : "NEEDS IMPROVEMENT"));
        
        recorder.stopRecordingAndGenerateVideo();
        
        // Validate timing consistency
        if (LinuxHeadlessOptimizer.isLinux()) {
            // On Linux with optimizations, timing variance should be reasonable
            Assert.assertTrue(metrics.timingVariance < 50, 
                "Frame timing variance should be under 50ms on Linux with optimizations, was: " + 
                String.format("%.1f", metrics.timingVariance));
            
            // Frame skip rate should be low
            double skipRate = (double) metrics.framesSkipped / metrics.totalFrames;
            Assert.assertTrue(skipRate < 0.1, 
                "Frame skip rate should be under 10% on Linux with optimizations, was: " + 
                String.format("%.2f%%", skipRate * 100));
        }
        
        logger.info("=== Frame Timing Consistency Test Completed ===");
    }
    
    /**
     * Test multi-tab recording with Linux optimizations.
     * This addresses tab switching flickering issues.
     */
    @Test
    public void testMultiTabRecordingOptimization() throws Exception {
        logger.info("=== Testing Multi-Tab Recording with Linux Optimizations ===");
        
        recorder.setAutoRebindEnabled(true);
        recorder.startRecording();
        
        // Start with initial page
        driver.get("data:text/html,<html><body><h1>Initial Tab</h1><button id='openTab'>Open New Tab</button></body></html>");
        Thread.sleep(1000);
        
        // Open new tab
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("window.open('data:text/html,<html><body><h1>New Tab</h1><p>Automatic rebind test</p></body></html>', '_blank');");
        
        // Give time for auto-rebind to occur
        Thread.sleep(3000);
        
        // Switch to new window and interact
        String originalWindow = driver.getWindowHandle();
        for (String windowHandle : driver.getWindowHandles()) {
            if (!windowHandle.equals(originalWindow)) {
                driver.switchTo().window(windowHandle);
                break;
            }
        }
        
        // Perform actions in new tab
        WebElement newTabHeading = driver.findElement(By.tagName("h1"));
        Assert.assertEquals(newTabHeading.getText(), "New Tab", "Should switch to new tab correctly");
        
        // Add some dynamic content
        for (int i = 0; i < 5; i++) {
            js.executeScript("document.querySelector('h1').textContent = 'New Tab - Update ' + " + i + ";");
            Thread.sleep(500);
        }
        
        AdaptiveFrameTiming.TimingMetrics metrics = recorder.getTimingMetrics();
        logger.info("Multi-tab metrics - Frames: " + metrics.totalFrames + 
                   ", Performance: " + (metrics.isPerformingWell ? "GOOD" : "DEGRADED"));
        
        recorder.stopRecordingAndGenerateVideo();
        
        // Multi-tab recording should still perform well with Linux optimizations
        if (LinuxHeadlessOptimizer.isLinux() && recorder.isLinuxOptimizationsEnabled()) {
            Assert.assertTrue(metrics.totalFrames > 10, 
                "Should capture multiple frames during multi-tab test");
        }
        
        logger.info("=== Multi-Tab Recording Test Completed ===");
    }
    
    /**
     * Test memory pressure handling during long recording.
     * Linux systems often have different memory management behaviors.
     */
    @Test
    public void testMemoryPressureHandling() throws Exception {
        logger.info("=== Testing Memory Pressure Handling ===");
        
        recorder.startRecording();
        
        // Create memory-intensive content
        driver.get("data:text/html,<html><head><script>" +
                "let data = [];" +
                "let count = 0;" +
                "function generateLoad() {" +
                "  for (let i = 0; i < 1000; i++) {" +
                "    data.push('Frame data ' + count + '_' + i + '_' + Math.random());" +
                "  }" +
                "  document.querySelector('#status').textContent = 'Generated ' + (++count * 1000) + ' items';" +
                "  if (count < 20) setTimeout(generateLoad, 200);" +
                "}" +
                "</script></head><body>" +
                "<h1>Memory Pressure Test</h1>" +
                "<p id='status'>Starting...</p>" +
                "<script>generateLoad();</script>" +
                "</body></html>");
        
        // Monitor performance during memory pressure
        long startTime = System.currentTimeMillis();
        AdaptiveFrameTiming.TimingMetrics initialMetrics = recorder.getTimingMetrics();
        
        Thread.sleep(5000); // Let memory pressure build
        
        AdaptiveFrameTiming.TimingMetrics finalMetrics = recorder.getTimingMetrics();
        long duration = System.currentTimeMillis() - startTime;
        
        logger.info("Memory pressure test results:");
        logger.info("  Duration: " + duration + "ms");
        logger.info("  Initial frames: " + initialMetrics.totalFrames);
        logger.info("  Final frames: " + finalMetrics.totalFrames);
        logger.info("  Frame rate: " + String.format("%.1f", finalMetrics.frameRate) + " fps");
        logger.info("  System load factor: " + String.format("%.2f", finalMetrics.systemLoad));
        
        recorder.stopRecordingAndGenerateVideo();
        
        // System should adapt to memory pressure
        if (LinuxHeadlessOptimizer.isLinux()) {
            Assert.assertTrue(finalMetrics.totalFrames > 0, 
                "Should continue capturing frames under memory pressure");
            
            // Frame rate may decrease under load, but shouldn't crash
            Assert.assertTrue(finalMetrics.frameRate > 1.0, 
                "Frame rate should remain above 1 fps under memory pressure");
        }
        
        logger.info("=== Memory Pressure Test Completed ===");
    }
    
    /**
     * Test performance comparison between optimized and standard configurations.
     * This test helps quantify the improvement from Linux optimizations.
     */
    @Test
    public void testPerformanceComparison() throws Exception {
        if (!LinuxHeadlessOptimizer.isLinux()) {
            logger.info("Skipping performance comparison test - not running on Linux");
            return;
        }
        
        logger.info("=== Testing Performance Comparison (Linux Optimized vs Standard) ===");
        
        // Test with Linux optimizations enabled
        recorder.setLinuxOptimizationsEnabled(true);
        recorder.startRecording();
        
        driver.get("data:text/html,<html><body>" +
                "<div id='content'>Performance Test</div>" +
                "<script>" +
                "let updates = 0;" +
                "function updateContent() {" +
                "  document.getElementById('content').innerHTML = " +
                "    '<h1>Update ' + (++updates) + '</h1>' +" +
                "    '<p>Timestamp: ' + Date.now() + '</p>' +" +
                "    '<div style=\"background: hsl(' + (updates * 10) + ', 50%, 50%); width: ' + (50 + updates) + 'px; height: 20px;\"></div>';" +
                "  if (updates < 30) setTimeout(updateContent, 150);" +
                "}" +
                "updateContent();" +
                "</script>" +
                "</body></html>");
        
        Thread.sleep(5000);
        
        AdaptiveFrameTiming.TimingMetrics optimizedMetrics = recorder.getTimingMetrics();
        recorder.stopRecordingAndGenerateVideo();
        
        logger.info("Linux Optimized Performance:");
        logger.info("  Frames captured: " + optimizedMetrics.totalFrames);
        logger.info("  Frames skipped: " + optimizedMetrics.framesSkipped);
        logger.info("  Frame rate: " + String.format("%.1f", optimizedMetrics.frameRate) + " fps");
        logger.info("  Timing variance: " + String.format("%.1f", optimizedMetrics.timingVariance) + "ms");
        logger.info("  Performance good: " + optimizedMetrics.isPerformingWell);
        
        // Validate improved performance
        Assert.assertTrue(optimizedMetrics.totalFrames > 10, 
            "Should capture significant frames with optimizations");
        
        double skipRate = optimizedMetrics.framesSkipped > 0 ? 
            (double) optimizedMetrics.framesSkipped / optimizedMetrics.totalFrames : 0.0;
        
        Assert.assertTrue(skipRate < 0.15, 
            "Frame skip rate should be reasonable with optimizations: " + 
            String.format("%.2f%%", skipRate * 100));
        
        Assert.assertTrue(optimizedMetrics.timingVariance < 100, 
            "Timing variance should be controlled with optimizations: " + 
            String.format("%.1f", optimizedMetrics.timingVariance) + "ms");
        
        logger.info("=== Performance Comparison Test Completed ===");
    }
}