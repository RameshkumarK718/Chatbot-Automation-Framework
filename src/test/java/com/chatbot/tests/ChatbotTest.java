package com.chatbot.tests;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import java.io.*;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
public class ChatbotTest {
    // Unified TestRowData containing all sheet columns
    public static class TestRowData {
        String sheetName;
        int rowIndex;
        String category;
        String subcategory;
        String testCaseId;
        String questionType;
        String question;
        String expectedAnswer;
        String chatbotAnswer;
        String relevance;
        String status;
        String passFailureReason;
        public TestRowData(String sheetName, int rowIndex, String category, String subcategory, 
                           String testCaseId, String questionType, String question, String expectedAnswer, 
                           String chatbotAnswer, String relevance, String status, String passFailureReason) {
            this.sheetName = sheetName;
            this.rowIndex = rowIndex;
            this.category = category;
            this.subcategory = subcategory;
            this.testCaseId = testCaseId;
            this.questionType = questionType;
            this.question = question;
            this.expectedAnswer = expectedAnswer;
            this.chatbotAnswer = chatbotAnswer;
            this.relevance = relevance;
            this.status = status;
            this.passFailureReason = passFailureReason;
        }
    }
    // Column indexes based on your exact sheet structure:
    // Category(0), Subcategory(1), Test Case ID(2), Question Type(3), User Question(4), Expected Answer(5), Chatbot Answer(6), Relevance(7), Status(8), Pass and Failure Reason(9)
    private static final int COL_CATEGORY = 0;
    private static final int COL_SUBCATEGORY = 1;
    private static final int COL_TESTCASE_ID = 2;
    private static final int COL_QUESTION_TYPE = 3;
    private static final int COL_QUESTION = 4;
    private static final int COL_EXPECTED = 5;
    private static final int COL_RESPONSE = 6; // Chatbot Answer column
    private static final int COL_RELEVANCE = 7;
    private static final int COL_STATUS = 8;
    private static final int COL_REASON = 9; 
    private static final String APP_URL = "https://d3rl0fkw0q6ssb.cloudfront.net/";
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(60);         
    private final String credentialsExcelPath = "https://raw.githubusercontent.com/RameshkumarK718/Chatbot-Automation-Framework/main/credentials.xlsx"; 
    private final String frameworkExcelPath = "https://raw.githubusercontent.com/RameshkumarK718/Chatbot-Automation-Framework/main/Frameworks.xlsx";    
    private WebDriver driver;
    private WebDriverWait wait;
    private String[] getCredentialsFromExcel() {
        String username = "";
        String password = "";  
        try (@SuppressWarnings("deprecation")
		InputStream is = new URL(credentialsExcelPath).openStream();
             Workbook workbook = new XSSFWorkbook(is)) {             
            Sheet sheet = workbook.getSheetAt(0);        
            Row row = sheet.getRow(3); 
            if (row != null) {
                Cell userCell = row.getCell(0); 
                Cell passCell = row.getCell(1);                             
                username = userCell != null ? getCellStringValue(userCell) : "";
                password = passCell != null ? getCellStringValue(passCell) : "";  
            }      
            System.out.println("--> Loaded Credentials from Cloud Excel | Member ID: " + username);             
        } catch (IOException e) {
            System.err.println("Error reading credentials Excel file from GitHub: " + e.getMessage());
        }      
        return new String[]{username, password};
    }
    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC) {
            long numericVal = (long) cell.getNumericCellValue();
            return String.valueOf(numericVal);
        } else if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue().trim();
        } else {
            String val = cell.toString().trim();
            if (val.endsWith(".0")) {
                val = val.substring(0, val.length() - 2);
            }
            return val;
        }
    }
    @BeforeMethod
    public void setUp() {
        initializeDriverAndLogin();
    }
    private void initializeDriverAndLogin() {
        String[] credentials = getCredentialsFromExcel();
        String memberId = credentials[0];
        String password = credentials[1];      
        Assert.assertFalse(memberId.isBlank(), "Member ID is missing from Cloud Excel.");
        Assert.assertFalse(password.isBlank(), "Password is missing from Cloud Excel.");                                
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");   
        driver = new ChromeDriver(options);
        driver.manage().window().maximize();
        wait = new WebDriverWait(driver, WAIT_TIMEOUT);            
        try {
            driver.get(APP_URL);
            WebElement memberInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("vaa-email")));
            memberInput.clear();
            memberInput.sendKeys(memberId);                                   
            WebElement passwordInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("vaa-pw")));
            passwordInput.clear();
            passwordInput.sendKeys(password);                                          
            WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("vaa-submit")));
            loginButton.click();                                                  
            wait.until(ExpectedConditions.or(
                ExpectedConditions.visibilityOfElementLocated(By.id("vaa-portal")),
                ExpectedConditions.visibilityOfElementLocated(By.xpath("//span[contains(normalize-space(), 'Conversational AI')]"))
            ));        
            WebElement conversationalAILink = wait.until(
                ExpectedConditions.elementToBeClickable(
                    By.xpath("//span[contains(normalize-space(), 'Conversational AI')]/ancestor::a | //a[contains(., 'Conversational AI')] | //a[contains(@href, 'javascript:void(0)')]")
                )
            );                     
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("arguments[0].click();", conversationalAILink);                
            System.out.println("Successfully logged in using Member ID: " + memberId);                                                        
            wait.until(
                ExpectedConditions.elementToBeClickable(
                    By.xpath("//input[@placeholder='Ask a question...'] | //textarea[@placeholder='Ask a question...'] | //input[contains(@placeholder, 'Ask')] | //div[@contenteditable='true']")
                )
            );
        } catch (Exception e) {
            Assert.fail("Failed to initialize chatbot test setup: " + e.getMessage(), e);
        }
    }
    @Test
    public void runAutomationFramework() {
        List<TestRowData> testDataList = fetchExcelDataFromGitHub(frameworkExcelPath);
        Assert.assertFalse(testDataList.isEmpty(), "Failed to fetch Excel data from GitHub or file is empty!");
        System.out.println("Successfully fetched questions from GitHub Excel. Total test cases: " + testDataList.size());                  
        for (int i = 0; i < testDataList.size(); i++) {      
            TestRowData rowData = testDataList.get(i);      
            String testCaseId = rowData.testCaseId;
            String question = rowData.question;                
            
            if (question == null || question.trim().isEmpty()) {
                System.out.println("--> Skipping [" + rowData.sheetName + "] Row " + (rowData.rowIndex + 1) + ": Question column is empty.");
                continue;
            }
            if (testCaseId == null || testCaseId.trim().isEmpty()) {
                testCaseId = String.format("TC-%03d", i + 1);
                rowData.testCaseId = testCaseId;
            }                                                            
            System.out.println("\n--- Processing [" + rowData.sheetName + "] (" + testCaseId + ") ---");
            System.out.println("Question: " + question);                         
            String chatbotResponse = askQuestion(question);             
            System.out.println("Chatbot Response: " + chatbotResponse);                                              
            rowData.chatbotAnswer = chatbotResponse; 
            rowData.status = "Executed";          
            // Periodically save results to local Excel report
            saveResultsToExcel(frameworkExcelPath, testDataList);
            
            if (i < testDataList.size() - 1) {
                sleep(2000); 
            }
        }
        System.out.println("\nAll questions processed successfully and stored in local Frameworks_Results.xlsx!");
    }
    private String askQuestion(String question) {
        try {
            WebElement chatInput = wait.until(
                ExpectedConditions.elementToBeClickable(
                    By.xpath("//input[@placeholder='Ask a question...'] | //textarea[@placeholder='Ask a question...'] | //input[contains(@placeholder, 'Ask')] | //div[@contenteditable='true']")
                )
            );        
            chatInput.click();
            chatInput.sendKeys(Keys.CONTROL + "a", Keys.BACK_SPACE);    
            chatInput.sendKeys(question);          
            chatInput.sendKeys(Keys.ENTER);                                                                                                                                                                                                                                                 
            try {
                WebElement sendButton = driver.findElement(By.xpath("//button[normalize-space()='Send'] | //button[contains(@class, 'send')] | //button[@type='submit']"));
                if (sendButton.isDisplayed() && sendButton.isEnabled()) {
                    sendButton.click();
                }
            } catch (Exception ignored) {}                            
            return waitForCompleteResponse();
        } catch (Exception e) {
            System.err.println("Error asking question: " + e.getMessage());
            return "ERROR: Response timeout or element not found";
        }
    }
    private String waitForCompleteResponse() {
        try {
            List<WebElement> initialResponses = driver.findElements(By.xpath(
                "//div[contains(@class, 'bot')] | //div[contains(@class, 'message-response')] | //div[contains(@class, 'chat-bubble')] | //div[contains(@class, 'response')] | //p[contains(@class, 'answer')]"
            ));
            int initialCount = initialResponses.size();
            WebDriverWait longWait = new WebDriverWait(driver, Duration.ofSeconds(60));                             
            final String[] lastKnownText = {""};
            final int[] stableCounter = {0};                             
            String finalAnswer = longWait.until(driverInstance -> {
                List<WebElement> currentResponses = driverInstance.findElements(By.xpath(
                    "//div[contains(@class, 'bot')] | //div[contains(@class, 'message-response')] | //div[contains(@class, 'chat-bubble')] | //div[contains(@class, 'response')] | //p[contains(@class, 'answer')]"
                ));                                                                                                                                                                                                                                        
                if (currentResponses.size() > initialCount) {
                    WebElement latestResponse = currentResponses.get(currentResponses.size() - 1);
                    String text = latestResponse.getText().trim();
                    String lowerText = text.toLowerCase();                                                      
                    boolean isFiller = lowerText.contains("reading that up") || 
                                       lowerText.contains("looking that up") || 
                                       lowerText.contains("checking") || 
                                       lowerText.contains("thinking") || 
                                       lowerText.equals("...") || 
                                       lowerText.isEmpty();                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            
                    if (!isFiller && text.length() > 2) {
                        if (text.equals(lastKnownText[0])) {
                            stableCounter[0]++;
                            if (stableCounter[0] >= 3) {
                                return text; 
                            }
                        } else {
                            lastKnownText[0] = text;
                            stableCounter[0] = 0;
                        }
                    }
                }
                return null; 
            });          
            return finalAnswer;
        } catch (Exception e) {
            try {
                List<WebElement> fallbackResponses = driver.findElements(By.xpath(
                    "//div[contains(@class, 'bot')] | //div[contains(@class, 'message-response')] | //div[contains(@class, 'chat-bubble')] | //div[contains(@class, 'response')] | //p[contains(@class, 'answer')]"
                ));
                if (!fallbackResponses.isEmpty()) {
                    String fallbackText = fallbackResponses.get(fallbackResponses.size() - 1).getText().trim();
                    if (!fallbackText.isEmpty() && !fallbackText.toLowerCase().contains("reading that up")) {
                        return fallbackText;
                    }
                }
            } catch (Exception ignored) {}
            return "ERROR: Response timeout or element not found";
        }
    }
    private List<TestRowData> fetchExcelDataFromGitHub(String urlString) {
        List<TestRowData> dataList = new ArrayList<>();
        try (@SuppressWarnings("deprecation")
		InputStream is = new URL(urlString).openStream();
             Workbook workbook = new XSSFWorkbook(is)) {         
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                String sheetName = sheet.getSheetName();               
                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;                  
                    String category = getCellStringValue(row.getCell(COL_CATEGORY));
                    String subcategory = getCellStringValue(row.getCell(COL_SUBCATEGORY));
                    String testCaseId = getCellStringValue(row.getCell(COL_TESTCASE_ID));
                    String questionType = getCellStringValue(row.getCell(COL_QUESTION_TYPE));
                    String question = getCellStringValue(row.getCell(COL_QUESTION));
                    String expectedAnswer = getCellStringValue(row.getCell(COL_EXPECTED));
                    String chatbotAnswer = getCellStringValue(row.getCell(COL_RESPONSE));
                    String relevance = getCellStringValue(row.getCell(COL_RELEVANCE));
                    String status = getCellStringValue(row.getCell(COL_STATUS));
                    String passFailureReason = getCellStringValue(row.getCell(COL_REASON));
                    
                    if (!question.isEmpty()) {
                        dataList.add(new TestRowData(sheetName, r, category, subcategory, testCaseId, 
                                                     questionType, question, expectedAnswer, 
                                                     chatbotAnswer, relevance, status, passFailureReason));
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Exception while reading Excel from GitHub: " + e.getMessage());
        }
        return dataList;
    }
    private void saveResultsToExcel(String sourceUrl, List<TestRowData> testDataList) {
        String outputFilePath = "Frameworks_Results.xlsx";
        try (@SuppressWarnings("deprecation")
		InputStream is = new URL(sourceUrl).openStream();
             Workbook workbook = new XSSFWorkbook(is)) {        
            for (TestRowData data : testDataList) {
                Sheet sheet = workbook.getSheet(data.sheetName);
                if (sheet != null) {
                    Row row = sheet.getRow(data.rowIndex);
                    if (row != null) {
                        Cell respCell = row.getCell(COL_RESPONSE, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        respCell.setCellValue(data.chatbotAnswer != null ? data.chatbotAnswer : "");
                        
                        Cell statusCell = row.getCell(COL_STATUS, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        statusCell.setCellValue(data.status != null ? data.status : "Executed");
                    }
                }
            }       
            try (FileOutputStream fos = new FileOutputStream(outputFilePath)) {
                workbook.write(fos);
            }
            System.out.println("Successfully saved updated multi-sheet results to " + outputFilePath);
        } catch (IOException e) {
            System.err.println("Error saving results to Excel file: " + e.getMessage());
        }
    }
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    @AfterMethod
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }
}
