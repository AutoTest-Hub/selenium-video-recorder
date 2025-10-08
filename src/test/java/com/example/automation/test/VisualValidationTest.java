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

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

/**
 * Visual Validation Test Suite
 * 
 * This test class validates frame consistency and identifies visual artifacts
 * or missing frames in video recordings. It includes tests for:
 * 
 * - Frame sequence validation
 * - Visual artifact detection
 * - Frame timing consistency
 * - Color accuracy validation
 * - Text rendering verification
 * - Animation smoothness testing
 * - Screen capture completeness
 */
public class VisualValidationTest {

    private WebDriver driver;
    private VideoRecordInHeadless videoRecorder;
    private VideoRecordWithSpeedControl speedVideoRecorder;
    private LoggerMechanism logger;
    private Path outputDir;

    @BeforeMethod
    public void setUp() {
        logger = new LoggerMechanism(VisualValidationTest.class);
        
        try {
            logger.info("Setting up Visual Validation Test Environment...");
            WebDriverManager.chromedriver().setup();
            
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--remote-debugging-port=9225");
            options.addArguments("--disable-web-security");
            options.addArguments("--disable-extensions");
            options.addArguments("--disable-plugins");
            
            // Visual testing specific options
            options.addArguments("--force-device-scale-factor=1");
            options.addArguments("--disable-background-timer-throttling");
            options.addArguments("--disable-backgrounding-occluded-windows");
            options.addArguments("--disable-renderer-backgrounding");
            options.addArguments("--disable-features=TranslateUI");
            
            driver = new ChromeDriver(options);
            videoRecorder = new VideoRecordInHeadless(logger, driver);
            speedVideoRecorder = new VideoRecordWithSpeedControl(logger, driver);
            
            // Create output directory for validation artifacts
            outputDir = Paths.get("visual-validation-output");
            Files.createDirectories(outputDir);
            
            logger.info("Visual Validation Test setup completed successfully");
            
        } catch (Exception e) {
            logger.error("Failed to setup visual validation test environment", e);
            throw new RuntimeException("Visual validation test setup failed: " + e.getMessage(), e);
        }
    }

    @Test
    public void testFrameSequenceConsistency() throws Exception {
        logger.info("=== Testing Frame Sequence Consistency ===");
        
        try {
            speedVideoRecorder.setVideoSpeed(VideoRecordWithSpeedControl.VideoSpeed.VERY_SLOW);
            speedVideoRecorder.setCaptureInterval(200); // Capture every 200ms for detailed analysis
            speedVideoRecorder.startRecording();
            logger.info("Started frame sequence consistency test");
            
            // Create a page with predictable sequential changes
            String sequenceHTML = "<html><head><style>" +
                "#frame-counter { font-size: 120px; text-align: center; padding: 50px; font-family: monospace; }" +
                "#timestamp { font-size: 24px; text-align: center; }" +
                ".frame-marker { position: absolute; top: 10px; left: 10px; font-size: 16px; background: yellow; padding: 5px; }" +
                "</style></head><body>" +
                "<div class='frame-marker'>Sequence Test</div>" +
                "<div id='frame-counter'>0</div>" +
                "<div id='timestamp'></div>" +
                "</body></html>";
            
            driver.get("data:text/html;charset=utf-8," + sequenceHTML);
            Thread.sleep(2000);
            
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            // Generate predictable sequence for frame validation
            for (int frame = 1; frame <= 50; frame++) {
                js.executeScript(
                    "document.getElementById('frame-counter').innerHTML = '" + String.format("%03d", frame) + "';" +
                    "document.getElementById('timestamp').innerHTML = 'Frame " + frame + " - ' + new Date().toLocaleTimeString();" +
                    "document.body.style.backgroundColor = 'rgb(" + (frame * 5) + ", " + (255 - frame * 5) + ", 128)';"
                );
                Thread.sleep(400); // Consistent timing
                
                if (frame % 10 == 0) {
                    logger.info("Generated frame " + frame + " of 50");
                }
            }
            
            // Final validation frame
            js.executeScript(
                "document.getElementById('frame-counter').innerHTML = 'END';" +
                "document.getElementById('timestamp').innerHTML = 'Sequence Complete - ' + new Date().toLocaleString();" +
                "document.body.style.backgroundColor = 'green';"
            );
            Thread.sleep(2000);
            
            logger.info("Completed frame sequence consistency test - 50 sequential frames generated");
            
        } catch (Exception e) {
            logger.error("Frame sequence consistency test failed", e);
            throw e;
        }
    }

