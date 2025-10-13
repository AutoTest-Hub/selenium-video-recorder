#!/bin/bash

# Linux Video Recording Optimization Tests Runner
# This script runs the Linux-specific video recording optimization tests

set -e

echo "🐧 Linux Video Recording Optimization Test Runner"
echo "================================================="

# Check if running on Linux
OS=$(uname -s | tr '[:upper:]' '[:lower:]')
if [[ "$OS" == "linux" ]]; then
    echo "✅ Detected Linux environment - optimizations will be tested"
else
    echo "⚠️  Warning: Not running on Linux (detected: $OS)"
    echo "   Linux optimizations will be tested but may not be active"
fi

# Display environment information
echo ""
echo "🔍 Environment Information:"
echo "  OS: $(uname -s)"
echo "  Architecture: $(uname -m)"
echo "  Kernel: $(uname -r)"
if command -v lsb_release &> /dev/null; then
    echo "  Distribution: $(lsb_release -d -s)"
fi
echo "  Display: ${DISPLAY:-"not set"}"
echo "  Wayland Display: ${WAYLAND_DISPLAY:-"not set"}"
echo "  XDG Session Type: ${XDG_SESSION_TYPE:-"not set"}"
echo "  SSH Connection: ${SSH_CLIENT:+"yes"}"

# Check for required dependencies
echo ""
echo "🔧 Checking Dependencies:"

# Check Java
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2)
    echo "  ✅ Java: $JAVA_VERSION"
else
    echo "  ❌ Java not found"
    exit 1
fi

# Check Maven
if command -v mvn &> /dev/null; then
    MVN_VERSION=$(mvn -version 2>/dev/null | head -n1 | cut -d' ' -f3)
    echo "  ✅ Maven: $MVN_VERSION"
else
    echo "  ❌ Maven not found"
    exit 1
fi

# Check Chrome
if command -v google-chrome &> /dev/null; then
    CHROME_VERSION=$(google-chrome --version 2>/dev/null | cut -d' ' -f3)
    echo "  ✅ Chrome: $CHROME_VERSION"
elif command -v chromium-browser &> /dev/null; then
    CHROME_VERSION=$(chromium-browser --version 2>/dev/null | cut -d' ' -f2)
    echo "  ✅ Chromium: $CHROME_VERSION"
else
    echo "  ❌ Chrome/Chromium not found"
    exit 1
fi

# Check FFmpeg
if command -v ffmpeg &> /dev/null; then
    FFMPEG_VERSION=$(ffmpeg -version 2>/dev/null | head -n1 | cut -d' ' -f3)
    echo "  ✅ FFmpeg: $FFMPEG_VERSION"
else
    echo "  ❌ FFmpeg not found"
    exit 1
fi

# Clean up previous test artifacts
echo ""
echo "🧹 Cleaning Previous Test Artifacts:"
rm -rf frames/ videos/ test-output/ target/surefire-reports/
echo "  ✅ Cleaned up frames, videos, and test reports"

# Compile the project
echo ""
echo "🔨 Compiling Project:"
if mvn clean compile test-compile -q; then
    echo "  ✅ Project compiled successfully"
else
    echo "  ❌ Project compilation failed"
    exit 1
fi

# Run the Linux optimization tests
echo ""
echo "🚀 Running Linux Optimization Tests:"
echo "  Test Suite: testng-linux-only.xml"
echo "  Focus: Linux-specific video recording optimizations"
echo ""

# Set memory options for better performance
export MAVEN_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC"

# Run the tests with specific profile
if mvn test -Dtest.suite=testng-linux-only.xml -Dheadless=true -Dparallel=false; then
    echo ""
    echo "✅ Linux Optimization Tests Completed Successfully!"
    
    # Display results summary
    echo ""
    echo "📊 Test Results Summary:"
    
    if [ -d "test-output" ]; then
        # Count test results
        TOTAL_TESTS=$(find test-output -name "*.xml" -exec grep -l "test-method" {} \; | wc -l)
        echo "  Test output directory found"
        
        if [ -f "target/surefire-reports/testng-results.xml" ]; then
            PASSED=$(grep -o "passed=\"[0-9]*\"" target/surefire-reports/testng-results.xml | cut -d'"' -f2)
            FAILED=$(grep -o "failed=\"[0-9]*\"" target/surefire-reports/testng-results.xml | cut -d'"' -f2)
            SKIPPED=$(grep -o "skipped=\"[0-9]*\"" target/surefire-reports/testng-results.xml | cut -d'"' -f2)
            
            echo "  ✅ Passed: ${PASSED:-0}"
            echo "  ❌ Failed: ${FAILED:-0}"
            echo "  ⏭️  Skipped: ${SKIPPED:-0}"
        fi
    fi
    
    # Check for video outputs
    if [ -d "videos" ] && [ "$(ls -A videos 2>/dev/null)" ]; then
        VIDEO_COUNT=$(ls -1 videos/*.mp4 2>/dev/null | wc -l)
        echo "  🎥 Videos created: $VIDEO_COUNT"
        echo "  📁 Video directory: $(pwd)/videos"
        
        # List video files with sizes
        echo ""
        echo "🎬 Generated Videos:"
        for video in videos/*.mp4; do
            if [ -f "$video" ]; then
                SIZE=$(du -h "$video" | cut -f1)
                echo "  📼 $(basename "$video") ($SIZE)"
            fi
        done
    else
        echo "  ⚠️  No videos were created"
    fi
    
    # Performance optimization suggestions
    echo ""
    echo "🔧 Linux Performance Notes:"
    if [[ "$OS" == "linux" ]]; then
        echo "  ✅ Linux optimizations were active during testing"
        echo "  🎯 Check logs for frame timing metrics and performance data"
        echo "  📈 Look for improved frame consistency and reduced flickering"
        
        # Check for specific Linux optimization features
        if [ -f "target/surefire-reports/testng-results.xml" ] && grep -q "Linux.*optimization" target/surefire-reports/testng-results.xml; then
            echo "  🐧 Linux-specific optimizations detected in test output"
        fi
    else
        echo "  ⚠️  Linux optimizations were simulated only"
        echo "  💡 Run on actual Linux system for full optimization testing"
    fi
    
else
    echo ""
    echo "❌ Linux Optimization Tests Failed!"
    echo ""
    echo "📋 Troubleshooting Steps:"
    echo "  1. Check the test output above for specific error messages"
    echo "  2. Verify Chrome is properly installed and accessible"
    echo "  3. Ensure sufficient disk space for video files"
    echo "  4. Check system resources (memory, CPU)"
    echo "  5. Review target/surefire-reports/ for detailed error logs"
    
    if [[ "$OS" == "linux" ]]; then
        echo "  6. Verify Linux-specific dependencies (X11/Wayland libs)"
        echo "  7. Check for proper headless display configuration"
    fi
    
    exit 1
fi

echo ""
echo "🎯 Next Steps:"
echo "  • Review generated videos for quality and consistency"
echo "  • Check test-output/emailable-report.html for detailed results"
echo "  • Compare performance metrics between optimized and standard runs"
echo "  • Use these results to validate Linux flickering fixes"
echo ""
echo "For comprehensive testing, run: ./test_runners.sh comprehensive"
echo "For only flickering tests, run: ./test_runners.sh flickering"