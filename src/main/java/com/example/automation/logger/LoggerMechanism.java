package com.example.automation.logger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple logging mechanism wrapper around SLF4J.
 * Provides consistent logging across the video recording framework.
 */
public class LoggerMechanism {
    
    private final Logger logger;
    
    /**
     * Create a logger for the specified class.
     * 
     * @param clazz The class to create a logger for
     */
    public LoggerMechanism(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
    }
    
    /**
     * Create a logger with the specified name.
     * 
     * @param name The logger name
     */
    public LoggerMechanism(String name) {
        this.logger = LoggerFactory.getLogger(name);
    }
    
    /**
     * Log an info message.
     * 
     * @param message The message to log
     */
    public void info(String message) {
        logger.info(message);
    }
    
    /**
     * Log an info message with parameters.
     * 
     * @param format The message format
     * @param args The message arguments
     */
    public void info(String format, Object... args) {
        logger.info(format, args);
    }
    
    /**
     * Log a debug message.
     * 
     * @param message The message to log
     */
    public void debug(String message) {
        logger.debug(message);
    }
    
    /**
     * Log a debug message with parameters.
     * 
     * @param format The message format
     * @param args The message arguments
     */
    public void debug(String format, Object... args) {
        logger.debug(format, args);
    }
    
    /**
     * Log a warning message.
     * 
     * @param message The message to log
     */
    public void warn(String message) {
        logger.warn(message);
    }
    
    /**
     * Log a warning message with parameters.
     * 
     * @param format The message format
     * @param args The message arguments
     */
    public void warn(String format, Object... args) {
        logger.warn(format, args);
    }
    
    /**
     * Log an error message.
     * 
     * @param message The message to log
     */
    public void error(String message) {
        logger.error(message);
    }
    
    /**
     * Log an error message with exception.
     * 
     * @param message The message to log
     * @param throwable The exception to log
     */
    public void error(String message, Throwable throwable) {
        logger.error(message, throwable);
    }
    
    /**
     * Log an error message with parameters.
     * 
     * @param format The message format
     * @param args The message arguments
     */
    public void error(String format, Object... args) {
        logger.error(format, args);
    }
    
    /**
     * Check if debug logging is enabled.
     * 
     * @return true if debug logging is enabled
     */
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }
    
    /**
     * Check if info logging is enabled.
     * 
     * @return true if info logging is enabled
     */
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }
}
