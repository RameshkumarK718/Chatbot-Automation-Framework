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

public class TestListener implements ITestListener {

    @Override
    public void onTestFailure(ITestResult result) {
        System.out.println("Test Failed: " + result.getName() + ". Capturing diagnostics...");
        
        // Note: Make sure your ChatbotTest passes its WebDriver instance or exposes it statically
        // For example, using a static getter in ChatbotTest: ChatbotTest.getDriver()
        WebDriver driver = io.qameta.allure.ChatbotTest.getDriver(); 

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
