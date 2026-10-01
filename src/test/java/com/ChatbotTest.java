package com.chatbot;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class ChatbotTest extends BaseTest {

    @DataProvider(name = "excelDataProvider", parallel = false)
    public Object[][] provideTestData() {
        // Call your Excel loading utility to return test rows
        // Each row becomes a separate independent TestNG test execution!
        return new Object[][]{
            // Example structure: {sheetName, rowIndex, testCaseId, question, expectedResult}
            {"Sheet1", 1, "TC001", "What is the error code?", "Expected description here"}
        };
    }

    @Test(dataProvider = "excelDataProvider")
    public void executeSingleTestCase(String sheetName, int rowIndex, String testCaseId, String question, String expectedResult) {
        System.out.println("Running " + testCaseId + ": " + question);

        ChatbotPage chatPage = new ChatbotPage(driver);
        chatPage.sendMessage(question);
        String actualResponse = chatPage.waitForStreamingToComplete();

        Assert.assertFalse(actualResponse.isBlank(), "Chatbot returned an empty response.");
        
        // Write result back
        ExcelUtils.writeResult(sheetName, rowIndex, actualResponse, "PASS");
    }
}
