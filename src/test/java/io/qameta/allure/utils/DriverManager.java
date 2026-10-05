package io.qameta.allure.listeners;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.OutputType;
import io.qameta.allure.utils.DriverManager;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class TestListener implements ITestListener {
    private static final Logger logger = Logger.getLogger(TestListener.class.getName());

    @Override
    public void onTestFailure(ITestResult result) {
        String testName = result.getName();
        logger.severe("Test Failed: " + testName + ". Capturing diagnostics...");
        
        WebDriver driver = DriverManager.getDriver();

        if (driver != null) {
            try {
                // 1. Save Screenshot
                File shotDir = new File("screenshots");
                if (!shotDir.exists()) shotDir.mkdirs();
                File srcFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                Files.copy(srcFile.toPath(), Paths.get("screenshots/" + testName + ".png"));
                
                // 2. Save Page Source HTML
                File htmlDir = new File("page-source");
                if (!htmlDir.exists()) htmlDir.mkdirs();
                String pageSource = driver.getPageSource();
                try (FileWriter writer = new FileWriter("page-source/" + testName + ".html")) {
                    writer.write(pageSource);
                }

                // 3. Save Execution Log
                File logDir = new File("logs");
                if (!logDir.exists()) logDir.mkdirs();
                try (FileWriter logWriter = new FileWriter("logs/test-execution.log", true)) {
                    logWriter.write("FAILURE: Test [" + testName + "] failed and diagnostics were captured.\n");
                }

                logger.info("Diagnostics saved successfully for: " + testName);
            } catch (Exception e) {
                logger.severe("Error saving test diagnostics: " + e.getMessage());
            }
        }
    }
}