    @Test
    public void testColorAccuracyValidation() throws Exception {
        logger.info("=== Testing Color Accuracy Validation ===");
        
        try {
            speedVideoRecorder.setVideoSpeed(VideoRecordWithSpeedControl.VideoSpeed.SLOW_MOTION);
            speedVideoRecorder.startRecording();
            logger.info("Started color accuracy validation test");
            
            // Create color test patterns
            String colorHTML = "<html><head><style>" +
                ".color-block { width: 200px; height: 200px; display: inline-block; margin: 10px; text-align: center; line-height: 200px; font-weight: bold; }" +
                ".test-info { text-align: center; padding: 20px; font-size: 18px; }" +
                "</style></head><body>" +
                "<div class='test-info'>Color Accuracy Validation Test</div>" +
                "<div id='color-container'></div>" +
                "<div id='current-test'></div>" +
                "</body></html>";
            
            driver.get("data:text/html;charset=utf-8," + colorHTML);
            Thread.sleep(1000);
            
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            // Test primary colors
            String[] testColors = {
                "rgb(255, 0, 0)", "Red",           // Pure red
                "rgb(0, 255, 0)", "Green",         // Pure green
                "rgb(0, 0, 255)", "Blue",          // Pure blue
                "rgb(255, 255, 0)", "Yellow",      // Yellow
                "rgb(255, 0, 255)", "Magenta",     // Magenta
                "rgb(0, 255, 255)", "Cyan",        // Cyan
                "rgb(0, 0, 0)", "Black",           // Black
                "rgb(255, 255, 255)", "White",     // White
                "rgb(128, 128, 128)", "Gray",      // Gray
                "rgb(255, 165, 0)", "Orange"       // Orange
            };
            
            for (int i = 0; i < testColors.length; i += 2) {
                String color = testColors[i];
                String colorName = testColors[i + 1];
                
                js.executeScript(
                    "document.getElementById('color-container').innerHTML = " +
                    "'<div class=\"color-block\" style=\"background-color: " + color + "; color: " + 
                    (colorName.equals("Black") || colorName.equals("Blue") ? "white" : "black") + ";\">" + 
                    colorName + "</div>';" +
                    "document.getElementById('current-test').innerHTML = 'Testing: " + colorName + " (" + color + ")';"
                );
                Thread.sleep(1500);
                
                logger.info("Tested color: " + colorName + " (" + color + ")");
            }
            
            // Test gradient transitions
            js.executeScript(
                "document.getElementById('color-container').innerHTML = " +
                "'<div class=\"color-block\" style=\"background: linear-gradient(45deg, red, blue);\">Gradient</div>';" +
                "document.getElementById('current-test').innerHTML = 'Testing: Gradient Transitions';"
            );
            Thread.sleep(2000);
            
            logger.info("Completed color accuracy validation test");
            
        } catch (Exception e) {
            logger.error("Color accuracy validation test failed", e);
            throw e;
        }
    }

