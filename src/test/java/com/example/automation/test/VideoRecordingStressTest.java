package com.example.automation.test;

import com.example.automation.logger.LoggerMechanism;
import com.example.automation.util.VideoRecordInHeadless;
import com.example.automation.util.VideoRecordWithSpeedControl;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Video Recording Stress Test Suite
 * 
 * This test class is designed to stress the video recording system under extreme conditions
 * to identify performance bottlenecks, memory leaks, and edge cases that could cause flickering.
 * 
 * Test scenarios include:
 * - Extreme duration recording (marathon tests)
 * - High frequency frame capture
 * - Multiple simultaneous recording sessions
 * - Resource exhaustion scenarios
 * - Recovery from failure conditions
 * - Platform-specific stress tests
 */
public class VideoRecordingStressTest {

    private WebDriver driver;
    private VideoRecordInHeadless videoRecorder;
    private VideoRecordWithSpeedControl speedVideoRecorder;
    private LoggerMechanism logger;

    @BeforeMethod
    public void setUp() {
        logger = new LoggerMechanism(VideoRecordingStressTest.class);
        
        try {
            logger.info("Setting up Video Recording Stress Test Environment...");
            WebDriverManager.chromedriver().setup();
            
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--remote-debugging-port=9223"); // Different port for stress tests
            options.addArguments("--disable-web-security");
            options.addArguments("--disable-extensions");
            options.addArguments("--disable-plugins");
            
            // Stress test specific optimizations
            options.addArguments("--max_old_space_size=4096");
            options.addArguments("--disable-background-timer-throttling");
            options.addArguments("--disable-backgrounding-occluded-windows");
            options.addArguments("--disable-renderer-backgrounding");
            options.addArguments("--disable-hang-monitor");
            
            driver = new ChromeDriver(options);
            videoRecorder = new VideoRecordInHeadless(logger, driver);
            speedVideoRecorder = new VideoRecordWithSpeedControl(logger, driver);
            
            logger.info("Video Recording Stress Test setup completed successfully");
            
        } catch (Exception e) {
            logger.error("Failed to setup stress test environment", e);
            throw new RuntimeException("Stress test setup failed: " + e.getMessage(), e);
        }
    }

    @Test(timeOut = 300000) // 5 minute timeout
    public void testExtremeDurationRecording() throws Exception {
        logger.info("=== Testing Extreme Duration Recording (Marathon Test) ===");
        
        try {
            speedVideoRecorder.setVideoSpeed(VideoRecordWithSpeedControl.VideoSpeed.FAST);
            speedVideoRecorder.setCaptureInterval(1000); // Capture every second
            speedVideoRecorder.startRecording();
            logger.info("Started marathon recording session");
            
            driver.get("data:text/html;charset=utf-8,<html><body><h1>Marathon Recording Test</h1><div id='counter'>0</div><div id='timestamp'></div></body></html>");
            Thread.sleep(2000);
            
            JavascriptExecutor js = (JavascriptExecutor) driver;
            int updateCount = 0;
            long startTime = System.currentTimeMillis();
            
            // Run for approximately 3 minutes with continuous updates
            while (System.currentTimeMillis() - startTime < 180000) { // 3 minutes
                updateCount++;
                
                js.executeScript(
                    "document.getElementById('counter').innerHTML = '" + updateCount + "';" +
                    "document.getElementById('timestamp').innerHTML = new Date().toLocaleString();" +
                    "document.body.style.backgroundColor = '#' + Math.floor(Math.random()*16777215).toString(16);"
                );
                
                // Log progress every 30 seconds
                if (updateCount % 30 == 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    logger.info("Marathon test progress: " + updateCount + " updates, " + (elapsed / 1000) + " seconds elapsed");
                }
                
                Thread.sleep(1000);
            }
            
            long totalDuration = System.currentTimeMillis() - startTime;
            logger.info("Marathon test completed: " + updateCount + " updates over " + (totalDuration / 1000) + " seconds");
            
        } catch (Exception e) {
            logger.error("Extreme duration recording test failed", e);
            throw e;
        }
    }

