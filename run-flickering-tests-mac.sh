#!/bin/bash

# Comprehensive Flickering Detection Test Runner for macOS/Linux
# Part of the selenium-video-recorder project

echo "🔍 Selenium Video Recording - Flickering Detection Test Suite"
echo "============================================================="

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to print colored output
print_status() {
    echo -e "${GREEN}✅${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠️${NC} $1"
}

print_error() {
    echo -e "${RED}❌${NC} $1"
}

print_info() {
    echo -e "${BLUE}ℹ️${NC} $1"
}

print_header() {
    echo -e "${PURPLE}🎯${NC} $1"
}

print_success() {
    echo -e "${CYAN}🎉${NC} $1"
}

# Parse command line arguments
SUITE="flickering"
VERBOSE=false
HELP=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            HELP=true
            shift
            ;;
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        comprehensive|all|full)
            SUITE="comprehensive"
            shift
            ;;
        flickering|flicker|quick)
            SUITE="flickering"
            shift
            ;;
        stress|load)
            SUITE="stress"
            shift
            ;;
        parallel|concurrent)
            SUITE="parallel"
            shift
            ;;
        visual|validation)
            SUITE="visual"
            shift
            ;;
        smoke|basic)
            SUITE="smoke"
            shift
            ;;
        marathon|extreme)
            SUITE="marathon"
            shift
            ;;
        *)
            print_warning "Unknown option: $1"
            shift
            ;;
    esac
done

# Show help
if [ "$HELP" = true ]; then
    echo ""
    echo "Usage: $0 [OPTIONS] [SUITE]"
    echo ""
    echo "Test Suites:"
    echo "  flickering   - Focused flickering detection tests (default, ~10 min)"
    echo "  comprehensive- All new tests including stress and parallel (~45 min)"
    echo "  stress       - Stress testing suite only (~20 min)"
    echo "  parallel     - Parallel execution tests only (~15 min)" 
    echo "  visual       - Visual validation tests only (~15 min)"
    echo "  smoke        - Quick smoke tests (~5 min)"
    echo "  marathon     - Extreme duration tests (~10+ min)"
    echo ""
    echo "Options:"
    echo "  -v, --verbose    Enable verbose output"
    echo "  -h, --help       Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                    # Run flickering detection tests"
    echo "  $0 comprehensive -v  # Run all tests with verbose output"
    echo "  $0 stress            # Run only stress tests"
    echo ""
    exit 0
fi

# Check prerequisites
echo ""
echo "🔍 Checking prerequisites..."

# Check Java
if command_exists java; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
    print_status "Java found: $JAVA_VERSION"
else
    print_error "Java not found. Please install Java 11 or higher:"
    echo "  brew install openjdk@11"
    exit 1
fi

# Check Maven
if command_exists mvn; then
    MVN_VERSION=$(mvn -version 2>/dev/null | head -n 1 | cut -d' ' -f3)
    print_status "Maven found: $MVN_VERSION"
else
    print_error "Maven not found. Please install Maven:"
    echo "  brew install maven"
    exit 1
fi

# Check Chrome
if command_exists google-chrome || command_exists "Google Chrome" || [ -d "/Applications/Google Chrome.app" ]; then
    print_status "Google Chrome found"
else
    print_error "Google Chrome not found. Please install Chrome:"
    echo "  brew install --cask google-chrome"
    exit 1
fi

# Check FFmpeg (optional for video generation)
if command_exists ffmpeg; then
    FFMPEG_VERSION=$(ffmpeg -version 2>/dev/null | head -n 1 | cut -d' ' -f3)
    print_status "FFmpeg found: $FFMPEG_VERSION (optional for video processing)"
else
    print_warning "FFmpeg not found (optional). Install with: brew install ffmpeg"
fi

print_status "All required prerequisites are installed"

# Create output directories
echo ""
echo "📁 Preparing output directories..."
mkdir -p videos logs frames visual-validation-output
print_status "Output directories created: videos/, logs/, frames/, visual-validation-output/"

# Compile the project
echo ""
echo "🔨 Compiling the flickering detection test suite..."
if [ "$VERBOSE" = true ]; then
    mvn clean compile
else
    mvn clean compile > /dev/null 2>&1
fi

if [ $? -ne 0 ]; then
    print_error "Compilation failed. Running with verbose output:"
    mvn clean compile
    exit 1
