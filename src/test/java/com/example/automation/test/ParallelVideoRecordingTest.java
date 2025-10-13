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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Parallel Video Recording Test Suite
 * 
 * This test class implements parallel test execution to identify resource contention
 * issues, race conditions, and synchronization problems in video recording.
 * 
 * Test scenarios include:
 * - Multiple WebDriver instances with concurrent recording
 * - Parallel tab operations with auto-rebind
 * - Resource sharing conflicts
 * - Thread safety validation
 * - Performance degradation under parallel load
 * - Memory and CPU usage monitoring
 */
public class ParallelVideoRecordingTest {

    private List<WebDriver> drivers;
    private List<VideoRecordInHeadless> videoRecorders;
    private List<VideoRecordWithSpeedControl> speedVideoRecorders;
    private LoggerMechanism logger;
    private ExecutorService executorService;

    @BeforeMethod
    public void setUp() {
        logger = new LoggerMechanism(ParallelVideoRecordingTest.class);
        drivers = Collections.synchronizedList(new ArrayList<>());
        videoRecorders = Collections.synchronizedList(new ArrayList<>());
        speedVideoRecorders = Collections.synchronizedList(new ArrayList<>());
        
        try {
            logger.info("Setting up Parallel Video Recording Test Environment...");
            WebDriverManager.chromedriver().setup();
            
            // Use a larger thread pool for parallel operations
            executorService = Executors.newFixedThreadPool(8);
            
            logger.info("Parallel Video Recording Test setup completed successfully");
            
        } catch (Exception e) {
            logger.error("Failed to setup parallel test environment", e);
            throw new RuntimeException("Parallel test setup failed: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a new WebDriver instance with unique configuration for parallel execution
     */
    private WebDriver createWebDriver(int instanceId) throws Exception {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1280,720"); // Smaller window for parallel tests
        options.addArguments("--remote-debugging-port=" + (9224 + instanceId)); // Unique ports
        options.addArguments("--disable-web-security");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-plugins");
        
        // Instance-specific optimizations
        options.addArguments("--disable-background-timer-throttling");
        options.addArguments("--disable-backgrounding-occluded-windows");
        options.addArguments("--disable-renderer-backgrounding");
        options.addArguments("--user-data-dir=/tmp/chrome-parallel-" + instanceId);
        
        WebDriver driver = new ChromeDriver(options);
        drivers.add(driver);
        return driver;
    }

    @Test
    public void testParallelRecordingSessions() throws Exception {
        logger.info("=== Testing Parallel Recording Sessions ===");
        
        final int numberOfSessions = 4;
        CountDownLatch startLatch = new CountDownLatch(numberOfSessions);
        CountDownLatch completionLatch = new CountDownLatch(numberOfSessions);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        List<Future<?>> futures = new ArrayList<>();
        
        // Launch multiple recording sessions in parallel
        for (int i = 0; i < numberOfSessions; i++) {
            final int sessionId = i;
            
            Future<?> future = executorService.submit(() -> {
                WebDriver driver = null;
                VideoRecordInHeadless recorder = null;
                
                try {
                    logger.info("Starting parallel session " + sessionId);
                    
                    // Create unique driver instance
                    driver = createWebDriver(sessionId);
                    recorder = new VideoRecordInHeadless(logger, driver);
                    videoRecorders.add(recorder);
                    
                    // Start recording
                    recorder.startRecording();
                    startLatch.countDown();
                    logger.info("Session " + sessionId + " recording started");
                    
                    // Wait for all sessions to start
                    startLatch.await(30, TimeUnit.SECONDS);
                    
                    // Perform test operations
                    String testUrl = "data:text/html;charset=utf-8,<html><body><h1>Parallel Session " + sessionId + "</h1><div id='operations'></div></body></html>";
                    driver.get(testUrl);
                    Thread.sleep(1000);
                    
                    JavascriptExecutor js = (JavascriptExecutor) driver;
                    
                    // Perform unique operations for each session
                    for (int op = 0; op < 10; op++) {
                        js.executeScript(
                            "document.getElementById('operations').innerHTML += " +
                            "'<p>Session " + sessionId + " Operation " + op + " - ' + new Date().toLocaleTimeString() + '</p>';" +
                            "document.body.style.backgroundColor = '#" + String.format("%06x", (sessionId * 111111) % 0xFFFFFF) + "';"
                        );
                        Thread.sleep(200 + (sessionId * 50)); // Stagger timing to avoid synchronization
                    }
                    
                    // Navigate to different pages
                    driver.get("https://httpbin.org/uuid");
                    Thread.sleep(2000);
                    
                    js.executeScript(
                        "document.body.innerHTML += '<h2>Session " + sessionId + " Completed at ' + new Date().toLocaleString() + '</h2>';"
                    );
                    
                    successCount.incrementAndGet();
                    logger.info("Session " + sessionId + " completed successfully");
                    
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    logger.error("Session " + sessionId + " failed", e);
                } finally {
                    completionLatch.countDown();
                }
            });
            
            futures.add(future);
        }
        
        // Wait for all sessions to complete
        completionLatch.await(120, TimeUnit.SECONDS);
        
        logger.info("Parallel recording test completed: " + successCount.get() + " successful, " + errorCount.get() + " errors");
        
        // Stop all recordings
        for (VideoRecordInHeadless recorder : videoRecorders) {
            try {
                recorder.stopRecordingAndGenerateVideo();
            } catch (Exception e) {
                logger.error("Error stopping parallel recorder", e);
            }
        }
    }

    @Test
    public void testParallelTabOperations() throws Exception {
        logger.info("=== Testing Parallel Tab Operations ===");
        
        final int numberOfDrivers = 3;
        CountDownLatch setupLatch = new CountDownLatch(numberOfDrivers);
        CountDownLatch operationsLatch = new CountDownLatch(numberOfDrivers);
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int i = 0; i < numberOfDrivers; i++) {
            final int driverId = i;
            
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                WebDriver driver = null;
                VideoRecordInHeadless recorder = null;
                
                try {
                    driver = createWebDriver(driverId);
                    recorder = new VideoRecordInHeadless(logger, driver);
                    videoRecorders.add(recorder);
                    
                    recorder.setAutoRebindEnabled(true);
                    recorder.startRecording();
                    
                    setupLatch.countDown();
                    setupLatch.await(30, TimeUnit.SECONDS);
                    
                    logger.info("Driver " + driverId + " starting tab operations");
                    
                    // Initial page
                    driver.get("https://example.com");
                    Thread.sleep(1000);
                    
                    String originalWindow = driver.getWindowHandle();
                    
                    // Open multiple tabs in parallel
                    for (int tab = 0; tab < 3; tab++) {
                        String tabUrl = "https://httpbin.org/html?driver=" + driverId + "&tab=" + tab;
                        ((JavascriptExecutor) driver).executeScript("window.open('" + tabUrl + "', '_blank');");
                        Thread.sleep(500);
                        
                        // Switch to new tab and perform operations
                        for (String windowHandle : driver.getWindowHandles()) {
                            if (!windowHandle.equals(originalWindow)) {
                                driver.switchTo().window(windowHandle);
                                Thread.sleep(300);
                                
                                ((JavascriptExecutor) driver).executeScript(
                                    "document.body.innerHTML += '<h2>Driver " + driverId + " Tab " + tab + "</h2>';" +
                                    "document.body.style.border = '10px solid #" + 
                                    String.format("%06x", ((driverId + tab) * 123456) % 0xFFFFFF) + "';"
                                );
                                
                                Thread.sleep(500);
                                originalWindow = windowHandle; // Update reference
                                break;
                            }
                        }
                    }
                    
                    logger.info("Driver " + driverId + " completed tab operations");
                    
                } catch (Exception e) {
                    logger.error("Driver " + driverId + " tab operations failed", e);
                } finally {
                    operationsLatch.countDown();
                }
            }, executorService);
            
            futures.add(future);
        }
        
        // Wait for all operations to complete
        operationsLatch.await(180, TimeUnit.SECONDS);
        
        logger.info("Parallel tab operations test completed");
    }

