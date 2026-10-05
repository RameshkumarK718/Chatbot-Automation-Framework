package io.qameta.allure.listeners;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.OutputType;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class TestListener implements ITestListener {
    private static final Logger logger = Logger.getLogger(TestListener.class.getName());

    // Store driver directly to avoid cross-class missing symbol issues
    private static WebDriver driverInstance;

    public static void setDriver(WebDriver driver) {
        driverInstance = driver;
    }

    @Override
    public void onTestFailure(ITestResult result) {
        String testName = result.getName();
        logger.severe("Test Failed: " + testName + ". Capturing diagnostics...");
        
        if (driverInstance != null) {
            try {
                // 1. Save local screenshot file
                File dir = new File("screenshots");
                if (!dir.exists()) dir.mkdirs();
                File srcFile = ((TakesScreenshot) driverInstance).getScreenshotAs(OutputType.FILE);
                Files.copy(srcFile.toPath(), Paths.get("screenshots/" + testName + ".png"));
                
                // 2. Save local Page Source HTML file
                File htmlDir = new File("page-source");
                if (!htmlDir.exists()) htmlDir.mkdirs();
                String pageSource = driverInstance.getPageSource();
                try (FileWriter writer = new FileWriter("page-source/" + testName + ".html")) {
                    writer.write(pageSource);
                }

                // 3. Save Execution Log
                File logDir = new File("logs");
                if (!logDir.exists()) logDir.mkdirs();
                try (FileWriter logWriter = new FileWriter("logs/test-execution.log", true)) {
                    logWriter.write("FAILURE: Test [" + testName + "] failed.\n");
                }

                logger.info("Diagnostics saved successfully for: " + testName);
            } catch (Exception e) {
                logger.severe("Error capturing test diagnostics: " + e.getMessage());
            }
        }
    }
}
