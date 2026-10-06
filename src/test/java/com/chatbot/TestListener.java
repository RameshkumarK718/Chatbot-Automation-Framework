package com.chatbot;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.testng.ITestListener;
import org.testng.ITestResult;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TestListener implements ITestListener {
    @Override
    public void onTestFailure(ITestResult result) {
        Object testClass = result.getInstance();
        WebDriver driver = getDriverFromObject(testClass);

        if (driver != null) {
            try {
                // Screenshot
                File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                Files.copy(src.toPath(), Paths.get("target/diagnostics/" + result.getName() + ".png"));
                
                // HTML Dump
                String pageSource = driver.getPageSource();
                Files.writeString(Paths.get("target/diagnostics/" + result.getName() + ".html"), pageSource);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

	private WebDriver getDriverFromObject(Object testClass) {
		// TODO Auto-generated method stub
		return null;
	}
}
