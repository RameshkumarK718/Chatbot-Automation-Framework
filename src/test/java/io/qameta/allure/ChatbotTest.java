package io.qameta.allure;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.Assert;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ChatbotTest {

    // Helper method to read the Excel cells safely
    private String getCellString(Cell cell) {
        if (cell == null) return "";
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue().trim();
    }

    // 1. Data Provider to read Excel data row by row
    Object[][] getDataFromExcel(String filePath, String sheetName) {
        List<Object[]> dataList = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(new File(filePath));
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                sheet = workbook.getSheetAt(0); // Fallback to first sheet
            }

            int rowCount = sheet.getPhysicalNumberOfRows();
            for (int i = 1; i < rowCount; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String testCaseId = getCellString(row.getCell(0));
                String question = getCellString(row.getCell(3)); // Adjust index based on your columns
                String expected = getCellString(row.getCell(4)); // Adjust index based on your columns

                dataList.add(new Object[]{testCaseId, question, expected});
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return dataList.toArray(new Object[0][0]);
    }

    @DataProvider(name = "chatbotTestData")
    public Object[][] provideChatbotData() {
        // Path to your input Excel file generated/read by Java
        return getDataFromExcel("Frameworks_Output.xlsx", "Sheet1");
    }

    // 2. Individual Test Method powered by DataProvider
    @Test(dataProvider = "chatbotTestData")
    public void testChatbotQuestion(String testCaseId, String question, String expectedAnswer) {
        System.out.println("Executing Test ID: " + testCaseId + " | Question: " + question);

        // Call your chatbot service / API here to get the actual answer
        String actualAnswer = callChatbotService(question);

        // Basic validation or assertion for TestNG reporting
        Assert.assertNotNull(actualAnswer, "Chatbot response should not be null");
        
        // Note: Final deep semantic/AI evaluation is handled by your Python script 
        // after Java writes these actual answers out to the output Excel file!
    }

    private String callChatbotService(String question) {
        // Your existing chatbot integration logic goes here
        return "Sample Chatbot Answer"; 
    }
}