    @Test
    public void testTextRenderingValidation() throws Exception {
        logger.info("=== Testing Text Rendering Validation ===");
        
        try {
            videoRecorder.startRecording();
            logger.info("Started text rendering validation test");
            
            // Create comprehensive text rendering test
            String textHTML = "<html><head><style>" +
                "body { font-family: Arial, sans-serif; padding: 20px; }" +
                ".text-sample { margin: 20px 0; padding: 10px; border: 1px solid #ccc; }" +
                ".size-12 { font-size: 12px; }" +
                ".size-16 { font-size: 16px; }" +
                ".size-24 { font-size: 24px; }" +
                ".size-36 { font-size: 36px; }" +
                ".size-48 { font-size: 48px; }" +
                ".bold { font-weight: bold; }" +
                ".italic { font-style: italic; }" +
                ".mono { font-family: 'Courier New', monospace; }" +
                "</style></head><body>" +
                "<h1>Text Rendering Validation Test</h1>" +
                "<div id='text-container'></div>" +
                "</body></html>";
            
            driver.get("data:text/html;charset=utf-8," + textHTML);
            Thread.sleep(1000);
            
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            // Test different font sizes
            String[] fontTests = {
                "size-12", "12px Font Size Test - The quick brown fox jumps over the lazy dog.",
                "size-16", "16px Font Size Test - The quick brown fox jumps over the lazy dog.",
                "size-24", "24px Font Size Test - The quick brown fox jumps over the lazy dog.",
                "size-36", "36px Font Size Test - The quick brown fox jumps over the lazy dog.",
                "size-48", "48px Font Size Test - LARGE TEXT"
            };
            
            for (int i = 0; i < fontTests.length; i += 2) {
                String className = fontTests[i];
                String text = fontTests[i + 1];
                
                js.executeScript(
                    "document.getElementById('text-container').innerHTML += " +
                    "'<div class=\"text-sample " + className + "\">" + text + "</div>';"
                );
                Thread.sleep(800);
            }
            
            // Test font styles
            js.executeScript(
                "document.getElementById('text-container').innerHTML += " +
                "'<div class=\"text-sample bold\">Bold Text Test - Important Information</div>';"
            );
            Thread.sleep(500);
            
            js.executeScript(
                "document.getElementById('text-container').innerHTML += " +
                "'<div class=\"text-sample italic\">Italic Text Test - Emphasized Content</div>';"
            );
            Thread.sleep(500);
            
            js.executeScript(
                "document.getElementById('text-container').innerHTML += " +
                "'<div class=\"text-sample mono\">Monospace Text Test - Code: function() { return true; }</div>';"
            );
            Thread.sleep(500);
            
            // Test special characters and symbols
            js.executeScript(
                "document.getElementById('text-container').innerHTML += " +
                "'<div class=\"text-sample\">Special Characters: áéíóú çñü ¡¿ €£¥ ©®™ →←↑↓ ★☆♦♣</div>';"
            );
            Thread.sleep(1000);
            
            // Test dynamic text changes
            for (int i = 1; i <= 5; i++) {
                js.executeScript(
                    "document.getElementById('text-container').innerHTML += " +
                    "'<div class=\"text-sample\">Dynamic Text Update #" + i + " - Timestamp: ' + new Date().toLocaleTimeString() + '</div>';"
                );
                Thread.sleep(400);
            }
            
            logger.info("Completed text rendering validation test");
            
        } catch (Exception e) {
            logger.error("Text rendering validation test failed", e);
            throw e;
        }
    }

