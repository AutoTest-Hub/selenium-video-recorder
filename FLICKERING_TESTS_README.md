# Comprehensive Flickering Detection Test Suite

This project now includes an extensive suite of tests specifically designed to help reproduce and identify flickering issues in video recordings. The test suite has been significantly expanded from the original basic tests to include comprehensive scenarios that stress-test the video recording system.

## 📊 Test Overview

### Original Test Coverage
- 2 basic test classes
- 7 simple test methods
- Basic single and multi-tab recording

### New Enhanced Coverage
- **6 comprehensive test classes**
- **40+ detailed test methods**  
- **Multiple specialized test suites**
- **Parallel execution support**
- **Visual validation framework**

## 🔍 Test Classes Added

### 1. FlickeringReproductionTest
**Purpose**: Reproduce various types of flickering issues through controlled scenarios.

**Test Methods**:
- `testRapidDOMChangesFlickering()` - Rapid element addition/removal
- `testCSSAnimationFlickering()` - Multiple concurrent CSS animations  
- `testRapidTabSwitchingFlickering()` - Fast tab switching with auto-rebind
- `testScrollingFlickering()` - Various scrolling patterns
- `testAjaxFlickering()` - Rapid AJAX requests and responses
- `testMemoryPressureFlickering()` - DOM manipulation under memory pressure
- `testConcurrentOperationsFlickering()` - Multi-threaded DOM operations
- `testResourceContentionFlickering()` - Dual recorder resource conflicts
- `testVisualConsistencyValidation()` - Predictable change validation

### 2. VideoRecordingStressTest
**Purpose**: Push the recording system to its limits to identify breaking points.

**Test Methods**:
- `testExtremeDurationRecording()` - 3+ minute marathon recording
- `testHighFrequencyFrameCapture()` - 50ms capture intervals (20 FPS)
- `testMultipleTabsStressTest()` - 10+ tabs with rapid switching
- `testMemoryExhaustionStressTest()` - Massive DOM creation/cleanup
- `testRecordingFailureRecovery()` - Error simulation and recovery
- `testConcurrentRecordingStress()` - Multiple simultaneous recorders
- `testPlatformSpecificStress()` - OS-specific optimizations
- `testResourceCleanupStress()` - Multiple start/stop cycles

### 3. ParallelVideoRecordingTest
**Purpose**: Test parallel execution scenarios for resource contention issues.

**Test Methods**:
- `testParallelRecordingSessions()` - 4 concurrent recording sessions
- `testParallelTabOperations()` - Parallel multi-tab operations
- `testResourceContentionDetection()` - Performance variance analysis
- `testThreadSafetyValidation()` - Shared resource thread safety
- `testParallelPerformanceBenchmark()` - Efficiency measurements

### 4. VisualValidationTest
**Purpose**: Validate frame consistency and identify visual artifacts.

**Test Methods**:
- `testFrameSequenceConsistency()` - Sequential frame validation
- `testColorAccuracyValidation()` - Color reproduction testing
- `testTextRenderingValidation()` - Font and text accuracy
- `testAnimationSmoothnessValidation()` - Animation capture quality
- `testVisualArtifactDetection()` - Artifact identification scenarios
- `testScreenCaptureCompleteness()` - Full screen coverage validation
- `testFrameTimingConsistency()` - Precise timing validation

## 🏃‍♂️ How to Run Tests

### Quick Flickering Detection
```bash
# Run focused flickering tests only (fastest)
mvn test -Dtest.suite=src/test/resources/testng-flickering-only.xml

# Alternative method
mvn test -Dsuite.xml=testng-flickering-only.xml
```

### Comprehensive Test Suite
```bash
# Run all new tests (comprehensive but longer)
mvn test -Dtest.suite=src/test/resources/testng-comprehensive.xml

# Or on macOS/Linux:
./run-tests-mac.sh comprehensive
```

### Individual Test Categories
```bash
# Run only stress tests
mvn test -Dtest="VideoRecordingStressTest"

# Run only flickering reproduction tests  
mvn test -Dtest="FlickeringReproductionTest"

# Run only visual validation tests
mvn test -Dtest="VisualValidationTest"

# Run only parallel tests
mvn test -Dtest="ParallelVideoRecordingTest"
```

### Smoke Tests (Quick Validation)
```bash
# Run a few key tests to verify basic functionality
mvn test -Dtest.suite=src/test/resources/testng-comprehensive.xml -Dtest.groups="Smoke Tests"
```

