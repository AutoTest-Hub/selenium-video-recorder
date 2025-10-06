package com.example.automation.test;

import com.example.automation.logger.LoggerMechanism;
import com.example.automation.util.VideoRecordInHeadless;
import com.example.automation.util.VideoRecordWithSpeedControl;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive Flickering Reproduction Test Suite
 * 
 * This test class is specifically designed to identify and reproduce various types 
 * of flickering issues that can occur during video recording with Selenium.
 * 
 * Types of flickering issues tested:
 * - Timing-based flickering (rapid state changes)
 * - Race condition flickering (concurrent operations)
 * - Visual artifacts (frame drops, corruption)
 * - Tab switching flickering
 * - Memory pressure flickering
 * - Network-induced flickering
 * - Animation-based flickering
 * - Resource contention flickering
 */
public class FlickeringReproductionTest {

    private WebDriver driver;
    private VideoRecordInHeadless videoRecorder;
    private VideoRecordWithSpeedControl speedVideoRecorder;
    private LoggerMechanism logger;
    private Actions actions;
    private WebDriverWait wait;

    @BeforeMethod
    public void setUp() {
        logger = new LoggerMechanism(FlickeringReproductionTest.class);
        
        try {
            logger.info("Setting up Flickering Reproduction Test Environment...");
            WebDriverManager.chromedriver().setup();
            
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--remote-debugging-port=9222");
            options.addArguments("--disable-web-security");
            options.addArguments("--allow-running-insecure-content");
            options.addArguments("--disable-extensions");
            options.addArguments("--disable-plugins");
            
            // Additional options to reduce potential flickering causes
            options.addArguments("--disable-background-timer-throttling");
            options.addArguments("--disable-backgrounding-occluded-windows");
            options.addArguments("--disable-renderer-backgrounding");
            options.addArguments("--disable-features=TranslateUI");
            options.addArguments("--disable-ipc-flooding-protection");
            
            driver = new ChromeDriver(options);
            actions = new Actions(driver);
            wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            
            // Initialize both video recorders for comparison tests
            videoRecorder = new VideoRecordInHeadless(logger, driver);
            speedVideoRecorder = new VideoRecordWithSpeedControl(logger, driver);
            
            logger.info("Flickering Reproduction Test setup completed successfully");
            
        } catch (Exception e) {
            logger.error("Failed to setup test environment", e);
            throw new RuntimeException("Test setup failed: " + e.getMessage(), e);
        }
    }

    @Test
    public void testRapidDOMChangesFlickering() throws Exception {
        logger.info("=== Testing Rapid DOM Changes Flickering ===");
        
        try {
            videoRecorder.startRecording();
            logger.info("Started recording for rapid DOM changes test");
            
            // Create a page with rapidly changing content
            driver.get("data:text/html;charset=utf-8,<html><head><style>body{font-family:Arial;padding:20px;}.box{width:200px;height:200px;margin:20px;display:inline-block;text-align:center;line-height:200px;font-size:24px;}</style></head><body><h1>Rapid DOM Changes Test</h1><div id='container'></div></body></html>");
            Thread.sleep(1000);
            
            // Rapidly add and remove elements to test flickering
            JavascriptExecutor js = (JavascriptExecutor) driver;
            for (int i = 0; i < 20; i++) {
                // Add elements rapidly
                js.executeScript(
                    "var container = document.getElementById('container');" +
                    "var box = document.createElement('div');" +
                    "box.className = 'box';" +
                    "box.style.backgroundColor = '#' + Math.floor(Math.random()*16777215).toString(16);" +
                    "box.innerText = '" + i + "';" +
                    "box.id = 'box' + " + i + ";" +
                    "container.appendChild(box);"
                );
                Thread.sleep(100);
                
                // Remove elements rapidly
                if (i > 5) {
                    js.executeScript("document.getElementById('box" + (i-5) + "')?.remove();");
                }
                Thread.sleep(50);
            }
            
            logger.info("Completed rapid DOM changes sequence");
            
        } catch (Exception e) {
            logger.error("Rapid DOM changes flickering test failed", e);
            throw e;
        }
    }