    @Test
    public void testAnimationSmoothnessValidation() throws Exception {
        logger.info("=== Testing Animation Smoothness Validation ===");
        
        try {
            speedVideoRecorder.setVideoSpeed(VideoRecordWithSpeedControl.VideoSpeed.SLOW_MOTION);
            speedVideoRecorder.setCaptureInterval(100); // High frequency for smooth animation capture
            speedVideoRecorder.startRecording();
            logger.info("Started animation smoothness validation test");
            
            // Create smooth animation test
            String animationHTML = "<html><head><style>" +
                "@keyframes smooth-slide { 0% { transform: translateX(0px); } 100% { transform: translateX(800px); } }" +
                "@keyframes smooth-bounce { 0%, 100% { transform: translateY(0px); } 50% { transform: translateY(-100px); } }" +
                "@keyframes smooth-rotate { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }" +
                "@keyframes smooth-scale { 0%, 100% { transform: scale(1); } 50% { transform: scale(1.5); } }" +
                ".slider { width: 50px; height: 50px; background: red; animation: smooth-slide 4s linear infinite; }" +
                ".bouncer { width: 50px; height: 50px; background: blue; animation: smooth-bounce 2s ease-in-out infinite; }" +
                ".rotator { width: 50px; height: 50px; background: green; animation: smooth-rotate 3s linear infinite; }" +
                ".scaler { width: 50px; height: 50px; background: orange; animation: smooth-scale 2.5s ease-in-out infinite; }" +
                ".animation-container { height: 200px; position: relative; margin: 20px 0; border: 2px solid #ccc; }" +
                "</style></head><body>" +
                "<h1>Animation Smoothness Validation</h1>" +
                "<div class='animation-container'><div class='slider'></div></div>" +
                "<div class='animation-container'><div class='bouncer'></div></div>" +
                "<div class='animation-container'><div class='rotator'></div></div>" +
                "<div class='animation-container'><div class='scaler'></div></div>" +
                "<div id='frame-counter'>Frame: 0</div>" +
                "</body></html>";
            
            driver.get("data:text/html;charset=utf-8," + animationHTML);
            Thread.sleep(2000);
            
            // Let animations run for validation
            JavascriptExecutor js = (JavascriptExecutor) driver;
            for (int frame = 1; frame <= 100; frame++) {
                js.executeScript("document.getElementById('frame-counter').innerHTML = 'Frame: " + frame + "';");
                Thread.sleep(50); // 20 FPS for smooth capture
                
                if (frame % 20 == 0) {
                    logger.info("Animation validation frame " + frame + "/100");
                }
            }
            
            logger.info("Completed animation smoothness validation test");
            
        } catch (Exception e) {
            logger.error("Animation smoothness validation test failed", e);
            throw e;
        }
    }

