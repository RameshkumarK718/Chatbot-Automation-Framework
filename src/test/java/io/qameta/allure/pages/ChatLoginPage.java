package io.qameta.allure.pages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;

public class ChatLoginPage {
    private static final Logger logger = LoggerFactory.getLogger(ChatLoginPage.class);
    private WebDriver driver;

    // Locators based on your HTML snapshot
    private By emailFieldLocator = By.xpath("//input[@type='email']");
    private By passwordFieldLocator = By.xpath("//input[@type='password']");
    private By submitButtonLocator = By.xpath("//button[@type='submit']");

    public ChatLoginPage(WebDriver driver) {
        this.driver = driver;
    }

    public void performLogin(String email, String password) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            
            // 1. Find and fill the email input field
            WebElement emailField = wait.until(ExpectedConditions.visibilityOfElementLocated(emailFieldLocator));
            emailField.clear();
            emailField.sendKeys(email); // Uses the actual email passed from config/test suite

            // 2. Find and fill the password input field
            WebElement passwordField = driver.findElement(passwordFieldLocator);
            passwordField.clear();
            passwordField.sendKeys(password); // Uses the actual password

            // 3. Click the Sign In submit button
            WebElement submitButton = driver.findElement(submitButtonLocator);
            submitButton.click();

            logger.info("Login credentials submitted successfully for: {}", email);
        } catch (Exception e) {
            logger.error("Failed to perform login", e);
            throw new RuntimeException("Login failed", e);
        }
    }
}
