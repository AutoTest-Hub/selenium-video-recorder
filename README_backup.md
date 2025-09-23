# Selenium Video Recorder

A comprehensive solution for recording Selenium test execution videos in headless Chrome with multi-tab support and configurable playback speeds.

## 🎯 **Key Features**

- **Multi-tab video recording** with automatic tab switching
- **Configurable video playback speeds** (slow motion, real-time, fast)
- **Deadlock-free implementation** for headless Chrome
- **DevTools v137 compatibility** (Selenium 4.35.0)
- **Cross-platform support** (Windows, macOS, Linux)
- **Proper resource management** and cleanup
- **Fixes the fast-forward video issue** with Thread.sleep() periods

## 🚨 **Problem Solved**

This project solves two critical issues with Selenium video recording:

### 1. **Multi-Tab Recording Failure in Headless Chrome**
- **Problem**: Second tab videos not recorded in headless mode
- **Root Cause**: Shared DevTools sessions between tabs causing conflicts
- **Solution**: Separate DevTools instances per tab with proper threading

### 2. **Fast-Forward Video Issue**
- **Problem**: Videos appear fast-forward, Thread.sleep() periods not visible
- **Root Cause**: Mismatch between frame capture timing and video playback rate
- **Solution**: Configurable video speeds with timed frame capture

## 🛠 **Prerequisites**

### **Required Software**
- **Java 11+** (OpenJDK or Oracle JDK)
- **Maven 3.6+** for build management
- **FFmpeg** for video generation
- **Google Chrome** (any recent version)

### **Installation Commands**

#### **Windows**
```cmd
winget install Eclipse.Temurin.11.JDK
winget install Apache.Maven
winget install Gyan.FFmpeg
winget install Google.Chrome
```

#### **macOS**
```bash
brew install openjdk@11 maven ffmpeg
brew install --cask google-chrome
```

#### **Linux (Ubuntu/Debian)**
```bash
sudo apt update
sudo apt install openjdk-11-jdk maven ffmpeg google-chrome-stable
```

## 🚀 **Quick Start**

### **1. Clone the Repository**
```bash
git clone https://github.com/AutoTest-Hub/selenium-video-recorder.git
cd selenium-video-recorder
```

### **2. Run Tests**
```bash
# Run all tests
mvn clean test

# Run only core video recording tests
mvn test -Dtest="VideoRecordingTest"

# Run only video speed tests
mvn test -Dtest="VideoSpeedTest"

# Run specific test method
mvn test -Dtest="VideoSpeedTest#testSlowMotionVideo"
```

### **3. Check Results**
- **Videos**: `videos/` directory
- **Logs**: `logs/video-recording.log`

## 📚 **Usage Examples**

### **Basic Video Recording**
```java
VideoRecordInHeadless recorder = new VideoRecordInHeadless(logger, driver);
recorder.startRecording();

// Your test actions here
driver.get("https://example.com");
Thread.sleep(3000); // This will be visible in the video

recorder.stopRecordingAndGenerateVideo();
recorder.cleanup();
```

### **Multi-Tab Recording with Auto-Switch**
```java
VideoRecordInHeadless recorder = new VideoRecordInHeadless(logger, driver);
recorder.setAutoRebindEnabled(true); // Enable automatic tab switching
recorder.startRecording();

driver.get("https://example.com");
// Open new tab - recording automatically switches
((JavascriptExecutor) driver).executeScript("window.open('https://google.com', '_blank');");

recorder.stopRecordingAndGenerateVideo();
recorder.cleanup();
```

### **Video Speed Control (Fixes Fast-Forward Issue)**
```java
VideoRecordWithSpeedControl recorder = new VideoRecordWithSpeedControl(logger, driver);
recorder.setVideoSpeed(VideoSpeed.SLOW_MOTION); // 2x slower - recommended
recorder.startRecording();

// Your test actions with Thread.sleep() will now be visible
driver.get("https://example.com");
Thread.sleep(4000); // Will show as 8 seconds in slow motion video

recorder.stopRecordingAndGenerateVideo();
recorder.cleanup();
```

### **Manual Tab Switching**
```java
VideoRecordInHeadless recorder = new VideoRecordInHeadless(logger, driver);
recorder.startRecording();

driver.get("https://example.com");
// Open new tab
((JavascriptExecutor) driver).executeScript("window.open('https://google.com', '_blank');");

// Manually switch recording to new tab
recorder.recordNewlyOpenedTab();

recorder.stopRecordingAndGenerateVideo();
recorder.cleanup();
```

## 🎬 **Video Speed Options**

| Speed Option | Frame Rate | Description | Use Case |
|--------------|------------|-------------|----------|
| `SLOW_MOTION` | 2 FPS | 2x slower than real-time | **Recommended default** - fixes fast-forward issue |
| `REAL_TIME` | 5 FPS | Matches execution time | When you want real-time playback |
| `VERY_SLOW` | 1 FPS | 4x slower than real-time | Detailed analysis of interactions |
| `FAST` | 10 FPS | 2x faster than real-time | Quick overviews |

