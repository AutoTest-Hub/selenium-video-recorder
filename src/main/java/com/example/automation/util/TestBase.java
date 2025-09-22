package com.example.automation.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Base utility class for test configuration and cross-platform support.
 * Provides methods for FFmpeg detection and path handling.
 */
public class TestBase {

    /**
     * Get the FFmpeg executable path for the current operating system.
     * Searches common installation locations and PATH environment variable.
     * 
     * @return The path to the FFmpeg executable
     * @throws RuntimeException if FFmpeg is not found
     */
    public static String getFFmpegPath() {
        String os = System.getProperty("os.name").toLowerCase();
        
        // Common FFmpeg locations by OS
        String[] possiblePaths;
        
        if (os.contains("win")) {
            possiblePaths = new String[] {
                "C:\\Program Files\\ffmpeg\\bin\\ffmpeg.exe",
                "C:\\Program Files (x86)\\ffmpeg\\bin\\ffmpeg.exe",
                "C:\\ffmpeg\\bin\\ffmpeg.exe",
                "ffmpeg.exe"
            };
        } else if (os.contains("mac")) {
            possiblePaths = new String[] {
                "/opt/homebrew/bin/ffmpeg",
                "/usr/local/bin/ffmpeg",
                "/usr/bin/ffmpeg",
                "ffmpeg"
            };
        } else {
            // Linux and other Unix-like systems
            possiblePaths = new String[] {
                "/usr/bin/ffmpeg",
                "/usr/local/bin/ffmpeg",
                "/snap/bin/ffmpeg",
                "ffmpeg"
            };
        }
        
        // Check each possible path
        for (String path : possiblePaths) {
            if (isExecutableFile(path)) {
                return path;
            }
        }
        
        // Try to find in PATH environment variable
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] pathDirs = pathEnv.split(File.pathSeparator);
            for (String dir : pathDirs) {
                String ffmpegPath = Paths.get(dir, os.contains("win") ? "ffmpeg.exe" : "ffmpeg").toString();
                if (isExecutableFile(ffmpegPath)) {
                    return ffmpegPath;
                }
            }
        }
        
        throw new RuntimeException(
            "FFmpeg not found. Please install FFmpeg and ensure it's in your PATH.\n" +
            "Installation instructions:\n" +
            "- Windows: Download from https://ffmpeg.org/download.html or use 'winget install Gyan.FFmpeg'\n" +
            "- macOS: Use 'brew install ffmpeg'\n" +
            "- Linux: Use 'sudo apt install ffmpeg' or 'sudo yum install ffmpeg'"
        );
    }
    
    /**
     * Check if a file exists and is executable.
     * 
     * @param path The file path to check
     * @return true if the file exists and is executable
     */
    private static boolean isExecutableFile(String path) {
        try {
            Path filePath = Paths.get(path);
            return Files.exists(filePath) && Files.isExecutable(filePath);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get the current operating system name.
     * 
     * @return A normalized OS name (windows, macos, linux)
     */
    public static String getOperatingSystem() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "windows";
        } else if (os.contains("mac")) {
            return "macos";
        } else {
            return "linux";
        }
    }
    
    /**
     * Normalize file paths for the current operating system.
     * 
     * @param path The path to normalize
     * @return The normalized path with correct separators
     */
    public static String normalizePath(String path) {
        if (path == null) return null;
        
        if (getOperatingSystem().equals("windows")) {
            return path.replace("/", "\\");
        } else {
            return path.replace("\\", "/");
        }
    }
    
    /**
     * Check if all required dependencies are available.
     * 
     * @return true if all dependencies are available
     */
    public static boolean checkDependencies() {
        try {
            // Check FFmpeg
            getFFmpegPath();
            
            // Check Java version
            String javaVersion = System.getProperty("java.version");
            if (javaVersion == null) {
                System.err.println("Java version not detected");
                return false;
            }
            
            System.out.println("✅ Dependencies check passed:");
            System.out.println("   - Java: " + javaVersion);
            System.out.println("   - FFmpeg: " + getFFmpegPath());
            System.out.println("   - OS: " + System.getProperty("os.name"));
            
            return true;
            
        } catch (Exception e) {
            System.err.println("❌ Dependencies check failed: " + e.getMessage());
            return false;
        }
    }
}
