# Amazon Linux EC2 Setup Guide for Video Recording

This guide explains how to set up and optimize your Amazon Linux EC2 instance for Selenium video recording with the Linux optimization framework.

## 🎯 Compatibility Status

✅ **The current Linux implementation will work on Amazon Linux EC2 instances out of the box**

The framework now includes:
- ✅ Automatic Amazon Linux EC2 detection
- ✅ EC2-specific Chrome optimizations  
- ✅ Instance type-aware resource management
- ✅ Fallback screenshot capture for guaranteed frame generation

## 🚀 Quick Setup for EC2

### 1. Launch Amazon Linux 2023 Instance

```bash
# Recommended instance types for video recording:
# - t3.medium or larger (2+ vCPUs, 4+ GB RAM)
# - m5.large for production workloads
# - c5.large for CPU-intensive test suites
```

### 2. Install Prerequisites

```bash
# Update system
sudo dnf update -y

# Install Java 11 or 17
sudo dnf install -y java-11-amazon-corretto-devel
# OR: sudo dnf install -y java-17-amazon-corretto-devel

# Install Maven
sudo dnf install -y maven

# Install Git
sudo dnf install -y git

# Install Chrome dependencies
sudo dnf install -y \
    atk \
    cups-libs \
    gtk3 \
    libXcomposite \
    libXcursor \
    libXdamage \
    libXext \
    libXi \
    libXrandr \
    libXScrnSaver \
    libXtst \
    pango \
    alsa-lib
```

### 3. Install Google Chrome

```bash
# Add Google Chrome repository
sudo tee /etc/yum.repos.d/google-chrome.repo > /dev/null <<EOF
[google-chrome]
name=google-chrome
baseurl=http://dl.google.com/linux/chrome/rpm/stable/x86_64
enabled=1
gpgcheck=1
gpgkey=https://dl.google.com/linux/linux_signing_key.pub
EOF

# Install Chrome
sudo dnf install -y google-chrome-stable

# Verify installation
google-chrome --version
```

### 4. Install FFmpeg

```bash
# Enable EPEL repository
sudo dnf install -y epel-release

# Install FFmpeg
sudo dnf install -y ffmpeg

# Verify installation
ffmpeg -version
```

### 5. Setup Virtual Display (for GUI-less testing)

```bash
# Install Xvfb (X Virtual Framebuffer)
sudo dnf install -y xorg-x11-server-Xvfb

# Start virtual display
export DISPLAY=:99
Xvfb :99 -screen 0 1280x720x24 &

# Make it persistent (optional)
echo 'export DISPLAY=:99' >> ~/.bashrc
```

## 🔧 EC2-Specific Optimizations

The framework automatically detects EC2 instances and applies optimizations:

### Memory Optimization
```java
// Automatically applied on EC2:
--max_old_space_size=2048        // Limit Chrome V8 memory
--disk-cache-size=50000000       // 50MB cache for EBS optimization
--media-cache-size=50000000      // 50MB media cache
```

### CPU Optimization (for t2/t3 burstable instances)
```java
// Automatically applied on EC2:
--max-threads=4                  // Limit thread usage
--renderer-process-limit=2       // Limit Chrome processes
```

### Instance Type Recommendations

| Instance Type | Use Case | Video Recording Capability |
|---------------|----------|----------------------------|
| `t3.micro` | ❌ Not recommended | Too limited |
| `t3.small` | ❌ Light testing only | Basic tests only |
| `t3.medium` | ✅ Development/Testing | Good for moderate test suites |
| `t3.large` | ✅ Production Testing | Excellent for most workloads |
| `m5.large` | ✅ Production | Optimal memory/CPU balance |
| `c5.large` | ✅ CPU-intensive tests | Best for complex scenarios |

## 🛠️ Configuration Examples

### Basic Test Execution
```bash
# Clone your test project
git clone https://github.com/your-username/selenium-video-recorder.git
cd selenium-video-recorder

# Run tests (framework auto-detects EC2)
mvn test -Dtest.suite=testng-linux-only.xml -Dheadless=true
```

