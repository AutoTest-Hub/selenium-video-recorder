#!/bin/bash

echo "🚀 Selenium Video Recording Test Runner for macOS"
echo "===================================================="

# Check prerequisites
echo "🔍 Checking prerequisites..."

# Check Java
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
    echo "✅ Java found: $JAVA_VERSION"
else
    echo "❌ Java not found. Please install Java 11+:"
    echo "   brew install openjdk@11"
    exit 1
fi

# Check Maven
if command -v mvn &> /dev/null; then
    MVN_VERSION=$(mvn -version | head -n 1 | cut -d' ' -f3)
    echo "✅ Maven found: $MVN_VERSION"
else
    echo "❌ Maven not found. Please install Maven:"
    echo "   brew install maven"
    exit 1
fi

# Check FFmpeg
if command -v ffmpeg &> /dev/null; then
    FFMPEG_VERSION=$(ffmpeg -version | head -n 1 | cut -d' ' -f3)
    echo "✅ FFmpeg found: $FFMPEG_VERSION"
else
    echo "❌ FFmpeg not found. Please install FFmpeg:"
    echo "   brew install ffmpeg"
    exit 1
fi

# Check Chrome
if [ -d "/Applications/Google Chrome.app" ] || command -v google-chrome &> /dev/null; then
    echo "✅ Google Chrome found"
else
    echo "❌ Google Chrome not found. Please install Chrome:"
    echo "   brew install --cask google-chrome"
    exit 1
fi

echo "✅ All prerequisites are installed"
echo ""

# Create directories
mkdir -p videos logs frames

# Compile and run tests
echo "🔨 Compiling the project..."
mvn clean compile
if [ $? -ne 0 ]; then
    echo "❌ Compilation failed"
    exit 1
fi
echo "✅ Compilation successful"

echo ""
echo "🧪 Running video recording tests..."
mvn test
if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Tests completed successfully!"
    echo "📹 Check the 'videos' directory for recorded videos"
    echo "📋 Check the 'logs' directory for detailed logs"
    
    # Show generated videos
    if [ -d "videos" ] && [ "$(ls -A videos)" ]; then
        echo ""
        echo "📹 Generated videos:"
        ls -la videos/
    fi
else
    echo "❌ Tests failed. Check the logs for details."
    exit 1
fi
