package com.example.automation.util;

import com.example.automation.logger.LoggerMechanism;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v137.target.model.TargetID;
import org.openqa.selenium.devtools.v137.page.Page;
import org.openqa.selenium.devtools.v137.target.Target;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enhanced DevTools Session Manager
 * 
 * This class addresses the critical DevTools session management issues identified
 * in log analysis, specifically the 11-second delays during manual tab switching
 * that cause flickering and frame drops.
 * 
 * Key improvements:
 * - Connection pooling and pre-warming
 * - Timeout handling with fallback mechanisms
 * - Async session creation with proper error handling
 * - Session health monitoring and recovery
 * - Memory-efficient resource management
 */
public class EnhancedDevToolsManager {
    
    // Remove this line as we'll use the LoggerMechanism instead
    // private static final Logger logger = LoggerFactory.getLogger(EnhancedDevToolsManager.class);
    
    // Configuration constants
    private static final int MAX_SESSION_CREATION_TIME_MS = 3000; // 3 seconds max
    private static final int SESSION_POOL_SIZE = 5;
    private static final int HEALTH_CHECK_INTERVAL_MS = 5000;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 500;
    
    private final WebDriver driver;
    private final LoggerMechanism loggerMechanism;
    
    // Connection pooling for faster session creation
    private final BlockingQueue<DevTools> sessionPool = new LinkedBlockingQueue<>();
    private final ExecutorService sessionCreationExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "DevTools-Session-Creator");
        t.setDaemon(true);
        return t;
    });
    
    // Active session management
    private final Map<TargetID, DevToolsSession> activeSessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService healthCheckExecutor = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "DevTools-Health-Monitor");
        t.setDaemon(true);
        return t;
    });
    
    // Session creation metrics for monitoring
    private final AtomicReference<SessionMetrics> metrics = new AtomicReference<>(new SessionMetrics());
    
    public EnhancedDevToolsManager(WebDriver driver, LoggerMechanism loggerMechanism) {
        this.driver = driver;
        this.loggerMechanism = loggerMechanism;
        
        initializeSessionPool();
        startHealthMonitoring();
        
        loggerMechanism.info("Enhanced DevTools Manager initialized with " + SESSION_POOL_SIZE + " pre-warmed sessions");
    }
    
    /**
     * Pre-warm a pool of DevTools sessions to eliminate creation delays
     */
    private void initializeSessionPool() {
        loggerMechanism.info("Pre-warming DevTools session pool...");
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int i = 0; i < SESSION_POOL_SIZE; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    DevTools devTools = createDevToolsSession(null);
                    if (devTools != null) {
                        sessionPool.offer(devTools);
                        loggerMechanism.info("Pre-warmed session added to pool");
                    }
                } catch (Exception e) {
                    loggerMechanism.error("Failed to pre-warm DevTools session", e);
                }
            }, sessionCreationExecutor);
            
            futures.add(future);
        }
        
        // Wait for pool initialization with timeout
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(10, TimeUnit.SECONDS);
            loggerMechanism.info("DevTools session pool initialization completed");
        } catch (TimeoutException e) {
            loggerMechanism.warn("Session pool initialization timed out, continuing with partial pool");
        } catch (Exception e) {
            loggerMechanism.error("Error during session pool initialization", e);
        }
    }
    
    /**
     * Create a DevTools session for a specific target with timeout and retry logic
     */
    public CompletableFuture<DevToolsSession> createSessionForTarget(TargetID targetId) {
        long startTime = System.currentTimeMillis();
        
        return CompletableFuture.supplyAsync(() -> {
            DevToolsSession session = null;
            Exception lastException = null;
            
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    loggerMechanism.info("Creating DevTools session for target " + targetId + " (attempt " + attempt + ")");
                    
                    // Try to get a pre-warmed session first
                    DevTools devTools = sessionPool.poll(100, TimeUnit.MILLISECONDS);
                    
                    if (devTools == null) {
                        // Create new session if pool is empty
                        loggerMechanism.info("Pool empty, creating new DevTools session for target: " + targetId);
                        devTools = createDevToolsSessionWithTimeout(targetId);
                    } else {
                        // Re-initialize the pooled session for the specific target
                        loggerMechanism.info("Using pooled DevTools session for target: " + targetId);
                        devTools = reinitializeSessionForTarget(devTools, targetId);
                    }
                    
                    if (devTools != null) {
                        session = new DevToolsSession(targetId, devTools, System.currentTimeMillis());
                        activeSessions.put(targetId, session);
                        
                        long duration = System.currentTimeMillis() - startTime;
                        updateMetrics(duration, true);
                        
                        loggerMechanism.info("Successfully created DevTools session for target " + targetId + " in " + duration + "ms");
                        break;
                    }
                    
                } catch (Exception e) {
                    lastException = e;
                    loggerMechanism.warn("DevTools session creation attempt " + attempt + " failed for target " + targetId + ": " + e.getMessage());
                    
                    if (attempt < MAX_RETRIES) {
                        try {
                            Thread.sleep(RETRY_DELAY_MS * attempt); // Exponential backoff
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            
            if (session == null) {
                long duration = System.currentTimeMillis() - startTime;
                updateMetrics(duration, false);
                
                String errorMsg = "Failed to create DevTools session for target " + targetId + " after " + MAX_RETRIES + " attempts";
                if (lastException != null) {
                    errorMsg += ": " + lastException.getMessage();
                }
                loggerMechanism.error(errorMsg);
                throw new RuntimeException(errorMsg, lastException);
            }
            
            return session;
            
        }, sessionCreationExecutor);
    }
    
    /**
     * Create a DevTools session with timeout protection
     */
    private DevTools createDevToolsSessionWithTimeout(TargetID targetId) throws Exception {
        CompletableFuture<DevTools> future = CompletableFuture.supplyAsync(() -> {
            return createDevToolsSession(targetId);
        }, sessionCreationExecutor);
        
        try {
            return future.get(MAX_SESSION_CREATION_TIME_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new Exception("DevTools session creation timed out after " + MAX_SESSION_CREATION_TIME_MS + "ms");
        }
    }
    
    /**
     * Core method to create DevTools session
     */
    private DevTools createDevToolsSession(TargetID targetId) {
        try {
            DevTools devTools = ((HasDevTools) driver).getDevTools();
            
            if (targetId != null) {
                devTools.createSession(targetId.toString());
            } else {
                devTools.createSession();
            }
            
            return devTools;
            
        } catch (Exception e) {
            loggerMechanism.error("Failed to create DevTools session" + (targetId != null ? " for target " + targetId : ""), e);
            return null;
        }
    }
    
    /**
     * Re-initialize a pooled session for a specific target
     */
    private DevTools reinitializeSessionForTarget(DevTools devTools, TargetID targetId) {
        try {
            // Clear any existing listeners and state
            devTools.clearListeners();
            
            // Create new session for the target
            devTools.createSession(targetId.toString());
            
            return devTools;
            
        } catch (Exception e) {
            loggerMechanism.error("Failed to reinitialize DevTools session for target " + targetId, e);
            // Return null to trigger fallback to new session creation
            return null;
        }
    }
    
    /**
     * Get an active session for a target
     */
    public DevToolsSession getSessionForTarget(TargetID targetId) {
        return activeSessions.get(targetId);
    }
    
    /**
     * Cleanup a session for a target
     */
    public void cleanupSession(TargetID targetId) {
        DevToolsSession session = activeSessions.remove(targetId);
        if (session != null) {
            try {
                session.getDevTools().clearListeners();
                
                // Try to return healthy sessions to the pool
                if (session.isHealthy() && sessionPool.size() < SESSION_POOL_SIZE) {
                    sessionPool.offer(session.getDevTools());
                    loggerMechanism.info("Returned session to pool for reuse");
                }
                
            } catch (Exception e) {
                loggerMechanism.error("Error during session cleanup for target " + targetId, e);
            }
        }
    }
    
    /**
     * Start health monitoring for active sessions
     */
    private void startHealthMonitoring() {
        healthCheckExecutor.scheduleAtFixedRate(() -> {
            try {
                monitorSessionHealth();
            } catch (Exception e) {
                loggerMechanism.error("Error during session health monitoring", e);
            }
        }, HEALTH_CHECK_INTERVAL_MS, HEALTH_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Monitor health of active sessions and remove unhealthy ones
     */
    private void monitorSessionHealth() {
        int unhealthyCount = 0;
        long currentTime = System.currentTimeMillis();
        
        for (Map.Entry<TargetID, DevToolsSession> entry : activeSessions.entrySet()) {
            DevToolsSession session = entry.getValue();
            
            if (!session.isHealthy() || (currentTime - session.getCreatedTime()) > 300000) { // 5 minutes max age
                loggerMechanism.warn("Removing unhealthy or aged session for target: " + entry.getKey());
                cleanupSession(entry.getKey());
                unhealthyCount++;
            }
        }
        
        if (unhealthyCount > 0) {
            loggerMechanism.info("Health check completed, removed " + unhealthyCount + " unhealthy sessions");
        }
        
        // Replenish pool if needed
        replenishSessionPool();
    }
    
    /**
     * Ensure the session pool maintains minimum size
     */
    private void replenishSessionPool() {
        int currentPoolSize = sessionPool.size();
        int needed = SESSION_POOL_SIZE - currentPoolSize;
        
        if (needed > 0) {
            loggerMechanism.info("Replenishing session pool, need " + needed + " more sessions");
            
            for (int i = 0; i < needed; i++) {
                sessionCreationExecutor.submit(() -> {
                    try {
                        DevTools devTools = createDevToolsSession(null);
                        if (devTools != null) {
                            sessionPool.offer(devTools);
                        }
                    } catch (Exception e) {
                        loggerMechanism.error("Failed to replenish session pool", e);
                    }
                });
            }
        }
    }
    
    /**
     * Update session creation metrics
     */
    private void updateMetrics(long duration, boolean success) {
        SessionMetrics current = metrics.get();
        SessionMetrics updated = new SessionMetrics(
            current.getTotalAttempts() + 1,
            success ? current.getSuccessfulAttempts() + 1 : current.getSuccessfulAttempts(),
            Math.max(current.getMaxCreationTime(), duration),
            (current.getAverageCreationTime() * current.getTotalAttempts() + duration) / (current.getTotalAttempts() + 1)
        );
        
        metrics.set(updated);
    }
    
    /**
     * Get session creation metrics
     */
    public SessionMetrics getMetrics() {
        return metrics.get();
    }
    
    /**
     * Shutdown the manager and cleanup resources
     */
    public void shutdown() {
        loggerMechanism.info("Shutting down Enhanced DevTools Manager");
        
        try {
            // Cleanup all active sessions
            for (TargetID targetId : activeSessions.keySet()) {
                cleanupSession(targetId);
            }
            
            // Clean up pooled sessions
            DevTools pooledSession;
            while ((pooledSession = sessionPool.poll()) != null) {
                try {
                    pooledSession.clearListeners();
                } catch (Exception e) {
                    loggerMechanism.error("Error cleaning up pooled session", e);
                }
            }
            
            // Shutdown executors
            sessionCreationExecutor.shutdown();
            healthCheckExecutor.shutdown();
            
            if (!sessionCreationExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                sessionCreationExecutor.shutdownNow();
            }
            
            if (!healthCheckExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                healthCheckExecutor.shutdownNow();
            }
            
        } catch (Exception e) {
            loggerMechanism.error("Error during Enhanced DevTools Manager shutdown", e);
        }
        
        loggerMechanism.info("Enhanced DevTools Manager shutdown completed");
    }
    
    /**
     * Inner class to represent a DevTools session with metadata
     */
    public static class DevToolsSession {
        private final TargetID targetId;
        private final DevTools devTools;
        private final long createdTime;
        private volatile boolean healthy = true;
        
        public DevToolsSession(TargetID targetId, DevTools devTools, long createdTime) {
            this.targetId = targetId;
            this.devTools = devTools;
            this.createdTime = createdTime;
        }
        
        public TargetID getTargetId() { return targetId; }
        public DevTools getDevTools() { return devTools; }
        public long getCreatedTime() { return createdTime; }
        public boolean isHealthy() { return healthy; }
        public void markUnhealthy() { this.healthy = false; }
    }
    
    /**
     * Session creation metrics
     */
    public static class SessionMetrics {
        private final long totalAttempts;
        private final long successfulAttempts;
        private final long maxCreationTime;
        private final double averageCreationTime;
        
        public SessionMetrics() {
            this(0, 0, 0, 0);
        }
        
        public SessionMetrics(long totalAttempts, long successfulAttempts, long maxCreationTime, double averageCreationTime) {
            this.totalAttempts = totalAttempts;
            this.successfulAttempts = successfulAttempts;
            this.maxCreationTime = maxCreationTime;
            this.averageCreationTime = averageCreationTime;
        }
        
        public long getTotalAttempts() { return totalAttempts; }
        public long getSuccessfulAttempts() { return successfulAttempts; }
        public long getMaxCreationTime() { return maxCreationTime; }
        public double getAverageCreationTime() { return averageCreationTime; }
        
        public double getSuccessRate() {
            return totalAttempts > 0 ? (double) successfulAttempts / totalAttempts * 100 : 0;
        }
        
        @Override
        public String toString() {
            return String.format("SessionMetrics{attempts=%d, success=%d (%.1f%%), maxTime=%dms, avgTime=%.1fms}",
                totalAttempts, successfulAttempts, getSuccessRate(), maxCreationTime, averageCreationTime);
        }
    }
}