    @Test
    public void testHighFrequencyFrameCapture() throws Exception {
        logger.info("=== Testing High Frequency Frame Capture ===");
        
        try {
            speedVideoRecorder.setVideoSpeed(VideoRecordWithSpeedControl.VideoSpeed.REAL_TIME);
            speedVideoRecorder.setCaptureInterval(50); // Very high frequency - every 50ms
            speedVideoRecorder.startRecording();
            logger.info("Started high frequency frame capture (50ms intervals)");
            
            // Create rapidly changing content
            driver.get("data:text/html;charset=utf-8,<html><head><style>.spinner{width:100px;height:100px;border:5px solid #f3f3f3;border-top:5px solid #3498db;border-radius:50%;animation:spin 0.1s linear infinite;}@keyframes spin{0%{transform:rotate(0deg);}100%{transform:rotate(360deg);}}</style></head><body><h1>High Frequency Capture Test</h1><div class='spinner'></div><div id='rapid-counter'>0</div></body></html>");
            Thread.sleep(1000);
            
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            // Rapidly changing counter to stress frame capture
            for (int i = 0; i < 1000; i++) {
                js.executeScript(
                    "document.getElementById('rapid-counter').innerHTML = '" + i + "';" +
                    "document.getElementById('rapid-counter').style.color = '#' + Math.floor(Math.random()*16777215).toString(16);"
                );
                Thread.sleep(25); // Update every 25ms - faster than capture interval
            }
            
            logger.info("Completed high frequency frame capture test with 1000 rapid updates");
            
        } catch (Exception e) {
            logger.error("High frequency frame capture test failed", e);
            throw e;
        }
    }

    @Test
    public void testMultipleTabsStressTest() throws Exception {
        logger.info("=== Testing Multiple Tabs Stress Test ===");
        
        try {
            videoRecorder.setAutoRebindEnabled(true);
            videoRecorder.startRecording();
            logger.info("Started multi-tab stress test with auto-rebind");
            
            // Initial tab
            driver.get("https://example.com");
            Thread.sleep(1000);
            
            String originalWindow = driver.getWindowHandle();
            List<String> allWindows = new ArrayList<>();
            allWindows.add(originalWindow);
            
            // Open many tabs rapidly
            logger.info("Opening 10 tabs rapidly...");
            for (int i = 0; i < 10; i++) {
                String url = "https://httpbin.org/html?tab=" + i;
                ((JavascriptExecutor) driver).executeScript("window.open('" + url + "', '_blank');");
                Thread.sleep(200);
                
                // Track new windows
                for (String windowHandle : driver.getWindowHandles()) {
                    if (!allWindows.contains(windowHandle)) {
                        allWindows.add(windowHandle);
                    }
                }
            }
            
            logger.info("Opened " + (allWindows.size() - 1) + " additional tabs");
            
            // Rapidly switch between all tabs
            logger.info("Rapidly switching between all tabs...");
            for (int cycle = 0; cycle < 3; cycle++) {
                for (String window : allWindows) {
                    driver.switchTo().window(window);
                    Thread.sleep(300);
                    
                    // Perform action in each tab
                    ((JavascriptExecutor) driver).executeScript(
                        "document.body.innerHTML += '<p>Cycle " + cycle + " - Visit at ' + new Date().toLocaleTimeString() + '</p>';" +
                        "document.body.style.backgroundColor = '#' + Math.floor(Math.random()*16777215).toString(16);"
                    );
                    Thread.sleep(200);
                }
                logger.info("Completed switching cycle " + (cycle + 1));
            }
            
            logger.info("Completed multiple tabs stress test");
            
        } catch (Exception e) {
            logger.error("Multiple tabs stress test failed", e);
            throw e;
        }
    }

