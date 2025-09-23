package com.example.automation.wait;

import org.openqa.selenium.*;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v137.network.Network;
import org.openqa.selenium.devtools.v137.network.model.RequestId;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.ExpectedCondition;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * SmartWait - An intelligent, context-aware wait framework for Selenium.
 * 
 * This framework replaces the traditional sequential wait approach with a
 * flexible, strategy-based system that adapts to different scenarios and
 * provides better performance and reliability.
 * 
 * Key Features:
 * - Strategy-based waiting (PAGE_LOAD, ELEMENT_INTERACTION, AJAX_REQUEST, etc.)
 * - Parallel condition checking for faster results
 * - Intelligent network monitoring with request filtering
 * - Context-aware timeouts and conditions
 * - Extensible and configurable design
 * - Framework-agnostic (works with React, Angular, Vue, etc.)
 */
public class SmartWait {
    
    private final WebDriver driver;
    private final DevTools devTools;
    private final Duration defaultTimeout;
    private final ExecutorService executor;
    
    // Configuration
    private final Set<Pattern> ignoredUrlPatterns;
    private final Set<String> ignoredResourceTypes;
    private Duration networkIdleTime = Duration.ofMillis(500);
    private Duration domStableTime = Duration.ofMillis(300);
    
    /**
     * Wait strategies that define the context and approach for waiting
     */
    public enum WaitStrategy {
        PAGE_LOAD,           // Initial page navigation
        ELEMENT_INTERACTION, // After clicks, form submissions, etc.
        AJAX_REQUEST,        // Waiting for specific API calls
        ANIMATION,           // UI transitions and animations
        CUSTOM              // User-defined conditions
    }
    
    /**
     * Individual conditions that can be checked
     */
    public enum WaitCondition {
        DOCUMENT_READY,      // document.readyState === 'complete'
        NETWORK_IDLE,        // No critical network activity
        DOM_STABLE,          // No significant DOM mutations
        ELEMENT_VISIBLE,     // Specific element is present and visible
        ELEMENT_CLICKABLE,   // Element is clickable
        JAVASCRIPT_READY     // Custom JavaScript condition
    }
    
    public SmartWait(WebDriver driver) {
        this(driver, Duration.ofSeconds(30));
    }
    
    public SmartWait(WebDriver driver, Duration defaultTimeout) {
        this.driver = driver;
        this.defaultTimeout = defaultTimeout;
        this.executor = Executors.newCachedThreadPool();
        
        // Initialize DevTools if available
        if (driver instanceof HasDevTools) {
            this.devTools = ((HasDevTools) driver).getDevTools();
            try {
                if (!isDevToolsSessionActive()) {
                    this.devTools.createSession();
                }
            } catch (Exception e) {
                System.err.println("Warning: Could not initialize DevTools: " + e.getMessage());
            }
        } else {
            this.devTools = null;
        }
        
        // Initialize default ignored patterns
        this.ignoredUrlPatterns = new HashSet<>();
        this.ignoredResourceTypes = new HashSet<>();
        initializeDefaultFilters();
    }
    
    /**
     * Main wait method using strategy-based approach
     */
    public void until(WaitStrategy strategy) {
        until(strategy, null, defaultTimeout);
    }
    
    public void until(WaitStrategy strategy, Duration timeout) {
        until(strategy, null, timeout);
    }
    
    public void until(WaitStrategy strategy, By locator) {
        until(strategy, locator, defaultTimeout);
    }
    
    public void until(WaitStrategy strategy, By locator, Duration timeout) {
        long startTime = System.currentTimeMillis();
        
        try {
            switch (strategy) {
                case PAGE_LOAD:
                    waitForPageLoad(timeout);
                    break;
                case ELEMENT_INTERACTION:
                    waitForElementInteraction(locator, timeout);
                    break;
                case AJAX_REQUEST:
                    waitForAjaxRequest(timeout);
                    break;
                case ANIMATION:
                    waitForAnimation(timeout);
                    break;
                case CUSTOM:
                    // Custom conditions should be handled separately
                    break;
            }
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            System.out.println("SmartWait completed in " + duration + "ms using strategy: " + strategy);
        }
    }
    
    /**
     * Wait for specific conditions with parallel checking
     */
    public void until(WaitCondition... conditions) {
        until(Arrays.asList(conditions), defaultTimeout);
    }
    
