package io.qameta.allure.base;
import io.qameta.allure.utils.ConfigReader;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

public class BaseTest {
    protected WebDriver driver;

    @BeforeMethod
    public void setUp() {
        driver = new ChromeDriver();
        driver.manage().window().maximize();
        
        // Dynamically fetch URL from configuration instead of hardcoding
        String appUrl = ConfigReader.getProperty("app.url");
        if (appUrl != null && !appUrl.isEmpty()) {
            driver.get(appUrl);
        } else {
            // Fallback default if properties fail to load
            driver.get("https://dtqponlzcij0l.cloudfront.net/");
        }
    }

    @AfterMethod
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }
}