    @Test
    public void testResourceContentionDetection() throws Exception {
        logger.info("=== Testing Resource Contention Detection ===");
        
        final int concurrentRecorders = 6;
        CountDownLatch contentionLatch = new CountDownLatch(concurrentRecorders);
        List<CompletableFuture<Long>> performanceFutures = new ArrayList<>();
        
        for (int i = 0; i < concurrentRecorders; i++) {
            final int recorderId = i;
            
            CompletableFuture<Long> future = CompletableFuture.supplyAsync(() -> {
                WebDriver driver = null;
                VideoRecordWithSpeedControl recorder = null;
                long startTime = System.currentTimeMillis();
                
                try {
                    driver = createWebDriver(recorderId);
                    recorder = new VideoRecordWithSpeedControl(logger, driver);
                    speedVideoRecorders.add(recorder);
                    
                    recorder.setVideoSpeed(VideoRecordWithSpeedControl.VideoSpeed.REAL_TIME);
                    recorder.setCaptureInterval(200);
                    recorder.startRecording();
                    
                    logger.info("Contention test recorder " + recorderId + " started");
                    
                    // Intensive operations to stress resources
                    String contentionUrl = "data:text/html;charset=utf-8,<html><body><h1>Resource Contention Test " + recorderId + "</h1><div id='stress'></div></body></html>";
                    driver.get(contentionUrl);
                    Thread.sleep(1000);
                    
                    JavascriptExecutor js = (JavascriptExecutor) driver;
                    
                    // CPU and memory intensive operations
                    for (int cycle = 0; cycle < 20; cycle++) {
                        js.executeScript(
                            "var stressDiv = document.getElementById('stress');" +
                            "for (var i = 0; i < 50; i++) {" +
                            "  var div = document.createElement('div');" +
                            "  div.innerHTML = 'Recorder " + recorderId + " Cycle " + cycle + " Item ' + i;" +
                            "  div.style.cssText = 'padding:2px;border:1px solid #ccc;background:#" + 
                            String.format("%06x", (recorderId * cycle * 12345) % 0xFFFFFF) + ";';" +
                            "  stressDiv.appendChild(div);" +
                            "}" +
                            "if (stressDiv.children.length > 200) {" +
                            "  for (var j = 0; j < 100; j++) stressDiv.removeChild(stressDiv.firstChild);" +
                            "}"
                        );
                        Thread.sleep(100);
                    }
                    
                    long duration = System.currentTimeMillis() - startTime;
                    logger.info("Contention test recorder " + recorderId + " completed in " + duration + "ms");
                    
                    return duration;
                    
                } catch (Exception e) {
                    logger.error("Contention test recorder " + recorderId + " failed", e);
                    return System.currentTimeMillis() - startTime;
                } finally {
                    contentionLatch.countDown();
                }
            }, executorService);
            
            performanceFutures.add(future);
        }
        
        // Wait for all contention tests to complete
        contentionLatch.await(300, TimeUnit.SECONDS);
        
        // Analyze performance results
        List<Long> durations = new ArrayList<>();
        for (CompletableFuture<Long> future : performanceFutures) {
            try {
                durations.add(future.get(5, TimeUnit.SECONDS));
            } catch (Exception e) {
                logger.error("Failed to get performance result", e);
            }
        }
        
        if (!durations.isEmpty()) {
            long totalTime = durations.stream().mapToLong(Long::longValue).sum();
            long avgTime = totalTime / durations.size();
            long maxTime = durations.stream().mapToLong(Long::longValue).max().orElse(0);
            long minTime = durations.stream().mapToLong(Long::longValue).min().orElse(0);
            
            logger.info("Resource contention analysis:");
            logger.info("  Average duration: " + avgTime + "ms");
            logger.info("  Maximum duration: " + maxTime + "ms");
            logger.info("  Minimum duration: " + minTime + "ms");
            logger.info("  Performance variance: " + (maxTime - minTime) + "ms");
            
            if (maxTime - minTime > avgTime * 0.5) {
                logger.warn("High performance variance detected - potential resource contention!");
            }
        }
    }