fi
print_status "Compilation successful"

# Display system info
echo ""
print_info "System Information:"
echo "  OS: $(uname -s) $(uname -r)"
echo "  Architecture: $(uname -m)"
echo "  Available CPU cores: $(sysctl -n hw.ncpu 2>/dev/null || nproc 2>/dev/null || echo 'Unknown')"
echo "  Memory: $(sysctl -n hw.memsize 2>/dev/null | awk '{print int($1/1024/1024/1024)"GB"}' || echo 'Unknown')"

# Set Maven options for performance
export MAVEN_OPTS="-Xmx4g -XX:MaxMetaspaceSize=512m -Djava.awt.headless=true"

# Function to run tests with timing
run_test_suite() {
    local suite_name="$1"
    local test_command="$2"
    local description="$3"
    
    echo ""
    print_header "Running $suite_name"
    echo "Description: $description"
    echo "Command: $test_command"
    echo ""
    
    start_time=$(date +%s)
    
    if [ "$VERBOSE" = true ]; then
        eval $test_command
    else
        eval $test_command -q
    fi
    
    test_result=$?
    end_time=$(date +%s)
    duration=$((end_time - start_time))
    minutes=$((duration / 60))
    seconds=$((duration % 60))
    
    if [ $test_result -eq 0 ]; then
        print_status "$suite_name completed successfully in ${minutes}m ${seconds}s"
    else
        print_error "$suite_name failed after ${minutes}m ${seconds}s"
        return 1
    fi
    
    return 0
}

# Main test execution based on selected suite
case $SUITE in
    "flickering")
        print_header "🔍 FLICKERING DETECTION TEST SUITE"
        print_info "Running focused tests to reproduce flickering issues"
        echo ""
        
        run_test_suite \
            "Core Flickering Detection Tests" \
            "mvn test -Dsurefire.suiteXmlFiles=src/test/resources/testng-flickering-only.xml" \
            "Tests specifically designed to reproduce various types of flickering"
        ;;
        
    "comprehensive")
        print_header "🎯 COMPREHENSIVE TEST SUITE"
        print_info "Running all new test classes - this will take 30-45 minutes"
        echo ""
        
        run_test_suite \
            "Comprehensive Test Suite" \
            "mvn test -Dsurefire.suiteXmlFiles=src/test/resources/testng-comprehensive.xml" \
            "Complete suite with flickering, stress, parallel, and visual tests"
        ;;
        
    "stress")
        print_header "⚡ STRESS TEST SUITE"
        print_info "Running stress tests to identify system limits and breaking points"
        echo ""
        
        run_test_suite \
            "Stress Tests" \
            "mvn test -Dtest=\"VideoRecordingStressTest\"" \
            "High-load tests including memory exhaustion and extreme duration"
        ;;
        
    "parallel")
        print_header "🔄 PARALLEL EXECUTION TEST SUITE"
        print_info "Running parallel tests to identify race conditions and resource contention"
        echo ""
        
        run_test_suite \
            "Parallel Tests" \
            "mvn test -Dtest=\"ParallelVideoRecordingTest\"" \
            "Concurrent recording sessions and thread safety validation"
        ;;
        
    "visual")
        print_header "🎨 VISUAL VALIDATION TEST SUITE"
        print_info "Running visual tests to detect frame consistency and artifacts"
        echo ""
        
        run_test_suite \
            "Visual Validation Tests" \
            "mvn test -Dtest=\"VisualValidationTest\"" \
            "Frame sequence, color accuracy, and animation smoothness validation"
        ;;
        
    "smoke")
        print_header "💨 SMOKE TEST SUITE"
        print_info "Running quick smoke tests to verify basic functionality"
        echo ""
        
        run_test_suite \
            "Smoke Tests" \
            "mvn test -Dtest=\"VideoRecordingTest#testSingleTabVideoRecording,FlickeringReproductionTest#testVisualConsistencyValidation,VisualValidationTest#testFrameSequenceConsistency\"" \
            "Quick validation of core functionality"
        ;;
        
    "marathon")
        print_header "🏃‍♂️ MARATHON TEST SUITE"
        print_info "Running extreme duration tests - this may take 10+ minutes"
        echo ""
        
        run_test_suite \
            "Marathon Tests" \
            "mvn test -Dtest=\"VideoRecordingStressTest#testExtremeDurationRecording\"" \
            "Extreme duration recording test (3+ minutes of continuous recording)"
        ;;
        
    *)
        print_error "Unknown test suite: $SUITE"
        echo "Run with --help to see available options"
        exit 1
        ;;