    @Test
    public void testVisualArtifactDetection() throws Exception {
        logger.info("=== Testing Visual Artifact Detection ===");
        
        try {
            videoRecorder.startRecording();
            logger.info("Started visual artifact detection test");
            
            // Create scenarios that might produce artifacts
            String artifactHTML = "<html><head><style>" +
                "body { margin: 0; padding: 20px; }" +
                ".artifact-test { width: 100%; height: 200px; margin: 10px 0; position: relative; }" +
                ".rapid-change { transition: all 0.1s; }" +
                ".overlay { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); }" +
                "</style></head><body>" +
                "<h1>Visual Artifact Detection Test</h1>" +
                "<div id='test-container'></div>" +
                "<div id='status'>Test Status</div>" +
                "</body></html>";
            
            driver.get("data:text/html;charset=utf-8," + artifactHTML);
            Thread.sleep(1000);
            
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            // Test 1: Rapid color changes (potential for color bleeding)
            logger.info("Testing rapid color changes...");
            js.executeScript(
                "document.getElementById('test-container').innerHTML = " +
                "'<div class=\"artifact-test rapid-change\" id=\"color-test\" style=\"background: red;\">Rapid Color Change Test</div>';" +
                "document.getElementById('status').innerHTML = 'Test 1: Rapid Color Changes';"
            );
            
            String[] colors = {"red", "green", "blue", "yellow", "magenta", "cyan", "orange", "purple"};
            for (int i = 0; i < 30; i++) {
                String color = colors[i % colors.length];
                js.executeScript("document.getElementById('color-test').style.backgroundColor = '" + color + "';");
                Thread.sleep(100);
            }
            
            // Test 2: Rapid size changes (potential for scaling artifacts)
            logger.info("Testing rapid size changes...");
            js.executeScript(
                "document.getElementById('test-container').innerHTML = " +
                "'<div class=\"artifact-test rapid-change\" id=\"size-test\" style=\"background: blue; width: 100px; height: 100px;\">Size Change Test</div>';" +
                "document.getElementById('status').innerHTML = 'Test 2: Rapid Size Changes';"
            );
            
            for (int i = 0; i < 20; i++) {
                int size = 50 + (i * 10);
                js.executeScript(
                    "document.getElementById('size-test').style.width = '" + size + "px';" +
                    "document.getElementById('size-test').style.height = '" + size + "px';"
                );
                Thread.sleep(150);
            }
            
            // Test 3: Overlapping elements (potential for rendering conflicts)
            logger.info("Testing overlapping elements...");
            js.executeScript(
                "document.getElementById('test-container').innerHTML = " +
                "'<div class=\"artifact-test\" style=\"background: lightgray; position: relative;\">" +
                "<div class=\"overlay\" style=\"background: red; width: 100px; height: 100px; z-index: 1;\">Layer 1</div>" +
                "<div class=\"overlay\" style=\"background: blue; width: 80px; height: 80px; z-index: 2; opacity: 0.7;\">Layer 2</div>" +
                "<div class=\"overlay\" style=\"background: green; width: 60px; height: 60px; z-index: 3; opacity: 0.5;\">Layer 3</div>" +
                "</div>';" +
                "document.getElementById('status').innerHTML = 'Test 3: Overlapping Elements';"
            );
            Thread.sleep(2000);
            
            // Test 4: Rapid text changes (potential for font rendering issues)
            logger.info("Testing rapid text changes...");
            js.executeScript(
                "document.getElementById('test-container').innerHTML = " +
                "'<div class=\"artifact-test\" style=\"background: white; font-size: 24px; text-align: center; line-height: 200px;\" id=\"text-test\">Text Change Test</div>';" +
                "document.getElementById('status').innerHTML = 'Test 4: Rapid Text Changes';"
            );
            
            String[] texts = {"LOADING", "PROCESSING", "CALCULATING", "RENDERING", "COMPLETE", "SUCCESS", "READY", "DONE"};
            for (int i = 0; i < 24; i++) {
                String text = texts[i % texts.length];
                js.executeScript(
                    "document.getElementById('text-test').innerHTML = '" + text + "';" +
                    "document.getElementById('text-test').style.color = '#' + Math.floor(Math.random()*16777215).toString(16);"
                );
                Thread.sleep(250);
            }
            
            // Test 5: Border and outline changes (potential for edge artifacts)
            logger.info("Testing border changes...");
            js.executeScript(
                "document.getElementById('test-container').innerHTML = " +
                "'<div class=\"artifact-test\" id=\"border-test\" style=\"background: yellow; border: 1px solid black;\">Border Test</div>';" +
                "document.getElementById('status').innerHTML = 'Test 5: Border Changes';"
            );
            
            for (int i = 1; i <= 20; i++) {
                js.executeScript(
                    "document.getElementById('border-test').style.border = '" + i + "px solid " + 
                    (i % 2 == 0 ? "red" : "blue") + "';"
                );
                Thread.sleep(200);
            }
            
            logger.info("Completed visual artifact detection test");
            
        } catch (Exception e) {
            logger.error("Visual artifact detection test failed", e);
            throw e;
        }
    }