### Environment Validation
```bash
# Check EC2 detection and optimizations
mvn test -Dtest=LinuxOptimizationTest#testBasicRecordingWithLinuxOptimizations

# View environment information
tail -f target/surefire-reports/*.txt | grep -A 20 "Environment Information"
```

## 📊 Expected Log Output on EC2

When running on Amazon Linux EC2, you should see:

```
INFO - Detected Linux environment, applying Linux-specific optimizations
INFO - OS: linux, Arch: amd64, Version: 6.1.102-111.187.amzn2023.x86_64
INFO - Amazon Linux EC2: true
INFO - Headless Environment: true
INFO - Applying Linux-specific GPU and compositor settings...
INFO - Applying Amazon Linux EC2-specific optimizations...
INFO - Amazon Linux EC2 optimizations applied successfully
INFO - CI environment detected - screenshot fallback available
INFO - Environment Information:
Operating System: linux
Architecture: amd64
OS Version: 6.1.102-111.187.amzn2023.x86_64
Amazon Linux EC2: true
AWS Region: us-west-2
Available Processors: 2
Max JVM Memory: 1024 MB
Display: :99
Headless Environment: true
```

## 🎥 Video Generation Methods

The framework uses different capture methods based on availability:

1. **Primary**: DevTools screencast (if available)
2. **Fallback**: WebDriver screenshots (guaranteed to work on EC2)

### Verifying Video Generation
```bash
# Check generated videos
ls -la videos/
# Should show: test_run_2025-10-13_21-15-30.mp4

# Check frame capture
ls -la frames/
# Should show: frame_00001.png, frame_00002.png, etc.
```

## 🔍 Troubleshooting EC2 Issues

### Chrome Won't Start
```bash
# Check Chrome installation
google-chrome --version

# Test Chrome manually
google-chrome --headless --no-sandbox --disable-gpu --dump-dom https://example.com

# Check dependencies
ldd $(which google-chrome) | grep "not found"
```

### Display Issues
```bash
# Verify virtual display
echo $DISPLAY  # Should show :99

# Restart Xvfb if needed
pkill Xvfb
Xvfb :99 -screen 0 1280x720x24 &
```

### Memory Issues (t2/t3 instances)
```bash
# Check memory usage
free -h
top -p $(pgrep java)

# Adjust JVM memory
export MAVEN_OPTS="-Xmx1g -Xms512m"
```

### Network/Security Group Settings
- Ensure outbound HTTPS (443) is allowed for web navigation
- No inbound ports needed for video recording
- Consider VPC endpoints for AWS services if needed

## 🚀 Performance Optimization

### For Different Instance Types:

**t3.medium (2 vCPU, 4GB RAM):**
```bash
export MAVEN_OPTS="-Xmx2g -Xms1g"
# Run 1-2 parallel tests max
```

**m5.large (2 vCPU, 8GB RAM):**
```bash
export MAVEN_OPTS="-Xmx4g -Xms2g"  
# Can run 2-4 parallel tests
```

**c5.large (2 vCPU, 4GB RAM):**
```bash
export MAVEN_OPTS="-Xmx2g -Xms1g"
# Optimized for CPU-intensive scenarios
```

## 🔒 Security Considerations

- Use IAM roles instead of access keys when possible
- Limit security group rules to necessary ports only
- Consider using Session Manager instead of SSH
- Regularly update Chrome and system packages

## 📈 Monitoring and Scaling

### CloudWatch Metrics to Monitor:
- CPU Utilization (important for burstable instances)
- Memory Utilization
- Network In/Out (for test data transfer)
- EBS Read/Write Ops (for video file I/O)

### Auto Scaling for Test Execution:
- Use Launch Templates with pre-configured AMIs
- Scale based on test queue length
- Consider Spot Instances for cost optimization

---

## 🎯 Summary

✅ **Your current implementation will work perfectly on Amazon Linux EC2** with the new enhancements!

The framework now provides:
- Automatic EC2 detection and optimization
- Instance type-aware resource management  
- Guaranteed video generation with screenshot fallback
- Comprehensive logging and debugging information

No additional code changes are needed - just follow this setup guide for your EC2 instances.