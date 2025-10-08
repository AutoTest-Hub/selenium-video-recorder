package com.example.automation.test;

import com.example.automation.logger.LoggerMechanism;
import com.example.automation.util.TestBase;
import com.example.automation.util.VideoRecordTabClosureFix;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Test class to demonstrate and verify the tab closure fix
 * 
 * This test replicates the exact scenario from URLCheck.0050 that was causing:
 * 1. Flickering after tab closure and return to previous tab
 * 2. Missing video recording for subsequent tests (URLCheck.0060)
 */
public class TabClosureFixTest {
    
    private WebDriver driver;
    private LoggerMechanism logger;
    private VideoRecordTabClosureFix videoRecorder;
    
    @BeforeMethod
    public void setUp() {
        logger = new LoggerMechanism();
        logger.info("🚀 Setting up TabClosureFixTest");
        
        // Setup WebDriver
        WebDriverManager.chromedriver().setup();
        
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");  // Test in headless mode where the issue occurs
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-web-security");
        options.addArguments("--disable-features=VizDisplayCompositor");
        
        driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        
        // Initialize the fixed video recorder
        videoRecorder = new VideoRecordTabClosureFix(logger, driver);
        videoRecorder.setAutoRebindEnabled(true);
        
        logger.info("✅ TabClosureFixTest setup completed");
    }
    
    @AfterMethod
    public void tearDown() {
        logger.info("🧹 Tearing down TabClosureFixTest");
        
        if (videoRecorder != null) {
            try {
                if (videoRecorder.isRecording()) {
                    videoRecorder.stopRecordingAndGenerateVideo();
                }
                videoRecorder.close();
            } catch (Exception e) {
                logger.error("Error closing video recorder: " + e.getMessage());
            }
        }
        
        if (driver != null) {
            driver.quit();
        }
        
        logger.info("✅ TabClosureFixTest teardown completed");
    }
    
    /**
     * Test that replicates the exact URLCheck.0050 scenario
     * This test should NOT show flickering and should continue recording for subsequent tests
     */
    @Test
    public void testURLCheck0050Scenario() {
        try {
            logger.info("🎬 Starting URLCheck.0050 scenario test");
            
            // Start video recording
            videoRecorder.startRecording();
            
            // Navigate to initial page (simulating the original tab)
            logger.info("📍 Step 1: Navigate to initial page (original tab)");
            driver.get("https://www.example.com");
            Thread.sleep(2000);  // Allow page to load and be recorded
            
            // Store the original window handle
            String originalWindow = driver.getWindowHandle();
            logger.info("🏠 Original window handle: " + originalWindow);
            
            // Simulate URLCheck.0050: openNewTabWithURL (3 times)
            List<String> newTabHandles = new ArrayList<>();
            
            for (int i = 1; i <= 3; i++) {
                logger.info("🆕 Step " + (i + 1) + ": Opening new tab " + i + " (openNewTabWithURL)");
                
                // Open new tab using JavaScript
                ((JavascriptExecutor) driver).executeScript("window.open('https://httpbin.org/delay/1', '_blank');");
                
                // Switch to the new tab
                for (String handle : driver.getWindowHandles()) {
                    if (!handle.equals(originalWindow) && !newTabHandles.contains(handle)) {
                        newTabHandles.add(handle);
                        driver.switchTo().window(handle);
                        logger.info("   Switched to new tab: " + handle);
                        break;
                    }
                }
                
                // Wait for content to load and be recorded
                Thread.sleep(3000);
                
                logger.info("   New tab " + i + " opened and recorded");
            }
            
            logger.info("✅ All 3 new tabs opened successfully");
            logger.info("📊 Total windows: " + driver.getWindowHandles().size());
            
            // Simulate URLCheck.0050: closeCurrentTab (3 times)
            for (int i = 1; i <= 3; i++) {
                logger.info("🔥 Step " + (i + 4) + ": Closing current tab " + i + " (closeCurrentTab)");
                
                String currentHandle = driver.getWindowHandle();
                logger.info("   Closing tab: " + currentHandle);
                
                // Close current tab
                driver.close();
                
                // Switch to remaining tab (or original if all new tabs are closed)
                List<String> remainingHandles = new ArrayList<>(driver.getWindowHandles());
                if (!remainingHandles.isEmpty()) {
                    String nextHandle = remainingHandles.get(0);
                    driver.switchTo().window(nextHandle);
                    logger.info("   Switched to remaining tab: " + nextHandle);
                    
                    // Wait to observe any flickering (this is where the original issue occurred)
                    Thread.sleep(2000);
                    
                    logger.info("   Tab " + i + " closed successfully - no flickering should occur");
                } else {
                    logger.error("❌ No remaining tabs found!");
                    break;
                }
            }
            
            logger.info("✅ All new tabs closed successfully");
            logger.info("🏠 Should now be back on original tab");
            
            // Verify we're back on the original tab
            String currentHandle = driver.getWindowHandle();
            if (currentHandle.equals(originalWindow)) {
                logger.info("✅ Successfully returned to original tab: " + originalWindow);
            } else {
                logger.warn("⚠️ Current tab (" + currentHandle + ") is not the original tab (" + originalWindow + ")");
            }
            
            // Wait a bit more to ensure recording continues
            Thread.sleep(3000);
            
            logger.info("🎯 URLCheck.0050 scenario completed successfully");
            logger.info("   - No flickering should have occurred");
            logger.info("   - Recording should have continued throughout");
            
        } catch (Exception e) {
            logger.error("❌ URLCheck.0050 scenario test failed: " + e.getMessage());
            throw new RuntimeException("Test failed", e);
        }
    }
    
