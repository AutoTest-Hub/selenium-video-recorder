package com.example.automation.test;

import com.example.automation.logger.LoggerMechanism;
import com.example.automation.util.VideoRecordWithSpeedControl;
import com.example.automation.util.VideoRecordWithSpeedControl.VideoSpeed;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Test suite for video recording with configurable playback speeds.
 * 
 * This class demonstrates how to fix the "fast-forward video" issue by
 * using different video speed configurations.
 * 
 * Tests include:
 * - Slow motion recording (2x slower - recommended)
 * - Real-time recording (matches execution time)
 * - Very slow recording (4x slower - for detailed analysis)
 * - Multi-tab recording with speed control
 */
public class VideoSpeedTest {

    private WebDriver driver;
    private VideoRecordWithSpeedControl videoRecorder;
    private LoggerMechanism logger;

    @BeforeMethod
    public void setUp() {
        logger = new LoggerMechanism(VideoSpeedTest.class);
        
        try {
            logger.info("Setting up WebDriverManager for Chrome...");
            WebDriverManager.chromedriver().setup();
            
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1280,780");
            options.addArguments("--remote-debugging-port=9222");
            options.addArguments("--disable-web-security");
            options.addArguments("--allow-running-insecure-content");
            options.addArguments("--disable-extensions");
            options.addArguments("--disable-plugins");
            
            // Platform-specific options
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                logger.info("Adding Windows-specific Chrome options");
                options.addArguments("--disable-features=VizDisplayCompositor");
                options.addArguments("--disable-background-timer-throttling");
                options.addArguments("--disable-backgrounding-occluded-windows");
                options.addArguments("--disable-renderer-backgrounding");
            } else if (os.contains("mac")) {
                logger.info("Detected macOS, adding macOS-specific Chrome options");
                options.addArguments("--disable-background-timer-throttling");
                options.addArguments("--disable-backgrounding-occluded-windows");
                options.addArguments("--disable-renderer-backgrounding");
            }
            
            driver = new ChromeDriver(options);
            videoRecorder = new VideoRecordWithSpeedControl(logger, driver);
            
            logger.info("Test setup completed successfully");
            
        } catch (Exception e) {
            logger.error("Failed to setup test environment", e);
            throw new RuntimeException("Test setup failed: " + e.getMessage(), e);
        }
    }

    @Test
    public void testSlowMotionVideo() throws Exception {
        logger.info("=== Testing SLOW MOTION video recording ===");
        
        try {
            // Configure for slow motion (2x slower than real-time)
            videoRecorder.setVideoSpeed(VideoSpeed.SLOW_MOTION);
            videoRecorder.setCaptureInterval(300); // Capture every 300ms
            
            videoRecorder.startRecording();
            logger.info("Started slow motion recording");
            
            // Navigate with clear timing
            driver.get("https://example.com");
            logger.info("Navigated to example.com - waiting 3 seconds");
            Thread.sleep(3000);
            
            // Scroll down slowly
            ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 200);");
            logger.info("Scrolled down - waiting 2 seconds");
            Thread.sleep(2000);
            
            // Navigate to another page
            driver.get("https://httpbin.org/html");
            logger.info("Navigated to httpbin.org/html - waiting 3 seconds");
            Thread.sleep(3000);
            
            // Interact with the page
            ((JavascriptExecutor) driver).executeScript(
                "document.body.style.backgroundColor = 'lightblue';"
            );
            logger.info("Changed background color - waiting 2 seconds");
            Thread.sleep(2000);
            
            logger.info("=== Slow motion recording test completed ===");
            
        } catch (Exception e) {
            logger.error("Slow motion recording test failed", e);
            throw e;
        }
    }

    @Test
    public void testRealTimeVideo() throws Exception {
        logger.info("=== Testing REAL-TIME video recording ===");
        
        try {
            // Configure for real-time playback
            videoRecorder.setVideoSpeed(VideoSpeed.REAL_TIME);
            videoRecorder.setCaptureInterval(200); // Capture every 200ms (5 FPS)
            
            videoRecorder.startRecording();
            logger.info("Started real-time recording");
            
            // Quick navigation sequence
            driver.get("https://httpbin.org/forms/post");
            logger.info("Navigated to forms page - waiting 2 seconds");
            Thread.sleep(2000);
            
            // Fill out a form quickly
            try {
                driver.findElement(By.name("custname")).sendKeys("Test User");
                Thread.sleep(1000);
                driver.findElement(By.name("custtel")).sendKeys("123-456-7890");
                Thread.sleep(1000);
                driver.findElement(By.name("custemail")).sendKeys("test@example.com");
                Thread.sleep(1000);
            } catch (Exception e) {
                logger.info("Form elements not found, continuing with other actions");
            }
            
            // Navigate to JSON endpoint
            driver.get("https://httpbin.org/json");
            logger.info("Navigated to JSON endpoint - waiting 2 seconds");
            Thread.sleep(2000);
            
            logger.info("=== Real-time recording test completed ===");
            
        } catch (Exception e) {
            logger.error("Real-time recording test failed", e);
            throw e;
        }
    }

    @Test
    public void testVerySlowVideo() throws Exception {
        logger.info("=== Testing VERY SLOW video recording ===");
        
        try {
            // Configure for very slow (4x slower)
            videoRecorder.setVideoSpeed(VideoSpeed.VERY_SLOW);
            videoRecorder.setCaptureInterval(250); // Capture every 250ms
            
            videoRecorder.startRecording();
            logger.info("Started very slow recording (4x slower)");
            
            // Demonstrate various interactions with timing
            driver.get("https://example.com");
            logger.info("Step 1: Loaded example.com - waiting 2 seconds");
            Thread.sleep(2000);
            
            // Add some visual changes
            ((JavascriptExecutor) driver).executeScript(
                "document.body.innerHTML += '<div style=\"position:fixed;top:10px;right:10px;background:red;color:white;padding:10px;border-radius:5px;\">Recording in Progress</div>';"
            );
            logger.info("Step 2: Added recording indicator - waiting 1.5 seconds");
            Thread.sleep(1500);
            
            // Scroll animation
            for (int i = 0; i < 5; i++) {
                ((JavascriptExecutor) driver).executeScript("window.scrollBy(0, 100);");
                Thread.sleep(500);
            }
            logger.info("Step 3: Completed scroll animation - waiting 1 second");
            Thread.sleep(1000);
            
            // Navigate to final page
            driver.get("https://httpbin.org/uuid");
            logger.info("Step 4: Navigated to UUID endpoint - waiting 2 seconds");
            Thread.sleep(2000);
            
            logger.info("=== Very slow recording test completed ===");
            
        } catch (Exception e) {
            logger.error("Very slow recording test failed", e);
            throw e;
        }
    }

    @Test
    public void testMultiTabSlowMotion() throws Exception {
        logger.info("=== Testing MULTI-TAB with SLOW MOTION ===");
        
        try {
            // Configure for slow motion to clearly see tab transitions
            videoRecorder.setVideoSpeed(VideoSpeed.SLOW_MOTION);
            videoRecorder.setCaptureInterval(400); // Capture every 400ms
            videoRecorder.setAutoRebindEnabled(true);
            
            videoRecorder.startRecording();
            logger.info("Started multi-tab slow motion recording");
            
            // Initial tab content
            driver.get("https://example.com");
            logger.info("Tab 1: Loaded example.com - waiting 3 seconds");
            Thread.sleep(3000);
            
            // Add visual indicator for tab 1
            ((JavascriptExecutor) driver).executeScript(
                "document.body.style.border = '10px solid blue';" +
                "document.body.innerHTML += '<h1 style=\"color:blue;text-align:center;\">TAB 1 - ORIGINAL</h1>';"
            );
            logger.info("Tab 1: Added visual indicator - waiting 2 seconds");
            Thread.sleep(2000);
            
            // Open new tab
            logger.info("Opening new tab...");
            ((JavascriptExecutor) driver).executeScript("window.open('https://httpbin.org/html', '_blank');");
            
            // Wait for auto-rebind
            Thread.sleep(3000);
            
            // Switch to new tab
            String originalWindow = driver.getWindowHandle();
            for (String windowHandle : driver.getWindowHandles()) {
                if (!windowHandle.equals(originalWindow)) {
                    driver.switchTo().window(windowHandle);
                    logger.info("Switched to new tab");
                    break;
                }
            }
            
            // Add visual indicator for tab 2
            ((JavascriptExecutor) driver).executeScript(
                "document.body.style.border = '10px solid red';" +
                "document.body.innerHTML += '<h1 style=\"color:red;text-align:center;\">TAB 2 - NEW TAB</h1>';"
            );
            logger.info("Tab 2: Added visual indicator - waiting 3 seconds");
            Thread.sleep(3000);
            
            // Navigate in new tab
            driver.get("https://httpbin.org/json");
            logger.info("Tab 2: Navigated to JSON endpoint - waiting 3 seconds");
            Thread.sleep(3000);
            
            logger.info("=== Multi-tab slow motion test completed ===");
            
        } catch (Exception e) {
            logger.error("Multi-tab slow motion test failed", e);
            throw e;
        }
    }

    @AfterMethod
    public void tearDown() throws Exception {
        try {
            logger.info("Starting test cleanup");
            
            // Stop recording and generate video with configured timing
            if (videoRecorder != null) {
                videoRecorder.stopRecordingAndGenerateVideo();
                logger.info("Video generation completed");
                
                videoRecorder.cleanup();
                logger.info("Video recorder cleanup completed");
            }
            
            VideoRecordWithSpeedControl.clearDEVTOOLS();
            logger.info("DevTools cleanup completed");
            
        } catch (Exception e) {
            logger.error("Error during cleanup", e);
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                    logger.info("WebDriver closed");
                } catch (Exception e) {
                    logger.error("Error closing WebDriver", e);
                }
            }
        }
    }
}
