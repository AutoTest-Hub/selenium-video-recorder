package com.example.automation.test;

import com.example.automation.logger.LoggerMechanism;
import com.example.automation.util.VideoRecordInHeadless;
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
 * Comprehensive test suite for Selenium video recording in headless Chrome.
 * 
 * Tests the core functionality:
 * - Single tab recording
 * - Multi-tab recording with automatic switching
 * - Manual tab switching
 * 
 * This test class uses the original VideoRecordInHeadless class that fixes
 * the multi-tab recording deadlock issue in headless Chrome.
 */
public class VideoRecordingTest {

    private WebDriver driver;
    private VideoRecordInHeadless videoRecorder;
    private LoggerMechanism logger;

    @BeforeMethod
    public void setUp() {
        logger = new LoggerMechanism(VideoRecordingTest.class);
        
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
            videoRecorder = new VideoRecordInHeadless(logger, driver);
            
            logger.info("Test setup completed successfully");
            
        } catch (Exception e) {
            logger.error("Failed to setup test environment", e);
            throw new RuntimeException("Test setup failed: " + e.getMessage(), e);
        }
    }

    @Test
    public void testSingleTabVideoRecording() throws Exception {
        logger.info("=== Testing single tab video recording ===");
        
        try {
            videoRecorder.startRecording();
            logger.info("Started video recording");
            
            // Navigate to a test page
            driver.get("https://example.com");
            logger.info("Navigated to example.com");
            Thread.sleep(3000);
            
            // Perform some interactions
            ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 200);");
            logger.info("Scrolled down");
            Thread.sleep(2000);
            
            // Navigate to another page
            driver.get("https://httpbin.org/html");
            logger.info("Navigated to httpbin.org/html");
            Thread.sleep(3000);
            
            logger.info("=== Single tab recording test completed ===");
            
        } catch (Exception e) {
            logger.error("Single tab recording test failed", e);
            throw e;
        }
    }

    @Test
    public void testMultiTabVideoRecording() throws Exception {
        logger.info("=== Testing multi-tab video recording with auto-rebind ===");
        
        try {
            // Enable auto-rebind for automatic tab switching
            videoRecorder.setAutoRebindEnabled(true);
            videoRecorder.startRecording();
            logger.info("Started video recording with auto-rebind enabled");
            
            // Initial tab content
            driver.get("https://example.com");
            logger.info("Navigated to example.com in initial tab");
            Thread.sleep(3000);
            
            // Open new tab (this should trigger auto-rebind)
            logger.info("Opening new tab...");
            ((JavascriptExecutor) driver).executeScript("window.open('https://httpbin.org/html', '_blank');");
            
            // Wait for auto-rebind to complete
            Thread.sleep(4000);
            
            // Switch to new tab manually to continue test
            String originalWindow = driver.getWindowHandle();
            for (String windowHandle : driver.getWindowHandles()) {
                if (!windowHandle.equals(originalWindow)) {
                    driver.switchTo().window(windowHandle);
                    logger.info("Switched to new tab");
                    break;
                }
            }
            
            // Perform actions in new tab
            Thread.sleep(2000);
            ((JavascriptExecutor) driver).executeScript("document.body.style.backgroundColor = 'lightblue';");
            logger.info("Changed background color in new tab");
            Thread.sleep(3000);
            
            // Navigate in new tab
            driver.get("https://httpbin.org/json");
            logger.info("Navigated to JSON endpoint in new tab");
            Thread.sleep(3000);
            
            logger.info("=== Multi-tab recording test completed ===");
            
        } catch (Exception e) {
            logger.error("Multi-tab recording test failed", e);
            throw e;
        }
    }

    @Test
    public void testManualTabSwitching() throws Exception {
        logger.info("=== Testing manual tab switching ===");
        
        try {
            videoRecorder.startRecording();
            logger.info("Started video recording");
            
            // Initial tab content
            driver.get("https://example.com");
            logger.info("Navigated to example.com in initial tab");
            Thread.sleep(2000);
            
            // Open new tab
            logger.info("Opening new tab...");
            ((JavascriptExecutor) driver).executeScript("window.open('https://httpbin.org/forms/post', '_blank');");
            Thread.sleep(2000);
            
            // Manually switch recording to new tab
            logger.info("Manually switching recording to new tab...");
            videoRecorder.recordNewlyOpenedTab();
            
            // Switch WebDriver to new tab
            String originalWindow = driver.getWindowHandle();
            for (String windowHandle : driver.getWindowHandles()) {
                if (!windowHandle.equals(originalWindow)) {
                    driver.switchTo().window(windowHandle);
                    logger.info("Switched WebDriver to new tab");
                    break;
                }
            }
            
            // Perform actions in new tab
            Thread.sleep(2000);
            try {
                driver.findElement(By.name("custname")).sendKeys("Test User");
                Thread.sleep(1000);
                driver.findElement(By.name("custtel")).sendKeys("123-456-7890");
                Thread.sleep(1000);
                logger.info("Filled form fields in new tab");
            } catch (Exception e) {
                logger.info("Form elements not found, continuing with other actions");
            }
            
            // Navigate to another page
            driver.get("https://httpbin.org/uuid");
            logger.info("Navigated to UUID endpoint");
            Thread.sleep(3000);
            
            logger.info("=== Manual tab switching test completed ===");
            
        } catch (Exception e) {
            logger.error("Manual tab switching test failed", e);
            throw e;
        }
    }

    @AfterMethod
    public void tearDown() throws Exception {
        try {
            logger.info("Starting test cleanup");
            
            // Stop recording and generate video
            if (videoRecorder != null) {
                videoRecorder.stopRecordingAndGenerateVideo();
                logger.info("Video generation completed");
                
                videoRecorder.cleanup();
                logger.info("Video recorder cleanup completed");
            }
            
            VideoRecordInHeadless.clearDEVTOOLS();
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