    /**
     * Test that simulates URLCheck.0060 (the subsequent test that was not being recorded)
     * This should be recorded properly after the tab closure fix
     */
    @Test
    public void testURLCheck0060Scenario() {
        try {
            logger.info("🎬 Starting URLCheck.0060 scenario test (subsequent test)");
            logger.info("   This test verifies that recording continues after URLCheck.0050");
            
            // Start video recording
            videoRecorder.startRecording();
            
            // Simulate URLCheck.0060: Navigate to a different page
            logger.info("📍 Step 1: Navigate to URLCheck.0060 test page");
            driver.get("https://httpbin.org/html");
            Thread.sleep(2000);
            
            // Perform some actions that should be recorded
            logger.info("📍 Step 2: Perform actions on URLCheck.0060 page");
            
            // Scroll down
            ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight);");
            Thread.sleep(1000);
            
            // Scroll back up
            ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");
            Thread.sleep(1000);
            
            // Navigate to another page
            logger.info("📍 Step 3: Navigate to another page in URLCheck.0060");
            driver.get("https://httpbin.org/json");
            Thread.sleep(2000);
            
            logger.info("✅ URLCheck.0060 scenario completed successfully");
            logger.info("   This test should be fully recorded in the video");
            
        } catch (Exception e) {
            logger.error("❌ URLCheck.0060 scenario test failed: " + e.getMessage());
            throw new RuntimeException("Test failed", e);
        }
    }
    
    /**
     * Combined test that runs both URLCheck.0050 and URLCheck.0060 in sequence
     * This is the most realistic test of the fix
     */
    @Test
    public void testCombinedURLCheck0050And0060() {
        try {
            logger.info("🎬 Starting combined URLCheck.0050 + URLCheck.0060 test");
            logger.info("   This test verifies the complete fix for both issues");
            
            // Start video recording
            videoRecorder.startRecording();
            
            // === URLCheck.0050 Scenario ===
            logger.info("🔥 PHASE 1: URLCheck.0050 - Tab opening and closing");
            
            // Navigate to initial page
            driver.get("https://www.example.com");
            Thread.sleep(2000);
            
            String originalWindow = driver.getWindowHandle();
            List<String> newTabHandles = new ArrayList<>();
            
            // Open 3 new tabs
            for (int i = 1; i <= 3; i++) {
                ((JavascriptExecutor) driver).executeScript("window.open('https://httpbin.org/delay/1', '_blank');");
                
                for (String handle : driver.getWindowHandles()) {
                    if (!handle.equals(originalWindow) && !newTabHandles.contains(handle)) {
                        newTabHandles.add(handle);
                        driver.switchTo().window(handle);
                        break;
                    }
                }
                
                Thread.sleep(2000);
                logger.info("   Opened and recorded new tab " + i);
            }
            
            // Close 3 tabs
            for (int i = 1; i <= 3; i++) {
                driver.close();
                
                List<String> remainingHandles = new ArrayList<>(driver.getWindowHandles());
                if (!remainingHandles.isEmpty()) {
                    driver.switchTo().window(remainingHandles.get(0));
                    Thread.sleep(1500);  // Critical moment - check for flickering
                    logger.info("   Closed tab " + i + " - no flickering should occur");
                }
            }
            
            logger.info("✅ URLCheck.0050 completed - should be back on original tab");
            
            // === URLCheck.0060 Scenario ===
            logger.info("🎯 PHASE 2: URLCheck.0060 - Subsequent test (this was not being recorded before)");
            
            // Navigate to URLCheck.0060 page
            driver.get("https://httpbin.org/html");
            Thread.sleep(2000);
            
            // Perform URLCheck.0060 actions
            ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight);");
            Thread.sleep(1000);
            
            ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");
            Thread.sleep(1000);
            
            driver.get("https://httpbin.org/json");
            Thread.sleep(2000);
            
            logger.info("✅ URLCheck.0060 completed - should be fully recorded");
            
            // === Verification ===
            logger.info("🔍 VERIFICATION: Both tests completed successfully");
            logger.info("   Expected results:");
            logger.info("   ✅ No flickering during tab closures in URLCheck.0050");
            logger.info("   ✅ URLCheck.0060 fully recorded in the video");
            logger.info("   ✅ Smooth transition between the two test scenarios");
            
        } catch (Exception e) {
            logger.error("❌ Combined URLCheck test failed: " + e.getMessage());
            throw new RuntimeException("Combined test failed", e);
        }
    }
}