esac

test_exit_code=$?

# Show results summary
echo ""
echo "📊 Test Execution Summary"
echo "========================"

if [ $test_exit_code -eq 0 ]; then
    print_success "All tests completed successfully!"
else
    print_error "Some tests failed. Check the output above for details."
fi

# Show generated artifacts
echo ""
print_info "Generated Artifacts:"

if [ -d "videos" ] && [ "$(ls -A videos)" ]; then
    echo "  📹 Videos generated:"
    ls -la videos/ | grep -E "\.(mp4|avi|mov)$" | head -10 | while read line; do
        echo "    $line"
    done
    video_count=$(ls videos/*.mp4 videos/*.avi videos/*.mov 2>/dev/null | wc -l)
    if [ $video_count -gt 10 ]; then
        echo "    ... and $((video_count - 10)) more videos"
    fi
else
    print_warning "No videos generated (this may be expected for some test suites)"
fi

if [ -d "frames" ] && [ "$(ls -A frames)" ]; then
    frame_count=$(ls frames/*.png 2>/dev/null | wc -l)
    echo "  🖼️  Frames captured: $frame_count"
else
    echo "  🖼️  Frames: None generated"
fi

if [ -d "visual-validation-output" ] && [ "$(ls -A visual-validation-output)" ]; then
    validation_count=$(ls visual-validation-output/* 2>/dev/null | wc -l)
    echo "  🔍 Visual validation files: $validation_count"
else
    echo "  🔍 Visual validation files: None generated"
fi

# Show logs location
echo "  📋 Test logs: Check target/surefire-reports/ for detailed test results"

# Performance recommendations
echo ""
print_info "Performance Recommendations:"

# Check system resources
CPU_COUNT=$(sysctl -n hw.ncpu 2>/dev/null || echo 4)
MEMORY_GB=$(sysctl -n hw.memsize 2>/dev/null | awk '{print int($1/1024/1024/1024)}' || echo 8)

if [ $CPU_COUNT -lt 4 ]; then
    print_warning "Consider using fewer parallel tests on systems with < 4 CPU cores"
fi

if [ $MEMORY_GB -lt 8 ]; then
    print_warning "Consider reducing JVM memory settings on systems with < 8GB RAM"
    echo "  export MAVEN_OPTS=\"-Xmx2g -XX:MaxMetaspaceSize=256m\""
fi

# Next steps
echo ""
print_info "Next Steps for Flickering Analysis:"
echo "  1. Review generated videos for visual flickering issues"
echo "  2. Check test logs for timing inconsistencies and performance warnings"
echo "  3. Run individual failing tests with -v for detailed output"
echo "  4. Compare results between different test suites to isolate issues"

# Troubleshooting tips
echo ""
print_info "Troubleshooting:"
echo "  • For port conflicts: lsof -i :9220-9230 && kill -9 <pid>"
echo "  • For memory issues: export MAVEN_OPTS=\"-Xmx2g\""
echo "  • For timeout issues: mvn test -Dtestng.timeout=900000"
echo "  • For verbose output: $0 $SUITE -v"

# Final recommendations based on suite
echo ""
case $SUITE in
    "flickering")
        print_info "Flickering Detection Complete:"
        echo "  • Check videos for visual flickering patterns"
        echo "  • Look for timing consistency warnings in logs"
        echo "  • Run 'comprehensive' suite if issues found"
        ;;
    "comprehensive")
        print_info "Comprehensive Analysis Complete:"
        echo "  • Review all generated artifacts for patterns"
        echo "  • Focus on tests that showed warnings or failures"
        echo "  • Consider running individual test classes for deeper analysis"
        ;;
    "stress")
        print_info "Stress Testing Complete:"
        echo "  • Check for memory-related flickering under load"
        echo "  • Review system resource usage during tests"
        echo "  • Identify breaking points and system limits"
        ;;
esac

if [ $test_exit_code -eq 0 ]; then
    print_success "Flickering detection test suite execution completed successfully!"
    exit 0
else
    print_error "Some tests failed. Use the information above to troubleshoot."
    exit 1
fi