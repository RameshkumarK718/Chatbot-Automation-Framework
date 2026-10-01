package com.chatbot;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
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
            // Default fallbacks in case Excel is missing
            String username = "user@example.com";
            String password = "password";

            // Dynamically load credentials from src/main/resources/credentials.xlsx
            try (InputStream credStream = getClass().getClassLoader().getResourceAsStream("credentials.xlsx")) {
                if (credStream != null) {
                    try (Workbook credWorkbook = new XSSFWorkbook(credStream)) {
                        Sheet credSheet = credWorkbook.getSheetAt(0);
                        Row firstRow = credSheet.getRow(1); // Row 1 assumes data row (Row 0 is header)
                        if (firstRow != null) {
                            username = getCellVal(firstRow.getCell(0));
                            password = getCellVal(firstRow.getCell(1));
                        }
                    }
                } else {
                    System.err.println("Warning: credentials.xlsx not found in classpath. Using default credentials.");
                }
            } catch (Exception e) {
                System.err.println("Warning: Error reading credentials.xlsx. Using default credentials. Error: " + e.getMessage());
            }

            driver.get(APP_URL);
            
            // Ensure document is fully loaded
            wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                .executeScript("return document.readyState").equals("complete"));

            // Locate email input field
            WebElement emailInput = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//input[@id='vaa-email' or contains(@name, 'email') or contains(@type, 'email')]")
            ));
            emailInput.clear();
            emailInput.sendKeys(username);

            // Locate password input field
            WebElement passwordInput = driver.findElement(
                By.xpath("//input[@id='vaa-pw' or contains(@name, 'password') or contains(@type, 'password')]")
            );
            passwordInput.clear();
            passwordInput.sendKeys(password);

            // Click submit button
            WebElement submitBtn = driver.findElement(
                By.xpath("//button[@id='vaa-submit' or @type='submit' or contains(text(), 'Login')]")
            );
            submitBtn.click();

            // Wait until login completes and main chatbot UI is loaded
            wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//*[contains(normalize-space(), 'Conversational AI') or contains(@class, 'chat-container')]")
            ));

        } catch (Exception e) {
            // Capture screenshot on failure for diagnostics and save to target directory
            try {
                File srcFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                File destFile = new File("target/failure-screenshot.png");
                destFile.getParentFile().mkdirs();
                Files.copy(srcFile.toPath(), destFile.toPath());
                System.err.println("Saved failure screenshot to: " + destFile.getAbsolutePath());
                System.err.println("Current Page URL: " + driver.getCurrentUrl());
            } catch (Exception ignored) {}

            Assert.fail("BaseTest setup/login failed: " + e.getMessage());
        }
    }

    @AfterMethod
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    private String getCellVal(Cell cell) {
        if (cell == null) return "";
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }
}