    @Test
    public void testMemoryExhaustionStressTest() throws Exception {
        logger.info("=== Testing Memory Exhaustion Stress Test ===");
        
        try {
            speedVideoRecorder.setVideoSpeed(VideoRecordWithSpeedControl.VideoSpeed.FAST);
            speedVideoRecorder.startRecording();
            logger.info("Started memory exhaustion stress test");
            
            driver.get("data:text/html;charset=utf-8,<html><body><h1>Memory Exhaustion Stress Test</h1><div id='memory-container'></div><div id='stats'></div></body></html>");
            Thread.sleep(1000);
            
            JavascriptExecutor js = (JavascriptExecutor) driver;
            int totalElements = 0;
            
            // Continuously add elements until we reach a reasonable limit
            logger.info("Creating massive DOM with thousands of elements...");
            for (int batch = 0; batch < 20; batch++) {
                StringBuilder massiveHTML = new StringBuilder();
                
                for (int i = 0; i < 100; i++) {
                    totalElements++;
                    massiveHTML.append("<div style='padding:5px;margin:2px;border:1px solid #ccc;background:#")
                              .append(String.format("%06x", (int)(Math.random() * 0xFFFFFF)))
                              .append(";'>Element ")
                              .append(totalElements)
                              .append(" - Batch ")
                              .append(batch)
                              .append(" - ")
                              .append(System.nanoTime())
                              .append("</div>");
                }
                
                js.executeScript(
                    "document.getElementById('memory-container').innerHTML += \"" + massiveHTML.toString() + "\";" +
                    "document.getElementById('stats').innerHTML = 'Total Elements: " + totalElements + ", Batch: " + (batch + 1) + "';"
                );
                
                Thread.sleep(500);
                
                // Occasionally trigger cleanup
                if (batch % 5 == 4) {
                    js.executeScript(
                        "var elements = document.getElementById('memory-container').children;" +
                        "for (var i = 0; i < Math.min(200, elements.length); i++) {" +
                        "  elements[0].remove();" +
                        "}"
                    );
                    logger.info("Performed cleanup after batch " + (batch + 1));
                }
                
                logger.info("Completed memory batch " + (batch + 1) + ", total elements: " + totalElements);
            }
            
            logger.info("Completed memory exhaustion stress test with " + totalElements + " total elements created");
            
        } catch (Exception e) {
            logger.error("Memory exhaustion stress test failed", e);
            throw e;
        }
    }

    @Test
    public void testRecordingFailureRecovery() throws Exception {
        logger.info("=== Testing Recording Failure Recovery ===");
        
        try {
            speedVideoRecorder.startRecording();
            logger.info("Started recording for failure recovery test");
            
            driver.get("data:text/html;charset=utf-8,<html><body><h1>Failure Recovery Test</h1><div id='status'>Normal Operation</div></body></html>");
            Thread.sleep(1000);
            
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            // Normal operation phase
            for (int i = 0; i < 5; i++) {
                js.executeScript("document.getElementById('status').innerHTML = 'Normal Operation " + i + " - ' + new Date().toLocaleTimeString();");
                Thread.sleep(500);
            }
            
            // Simulate failure conditions
            logger.info("Simulating failure conditions...");
            
            // 1. JavaScript errors
            try {
                js.executeScript("nonexistentFunction();");
            } catch (Exception e) {
                logger.info("Expected JavaScript error occurred: " + e.getMessage());
            }
            
            js.executeScript("document.getElementById('status').innerHTML = 'Recovered from JS error';");
            Thread.sleep(1000);
            
            // 2. DOM manipulation errors
            try {
                js.executeScript("document.getElementById('nonexistent').innerHTML = 'This should fail';");
            } catch (Exception e) {
                logger.info("Expected DOM error occurred: " + e.getMessage());
            }
            
            js.executeScript("document.getElementById('status').innerHTML = 'Recovered from DOM error';");
            Thread.sleep(1000);
            
            // 3. Network timeout simulation
            js.executeScript(
                "var img = document.createElement('img');" +
                "img.src = 'https://httpstat.us/408';" +
                "img.onerror = function() { document.getElementById('status').innerHTML = 'Network error handled'; };" +
                "document.body.appendChild(img);"
            );
            Thread.sleep(3000);
            
            // Continue normal operation after failures
            logger.info("Resuming normal operation after failures...");
            for (int i = 0; i < 5; i++) {
                js.executeScript("document.getElementById('status').innerHTML = 'Post-Recovery Operation " + i + " - ' + new Date().toLocaleTimeString();");
                Thread.sleep(500);
            }
            
            logger.info("Completed recording failure recovery test");
            
        } catch (Exception e) {
            logger.error("Recording failure recovery test failed", e);
            throw e;
        }
    }