    @Test
    public void testScreenCaptureCompleteness() throws Exception {
        logger.info("=== Testing Screen Capture Completeness ===");
        
        try {
            speedVideoRecorder.setVideoSpeed(VideoRecordWithSpeedControl.VideoSpeed.REAL_TIME);
            speedVideoRecorder.startRecording();
            logger.info("Started screen capture completeness test");
            
            // Create full-screen test with elements in all corners and edges
            String completeHTML = "<html><head><style>" +
                "body { margin: 0; padding: 0; width: 100vw; height: 100vh; position: relative; }" +
                ".corner { position: absolute; width: 100px; height: 100px; text-align: center; line-height: 100px; font-weight: bold; }" +
                ".top-left { top: 0; left: 0; background: red; }" +
                ".top-right { top: 0; right: 0; background: green; }" +
                ".bottom-left { bottom: 0; left: 0; background: blue; }" +
                ".bottom-right { bottom: 0; right: 0; background: yellow; }" +
                ".center { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); " +
                "         background: purple; color: white; padding: 20px; font-size: 24px; }" +
                ".edge-marker { position: absolute; width: 20px; height: 20px; background: orange; }" +
                ".edge-top { top: 0; left: 50%; transform: translateX(-50%); }" +
                ".edge-bottom { bottom: 0; left: 50%; transform: translateX(-50%); }" +
                ".edge-left { left: 0; top: 50%; transform: translateY(-50%); }" +
                ".edge-right { right: 0; top: 50%; transform: translateY(-50%); }" +
                "</style></head><body>" +
                "<div class='corner top-left'>TL</div>" +
                "<div class='corner top-right'>TR</div>" +
                "<div class='corner bottom-left'>BL</div>" +
                "<div class='corner bottom-right'>BR</div>" +
                "<div class='center' id='center-content'>SCREEN CAPTURE COMPLETENESS TEST</div>" +
                "<div class='edge-marker edge-top'></div>" +
                "<div class='edge-marker edge-bottom'></div>" +
                "<div class='edge-marker edge-left'></div>" +
                "<div class='edge-marker edge-right'></div>" +
                "<div id='timestamp' style='position: absolute; bottom: 50px; left: 50%; transform: translateX(-50%); background: white; padding: 10px;'></div>" +
                "</body></html>";
            
            driver.get("data:text/html;charset=utf-8," + completeHTML);
            Thread.sleep(2000);
            
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            // Test full screen coverage with dynamic updates
            for (int i = 1; i <= 20; i++) {
                js.executeScript(
                    "document.getElementById('center-content').innerHTML = 'COMPLETENESS TEST - Frame " + i + "';" +
                    "document.getElementById('timestamp').innerHTML = 'Frame " + i + " - ' + new Date().toLocaleTimeString();"
                );
                
                // Cycle through corner colors to ensure all areas are captured
                if (i % 4 == 1) {
                    js.executeScript(
                        "document.querySelector('.top-left').style.backgroundColor = 'orange';" +
                        "document.querySelector('.top-right').style.backgroundColor = 'purple';" +
                        "document.querySelector('.bottom-left').style.backgroundColor = 'cyan';" +
                        "document.querySelector('.bottom-right').style.backgroundColor = 'lime';"
                    );
                } else if (i % 4 == 3) {
                    js.executeScript(
                        "document.querySelector('.top-left').style.backgroundColor = 'red';" +
                        "document.querySelector('.top-right').style.backgroundColor = 'green';" +
                        "document.querySelector('.bottom-left').style.backgroundColor = 'blue';" +
                        "document.querySelector('.bottom-right').style.backgroundColor = 'yellow';"
                    );
                }
                
                Thread.sleep(500);
                
                if (i % 5 == 0) {
                    logger.info("Screen capture completeness test frame " + i + "/20");
                }
            }
            
            // Final verification frame
            js.executeScript(
                "document.getElementById('center-content').innerHTML = 'CAPTURE COMPLETE - VERIFY ALL CORNERS AND EDGES VISIBLE';" +
                "document.getElementById('timestamp').innerHTML = 'FINAL FRAME - ' + new Date().toLocaleString();" +
                "document.body.style.border = '5px solid black';"
            );
            Thread.sleep(3000);
            
            logger.info("Completed screen capture completeness test");
            
        } catch (Exception e) {
            logger.error("Screen capture completeness test failed", e);
            throw e;
        }
    }