    @Test
    public void testCSSAnimationFlickering() throws Exception {
        logger.info("=== Testing CSS Animation Flickering ===");
        
        try {
            speedVideoRecorder.setVideoSpeed(VideoRecordWithSpeedControl.VideoSpeed.SLOW_MOTION);
            speedVideoRecorder.startRecording();
            
            // Create a page with multiple CSS animations
            String animatedHTML = "<html><head><style>" +
                "@keyframes bounce { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(-50px); } }" +
                "@keyframes rotate { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }" +
                "@keyframes fade { 0%, 100% { opacity: 1; } 50% { opacity: 0.1; } }" +
                "@keyframes scale { 0%, 100% { transform: scale(1); } 50% { transform: scale(1.5); } }" +
                ".bounce { animation: bounce 0.5s infinite; }" +
                ".rotate { animation: rotate 1s linear infinite; }" +
                ".fade { animation: fade 0.8s infinite; }" +
                ".scale { animation: scale 1.2s infinite; }" +
                ".box { width: 100px; height: 100px; margin: 20px; display: inline-block; }" +
                "</style></head><body>" +
                "<h1>CSS Animation Flickering Test</h1>" +
                "<div class='box bounce' style='background: red;'></div>" +
                "<div class='box rotate' style='background: blue;'></div>" +
                "<div class='box fade' style='background: green;'></div>" +
                "<div class='box scale' style='background: orange;'></div>" +
                "</body></html>";
            
            driver.get("data:text/html;charset=utf-8," + animatedHTML);
            logger.info("Loaded page with multiple CSS animations");
            
            // Let animations run while recording
            Thread.sleep(5000);
            
            // Add more animated elements dynamically
            JavascriptExecutor js = (JavascriptExecutor) driver;
            for (int i = 0; i < 10; i++) {
                js.executeScript(
                    "var box = document.createElement('div');" +
                    "box.className = 'box ' + ['bounce', 'rotate', 'fade', 'scale'][" + (i % 4) + "];" +
                    "box.style.backgroundColor = '#' + Math.floor(Math.random()*16777215).toString(16);" +
                    "document.body.appendChild(box);"
                );
                Thread.sleep(300);
            }
            
            logger.info("Added dynamic animated elements");
            Thread.sleep(3000);
            
        } catch (Exception e) {
            logger.error("CSS Animation flickering test failed", e);
            throw e;
        }
    }

    @Test
    public void testRapidTabSwitchingFlickering() throws Exception {
        logger.info("=== Testing Rapid Tab Switching Flickering ===");
        
        try {
            videoRecorder.setAutoRebindEnabled(true);
            videoRecorder.startRecording();
            
            // Initial tab
            driver.get("https://example.com");
            Thread.sleep(1000);
            
            String originalWindow = driver.getWindowHandle();
            
            // Rapidly open and switch between multiple tabs
            for (int i = 0; i < 5; i++) {
                logger.info("Opening tab " + (i + 1));
                
                // Open new tab
                ((JavascriptExecutor) driver).executeScript("window.open('https://httpbin.org/uuid', '_blank');");
                Thread.sleep(500);
                
                // Switch to new tab
                for (String windowHandle : driver.getWindowHandles()) {
                    if (!windowHandle.equals(originalWindow)) {
                        driver.switchTo().window(windowHandle);
                        break;
                    }
                }
                
                // Perform actions in new tab
                Thread.sleep(800);
                ((JavascriptExecutor) driver).executeScript(
                    "document.body.style.backgroundColor = '#' + Math.floor(Math.random()*16777215).toString(16);" +
                    "document.body.innerHTML += '<h1>Tab " + (i + 1) + " - ' + new Date().toLocaleTimeString() + '</h1>';"
                );
                Thread.sleep(700);
                
                // Rapidly switch back to original
                driver.switchTo().window(originalWindow);
                Thread.sleep(300);
                
                // Quick action in original tab
                ((JavascriptExecutor) driver).executeScript(
                    "document.body.innerHTML += '<p>Switched back from tab " + (i + 1) + "</p>';"
                );
                Thread.sleep(200);
            }
            
            logger.info("Completed rapid tab switching sequence");
            
        } catch (Exception e) {
            logger.error("Rapid tab switching flickering test failed", e);
            throw e;
        }
    }

