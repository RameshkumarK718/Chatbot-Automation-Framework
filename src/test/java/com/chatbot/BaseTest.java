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
import java.util.Properties;

public class BaseTest {
    protected WebDriver driver;
    protected WebDriverWait wait;
    protected static Properties config = new Properties();
    protected static String APP_URL;
    protected static boolean LOGIN_REQUIRED;
    static {
        try (InputStream input = BaseTest.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                config.load(input);
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not load config.properties. Using default fallbacks. Error: " + e.getMessage());
        }

        APP_URL = System.getProperty("app.url", config.getProperty("app.url", "https://devdedham.aptussoft.com/MemberAPI"));
        LOGIN_REQUIRED = Boolean.parseBoolean(config.getProperty("login.required", "true"));
    }
    @BeforeMethod
    public void setUp() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");

        driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(120));
        wait = new WebDriverWait(driver, Duration.ofSeconds(60));

        try {
            driver.get(APP_URL);
            
            wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                .executeScript("return document.readyState").equals("complete"));

            // 1. Initial Gate / Role Selection if configured
            String roleLocatorVal = config.getProperty("locator.value.role", "");
            if (!roleLocatorVal.isBlank()) {
                try {
                    WebElement roleBtn = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(roleLocatorVal)));
                    roleBtn.click();
                    System.out.println("--> Selected initial role gate successfully.");
                } catch (Exception ignored) {}
            }

            // 2. Perform Login if required
            if (LOGIN_REQUIRED) {
                String username = "user@example.com";
                String password = "password";

                try (InputStream credStream = getClass().getClassLoader().getResourceAsStream("credentials.xlsx")) {
                    if (credStream != null) {
                        try (Workbook credWorkbook = new XSSFWorkbook(credStream)) {
                            Sheet credSheet = credWorkbook.getSheetAt(0);
                            Row firstRow = credSheet.getRow(1);
                            if (firstRow != null) {
                                username = getCellVal(firstRow.getCell(0));
                                password = getCellVal(firstRow.getCell(1));
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Error reading credentials.xlsx. Using default credentials.");
                }

                // Email Input
                By emailLocator = getLocator(config.getProperty("locator.type.email", "id"), config.getProperty("locator.value.email", "vaa-email"));
                WebElement emailInput = wait.until(ExpectedConditions.presenceOfElementLocated(emailLocator));
                emailInput.clear();
                emailInput.sendKeys(username);

                // Password Input
                By passwordLocator = getLocator(config.getProperty("locator.type.password", "id"), config.getProperty("locator.value.password", "vaa-pw"));
                WebElement passwordInput = driver.findElement(passwordLocator);
                passwordInput.clear();
                passwordInput.sendKeys(password);

                // Submit Button
                By submitLocator = getLocator(config.getProperty("locator.type.submit", "id"), config.getProperty("locator.value.submit", "vaa-submit"));
                WebElement submitBtn = driver.findElement(submitLocator);
                try {
                    submitBtn.click();
                } catch (Exception clickEx) {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", submitBtn);
                }
            }

            // 3. Post-Login Navigation / Portal Button if configured
            String portalLocatorVal = config.getProperty("locator.value.portal", "");
            if (!portalLocatorVal.isBlank()) {
                try {
                    WebElement portalBtn = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(portalLocatorVal)));
                    portalBtn.click();
                    System.out.println("--> Navigated through portal navigation element successfully.");
                } catch (Exception ignored) {}
            }

        } catch (Exception e) {
            try {
                File srcFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                File destFile = new File("target/failure-screenshot.png");
                destFile.getParentFile().mkdirs();
                Files.copy(srcFile.toPath(), destFile.toPath());
                System.err.println("Saved failure screenshot to: " + destFile.getAbsolutePath());
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

    protected By getLocator(String type, String value) {
        switch (type.toLowerCase().trim()) {
            case "id": return By.id(value);
            case "name": return By.name(value);
            case "class": case "classname": return By.className(value);
            case "css": return By.cssSelector(value);
            case "xpath": return By.xpath(value);
            case "linktext": return By.linkText(value);
            default: return By.xpath(value);
        }
    }

    private String getCellVal(Cell cell) {
        if (cell == null) return "";
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }
}
