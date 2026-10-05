package io.qameta.allure.tests;
import io.qameta.allure.pages.ChatbotPage;
import io.qameta.allure.utils.ExcelUtils;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.Assert;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

public class ChatbotTest {
    private static WebDriver driver;
    private static ChatbotPage chatbotPage;

    @BeforeClass
    public static void setUpClass() {
        driver = new ChromeDriver();
        driver.manage().window().maximize();
        
        // 1. Open application URL
        driver.get("https://your-chatbot-application-url.com");
        
        // 2. Perform Login & Role Selection here if needed

        chatbotPage = new ChatbotPage(driver);
    }

    // FIXED: Added 'static' keyword here
    @DataProvider(name = "chatbotTestData")
    public static Object[][] provideChatbotData() {
        return ExcelUtils.getDataFromExcel("Frameworks_Output.xlsx", "Sheet1");
    }

    @Test(dataProvider = "chatbotTestData")
    public void testChatbotQuestion(String testCaseId, String question, String expectedAnswer) {
        System.out.println("Executing Test ID: " + testCaseId + " | Question: " + question);

        chatbotPage.sendMessage(question);
        String actualAnswer = chatbotPage.getLatestResponse();

        System.out.println("Captured Answer: " + actualAnswer);

        Assert.assertNotNull(actualAnswer, "Chatbot response should not be null");
        Assert.assertFalse(actualAnswer.isEmpty(), "Chatbot response should not be empty");
    }

    @AfterClass
    public static void tearDownClass() {
        if (driver != null) {
            driver.quit();
        }
    }
}