    @Test
    public void testScrollingFlickering() throws Exception {
        logger.info("=== Testing Scrolling Flickering ===");
        
        try {
            speedVideoRecorder.setVideoSpeed(VideoRecordWithSpeedControl.VideoSpeed.SLOW_MOTION);
            speedVideoRecorder.setCaptureInterval(200);
            speedVideoRecorder.startRecording();
            
            // Create a long page with dynamic content
            StringBuilder longContent = new StringBuilder("<html><body><h1>Scrolling Flickering Test</h1>");
            for (int i = 0; i < 100; i++) {
                longContent.append("<div style='height:100px; border:1px solid #ccc; margin:10px; padding:20px; background:#f").append(i % 10).append("f").append(i % 10).append("f").append(i % 10).append(";'>Content Block ").append(i).append("</div>");
            }
            longContent.append("</body></html>");
            
            driver.get("data:text/html;charset=utf-8," + longContent.toString());
            Thread.sleep(1000);
            
            // Perform various scrolling patterns that might cause flickering
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            // Rapid scrolling down
            logger.info("Starting rapid scroll down");
            for (int i = 0; i < 20; i++) {
                js.executeScript("window.scrollBy(0, 200);");
                Thread.sleep(100);
            }
            
            // Rapid scrolling up
            logger.info("Starting rapid scroll up");
            for (int i = 0; i < 20; i++) {
                js.executeScript("window.scrollBy(0, -200);");
                Thread.sleep(100);
            }
            
            // Erratic scrolling (up and down rapidly)
            logger.info("Starting erratic scrolling");
            for (int i = 0; i < 30; i++) {
                int direction = (i % 2 == 0) ? 300 : -300;
                js.executeScript("window.scrollBy(0, " + direction + ");");
                Thread.sleep(80);
            }
            
            // Smooth scroll to specific positions
            logger.info("Starting smooth scroll to positions");
            int[] positions = {0, 2000, 5000, 1000, 8000, 500};
            for (int pos : positions) {
                js.executeScript("window.scrollTo({top: " + pos + ", behavior: 'smooth'});");
                Thread.sleep(800);
            }
            
            logger.info("Completed scrolling flickering test");
            
        } catch (Exception e) {
            logger.error("Scrolling flickering test failed", e);
            throw e;
        }
    }

    @Test
    public void testAjaxFlickering() throws Exception {
        logger.info("=== Testing AJAX Flickering ===");
        
        try {
            videoRecorder.startRecording();
            
            // Create a page that makes rapid AJAX calls
            String ajaxHTML = "<html><head><script>" +
                "function makeAjaxCall(id) {" +
                "  fetch('https://httpbin.org/delay/' + Math.random())" +
                "    .then(response => response.json())" +
                "    .then(data => {" +
                "      document.getElementById('result' + id).innerHTML = 'Response ' + id + ': ' + new Date().toLocaleTimeString();" +
                "      document.getElementById('result' + id).style.backgroundColor = '#' + Math.floor(Math.random()*16777215).toString(16);" +
                "    })" +
                "    .catch(error => {" +
                "      document.getElementById('result' + id).innerHTML = 'Error ' + id + ': ' + error;" +
                "    });" +
                "}" +
                "</script></head><body>" +
                "<h1>AJAX Flickering Test</h1>" +
                "<div id='container'></div>" +
                "</body></html>";
            
            driver.get("data:text/html;charset=utf-8," + ajaxHTML);
            Thread.sleep(1000);
            
            // Create result divs and trigger rapid AJAX calls
            JavascriptExecutor js = (JavascriptExecutor) driver;
            for (int i = 0; i < 15; i++) {
                // Add result div
                js.executeScript(
                    "var div = document.createElement('div');" +
                    "div.id = 'result' + " + i + ";" +
                    "div.innerHTML = 'Loading " + i + "...';" +
                    "div.style.padding = '10px';" +
                    "div.style.margin = '5px';" +
                    "div.style.border = '1px solid #ccc';" +
                    "document.getElementById('container').appendChild(div);"
                );
                
                // Trigger AJAX call
                js.executeScript("makeAjaxCall(" + i + ");");
                Thread.sleep(200);
            }
            
            // Wait for responses and continue adding more
            Thread.sleep(3000);
            
            // Add more AJAX calls while previous ones might still be processing
            for (int i = 15; i < 25; i++) {
                js.executeScript(
                    "var div = document.createElement('div');" +
                    "div.id = 'result' + " + i + ";" +
                    "div.innerHTML = 'Loading " + i + "...';" +
                    "div.style.padding = '10px';" +
                    "div.style.margin = '5px';" +
                    "div.style.border = '1px solid #ccc';" +
                    "document.getElementById('container').appendChild(div);"
                );
                js.executeScript("makeAjaxCall(" + i + ");");
                Thread.sleep(150);
            }
            
            // Wait for all requests to complete
            Thread.sleep(5000);
            logger.info("Completed AJAX flickering test");
            
        } catch (Exception e) {
            logger.error("AJAX flickering test failed", e);
            throw e;
        }
    }