## 🏗 **Project Structure**

```
selenium-video-recorder/
├── src/
│   ├── main/java/com/example/automation/
│   │   ├── util/
│   │   │   ├── VideoRecordInHeadless.java          # Core multi-tab recording
│   │   │   ├── VideoRecordWithSpeedControl.java    # Speed control version
│   │   │   └── TestBase.java                       # Utility methods
│   │   └── logger/
│   │       └── LoggerMechanism.java                # Logging wrapper
│   ├── test/java/com/example/automation/test/
│   │   ├── VideoRecordingTest.java                 # Core functionality tests
│   │   └── VideoSpeedTest.java                     # Speed control tests
│   └── resources/
│       ├── logback.xml                             # Logging configuration
│       └── testng.xml                              # TestNG suite configuration
├── videos/                                         # Generated video files
├── logs/                                           # Log files
├── frames/                                         # Temporary frame storage
└── pom.xml                                         # Maven configuration
```

## 🔧 **Configuration**

### **Chrome Options**
The project includes platform-specific Chrome options for optimal performance:

```java
ChromeOptions options = new ChromeOptions();
options.addArguments("--headless");
options.addArguments("--no-sandbox");
options.addArguments("--disable-dev-shm-usage");
options.addArguments("--disable-gpu");
options.addArguments("--window-size=1280,780");
options.addArguments("--remote-debugging-port=9222");

// Platform-specific optimizations
if (isWindows()) {
    options.addArguments("--disable-features=VizDisplayCompositor");
}
```

### **Video Configuration**
```java
// Configure video speed
recorder.setVideoSpeed(VideoSpeed.SLOW_MOTION);

// Configure frame capture interval
recorder.setCaptureInterval(300); // milliseconds

// Enable/disable auto-rebind for multi-tab
recorder.setAutoRebindEnabled(true);
```

## 🐛 **Troubleshooting**

### **Common Issues**

#### **1. ChromeDriver Version Mismatch**
```
SessionNotCreatedException: This version of ChromeDriver only supports Chrome version X
```
**Solution**: The project uses WebDriverManager to automatically handle ChromeDriver versions.

#### **2. FFmpeg Not Found**
```
RuntimeException: FFmpeg not found
```
**Solution**: Install FFmpeg using the commands in the Prerequisites section.

#### **3. No Frames Saved**
```
IllegalStateException: No frames saved. Video generation aborted.
```
**Solution**: Ensure Chrome is running in headless mode and DevTools port is available.

#### **4. Fast-Forward Videos**
**Problem**: Videos play too fast, Thread.sleep() periods not visible.
**Solution**: Use `VideoRecordWithSpeedControl` with `VideoSpeed.SLOW_MOTION`.

### **Debug Mode**
Enable debug logging by setting the log level to DEBUG in `logback.xml`:
```xml
<logger name="com.example.automation" level="DEBUG"/>
```

## 🧪 **Testing**

### **Test Profiles**
```bash
# Run stable tests only (excludes problematic ones)
mvn test -P stable-tests

# Run video-specific tests
mvn test -P video-tests

# Run with specific TestNG suite
mvn test -DsuiteXmlFile=src/test/resources/testng.xml
```

### **Test Categories**

#### **Core Tests** (`VideoRecordingTest`)
- `testSingleTabVideoRecording` - Basic single tab recording
- `testMultiTabVideoRecording` - Auto-rebind multi-tab recording
- `testManualTabSwitching` - Manual tab switching

#### **Speed Tests** (`VideoSpeedTest`)
- `testSlowMotionVideo` - 2x slower recording (recommended)
- `testRealTimeVideo` - Real-time playback
- `testVerySlowVideo` - 4x slower for detailed analysis
- `testMultiTabSlowMotion` - Multi-tab with speed control

## 📊 **Performance**

### **Resource Usage**
- **Memory**: ~100MB additional for DevTools sessions
- **CPU**: Minimal impact during recording
- **Disk**: ~1MB per minute of video (1280x780, 5 FPS)

### **Video Quality**
- **Resolution**: 1280x780 (configurable)
- **Format**: MP4 (H.264)
- **Frame Rate**: Configurable (1-10 FPS)
- **Compression**: Optimized for file size

## 🤝 **Contributing**

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 **License**

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 **Acknowledgments**

- Selenium WebDriver team for DevTools Protocol support
- Chrome DevTools team for the screencast API
- FFmpeg project for video processing capabilities

## 📞 **Support**

- **Issues**: [GitHub Issues](https://github.com/AutoTest-Hub/selenium-video-recorder/issues)
- **Discussions**: [GitHub Discussions](https://github.com/AutoTest-Hub/selenium-video-recorder/discussions)
- **Documentation**: This README and inline code comments

---

**Made with ❤️ for the Selenium testing community**
