package io.qameta.allure.tests;
import io.qameta.allure.pages.ChatbotPage;
import io.qameta.allure.utils.ExcelUtils; // Assuming you have an Excel utility
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.Assert;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

public class ChatbotTest {
    private WebDriver driver;
    private ChatbotPage chatbotPage;

    @BeforeMethod
    public void setUp() {
        // Initialize WebDriver (or fetch from a BaseTest/DriverFactory class)
        driver = new ChromeDriver();
        driver.manage().window().maximize();
        driver.หนด = driver; // Navigate to your chatbot URL
        driver.get("https://your-chatbot-application-url.com");
        
        chatbotPage = new ChatbotPage(driver);
    }
    @DataProvider(name = "chatbotTestData")
    public Object[][] provideChatbotData() {
        // Read test data row by row using your utility
        return ExcelUtils.getDataFromExcel("Frameworks_Output.xlsx", "Sheet1");
    }
    @Test(dataProvider = "chatbotTestData")
    public void testChatbotQuestion(String testCaseId, String question, String expectedAnswer) {
        System.out.println("Executing Test ID: " + testCaseId + " | Question: " + question);

        // 1. Send question via Page Object
        chatbotPage.sendMessage(question);

        // 2. Fetch the actual response cleanly through the Page Object layer
        String actualAnswer = chatbotPage.getLatestResponse();

        System.out.println("Chatbot Answer Captured: " + actualAnswer);

        // 3. Basic TestNG validation
        Assert.assertNotNull(actualAnswer, "Chatbot response should not be null");
        Assert.assertFalse(actualAnswer.isEmpty(), "Chatbot response should not be empty");
        
        // (Optional: Write actualAnswer back to your Excel sheet so your Python script can pick it up)
    }
    @AfterMethod
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }
}
