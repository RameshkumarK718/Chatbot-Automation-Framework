package io.qameta.allure;
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
        
        // Open your chatbot application URL
        driver.get("https://your-chatbot-application-url.com");
        
        chatbotPage = new ChatbotPage(driver);
    }

    // DataProvider method must be static to work cleanly with TestNG
    @DataProvider(name = "chatbotTestData")
    public static Object[][] provideChatbotData() {
        return ExcelUtils.getDataFromExcel("Frameworks_Output.xlsx", "Sheet1");
    }

    @Test(dataProvider = "chatbotTestData")
    public void testChatbotQuestion(String testCaseId, String question, String expectedAnswer) {
        System.out.println("Executing Test ID: " + testCaseId + " | Question: " + question);

        // Send question through Page Object and capture streamed response
        chatbotPage.sendMessage(question);
        String actualAnswer = chatbotPage.getLatestResponse();

        System.out.println("Captured Answer: " + actualAnswer);

        // Assertions for TestNG reporting
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
