package com.example.automation.wait;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.time.Duration;
import java.util.List;

/**
 * WaitUtils - Convenience methods for common wait scenarios using SmartWait.
 * 
 * This class provides high-level, easy-to-use methods for common waiting scenarios
 * in Selenium tests. It wraps the SmartWait framework to provide a simple API
 * for the most frequent use cases.
 * 
 * Integration with existing selenium-video-recorder project:
 * - Use this to replace old waitForPageLoad, checkNetworkCalls, waitForDomToSettle calls
 * - Provides significant performance improvements on network-heavy sites
 * - Framework-agnostic (works with React, Angular, Vue, etc.)
 */
public class WaitUtils {
    
    private final SmartWait smartWait;
    private final WebDriver driver;
    
    public WaitUtils(WebDriver driver) {
        this.driver = driver;
        this.smartWait = new SmartWait(driver);
    }
    
    public WaitUtils(WebDriver driver, Duration defaultTimeout) {
        this.driver = driver;
        this.smartWait = new SmartWait(driver, defaultTimeout);
    }
    
    /**
     * Wait for page to fully load after navigation
     * 
     * REPLACES: The old sequence of waitForPageLoad + checkNetworkCalls + waitForDomToSettle
     * PERFORMANCE: 2-8 seconds vs 30+ seconds on network-heavy sites
     */
    public void waitForPageLoad() {
        smartWait.until(SmartWait.WaitStrategy.PAGE_LOAD);
    }
    
    public void waitForPageLoad(Duration timeout) {
        smartWait.until(SmartWait.WaitStrategy.PAGE_LOAD, timeout);
    }
    
    /**
     * Wait after clicking an element
     * 
     * Use this after button clicks, form submissions, menu interactions, etc.
     */
    public void waitAfterClick() {
        smartWait.until(SmartWait.WaitStrategy.ELEMENT_INTERACTION);
    }
    
    public void waitAfterClick(By resultLocator) {
        smartWait.until(SmartWait.WaitStrategy.ELEMENT_INTERACTION, resultLocator);
    }
    
    public void waitAfterClick(By resultLocator, Duration timeout) {
        smartWait.until(SmartWait.WaitStrategy.ELEMENT_INTERACTION, resultLocator, timeout);
    }
    
    /**
     * Wait for AJAX/API calls to complete
     * 
     * Use this when you need to wait for background data loading
     */
    public void waitForAjax() {
        smartWait.until(SmartWait.WaitStrategy.AJAX_REQUEST);
    }
    
    public void waitForAjax(Duration timeout) {
        smartWait.until(SmartWait.WaitStrategy.AJAX_REQUEST, timeout);
    }
    
    /**
     * Wait for animations to complete
     * 
     * Use this for UI transitions, CSS animations, etc.
     */
    public void waitForAnimation() {
        smartWait.until(SmartWait.WaitStrategy.ANIMATION);
    }
    
    public void waitForAnimation(Duration timeout) {
        smartWait.until(SmartWait.WaitStrategy.ANIMATION, timeout);
    }
    
    /**
     * Wait for element to be visible and clickable
     */
    public WebElement waitForElement(By locator) {
        return waitForElement(locator, Duration.ofSeconds(30));
    }
    
    public WebElement waitForElement(By locator, Duration timeout) {
        WebDriverWait wait = new WebDriverWait(driver, timeout);
        return wait.until(ExpectedConditions.elementToBeClickable(locator));
    }
    
    /**
     * Wait for element to be visible
     */
    public WebElement waitForElementVisible(By locator) {
        return waitForElementVisible(locator, Duration.ofSeconds(30));
    }
    
    public WebElement waitForElementVisible(By locator, Duration timeout) {
        WebDriverWait wait = new WebDriverWait(driver, timeout);
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }
    
    /**
     * Wait for element to disappear
     */
    public void waitForElementToDisappear(By locator) {
        waitForElementToDisappear(locator, Duration.ofSeconds(30));
    }
    
    public void waitForElementToDisappear(By locator, Duration timeout) {
        WebDriverWait wait = new WebDriverWait(driver, timeout);
        wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
    }
    
    /**
     * Wait for text to be present in element
     */
    public void waitForTextInElement(By locator, String text) {
        waitForTextInElement(locator, text, Duration.ofSeconds(30));
    }
    
    public void waitForTextInElement(By locator, String text, Duration timeout) {
        WebDriverWait wait = new WebDriverWait(driver, timeout);
        wait.until(ExpectedConditions.textToBePresentInElementLocated(locator, text));
    }
    
    /**
     * Wait for a specific number of elements
     */
    public List<WebElement> waitForElements(By locator, int expectedCount) {
        return waitForElements(locator, expectedCount, Duration.ofSeconds(30));
    }
    
    public List<WebElement> waitForElements(By locator, int expectedCount, Duration timeout) {
        WebDriverWait wait = new WebDriverWait(driver, timeout);
        wait.until(ExpectedConditions.numberOfElementsToBe(locator, expectedCount));
        return driver.findElements(locator);
    }
    
    /**
     * Wait for URL to contain specific text
     */
    public void waitForUrlContains(String urlFragment) {
        waitForUrlContains(urlFragment, Duration.ofSeconds(30));
    }
    
    public void waitForUrlContains(String urlFragment, Duration timeout) {
        WebDriverWait wait = new WebDriverWait(driver, timeout);
        wait.until(ExpectedConditions.urlContains(urlFragment));
    }
    
    /**
     * Wait for title to contain specific text
     */
    public void waitForTitleContains(String title) {
        waitForTitleContains(title, Duration.ofSeconds(30));
    }
    
    public void waitForTitleContains(String title, Duration timeout) {
        WebDriverWait wait = new WebDriverWait(driver, timeout);
        wait.until(ExpectedConditions.titleContains(title));
    }
    
    /**
     * Combined wait for common scenarios
     */
    public void waitForPageAndElement(By locator) {
        waitForPageLoad();
        waitForElement(locator);
    }
    
    public void waitForClickAndResult(By clickTarget, By resultLocator) {
        waitAfterClick(resultLocator);
    }
    
    public void waitForFormSubmission(By submitButton, By successIndicator) {
        waitAfterClick(successIndicator);
    }
    
    /**
     * Configuration methods for fine-tuning performance
     */
    public WaitUtils configureNetworkIdleTime(Duration idleTime) {
        smartWait.setNetworkIdleTime(idleTime);
        return this;
    }
    
    public WaitUtils configureDomStableTime(Duration stableTime) {
        smartWait.setDomStableTime(stableTime);
        return this;
    }
    
    public WaitUtils ignoreUrlPattern(String pattern) {
        smartWait.addIgnoredUrlPattern(pattern);
        return this;
    }
    
    public WaitUtils ignoreResourceType(String resourceType) {
        smartWait.addIgnoredResourceType(resourceType);
        return this;
    }
    
    /**
     * Migration helper - shows how to replace old wait calls
     */
    public void replaceOldWaitCalls() {
        // OLD CODE (slow and unreliable):
        /*
        waitForPageLoad(driver, Duration.ofSeconds(30));
        checkNetworkCalls(driver, Duration.ofSeconds(1), Duration.ofSeconds(30), devTools);
        waitForDomToSettle(driver, Duration.ofSeconds(30));
        */
        
        // NEW CODE (fast and reliable):
        waitForPageLoad();
        
        // That's it! One line replaces the entire old sequence.
    }
    
    /**
     * Cleanup
     */
    public void close() {
        smartWait.close();
    }
}