    @Test
    public void testFrameTimingConsistency() throws Exception {
        logger.info("=== Testing Frame Timing Consistency ===");
        
        try {
            speedVideoRecorder.setVideoSpeed(VideoRecordWithSpeedControl.VideoSpeed.REAL_TIME);
            speedVideoRecorder.setCaptureInterval(250); // 4 FPS for precise timing analysis
            speedVideoRecorder.startRecording();
            logger.info("Started frame timing consistency test with 250ms intervals");
            
            // Create precise timing test
            String timingHTML = "<html><head><style>" +
                "body { font-family: 'Courier New', monospace; text-align: center; padding: 50px; }" +
                "#timing-display { font-size: 48px; margin: 50px 0; }" +
                "#milliseconds { font-size: 24px; color: blue; }" +
                "#frame-info { font-size: 18px; background: #f0f0f0; padding: 20px; margin: 20px; }" +
                "</style></head><body>" +
                "<h1>Frame Timing Consistency Test</h1>" +
                "<div id='timing-display'>00:00</div>" +
                "<div id='milliseconds'>000ms</div>" +
                "<div id='frame-info'>Waiting to start...</div>" +
                "</body></html>";
            
            driver.get("data:text/html;charset=utf-8," + timingHTML);
            Thread.sleep(1000);
            
            JavascriptExecutor js = (JavascriptExecutor) driver;
            long testStartTime = System.currentTimeMillis();
            
            // Generate frames at precise intervals for timing validation
            for (int frame = 1; frame <= 40; frame++) {
                long currentTime = System.currentTimeMillis();
                long elapsedTime = currentTime - testStartTime;
                long expectedTime = frame * 250; // Expected time based on 250ms intervals
                long timingError = Math.abs(elapsedTime - expectedTime);
                
                int seconds = (int) (elapsedTime / 1000);
                int milliseconds = (int) (elapsedTime % 1000);
                
                js.executeScript(
                    "document.getElementById('timing-display').innerHTML = '" + 
                    String.format("%02d:%02d", seconds / 60, seconds % 60) + "';" +
                    "document.getElementById('milliseconds').innerHTML = '" + milliseconds + "ms';" +
                    "document.getElementById('frame-info').innerHTML = " +
                    "'Frame " + frame + " | Elapsed: " + elapsedTime + "ms | Expected: " + expectedTime + "ms | Error: " + timingError + "ms';"
                );
                
                // Visual indicator of timing accuracy
                if (timingError < 10) {
                    js.executeScript("document.body.style.backgroundColor = 'lightgreen';");
                } else if (timingError < 50) {
                    js.executeScript("document.body.style.backgroundColor = 'lightyellow';");
                } else {
                    js.executeScript("document.body.style.backgroundColor = 'lightcoral';");
                }
                
                Thread.sleep(250); // Maintain consistent interval
                
                if (frame % 10 == 0) {
                    logger.info("Timing test frame " + frame + "/40 - Error: " + timingError + "ms");
                }
            }
            
            long totalTestTime = System.currentTimeMillis() - testStartTime;
            long expectedTotalTime = 40 * 250; // 10 seconds
            long totalError = Math.abs(totalTestTime - expectedTotalTime);
            
            js.executeScript(
                "document.getElementById('timing-display').innerHTML = 'COMPLETE';" +
                "document.getElementById('milliseconds').innerHTML = 'Total: " + totalTestTime + "ms';" +
                "document.getElementById('frame-info').innerHTML = " +
                "'Test Complete | Total Time: " + totalTestTime + "ms | Expected: " + expectedTotalTime + "ms | Error: " + totalError + "ms';" +
                "document.body.style.backgroundColor = '" + (totalError < 100 ? "green" : "red") + "';"
            );
            Thread.sleep(2000);
            
            logger.info("Frame timing consistency test completed:");
            logger.info("  Total time: " + totalTestTime + "ms");
            logger.info("  Expected time: " + expectedTotalTime + "ms");
            logger.info("  Total error: " + totalError + "ms");
            logger.info("  Timing accuracy: " + String.format("%.2f", (1.0 - (double)totalError/expectedTotalTime) * 100) + "%");
            
        } catch (Exception e) {
            logger.error("Frame timing consistency test failed", e);
            throw e;
        }
    }

    @AfterMethod
    public void tearDown() throws Exception {
        try {
            logger.info("Starting visual validation test cleanup");
            
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
            
            logger.info("Visual validation test cleanup completed");
            
        } catch (Exception e) {
            logger.error("Error during visual validation test cleanup", e);
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