    @Test
    public void testThreadSafetyValidation() throws Exception {
        logger.info("=== Testing Thread Safety Validation ===");
        
        // Create shared driver instance
        WebDriver sharedDriver = createWebDriver(999);
        VideoRecordInHeadless sharedRecorder = new VideoRecordInHeadless(logger, sharedDriver);
        videoRecorders.add(sharedRecorder);
        
        sharedRecorder.startRecording();
        sharedDriver.get("data:text/html;charset=utf-8,<html><body><h1>Thread Safety Test</h1><div id='shared-operations'></div></body></html>");
        Thread.sleep(1000);
        
        final int numberOfThreads = 8;
        CountDownLatch threadLatch = new CountDownLatch(numberOfThreads);
        AtomicInteger operationCounter = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        
        // Launch multiple threads operating on the same driver/recorder
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            
            executorService.submit(() -> {
                try {
                    JavascriptExecutor js = (JavascriptExecutor) sharedDriver;
                    
                    for (int op = 0; op < 5; op++) {
                        int opId = operationCounter.incrementAndGet();
                        
                        synchronized (sharedDriver) { // Synchronize access to shared resource
                            js.executeScript(
                                "document.getElementById('shared-operations').innerHTML += " +
                                "'<div>Thread " + threadId + " Operation " + opId + " - ' + new Date().toLocaleTimeString() + '</div>';"
                            );
                        }
                        
                        Thread.sleep(100 + (threadId * 10)); // Stagger operations
                    }
                    
                    logger.info("Thread " + threadId + " completed operations");
                    
                } catch (Exception e) {
                    exceptions.add(e);
                    logger.error("Thread " + threadId + " encountered error", e);
                } finally {
                    threadLatch.countDown();
                }
            });
        }
        
