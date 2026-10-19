package com.chatbot;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.ITestResult;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class ChatbotTest {

    public static class TestRowData {
        public String sheetName;
        public int rowIndex;
        public String testCaseId;
        public String role;
        public String questionType;
        public String question;
        public String expectedResult;
        public String context;
        public String runnable;
        public String actualResult;
        public String reason;
        public String status;

        public TestRowData(String sheetName, int rowIndex, String testCaseId, String role,
                           String questionType, String question, String expectedResult, 
                           String context, String runnable, String actualResult, 
                           String reason, String status) {
            this.sheetName = sheetName;
            this.rowIndex = rowIndex;
            this.testCaseId = testCaseId;
            this.role = role;
            this.questionType = questionType;
            this.question = question;
            this.expectedResult = expectedResult;
            this.context = context;
            this.runnable = runnable;
            this.actualResult = actualResult;
            this.reason = reason;
            this.status = status;
        }
    }

    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(120);

    // Framework & GitHub Repository Configurations
    private static final String ACTIVE_FRAMEWORK = System.getProperty("active.framework", "Vedas");
    private static final String APP_URL = System.getProperty("app.url", "https://devdedham.aptussoft.com/MemberAPI");
    
    private static final String CREDENTIALS_EXCEL_URL = System.getProperty("credentials.excel.url",
            "https://raw.githubusercontent.com/RameshkumarK718/Chatbot-Automation-Framework/main/credentials(1).xlsx");
    
    private static final String FRAMEWORK_EXCEL_URL = System.getProperty("framework.excel.url",
            "https://raw.githubusercontent.com/RameshkumarK718/Chatbot-Automation-Framework/main/Frameworks(" + ACTIVE_FRAMEWORK + ").xlsx");
    
    private static final String RESULT_EXCEL_URL = System.getProperty("result.excel.url",
            "https://raw.githubusercontent.com/RameshkumarK718/Chatbot-Automation-Framework/main/Frameworks-Result(" + ACTIVE_FRAMEWORK + ").xlsx");

    private static final String CREDENTIALS_FILE_PATH = System.getProperty("credentials.excel.path",
            "C:\\Users\\rames\\OneDrive\\Documents\\My Data Sources\\credentials(1).xlsx");
    
    private static final String FRAMEWORK_EXCEL_FILE = System.getProperty("framework.excel.path",
            "C:\\Users\\rames\\OneDrive\\Documents\\My Data Sources\\Frameworks(" + ACTIVE_FRAMEWORK + ").xlsx");
    
    private static final String OUTPUT_EXCEL_FILE = System.getProperty("output.excel.path",
            "C:\\Users\\rames\\OneDrive\\Documents\\My Data Sources\\Frameworks-Result(" + ACTIVE_FRAMEWORK + ").xlsx");
    
    private static final String OUTPUT_JSON_FILE = "target/test_results_" + ACTIVE_FRAMEWORK.toLowerCase() + ".json";
    
    private WebDriver driver;
    private WebDriverWait wait;
    private final List<TestRowData> executedResults = new ArrayList<>();
    private String lastConversationContext = "";

    private File getOrDownloadFile(String localPath, String remoteUrl) {
        File file = new File(localPath);
        if (!file.exists() && remoteUrl != null && !remoteUrl.isBlank()) {
            try {
                file.getParentFile().mkdirs();
                try (InputStream in = new URL(remoteUrl).openStream()) {
                    Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                System.out.println("--> Downloaded resource from GitHub to: " + localPath);
            } catch (Exception e) {
                System.err.println("--> Warning: Could not download remote file from " + remoteUrl + ": " + e.getMessage());
            }
        }
        return file;
    }

    private String[] getCredentialsFromExcel() {
        String username = "";
        String password = "";
        
        File localFile = getOrDownloadFile(CREDENTIALS_FILE_PATH, CREDENTIALS_EXCEL_URL);
        try (InputStream is = new FileInputStream(localFile);
             Workbook workbook = new XSSFWorkbook(is)) {
            
            if (workbook.getNumberOfSheets() == 0) {
                throw new RuntimeException("Credentials Excel contains no sheets.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row row = sheet.getRow(1);
            if (row == null) row = sheet.getRow(0);
            if (row == null) {
                throw new RuntimeException("Credentials row was not found in Excel.");
            }
            
            username = getCellStringValue(row.getCell(0));
            password = getCellStringValue(row.getCell(1));
            
            if (username.isBlank()) {
                username = getCellStringValue(row.getCell(1));
            }
            if (password.isBlank()) {
                password = username;
            }
            
            System.out.println("--> Credentials loaded successfully for user: " + username);
        } catch (Exception e) {
            throw new RuntimeException("Error reading credentials Excel: " + e.getMessage(), e);
        }
        return new String[]{username, password};
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        DataFormatter formatter = new DataFormatter();
        try {
            return formatter.formatCellValue(cell).trim();
        } catch (Exception e) {
            return cell.toString().trim();
        }
    }

    private List<TestRowData> fetchExcelData() {
        List<TestRowData> dataList = new ArrayList<>();
        File localFile = getOrDownloadFile(FRAMEWORK_EXCEL_FILE, FRAMEWORK_EXCEL_URL);
        
        try (InputStream is = new FileInputStream(localFile);
             Workbook workbook = new XSSFWorkbook(is)) {

            if (workbook.getNumberOfSheets() == 0) {
                throw new RuntimeException("Framework Excel contains no sheets.");
            }

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName();
                System.out.println("--> Reading sheet [" + ACTIVE_FRAMEWORK + "]: " + sheetName);

                Row headerRow = sheet.getRow(0);
                if (headerRow == null) continue;

                Map<String, Integer> colMap = new HashMap<>();
                for (Cell cell : headerRow) {
                    String headerVal = getCellStringValue(cell).toLowerCase();
                    if (!headerVal.isBlank()) {
                        colMap.put(headerVal, cell.getColumnIndex());
                    }
                }

                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;

                    String role = getCellByExactOrPartialHeader(row, colMap, "role", "category", "set #", "#");
                    String questionType = getCellByExactOrPartialHeader(row, colMap, "question type", "type", "subcategory");
                    String question = getCellByExactOrPartialHeader(row, colMap, "user question", "question / input to enter", "question");
                    String expectedResult = getCellByExactOrPartialHeader(row, colMap, "expected answer", "expected result", "what it tests / expected answer");
                    String context = getCellByExactOrPartialHeader(row, colMap, "context", "conversation context");
                    String runnable = getCellByExactOrPartialHeader(row, colMap, "runnable for this role today?", "runnable", "run");

                    if (question.isBlank()) continue;

                    if (!runnable.isBlank() && (runnable.equalsIgnoreCase("no") || runnable.equalsIgnoreCase("n"))) {
                        continue;
                    }

                    String testCaseId = getCellByExactOrPartialHeader(row, colMap, "test case id", "tc id");
                    if (testCaseId.isBlank()) {
                        testCaseId = String.format("%s-%s-TC%03d", ACTIVE_FRAMEWORK, sheetName.replaceAll("\\s+", ""), r);
                    }
                    
                    dataList.add(new TestRowData(
                            sheetName, r, testCaseId, role, questionType, question, 
                            expectedResult, context, runnable, "", "", ""
                    ));
                }
            }
            System.out.println("--> Successfully parsed " + dataList.size() + " test cases for: " + ACTIVE_FRAMEWORK);
        } catch (Exception e) {
            throw new RuntimeException("Error fetching Framework Excel: " + e.getMessage(), e);
        }
        return dataList;
    }

    private String getCellByExactOrPartialHeader(Row row, Map<String, Integer> colMap, String... possibleHeaders) {
        for (String header : possibleHeaders) {
            for (Map.Entry<String, Integer> entry : colMap.entrySet()) {
                if (entry.getKey().equals(header) || entry.getKey().contains(header)) {
                    Cell cell = row.getCell(entry.getValue());
                    String val = getCellStringValue(cell);
                    if (!val.isBlank()) return val;
                }
            }
        }
        return "";
    }

    @BeforeClass
    public void setUpClass() {
        initializeDriverAndLogin();
    }

    @AfterClass
    public void tearDownClass() {
        try {
            updateFrameworkExcel(executedResults);
            exportResultsToJson(executedResults);
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                    System.out.println("--> WebDriver closed successfully.");
                } catch (Exception e) {
                    System.err.println("--> Error closing WebDriver: " + e.getMessage());
                }
            }
        }
    }

    @AfterMethod
    public void captureDiagnosticsOnFailure(ITestResult result) {
        if (result.getStatus() != ITestResult.FAILURE || driver == null) return;

        String testName = result.getName() + "_" + System.currentTimeMillis();
        try {
            File htmlFile = new File("target/diagnostics/html/" + testName + ".html");
            File parent = htmlFile.getParentFile();
            if (parent != null) parent.mkdirs();

            try (FileWriter writer = new FileWriter(htmlFile)) {
                writer.write(driver.getPageSource());
            }
            System.out.println("--> Failure diagnostics saved: " + htmlFile.getPath());
        } catch (Exception e) {
            System.err.println("--> Failed to write failure diagnostics: " + e.getMessage());
        }
    }

    private void initializeDriverAndLogin() {
        String[] credentials = getCredentialsFromExcel();
        String memberId = credentials[0];
        String password = credentials[1];

        Assert.assertFalse(memberId == null || memberId.isBlank(), "Member ID is missing.");
        Assert.assertFalse(password == null || password.isBlank(), "Password is missing.");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");

        driver = new ChromeDriver(options);

        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(120));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));

        wait = new WebDriverWait(driver, Duration.ofSeconds(45));

        try {
            System.out.println("--> Opening application URL: " + APP_URL);
            driver.get(APP_URL);

            wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                    .executeScript("return document.readyState")
                    .equals("complete"));

            By emailLocator = By.id("vaa-email");
            By passwordLocator = By.id("vaa-pw");
            By loginButtonLocator = By.id("vaa-submit");

            List<WebElement> emailInputs = driver.findElements(emailLocator);
            if (!emailInputs.isEmpty()) {
                WebElement memberInput = wait.until(ExpectedConditions.elementToBeClickable(emailLocator));
                memberInput.clear();
                memberInput.sendKeys(memberId);

                WebElement passwordInput = wait.until(ExpectedConditions.elementToBeClickable(passwordLocator));
                passwordInput.clear();
                passwordInput.sendKeys(password);

                WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(loginButtonLocator));
                clickElement(loginButton);
                System.out.println("--> Login credentials submitted successfully.");
            }

            navigateToConversationalAI();

            waitForChatInput();
            System.out.println("--> Chatbot input ready.");

        } catch (Exception e) {
            try {
                File debugFile = new File("target/login_debug.html");
                File parent = debugFile.getParentFile();
                if (parent != null) parent.mkdirs();
                try (FileWriter writer = new FileWriter(debugFile)) {
                    writer.write(driver.getPageSource());
                }
                System.err.println("--> Debug DOM saved to: target/login_debug.html");
            } catch (Exception debugException) {
                System.err.println("--> Failed saving page source: " + debugException.getMessage());
            }

            throw new AssertionError("Failed to initialize chatbot test setup: " + e.getMessage(), e);
        }
    }

    private void navigateToConversationalAI() {
        try {
            By conversationalAiLocator = By.xpath(
                "//a[contains(normalize-space(.), 'Conversational AI')] | " +
                "//span[contains(normalize-space(.), 'Conversational AI')] | " +
                "//li[contains(normalize-space(.), 'Conversational AI')]"
            );
            WebElement aiMenuButton = wait.until(ExpectedConditions.elementToBeClickable(conversationalAiLocator));
            clickElement(aiMenuButton);
            System.out.println("--> Navigated to Conversational AI section successfully.");
        } catch (Exception e) {
            System.out.println("--> Conversational AI menu already active or skipped: " + e.getMessage());
        }
    }

    private By getChatInputLocator() {
        return By.xpath(
            "//input[contains(@placeholder,'Ask a question')] | " +
            "//textarea[contains(@placeholder,'Ask a question')] | " +
            "//input[contains(@placeholder,'Ask')] | " +
            "//textarea | //input[@type='text'] | //*[@contenteditable='true']"
        );
    }
    
    private WebElement waitForChatInput() {
        return wait.until(ExpectedConditions.elementToBeClickable(getChatInputLocator()));
    }

    private void clickElement(WebElement element) {
        try {
            element.click();
        } catch (Exception e) {
            try {
                JavascriptExecutor js = (JavascriptExecutor) driver;
                js.executeScript("arguments[0].click();", element);
            } catch (Exception ignored) {}
        }
    }

    private String getLastChatMessageText() {
        try {
            By messageLocator = By.xpath(
                "//div[contains(@class,'bot-message') or contains(@class,'chat-response') or contains(@class,'message-bot') or contains(@class,'bot')]" +
                "//span | //div[contains(@class,'bot-message') or contains(@class,'chat-response') or contains(@class,'message-bot')]" +
                "| //*[contains(@class,'chat-bubble') or contains(@class,'response') or contains(@class,'message')]"
            );

            List<WebElement> messages = driver.findElements(messageLocator);
            if (!messages.isEmpty()) {
                WebElement lastMessage = messages.get(messages.size() - 1);
                String text = lastMessage.getText();
                return text != null ? text.trim() : "";
            }
        } catch (Exception e) {
            System.err.println("Error reading last chat message: " + e.getMessage());
        }
        return null;
    }

    private String waitForChatbotResponse(String previousResponse) {
        WebDriverWait responseWait = new WebDriverWait(driver, RESPONSE_TIMEOUT);
        return responseWait.until(d -> {
            String currentResponse = getLastChatMessageText();
            
            if (currentResponse == null || currentResponse.isBlank()) {
                return null;
            }
            
            if (!currentResponse.equals(previousResponse)) {
                return currentResponse;
            }
            
            return null;
        });
    }

    private String sendQuestionToChatbot(String question, String questionType, String context) {
        if (questionType != null && (questionType.equalsIgnoreCase("Initial") || questionType.equalsIgnoreCase("Direct"))) {
            lastConversationContext = ""; 
        }

        String previousResponse = getLastChatMessageText();
        WebElement chatInput = waitForChatInput();
        try {
            chatInput.click();
            chatInput.clear();
        } catch (Exception ignored) {}

        chatInput.sendKeys(question);
        chatInput.sendKeys(Keys.ENTER);

        try {
            By sendBtnLocator = By.xpath("//button[contains(normalize-space(.), 'Send') or contains(@class,'send')]");
            List<WebElement> sendButtons = driver.findElements(sendBtnLocator);
            if (!sendButtons.isEmpty() && sendButtons.get(0).isDisplayed()) {
                JavascriptExecutor js = (JavascriptExecutor) driver;
                js.executeScript("arguments[0].click();", sendButtons.get(0));
            }
        } catch (Exception ignored) {}

        String response = waitForChatbotResponse(previousResponse);
        if (response != null && !response.isBlank()) {
            lastConversationContext = response;
        }
        return response;
    }
    
    private void selectRole(String targetRole) {
        if (targetRole == null || targetRole.isBlank()) return;
        try {
            By roleDropdownLocator = By.xpath("//select[contains(@id,'role') or contains(@name,'role')]");
            List<WebElement> dropdowns = driver.findElements(roleDropdownLocator);
            if (!dropdowns.isEmpty()) {
                WebElement dropdown = dropdowns.get(0);
                dropdown.click();
                WebElement option = driver.findElement(By.xpath("//option[text()='" + targetRole + "']"));
                option.click();
            }
        } catch (Exception ignored) {}
    }

    @DataProvider(name = "excelTestData")
    public Object[][] provideExcelData() {
        List<TestRowData> dataList = fetchExcelData();
        Object[][] data = new Object[dataList.size()][1];
        for (int i = 0; i < dataList.size(); i++) {
            data[i][0] = dataList.get(i);
        }
        return data;
    }

    @Test(dataProvider = "excelTestData")
    public void runSingleQuestionTest(TestRowData rowData) {
        try {
            if (driver == null) {
                initializeDriverAndLogin();
            } else {
                waitForChatInput();
            }
            selectRole(rowData.role);

            System.out.println("--> Running test: " + rowData.testCaseId
                    + " | Sheet: " + rowData.sheetName
                    + " | Question: " + rowData.question);
            
            String chatbotResponse = sendQuestionToChatbot(rowData.question, rowData.questionType, rowData.context);
            rowData.actualResult = (chatbotResponse != null) ? chatbotResponse : "";

            if (chatbotResponse == null || chatbotResponse.isBlank()) {
                rowData.status = "FAIL";
                rowData.reason = "Chatbot returned an empty or null response.";
            } else {
                rowData.status = "PASS";
                rowData.reason = "Success. Response received and captured.";
            }
        } catch (Exception e) {
            rowData.actualResult = "EXCEPTION: " + e.getMessage();
            rowData.status = "FAIL";
            rowData.reason = e.getMessage();
            if (e.getMessage() != null && e.getMessage().contains("invalid session id")) {
                driver = null; 
            }
        }

        synchronized (executedResults) {
            executedResults.add(rowData);
        }

        Assert.assertEquals(rowData.status, "PASS", "Test failed for TC " + rowData.testCaseId + ": " + rowData.reason);
    }
    
    private void updateFrameworkExcel(List<TestRowData> results) {
        File localFile = getOrDownloadFile(OUTPUT_EXCEL_FILE, RESULT_EXCEL_URL);
        File templateFile = getOrDownloadFile(FRAMEWORK_EXCEL_FILE, FRAMEWORK_EXCEL_URL);
        
        File sourceFile = localFile.exists() ? localFile : templateFile;

        try (InputStream is = new FileInputStream(sourceFile);
             Workbook workbook = new XSSFWorkbook(is);
             FileOutputStream outputStream = new FileOutputStream(OUTPUT_EXCEL_FILE)) {
            
            for (TestRowData result : results) {
                Sheet sheet = workbook.getSheet(result.sheetName);
                if (sheet == null) sheet = workbook.getSheetAt(0);
                Row row = sheet.getRow(result.rowIndex);
                if (row == null) continue;

                Row headerRow = sheet.getRow(0);
                if (headerRow == null) continue;

                for (Cell cell : headerRow) {
                    String header = getCellStringValue(cell).toLowerCase().trim();
                    int colIdx = cell.getColumnIndex();
                    
                    if (header.contains("actual result") || header.contains("chatbot answer")) {
                        Cell c = row.getCell(colIdx, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        c.setCellValue(result.actualResult);
                    } else if (header.contains("pass / fail") || header.contains("status")) {
                        Cell c = row.getCell(colIdx, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        c.setCellValue(result.status);
                    } else if (header.contains("reason") || header.contains("pass and failure reason")) {
                        Cell c = row.getCell(colIdx, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        c.setCellValue(result.reason);
                    }
                }
            }
            workbook.write(outputStream);
            System.out.println("--> [SUCCESS] Results workbook populated successfully: " + OUTPUT_EXCEL_FILE);
        } catch (Exception e) {
            System.err.println("Critical Error updating Excel workbook: " + e.getMessage());
        }
    }
        
    private void exportResultsToJson(List<TestRowData> results) {
        try {
            File file = new File(OUTPUT_JSON_FILE);
            file.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(file)) {
                writer.write("[\n");
                for (int i = 0; i < results.size(); i++) {
                    TestRowData r = results.get(i);
                    writer.write(String.format(
                            "  {\n" +
                            "    \"testCaseId\": \"%s\",\n" +
                            "    \"role\": \"%s\",\n" +
                            "    \"questionType\": \"%s\",\n" +
                            "    \"question\": \"%s\",\n" +
                            "    \"expectedResult\": \"%s\",\n" +
                            "    \"actualResult\": \"%s\",\n" +
                            "    \"status\": \"%s\"\n" +
                            "  }%s\n",
                            escapeJson(r.testCaseId),
                            escapeJson(r.role),
                            escapeJson(r.questionType),
                            escapeJson(r.question),
                            escapeJson(r.expectedResult),
                            escapeJson(r.actualResult),
                            escapeJson(r.status),
                            (i < results.size() - 1) ? "," : ""
                    ));
                }
                writer.write("]\n");
            }
            System.out.println("--> Execution JSON written to " + file.getAbsolutePath()); 
        } catch (Exception e) {
            System.err.println("Failed to export JSON: " + e.getMessage());
        }
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}
