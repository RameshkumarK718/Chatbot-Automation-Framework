package com.chatbot;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.time.Duration;

public class BaseTest {
    protected WebDriver driver;
    protected WebDriverWait wait;

    protected static final String APP_URL = "https://dtqponlzcij0l.cloudfront.net/";

    @BeforeMethod
    public void setUp() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");

        driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(90));
        wait = new WebDriverWait(driver, Duration.ofSeconds(60));

        try {
            driver.get(APP_URL);
            // Replace with dynamic credential loading from your existing methods if needed
            WebElement emailInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("vaa-email")));
            emailInput.sendKeys("user@example.com");

            WebElement passwordInput = driver.findElement(By.id("vaa-pw"));
            passwordInput.sendKeys("password");

            driver.findElement(By.id("vaa-submit")).click();
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(normalize-space(), 'Conversational AI')]")));
        } catch (Exception e) {
            Assert.fail("BaseTest setup/login failed: " + e.getMessage());
        }
    }

    @AfterMethod
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }
}