    public void until(List<WaitCondition> conditions, Duration timeout) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeout.toMillis();
        
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        
        for (WaitCondition condition : conditions) {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return checkCondition(condition, timeout);
                } catch (Exception e) {
                    return false;
                }
            }, executor);
            futures.add(future);
        }
        
        try {
            // Wait for all conditions to be met or timeout
            CompletableFuture<Void> allConditions = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
            );
            
            allConditions.get(timeoutMs, TimeUnit.MILLISECONDS);
            
        } catch (java.util.concurrent.TimeoutException e) {
            throw new RuntimeException("SmartWait timeout after " + timeout.toMillis() + "ms");
        } catch (Exception e) {
            throw new RuntimeException("SmartWait failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * PAGE_LOAD strategy implementation
     */
    private void waitForPageLoad(Duration timeout) {
        List<CompletableFuture<Boolean>> conditions = Arrays.asList(
            // Primary condition: document ready
            CompletableFuture.supplyAsync(() -> checkCondition(WaitCondition.DOCUMENT_READY, timeout), executor),
            // Secondary condition: critical network requests complete
            CompletableFuture.supplyAsync(() -> checkCondition(WaitCondition.NETWORK_IDLE, timeout), executor)
        );
        
        try {
            // Wait for document ready first, then give network a chance to settle
            conditions.get(0).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            
            // Give network a shorter time to settle after document ready
            Duration networkTimeout = Duration.ofMillis(Math.min(timeout.toMillis() / 3, 5000));
            try {
                conditions.get(1).get(networkTimeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                // Network didn't settle, but document is ready - continue
                System.out.println("Network didn't settle within " + networkTimeout.toMillis() + "ms, but document is ready");
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Page load wait failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * ELEMENT_INTERACTION strategy implementation
     */
    private void waitForElementInteraction(By locator, Duration timeout) {
        List<CompletableFuture<Boolean>> conditions = new ArrayList<>();
        
        // If a locator is provided, wait for that element
        if (locator != null) {
            conditions.add(CompletableFuture.supplyAsync(() -> {
                try {
                    new WebDriverWait(driver, timeout)
                        .until(ExpectedConditions.visibilityOfElementLocated(locator));
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }, executor));
        }
        
        // Wait for DOM to stabilize
        conditions.add(CompletableFuture.supplyAsync(() -> 
            checkCondition(WaitCondition.DOM_STABLE, timeout), executor));
        
        // Wait for any triggered network requests to complete (with shorter timeout)
        Duration networkTimeout = Duration.ofMillis(Math.min(timeout.toMillis() / 2, 3000));
        conditions.add(CompletableFuture.supplyAsync(() -> 
            checkCondition(WaitCondition.NETWORK_IDLE, networkTimeout), executor));
        
        try {
            CompletableFuture.allOf(conditions.toArray(new CompletableFuture[0]))
                .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Element interaction wait failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * AJAX_REQUEST strategy implementation
     */
    private void waitForAjaxRequest(Duration timeout) {
        // Focus primarily on network activity with some DOM stability
        List<CompletableFuture<Boolean>> conditions = Arrays.asList(
            CompletableFuture.supplyAsync(() -> checkCondition(WaitCondition.NETWORK_IDLE, timeout), executor),
            CompletableFuture.supplyAsync(() -> checkCondition(WaitCondition.DOM_STABLE, timeout), executor)
        );
        
        try {
            CompletableFuture.allOf(conditions.toArray(new CompletableFuture[0]))
                .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RuntimeException("AJAX request wait failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * ANIMATION strategy implementation
     */
    private void waitForAnimation(Duration timeout) {
        // Focus on DOM stability and CSS transitions
        try {
            checkCondition(WaitCondition.DOM_STABLE, timeout);
            
            // Additional wait for CSS animations/transitions
            Thread.sleep(domStableTime.toMillis());
            
        } catch (Exception e) {
            throw new RuntimeException("Animation wait failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check individual conditions
     */
    private boolean checkCondition(WaitCondition condition, Duration timeout) {
        switch (condition) {
            case DOCUMENT_READY:
                return waitForDocumentReady(timeout);
            case NETWORK_IDLE:
                return waitForNetworkIdle(timeout);
            case DOM_STABLE:
                return waitForDomStable(timeout);
            default:
                return false;
        }
    }
    
    /**
     * Enhanced document ready check
     */
    private boolean waitForDocumentReady(Duration timeout) {
        try {
            new WebDriverWait(driver, timeout).until(
                (ExpectedCondition<Boolean>) wd ->
                    ((JavascriptExecutor) wd).executeScript("return document.readyState").equals("complete")
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Intelligent network idle check with filtering
     */
    private boolean waitForNetworkIdle(Duration timeout) {
        if (devTools == null) {
            // Fallback to a simple wait if DevTools not available
            try {
                Thread.sleep(networkIdleTime.toMillis());
                return true;
            } catch (InterruptedException e) {
                return false;
            }
        }
        
        Set<RequestId> criticalRequests = Collections.synchronizedSet(new HashSet<>());
        
        try {
            devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()));
            
            devTools.addListener(Network.requestWillBeSent(), event -> {
                String url = event.getRequest().getUrl();
                String resourceType = event.getType().toString();
                
                // Only track critical requests
                if (isCriticalRequest(url, resourceType)) {
                    criticalRequests.add(event.getRequestId());
                }
            });
            
            devTools.addListener(Network.loadingFinished(), event -> {
                criticalRequests.remove(event.getRequestId());
            });
            
            devTools.addListener(Network.loadingFailed(), event -> {
                criticalRequests.remove(event.getRequestId());
            });
            
            long startTime = System.currentTimeMillis();
            long idleStart = -1;
            long timeoutMs = timeout.toMillis();
            long idleMs = networkIdleTime.toMillis();
            
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                boolean isIdle = criticalRequests.isEmpty();
                
                if (isIdle) {
                    if (idleStart == -1) {
                        idleStart = System.currentTimeMillis();
                    } else if (System.currentTimeMillis() - idleStart >= idleMs) {
                        return true;
                    }
                } else {
                    idleStart = -1;
                }
                
                Thread.sleep(50); // Reduced polling interval
            }
            
            return false;
            
        } catch (Exception e) {
            return false;
        } finally {
            // Clean up listeners
            try {
                devTools.clearListeners();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }
    
    /**
     * DOM stability check
     */
    private boolean waitForDomStable(Duration timeout) {
        try {
            // Use MutationObserver to detect DOM changes
            String script = 
                "var callback = arguments[arguments.length - 1];" +
                "var observer = new MutationObserver(function(mutations) {" +
                "  window.domMutationCount = (window.domMutationCount || 0) + mutations.length;" +
                "});" +
                "observer.observe(document.body, {" +
                "  childList: true, subtree: true, attributes: true" +
                "});" +
                "window.domMutationCount = 0;" +
                "setTimeout(function() {" +
                "  observer.disconnect();" +
                "  callback(window.domMutationCount);" +
                "}, " + domStableTime.toMillis() + ");";
            
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Long mutationCount = (Long) js.executeAsyncScript(script);
            
            // Consider DOM stable if there were fewer than 5 mutations in the observation period
            return mutationCount != null && mutationCount < 5;
            
        } catch (Exception e) {
            // Fallback to simple wait
            try {
                Thread.sleep(domStableTime.toMillis());
                return true;
            } catch (InterruptedException ie) {
                return false;
            }
        }
    }
    
    /**
     * Determine if a request is critical for page functionality
     */
    private boolean isCriticalRequest(String url, String resourceType) {
        // Ignore known non-critical patterns
        for (Pattern pattern : ignoredUrlPatterns) {
            if (pattern.matcher(url).find()) {
                return false;
            }
        }
        
        // Ignore non-critical resource types
        if (ignoredResourceTypes.contains(resourceType.toLowerCase())) {
            return false;
        }
        
        // Consider XHR and Fetch requests as critical
        return resourceType.equalsIgnoreCase("xhr") || 
               resourceType.equalsIgnoreCase("fetch") ||
               resourceType.equalsIgnoreCase("document");
    }
    
    /**
     * Initialize default filters for non-critical requests
     */
    private void initializeDefaultFilters() {
        // Common analytics and tracking services
        ignoredUrlPatterns.add(Pattern.compile(".*google-analytics\\.com.*"));
        ignoredUrlPatterns.add(Pattern.compile(".*googletagmanager\\.com.*"));
        ignoredUrlPatterns.add(Pattern.compile(".*facebook\\.com.*"));
        ignoredUrlPatterns.add(Pattern.compile(".*doubleclick\\.net.*"));
        ignoredUrlPatterns.add(Pattern.compile(".*fullstory\\.com.*"));
        ignoredUrlPatterns.add(Pattern.compile(".*hotjar\\.com.*"));
        ignoredUrlPatterns.add(Pattern.compile(".*mixpanel\\.com.*"));
        ignoredUrlPatterns.add(Pattern.compile(".*segment\\.io.*"));
        ignoredUrlPatterns.add(Pattern.compile(".*amplitude\\.com.*"));
        
        // Cricket-specific (for cricinfo.com testing)
        ignoredUrlPatterns.add(Pattern.compile(".*espncricinfo\\.com.*analytics.*"));
        ignoredUrlPatterns.add(Pattern.compile(".*espncricinfo\\.com.*tracking.*"));
        
        // News and media sites
        ignoredUrlPatterns.add(Pattern.compile(".*chartbeat\\.com.*"));
        ignoredUrlPatterns.add(Pattern.compile(".*parsely\\.com.*"));
        ignoredUrlPatterns.add(Pattern.compile(".*outbrain\\.com.*"));
        ignoredUrlPatterns.add(Pattern.compile(".*taboola\\.com.*"));
        
        // Non-critical resource types
        ignoredResourceTypes.add("image");
        ignoredResourceTypes.add("font");
        ignoredResourceTypes.add("media");
        ignoredResourceTypes.add("texttrack");
    }
    
    /**
     * Configuration methods
     */
    public SmartWait setNetworkIdleTime(Duration idleTime) {
        this.networkIdleTime = idleTime;
        return this;
    }
    
    public SmartWait setDomStableTime(Duration stableTime) {
        this.domStableTime = stableTime;
        return this;
    }
    
    public SmartWait addIgnoredUrlPattern(String pattern) {
        this.ignoredUrlPatterns.add(Pattern.compile(pattern));
        return this;
    }
    
    public SmartWait addIgnoredResourceType(String resourceType) {
        this.ignoredResourceTypes.add(resourceType.toLowerCase());
        return this;
    }
    
    /**
     * Utility methods
     */
    private boolean isDevToolsSessionActive() {
        try {
            // Try to send a simple command to check if session is active
            devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Cleanup resources
     */
    public void close() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }
    }
}
