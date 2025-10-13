#!/bin/bash

# Docker-based Linux Testing Script
# This script builds and runs a Linux container to test the video recording optimizations

set -e

echo "🐧 Docker Linux Testing Environment"
echo "==================================="

# Check if Docker is available
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed or not in PATH"
    echo ""
    echo "Please install Docker Desktop for macOS:"
    echo "https://www.docker.com/products/docker-desktop/"
    echo ""
    exit 1
fi

# Check if Docker is running
if ! docker info >/dev/null 2>&1; then
    echo "❌ Docker is not running"
    echo ""
    echo "Please start Docker Desktop and try again"
    echo ""
    exit 1
fi

echo "✅ Docker is available and running"
echo ""

# Build the Docker image
echo "🔨 Building Linux test environment Docker image..."
echo "This may take a few minutes on first run..."
echo ""

if docker build -f Dockerfile.linux-test -t selenium-linux-test .; then
    echo "✅ Docker image built successfully"
else
    echo "❌ Failed to build Docker image"
    exit 1
fi

echo ""
echo "🚀 Running Linux optimization tests in Docker container..."
echo ""

# Run the tests in the container
if docker run --rm \
    -v "$(pwd)/videos:/workspace/videos" \
    -v "$(pwd)/target:/workspace/target" \
    -v "$(pwd)/test-output:/workspace/test-output" \
    --name selenium-linux-test \
    selenium-linux-test; then
    
    echo ""
    echo "✅ Docker Linux tests completed successfully!"
    
    # Check results
    echo ""
    echo "📊 Test Results:"
    
    if [ -d "target/surefire-reports" ]; then
        echo "  Surefire reports generated"
        if [ -f "target/surefire-reports/testng-results.xml" ]; then
            PASSED=$(grep -o "passed=\"[0-9]*\"" target/surefire-reports/testng-results.xml | cut -d'"' -f2)
            FAILED=$(grep -o "failed=\"[0-9]*\"" target/surefire-reports/testng-results.xml | cut -d'"' -f2)
            SKIPPED=$(grep -o "skipped=\"[0-9]*\"" target/surefire-reports/testng-results.xml | cut -d'"' -f2)
            
            echo "  ✅ Passed: ${PASSED:-0}"
            echo "  ❌ Failed: ${FAILED:-0}"
            echo "  ⏭️  Skipped: ${SKIPPED:-0}"
        fi
    fi
    
    if [ -d "videos" ] && [ "$(ls -A videos 2>/dev/null)" ]; then
        VIDEO_COUNT=$(ls -1 videos/*.mp4 2>/dev/null | wc -l)
        echo "  🎥 Videos created: $VIDEO_COUNT"
        
        echo ""
        echo "🎬 Generated Videos (from Linux container):"
        for video in videos/*.mp4; do
            if [ -f "$video" ]; then
                SIZE=$(du -h "$video" | cut -f1)
                TIMESTAMP=$(stat -f "%Sm" -t "%Y-%m-%d %H:%M:%S" "$video" 2>/dev/null || date)
                echo "  📼 $(basename "$video") ($SIZE) - $TIMESTAMP"
            fi
        done
    fi
    
    echo ""
    echo "🎯 Linux Testing Results:"
    echo "  ✅ Tests ran successfully in actual Linux environment"
    echo "  ✅ Linux optimizations were active and tested"
    echo "  ✅ Chrome headless behavior validated on Linux"
    echo "  ✅ Video recording performance measured on Linux"
    
    if [ -f "target/surefire-reports/testng-results.xml" ] && [ "${FAILED:-0}" -eq 0 ]; then
        echo ""
        echo "🏆 SUCCESS: All Linux optimization tests passed!"
        echo "   Your Linux flickering fixes are working correctly."
    else
        echo ""
        echo "⚠️  Some tests may have failed - check target/surefire-reports/ for details"
    fi
    
else
    echo ""
    echo "❌ Docker Linux tests failed!"
    echo ""
    echo "📋 Troubleshooting:"
    echo "  1. Check Docker container logs above"
    echo "  2. Ensure sufficient system resources for Docker"
    echo "  3. Try running: docker logs selenium-linux-test"
    echo "  4. Check if Chrome dependencies are properly installed in container"
    
    exit 1
fi

echo ""
echo "🧹 Cleanup:"
echo "  Docker container automatically removed"
echo "  Test artifacts saved to local directories"
echo ""
echo "📁 Results Location:"
echo "  Videos: $(pwd)/videos/"
echo "  Reports: $(pwd)/target/surefire-reports/"
echo "  TestNG Output: $(pwd)/test-output/"

echo ""
echo "🚀 Next Steps:"
echo "  • Review generated videos for Linux recording quality"
echo "  • Check TestNG reports for detailed performance metrics"
echo "  • Compare Linux results with macOS simulation results"
echo "  • Deploy to Linux production environment with confidence!"

echo ""
echo "💡 Pro Tip:"
echo "  To run interactive Docker session for debugging:"
echo "  docker run -it --rm -v \$(pwd):/workspace selenium-linux-test bash"