    @Test
    public void testMemoryPressureFlickering() throws Exception {
        logger.info("=== Testing Memory Pressure Flickering ===");
        
        try {
            speedVideoRecorder.setVideoSpeed(VideoRecordWithSpeedControl.VideoSpeed.REAL_TIME);
            speedVideoRecorder.startRecording();
            
            // Create memory pressure through DOM manipulation
            driver.get("data:text/html;charset=utf-8,<html><body><h1>Memory Pressure Test</h1><div id='container'></div></body></html>");
            Thread.sleep(1000);
            
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            // Create large amounts of DOM elements
            logger.info("Creating memory pressure with large DOM");
            for (int batch = 0; batch < 10; batch++) {
                StringBuilder batchScript = new StringBuilder("var container = document.getElementById('container');");
                
                for (int i = 0; i < 50; i++) {
                    int elementId = batch * 50 + i;
                    batchScript.append("var div").append(elementId).append(" = document.createElement('div');")
                             .append("div").append(elementId).append(".innerHTML = '")
                             .append("<img src=\"data:image/svg+xml,%3Csvg xmlns=\\'http://www.w3.org/2000/svg\\' width=\\'100\\' height=\\'100\\'%3E%3Crect width=\\'100\\' height=\\'100\\' fill=\\'%23")
                             .append(String.format("%06x", (int)(Math.random() * 0xFFFFFF)))
                             .append("\\'/%3E%3C/svg%3E\">Element ").append(elementId).append("';")
                             .append("div").append(elementId).append(".style.cssText = 'padding:10px;margin:5px;border:2px solid #333;display:inline-block;';")
                             .append("container.appendChild(div").append(elementId).append(");");
                }
                
                js.executeScript(batchScript.toString());
                Thread.sleep(500);
                
                // Occasionally remove old elements to simulate cleanup
                if (batch > 2) {
                    js.executeScript(
                        "var elementsToRemove = document.querySelectorAll('#container > div');" +
                        "for(var i = 0; i < Math.min(20, elementsToRemove.length); i++) {" +
                        "  elementsToRemove[i].remove();" +
                        "}"
                    );
                }
                
                logger.info("Completed batch " + (batch + 1) + " of memory pressure test");
            }
            
            // Force garbage collection attempts
            for (int i = 0; i < 5; i++) {
                js.executeScript("if (window.gc) window.gc();");
                Thread.sleep(1000);
            }
            
            logger.info("Completed memory pressure flickering test");
            
        } catch (Exception e) {
            logger.error("Memory pressure flickering test failed", e);
            throw e;
        }
    }

    @Test
    public void testConcurrentOperationsFlickering() throws Exception {
        logger.info("=== Testing Concurrent Operations Flickering ===");
        
        try {
            speedVideoRecorder.setVideoSpeed(VideoRecordWithSpeedControl.VideoSpeed.SLOW_MOTION);
            speedVideoRecorder.setAutoRebindEnabled(true);
            speedVideoRecorder.startRecording();
            
            driver.get("data:text/html;charset=utf-8,<html><body><h1>Concurrent Operations Test</h1><div id='results'></div></body></html>");
            Thread.sleep(1000);
            
            ExecutorService executor = Executors.newFixedThreadPool(3);
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            // Submit concurrent tasks that modify the page
            Future<?> task1 = executor.submit(() -> {
                try {
                    for (int i = 0; i < 20; i++) {
                        js.executeScript(
                            "var p = document.createElement('p');" +
                            "p.innerHTML = 'Task 1 - Item " + i + " - ' + new Date().toLocaleTimeString();" +
                            "p.style.color = 'red';" +
                            "document.getElementById('results').appendChild(p);"
                        );
                        Thread.sleep(200);
                    }
                } catch (Exception e) {
                    logger.error("Task 1 failed", e);
                }
            });
            
            Future<?> task2 = executor.submit(() -> {
                try {
                    for (int i = 0; i < 20; i++) {
                        js.executeScript(
                            "var p = document.createElement('p');" +
                            "p.innerHTML = 'Task 2 - Item " + i + " - ' + new Date().toLocaleTimeString();" +
                            "p.style.color = 'blue';" +
                            "document.getElementById('results').appendChild(p);"
                        );
                        Thread.sleep(250);
                    }
                } catch (Exception e) {
                    logger.error("Task 2 failed", e);
                }
            });
            
            Future<?> task3 = executor.submit(() -> {
                try {
                    for (int i = 0; i < 15; i++) {
                        js.executeScript(
                            "var elements = document.querySelectorAll('#results p');" +
                            "if (elements.length > 0) {" +
                            "  var randomElement = elements[Math.floor(Math.random() * elements.length)];" +
                            "  randomElement.style.backgroundColor = '#' + Math.floor(Math.random()*16777215).toString(16);" +
                            "}"
                        );
                        Thread.sleep(300);
                    }
                } catch (Exception e) {
                    logger.error("Task 3 failed", e);
                }
            });
            
            // Wait for tasks to complete
            task1.get(30, TimeUnit.SECONDS);
            task2.get(30, TimeUnit.SECONDS);
            task3.get(30, TimeUnit.SECONDS);
            
            executor.shutdown();
            Thread.sleep(2000);
            
            logger.info("Completed concurrent operations flickering test");
            
        } catch (Exception e) {
            logger.error("Concurrent operations flickering test failed", e);
            throw e;
        }
    }

