package io.qameta.allure.listeners;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.OutputType;
import io.qameta.allure.utils.DriverManager; // <-- CRITICAL: This import resolves the "cannot find symbol" error
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TestListener implements ITestListener {

    @Override
    public void onTestFailure(ITestResult result) {
        System.out.println("Test Failed: " + result.getName() + ". Capturing diagnostics...");
        
        // Fetches the thread-safe driver from your utils package
        WebDriver driver = DriverManager.getDriver();

        if (driver != null) {
            String testName = result.getName();
            try {
                // 1. Save Screenshot
                File dir = new File("screenshots");
                if (!dir.exists()) dir.mkdirs();
                File srcFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                Files.copy(srcFile.toPath(), Paths.get("screenshots/" + testName + ".png"));
                
                // 2. Save Page Source HTML
                File htmlDir = new File("page-source");
                if (!htmlDir.exists()) htmlDir.mkdirs();
                String pageSource = driver.getPageSource();
                try (FileWriter writer = new FileWriter("page-source/" + testName + ".html")) {
                    writer.write(pageSource);
                }

                System.out.println("Diagnostics saved successfully for: " + testName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