## 📋 Test Suites Available

### 1. testng-comprehensive.xml
**Complete test suite with all new functionality**
- Core video recording tests
- Video speed control tests
- **Flickering detection tests** ⭐
- **Stress testing suite** ⭐
- **Parallel execution tests** ⭐
- **Visual validation tests** ⭐
- SmartWait framework tests
- Performance comparison tests (disabled by default)
- Real-world challenge tests (disabled by default)
- Quick smoke tests

### 2. testng-flickering-only.xml  
**Focused flickering detection suite (recommended for flickering issues)**
- Core flickering detection
- Visual flickering validation
- Flickering under stress
- Race condition flickering

### 3. testng.xml (original)
**Basic test suite with original functionality**
- Core video recording tests
- Video speed control tests

## 🔧 Configuration Options

### Enable/Disable Test Groups
Edit the XML configuration files to enable/disable specific test groups:

```xml
<!-- Enable long-running tests -->
<test name="Marathon Stress Test" preserve-order="true" enabled="true">

<!-- Disable network-dependent tests -->  
<test name="Real-World Challenge Tests" preserve-order="true" enabled="false">
```

### Adjust Test Parameters
Modify test behavior by editing the test classes:

```java
// High frequency capture for detailed flickering analysis
speedVideoRecorder.setCaptureInterval(50); // 50ms = 20 FPS

// Longer duration for extended flickering reproduction
while (System.currentTimeMillis() - startTime < 300000) { // 5 minutes
```

## 📈 What to Look For

### Common Flickering Indicators
1. **Frame Drops**: Missing frames in sequence
2. **Visual Artifacts**: Corrupted or incomplete frames  
3. **Timing Issues**: Inconsistent frame intervals
4. **Color Problems**: Color bleeding or incorrect colors
5. **Text Issues**: Blurry or missing text
6. **Animation Problems**: Choppy or incomplete animations

### Performance Metrics to Monitor
- Frame capture timing consistency
- Memory usage during recording
- CPU utilization patterns
- Resource contention indicators
- Error rates under stress

### Log Analysis
The tests generate detailed logs showing:
- Frame generation timestamps
- Performance measurements  
- Error conditions and recovery
- Resource usage statistics
- Test execution summaries

## 🚨 Troubleshooting

### Common Issues

#### Test Timeouts
```bash
# Increase timeout for long-running tests
mvn test -Dtestng.timeout=600000  # 10 minutes
```

#### Memory Issues  
```bash
# Increase JVM memory
export MAVEN_OPTS="-Xmx4g -XX:MaxPermSize=512m"
mvn test
```

#### Port Conflicts (Parallel Tests)
The parallel tests use different debugging ports (9224+). If you get port conflicts:
```bash
# Check for processes using ports
lsof -i :9224-9230
# Kill conflicting processes
kill -9 <pid>
```

#### Chrome Driver Issues
```bash
# Update Chrome driver
mvn clean test -Dwebdriver.chrome.driver=/path/to/new/chromedriver
```

## 📊 Expected Results

### Successful Test Runs Should Show:
- ✅ All frame sequences captured correctly
- ✅ Consistent timing measurements (< 5% variance)  
- ✅ No visual artifacts detected
- ✅ Proper resource cleanup
- ✅ Parallel execution without conflicts

### Flickering Issues May Present As:
- ❌ Missing frames in sequence  
- ❌ High timing variance (> 20%)
- ❌ Visual artifacts detected
- ❌ Resource contention warnings
- ❌ Thread safety violations

## 🎯 Next Steps

1. **Run the flickering-focused tests first**: `testng-flickering-only.xml`
2. **Analyze the generated videos** for visual issues
3. **Check logs** for performance warnings
4. **Run stress tests** to identify breaking points
5. **Use parallel tests** to find race conditions

The comprehensive test suite provides multiple angles to reproduce and analyze flickering issues that may not appear in simple recording scenarios.

## 📝 Notes

- Some tests are disabled by default due to long execution times
- Real-world tests require internet connectivity
- Marathon tests can run for 5+ minutes
- Parallel tests may require system tuning for optimal performance
- Generated videos are stored in the `videos/` directory for manual analysis

This enhanced test suite transforms your basic video recording tests into a comprehensive flickering detection and analysis system.