    @Test
    public void testResourceContentionFlickering() throws Exception {
        logger.info("=== Testing Resource Contention Flickering ===");
        
        try {
            // Use both recorders simultaneously to create resource contention
            videoRecorder.startRecording();
            Thread.sleep(500);
            speedVideoRecorder.startRecording();
            
            driver.get("https://httpbin.org/html");
            Thread.sleep(2000);
            
            // Perform intensive operations while both recorders are running
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            // CPU intensive operations
            js.executeScript(
                "var start = Date.now();" +
                "while (Date.now() - start < 2000) {" +
                "  document.body.innerHTML += Math.random().toString(36).substring(7);" +
                "  if (document.body.innerHTML.length > 10000) {" +
                "    document.body.innerHTML = 'CPU Intensive Test - ' + new Date().toLocaleTimeString();" +
                "  }" +
                "}"
            );
            
            Thread.sleep(1000);
            
            // Memory intensive operations
            js.executeScript(
                "var bigArray = [];" +
                "for (var i = 0; i < 100000; i++) {" +
                "  bigArray.push(Math.random().toString(36));" +
                "}" +
                "document.body.innerHTML += '<p>Created array with ' + bigArray.length + ' elements</p>';"
            );
            
            Thread.sleep(2000);
            logger.info("Completed resource contention flickering test");
            
        } catch (Exception e) {
            logger.error("Resource contention flickering test failed", e);
            throw e;
        }
    }

    @Test
    public void testVisualConsistencyValidation() throws Exception {
        logger.info("=== Testing Visual Consistency Validation ===");
        
        try {
            speedVideoRecorder.setVideoSpeed(VideoRecordWithSpeedControl.VideoSpeed.VERY_SLOW);
            speedVideoRecorder.setCaptureInterval(100);
            speedVideoRecorder.startRecording();
            
            // Create a page with predictable visual changes
            String consistentHTML = "<html><head><style>" +
                "#counter { font-size: 48px; text-align: center; padding: 50px; }" +
                ".stable { background: #f0f0f0; border: 5px solid #333; margin: 20px; padding: 20px; }" +
                "</style></head><body>" +
                "<div class='stable'><h1>Visual Consistency Test</h1></div>" +
                "<div class='stable' id='counter'>0</div>" +
                "<div class='stable' id='status'>Ready</div>" +
                "</body></html>";
            
            driver.get("data:text/html;charset=utf-8," + consistentHTML);
            Thread.sleep(2000);
            
            // Perform predictable changes that should be captured consistently
            JavascriptExecutor js = (JavascriptExecutor) driver;
            for (int i = 1; i <= 20; i++) {
                js.executeScript(
                    "document.getElementById('counter').innerHTML = '" + i + "';" +
                    "document.getElementById('status').innerHTML = 'Step " + i + " of 20 - ' + new Date().toLocaleTimeString();"
                );
                Thread.sleep(500); // Consistent timing
                
                // Add visual marker every 5 steps
                if (i % 5 == 0) {
                    js.executeScript(
                        "document.body.style.backgroundColor = '#" + 
                        String.format("%06x", (i * 123456) % 0xFFFFFF) + "';"
                    );
                    Thread.sleep(1000);
                    js.executeScript("document.body.style.backgroundColor = '#ffffff';");
                }
            }
            
            logger.info("Completed visual consistency validation test");
            
        } catch (Exception e) {
            logger.error("Visual consistency validation test failed", e);
            throw e;
        }
    }

    @AfterMethod
    public void tearDown() throws Exception {
        try {
            logger.info("Starting flickering test cleanup");
            
            // Stop both recorders if they were started
            if (videoRecorder != null) {
                try {
                    videoRecorder.stopRecordingAndGenerateVideo();
                    videoRecorder.cleanup();
                } catch (Exception e) {
                    logger.error("Error stopping videoRecorder", e);
                }
            }
            
            if (speedVideoRecorder != null) {
                try {
                    speedVideoRecorder.stopRecordingAndGenerateVideo();
                    speedVideoRecorder.cleanup();
                } catch (Exception e) {
                    logger.error("Error stopping speedVideoRecorder", e);
                }
            }
            
            // Clear DevTools
            VideoRecordInHeadless.clearDEVTOOLS();
            VideoRecordWithSpeedControl.clearDEVTOOLS();
            
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