    @Test
    public void testConcurrentRecordingStress() throws Exception {
        logger.info("=== Testing Concurrent Recording Stress ===");
        
        try {
            // Start both recorders simultaneously
            CountDownLatch startLatch = new CountDownLatch(2);
            AtomicInteger errorCount = new AtomicInteger(0);
            
            ExecutorService executor = Executors.newFixedThreadPool(2);
            
            Future<?> recorder1Task = executor.submit(() -> {
                try {
                    videoRecorder.startRecording();
                    startLatch.countDown();
                    logger.info("Primary recorder started");
                } catch (Exception e) {
                    logger.error("Primary recorder failed to start", e);
                    errorCount.incrementAndGet();
                    startLatch.countDown();
                }
            });
            
            Future<?> recorder2Task = executor.submit(() -> {
                try {
                    Thread.sleep(200); // Slight delay
                    speedVideoRecorder.startRecording();
                    startLatch.countDown();
                    logger.info("Secondary recorder started");
                } catch (Exception e) {
                    logger.error("Secondary recorder failed to start", e);
                    errorCount.incrementAndGet();
                    startLatch.countDown();
                }
            });
            
            // Wait for both recorders to start
            startLatch.await(30, TimeUnit.SECONDS);
            
            if (errorCount.get() > 0) {
                logger.warn("Some recorders failed to start, continuing with available recorders");
            }
            
            // Perform intensive operations with concurrent recording
            driver.get("data:text/html;charset=utf-8,<html><body><h1>Concurrent Recording Stress Test</h1><div id='operations'></div></body></html>");
            Thread.sleep(1000);
            
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            // Intensive concurrent operations
            for (int i = 0; i < 50; i++) {
                js.executeScript(
                    "var div = document.createElement('div');" +
                    "div.innerHTML = 'Concurrent Operation " + i + " - ' + new Date().toLocaleTimeString();" +
                    "div.style.cssText = 'padding:10px;margin:5px;border:2px solid #' + Math.floor(Math.random()*16777215).toString(16) + ';';" +
                    "document.getElementById('operations').appendChild(div);"
                );
                Thread.sleep(100);
                
                // Occasionally perform cleanup
                if (i % 10 == 9) {
                    js.executeScript(
                        "var ops = document.getElementById('operations');" +
                        "while (ops.children.length > 15) ops.removeChild(ops.firstChild);"
                    );
                }
            }
            
            executor.shutdown();
            logger.info("Completed concurrent recording stress test");
            
        } catch (Exception e) {
            logger.error("Concurrent recording stress test failed", e);
            throw e;
        }
    }

    @Test
    public void testPlatformSpecificStress() throws Exception {
        logger.info("=== Testing Platform-Specific Stress Scenarios ===");
        
        try {
            speedVideoRecorder.setVideoSpeed(VideoRecordWithSpeedControl.VideoSpeed.REAL_TIME);
            speedVideoRecorder.startRecording();
            
            String os = System.getProperty("os.name").toLowerCase();
            logger.info("Running platform-specific stress test for: " + os);
            
            driver.get("data:text/html;charset=utf-8,<html><body><h1>Platform Stress Test</h1><div id='platform-info'></div><div id='stress-results'></div></body></html>");
            Thread.sleep(1000);
            
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            // Display platform information
            js.executeScript(
                "document.getElementById('platform-info').innerHTML = " +
                "'OS: " + os + "<br>" +
                "Java Version: " + System.getProperty("java.version") + "<br>" +
                "Available Processors: " + Runtime.getRuntime().availableProcessors() + "<br>" +
                "Max Memory: ' + (Runtime.getRuntime().maxMemory() / 1024 / 1024) + ' MB<br>" +
                "Free Memory: ' + (Runtime.getRuntime().freeMemory() / 1024 / 1024) + ' MB';"
            );
            Thread.sleep(2000);
            
            // Platform-specific stress operations
            if (os.contains("mac")) {
                logger.info("Running macOS-specific stress operations");
                for (int i = 0; i < 30; i++) {
                    js.executeScript(
                        "document.getElementById('stress-results').innerHTML += " +
                        "'<p>macOS Stress Operation " + i + " - Memory: ' + " +
                        "(Runtime.getRuntime().totalMemory() / 1024 / 1024) + ' MB</p>';"
                    );
                    Thread.sleep(200);
                }
            } else if (os.contains("win")) {
                logger.info("Running Windows-specific stress operations");
                for (int i = 0; i < 30; i++) {
                    js.executeScript(
                        "document.getElementById('stress-results').innerHTML += " +
                        "'<p>Windows Stress Operation " + i + " - Timestamp: ' + Date.now() + '</p>';"
                    );
                    Thread.sleep(200);
                }
            } else {
                logger.info("Running generic Linux/Unix stress operations");
                for (int i = 0; i < 30; i++) {
                    js.executeScript(
                        "document.getElementById('stress-results').innerHTML += " +
                        "'<p>Linux Stress Operation " + i + " - Random: ' + Math.random() + '</p>';"
                    );
                    Thread.sleep(200);
                }
            }
            
            logger.info("Completed platform-specific stress test for " + os);
            
        } catch (Exception e) {
            logger.error("Platform-specific stress test failed", e);
            throw e;
        }
    }

