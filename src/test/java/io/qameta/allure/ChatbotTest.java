package io.qameta.allure;
import io.qameta.allure.pages.ChatbotPage;
import io.qameta.allure.utils.ExcelUtils;
import io.qameta.allure.listeners.TestListener;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Listeners;
import org.testng.Assert;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.time.Duration;

@Listeners(TestListener.class)
public class ChatbotTest {
    private static WebDriver driver;
    private static ChatbotPage chatbotPage;

    // Vedas MemberAPI Application URL
    private static final String APP_URL = "https://devdedham.aptussoft.com/MemberAPI";
    
    // Local Vedas Framework path
    private static final String FRAMEWORK_PATH = "C:\\Users\\rames\\OneDrive\\Documents\\My Data Sources\\Frameworks(Vedas).xlsx";

    @BeforeClass
    public static void setUpClass() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");

        driver = new ChromeDriver(options);
        driver.manage().window().maximize();
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(90));
        
        System.out.println("--> Opening URL: " + APP_URL);
        driver.get(APP_URL);
        
        // Pass driver directly to TestListener for failure diagnostics capture
        TestListener.setDriver(driver);
        
        chatbotPage = new ChatbotPage(driver);
    }

    @DataProvider(name = "chatbotTestData")
    public static Object[][] provideChatbotData() {
        return ExcelUtils.getDataFromExcel(FRAMEWORK_PATH, "Frameworks");
    }

    @Test(dataProvider = "chatbotTestData")
    public void testChatbotQuestion(String sheetName, int rowIndex, String testCaseId, String question, String expectedAnswer) {
        System.out.println("Executing Test ID: " + testCaseId + " | Question: " + question);

        chatbotPage.sendMessage(question);
        String actualAnswer = chatbotPage.getLatestResponse();

        System.out.println("Captured Answer: " + actualAnswer);

        // Determine status and write back to results Excel file
        String status = (actualAnswer != null && !actualAnswer.trim().isEmpty()) ? "PASS" : "FAIL";
        ExcelUtils.writeResult(sheetName, rowIndex, actualAnswer, status);

        // Assertions
        Assert.assertNotNull(actualAnswer, "Chatbot response should not be null");
        Assert.assertFalse(actualAnswer.trim().isEmpty(), "Chatbot response should not be empty");
    }

    @AfterClass
    public static void tearDownClass() {
        if (driver != null) {
            driver.quit();
            System.out.println("--> WebDriver closed successfully.");
        }
    }
}
