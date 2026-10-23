package io.qameta.allure;
import io.qameta.allure.listeners.TestListener;
import io.qameta.allure.pages.ChatbotPage;
import io.qameta.allure.utils.ExcelUtils;
import java.time.Duration;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(TestListener.class)
public class ChatbotTest {

    private static final String APP_URL =
            "https://devdedham.aptussoft.com/MemberAPI";

    private static final String FRAMEWORK_PATH =
            "https://raw.githubusercontent.com/RameshkumarK718/Chatbot-Automation-Framework/main/Frameworks(Vedas).xlsx";

    private static final String SHEET_NAME = "Frameworks";
    private static final Duration PAGE_LOAD_TIMEOUT = Duration.ofSeconds(90);

    private WebDriver driver;
    private ChatbotPage chatbotPage;

    @BeforeClass(alwaysRun = true)
    public void setUpClass() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--headless=new",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--window-size=1920,1080"
        );

        driver = new ChromeDriver(options);

        driver.manage().timeouts()
                .pageLoadTimeout(PAGE_LOAD_TIMEOUT);

        TestListener.setDriver(driver);

        driver.get(APP_URL);
        chatbotPage = new ChatbotPage(driver);

        System.out.println("Application opened: " + APP_URL);
    }

    @DataProvider(name = "chatbotTestData")
    public Object[][] provideChatbotData() {
        Object[][] testData =
                ExcelUtils.getDataFromExcel(FRAMEWORK_PATH, SHEET_NAME);

        Assert.assertNotNull(testData,
                "Excel test data must not be null.");

        Assert.assertTrue(testData.length > 0,
                "Excel test data must contain at least one test case.");

        return testData;
    }

    @Test(dataProvider = "chatbotTestData")
    public void testChatbotQuestion(
            String sheetName,
            int rowIndex,
            String testCaseId,
            String question,
            String expectedAnswer) {

        Assert.assertNotNull(question,
                "Question must not be null. Test case: " + testCaseId);

        Assert.assertFalse(question.trim().isEmpty(),
                "Question must not be empty. Test case: " + testCaseId);

        System.out.printf(
                "Executing test case: %s | Question: %s%n",
                testCaseId,
                question
        );

        chatbotPage.sendMessage(question);

        String actualAnswer = chatbotPage.getLatestResponse();

        System.out.println("Captured answer: " + actualAnswer);

        String status =
                actualAnswer != null && !actualAnswer.trim().isEmpty()
                        ? "PASS"
                        : "FAIL";

        ExcelUtils.writeResult(
                sheetName,
                rowIndex,
                actualAnswer,
                status
        );

        Assert.assertNotNull(actualAnswer,
                "Chatbot response must not be null. Test case: " + testCaseId);

        Assert.assertFalse(actualAnswer.trim().isEmpty(),
                "Chatbot response must not be empty. Test case: " + testCaseId);
    }

    @AfterClass(alwaysRun = true)
    public void tearDownClass() {
        if (driver != null) {
            try {
                driver.quit();
                System.out.println("WebDriver closed successfully.");
            } catch (Exception exception) {
                System.err.println(
                        "Error while closing WebDriver: "
                                + exception.getMessage()
                );
            } finally {
                driver = null;
                chatbotPage = null;
            }
        }
    }
}