    @Test
    public void testResourceCleanupStress() throws Exception {
        logger.info("=== Testing Resource Cleanup Stress ===");
        
        try {
            // Test multiple start/stop cycles
            for (int cycle = 0; cycle < 5; cycle++) {
                logger.info("Starting cleanup stress cycle " + (cycle + 1));
                
                speedVideoRecorder.setVideoSpeed(VideoRecordWithSpeedControl.VideoSpeed.FAST);
                speedVideoRecorder.startRecording();
                
                driver.get("data:text/html;charset=utf-8,<html><body><h1>Cleanup Stress Cycle " + (cycle + 1) + "</h1><div id='cycle-operations'></div></body></html>");
                Thread.sleep(1000);
                
                JavascriptExecutor js = (JavascriptExecutor) driver;
                
                // Quick operations
                for (int i = 0; i < 10; i++) {
                    js.executeScript(
                        "document.getElementById('cycle-operations').innerHTML += " +
                        "'<div>Cycle " + (cycle + 1) + " Operation " + i + " - ' + new Date().toLocaleTimeString() + '</div>';"
                    );
                    Thread.sleep(100);
                }
                
                // Stop recording
                speedVideoRecorder.stopRecordingAndGenerateVideo();
                Thread.sleep(1000);
                
                logger.info("Completed cleanup stress cycle " + (cycle + 1));
            }
            
            logger.info("Completed resource cleanup stress test with 5 cycles");
            
        } catch (Exception e) {
            logger.error("Resource cleanup stress test failed", e);
            throw e;
        }
    }

    @AfterMethod
    public void tearDown() throws Exception {
        try {
            logger.info("Starting stress test cleanup");
            
            // Stop both recorders if they were started
            if (videoRecorder != null) {
                try {
                    videoRecorder.stopRecordingAndGenerateVideo();
                    videoRecorder.cleanup();
                } catch (Exception e) {
                    logger.error("Error stopping videoRecorder during stress test cleanup", e);
                }
            }
            
            if (speedVideoRecorder != null) {
                try {
                    speedVideoRecorder.stopRecordingAndGenerateVideo();
                    speedVideoRecorder.cleanup();
                } catch (Exception e) {
                    logger.error("Error stopping speedVideoRecorder during stress test cleanup", e);
                }
            }
            
            // Force cleanup
            VideoRecordInHeadless.clearDEVTOOLS();
            VideoRecordWithSpeedControl.clearDEVTOOLS();
            
            // Force garbage collection
            System.gc();
            Thread.sleep(1000);
            
        } catch (Exception e) {
            logger.error("Error during stress test cleanup", e);
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                    logger.info("WebDriver closed after stress test");
                } catch (Exception e) {
                    logger.error("Error closing WebDriver after stress test", e);
                }
            }
        }
    }
}