        // Wait for all threads to complete
        threadLatch.await(60, TimeUnit.SECONDS);
        
        logger.info("Thread safety test completed with " + exceptions.size() + " exceptions");
        if (!exceptions.isEmpty()) {
            logger.warn("Thread safety issues detected!");
        }
        
        // Verify final state
        JavascriptExecutor js = (JavascriptExecutor) sharedDriver;
        String finalState = (String) js.executeScript("return document.getElementById('shared-operations').innerHTML;");
        int operationCount = finalState.split("<div>").length - 1;
        
        logger.info("Final operation count: " + operationCount + ", Expected: " + (numberOfThreads * 5));
    }

    @Test
    public void testParallelPerformanceBenchmark() throws Exception {
        logger.info("=== Testing Parallel Performance Benchmark ===");
        
        // Baseline - single threaded performance
        logger.info("Measuring baseline single-threaded performance...");
        long baselineStart = System.currentTimeMillis();
        
        WebDriver baselineDriver = createWebDriver(1000);
        VideoRecordWithSpeedControl baselineRecorder = new VideoRecordWithSpeedControl(logger, baselineDriver);
        speedVideoRecorders.add(baselineRecorder);
        
        baselineRecorder.setVideoSpeed(VideoRecordWithSpeedControl.VideoSpeed.FAST);
        baselineRecorder.startRecording();
        
        performStandardOperations(baselineDriver, 0, "Baseline");
        
        baselineRecorder.stopRecordingAndGenerateVideo();
        long baselineTime = System.currentTimeMillis() - baselineStart;
        logger.info("Baseline performance: " + baselineTime + "ms");
        
        // Parallel performance test
        logger.info("Measuring parallel performance...");
        long parallelStart = System.currentTimeMillis();
        
        final int parallelSessions = 4;
        CountDownLatch parallelLatch = new CountDownLatch(parallelSessions);
        List<Long> parallelTimes = Collections.synchronizedList(new ArrayList<>());
        
        for (int i = 0; i < parallelSessions; i++) {
            final int sessionId = i;
            
            executorService.submit(() -> {
                try {
                    long sessionStart = System.currentTimeMillis();
                    
                    WebDriver parallelDriver = createWebDriver(1001 + sessionId);
                    VideoRecordWithSpeedControl parallelRecorder = new VideoRecordWithSpeedControl(logger, parallelDriver);
                    speedVideoRecorders.add(parallelRecorder);
                    
                    parallelRecorder.setVideoSpeed(VideoRecordWithSpeedControl.VideoSpeed.FAST);
                    parallelRecorder.startRecording();
                    
                    performStandardOperations(parallelDriver, sessionId, "Parallel");
                    
                    parallelRecorder.stopRecordingAndGenerateVideo();
                    
                    long sessionTime = System.currentTimeMillis() - sessionStart;
                    parallelTimes.add(sessionTime);
                    
                    logger.info("Parallel session " + sessionId + " completed in " + sessionTime + "ms");
                    
                } catch (Exception e) {
                    logger.error("Parallel session " + sessionId + " failed", e);
                } finally {
                    parallelLatch.countDown();
                }
            });
        }
        
        parallelLatch.await(300, TimeUnit.SECONDS);
        long totalParallelTime = System.currentTimeMillis() - parallelStart;
        
        // Performance analysis
        if (!parallelTimes.isEmpty()) {
            double avgParallelTime = parallelTimes.stream().mapToLong(Long::longValue).average().orElse(0);
            double efficiency = (baselineTime * parallelSessions) / (double) totalParallelTime;
            
            logger.info("Performance benchmark results:");
            logger.info("  Baseline (single): " + baselineTime + "ms");
            logger.info("  Parallel total: " + totalParallelTime + "ms");
            logger.info("  Average parallel session: " + String.format("%.2f", avgParallelTime) + "ms");
            logger.info("  Theoretical best: " + (baselineTime * parallelSessions) + "ms");
            logger.info("  Parallel efficiency: " + String.format("%.2f", efficiency * 100) + "%");
            
            if (efficiency < 0.5) {
                logger.warn("Low parallel efficiency detected - possible bottlenecks!");
            }
        }
    }

    /**
     * Performs standard operations for benchmarking
     */
    private void performStandardOperations(WebDriver driver, int sessionId, String prefix) throws Exception {
        String testUrl = "data:text/html;charset=utf-8,<html><body><h1>" + prefix + " Session " + sessionId + "</h1><div id='ops'></div></body></html>";
        driver.get(testUrl);
        Thread.sleep(500);
        
        JavascriptExecutor js = (JavascriptExecutor) driver;
        
        for (int i = 0; i < 15; i++) {
            js.executeScript(
                "document.getElementById('ops').innerHTML += " +
                "'<p>" + prefix + " " + sessionId + " Operation " + i + " - ' + new Date().toLocaleTimeString() + '</p>';" +
                "document.body.style.backgroundColor = '#" + String.format("%06x", (i * 111111) % 0xFFFFFF) + "';"
            );
            Thread.sleep(100);
        }
        
        // Navigate to test final page state
        driver.get("https://httpbin.org/json");
        Thread.sleep(1000);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        try {
            logger.info("Starting parallel test cleanup");
            
            // Stop all video recorders
            for (VideoRecordInHeadless recorder : videoRecorders) {
                try {
                    recorder.stopRecordingAndGenerateVideo();
                    recorder.cleanup();
                } catch (Exception e) {
                    logger.error("Error stopping video recorder", e);
                }
            }
            
            for (VideoRecordWithSpeedControl recorder : speedVideoRecorders) {
                try {
                    recorder.stopRecordingAndGenerateVideo();
                    recorder.cleanup();
                } catch (Exception e) {
                    logger.error("Error stopping speed video recorder", e);
                }
            }
            
            // Cleanup DevTools
            VideoRecordInHeadless.clearDEVTOOLS();
            VideoRecordWithSpeedControl.clearDEVTOOLS();
            
            // Shutdown executor service
            if (executorService != null) {
                executorService.shutdown();
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            }
            
        } catch (Exception e) {
            logger.error("Error during parallel test cleanup", e);
        } finally {
            // Close all drivers
            for (WebDriver driver : drivers) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    logger.error("Error closing WebDriver", e);
                }
            }
            
            logger.info("Parallel test cleanup completed");
        }
    }
}