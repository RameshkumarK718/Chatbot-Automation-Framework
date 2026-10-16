package com.chatbot;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogType;
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
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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

    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(90);

    // Dynamic framework property support (defaults to EFI, easily switchable via -Dframework=Vedas)
    private static final String ACTIVE_FRAMEWORK = System.getProperty("active.framework", "EFI");
    
    private static final String APP_URL = System.getProperty("app.url", "https://dtqponlzcij0l.cloudfront.net/");
    private static final String CREDENTIALS_EXCEL_URL = System.getProperty("credentials.excel.url",
            "https://raw.githubusercontent.com/RameshkumarK718/Chatbot-Automation-Framework/main/credentials(2).xlsx");
    
    private static final String FRAMEWORK_EXCEL_FILE = "Frameworks(" + ACTIVE_FRAMEWORK + ").xlsx";
    private static final String OUTPUT_EXCEL_FILE = "Frameworks-Result(" + ACTIVE_FRAMEWORK + ").xlsx";
    private static final String OUTPUT_JSON_FILE = "target/test_results_" + ACTIVE_FRAMEWORK.toLowerCase() + ".json";
    
    private WebDriver driver;
    private WebDriverWait wait;
    private final List<TestRowData> executedResults = new ArrayList<>();
    private String lastConversationContext = "";

    private InputStream openUrlStream(String fileUrl) throws Exception {
        @SuppressWarnings("deprecation")
        URL url = new URL(fileUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(60000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setRequestProperty("Accept", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            throw new RuntimeException("Unable to download file. HTTP response code: " + responseCode + " | URL: " + fileUrl);
        }
        return connection.getInputStream();
    }

    private String[] getCredentialsFromExcel() {
        String username = "";
        String password = "";
        
        String credFilename = ACTIVE_FRAMEWORK.equalsIgnoreCase("Vedas") ? "credentials(1).xlsx" : "credentials(2).xlsx";
        File localFile = new File(credFilename);
        InputStream is = null;
        try {
            if (localFile.exists()) {
                is = new java.io.FileInputStream(localFile);
                System.out.println("--> Loading credentials from local file: " + credFilename);
            } else {
                is = openUrlStream(CREDENTIALS_EXCEL_URL);
                System.out.println("--> Loading credentials from URL.");
            }

            try (Workbook workbook = new XSSFWorkbook(is)) {
                if (workbook.getNumberOfSheets() == 0) {
                    throw new RuntimeException("Credentials Excel contains no sheets.");
                }
                Sheet sheet = workbook.getSheetAt(0);
                Row row = sheet.getRow(1);
                if (row == null) row = sheet.getRow(0);
                if (row == null) {
                    throw new RuntimeException("Credentials row was not found in Excel.");
                }
                username = getCellStringValue(row.getCell(1));
                if (username.isBlank()) {
                    username = getCellStringValue(row.getCell(0));
                }
                password = username;
                System.out.println("--> Credentials loaded successfully for user: " + username);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error reading credentials Excel: " + e.getMessage(), e);
        }
        return new String[]{username, password};
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        DataFormatter formatter = new DataFormatter();
        try {
            return formatter.formatCellValue(cell).trim();
        } catch (Exception e) {
            return cell.toString().trim();
        }
    }

    private List<TestRowData> fetchExcelData() {
        List<TestRowData> dataList = new ArrayList<>();
        File localFile = new File(FRAMEWORK_EXCEL_FILE);
        
        try (InputStream is = localFile.exists() 
                ? new java.io.FileInputStream(localFile) 
                : openUrlStream("https://raw.githubusercontent.com/RameshkumarK718/Chatbot-Automation-Framework/main/" + FRAMEWORK_EXCEL_FILE);
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

                    String role = getCellByExactOrPartialHeader(row, colMap, "role", "set #", "#");
                    String questionType = getCellByExactOrPartialHeader(row, colMap, "question type", "type", "category");
                    String question = getCellByExactOrPartialHeader(row, colMap, "question / input to enter", "question");
                    String expectedResult = getCellByExactOrPartialHeader(row, colMap, "expected result", "what it tests / expected answer");
                    String context = getCellByExactOrPartialHeader(row, colMap, "context", "conversation context");
                    String runnable = getCellByExactOrPartialHeader(row, colMap, "runnable for this role today?", "runnable", "run");

                    if (question.isBlank()) {
                        continue;
                    }

                    // Filter out non-runnable tests if specified in Excel
                    if (!runnable.isBlank() && (runnable.equalsIgnoreCase("no") || runnable.equalsIgnoreCase("n"))) {
                        continue;
                    }

                    String testCaseId = String.format("%s-%s-TC%03d", ACTIVE_FRAMEWORK, sheetName.replaceAll("\\s+", ""), r);
                    dataList.add(new TestRowData(
                            sheetName, r, testCaseId, role, questionType, question, 
                            expectedResult, context, runnable, "", "", ""
                    ));
                }
            }
            System.out.println("--> Successfully parsed " + dataList.size() + " runnable test cases for: " + ACTIVE_FRAMEWORK);
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
                    if (!val.isBlank()) {
                        return val;
                    }
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
    if (result.getStatus() != ITestResult.FAILURE || driver == null) {
        return;
    }

    String testName =
        result.getName() + "_" + System.currentTimeMillis();

    try {
        // Verify that the browser session is still available.
        driver.getWindowHandles();

        File htmlFile = new File(
            "target/diagnostics/html/" + testName + ".html"
        );

        File parent = htmlFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        try (FileWriter writer = new FileWriter(htmlFile)) {
            writer.write(driver.getPageSource());
        }

        System.out.println(
            "--> Failure diagnostics saved: " + htmlFile.getPath()
        );

    } catch (WebDriverException e) {
        System.err.println(
            "--> Browser session unavailable; skipping diagnostics: "
            + e.getMessage()
        );
    } catch (Exception e) {
        System.err.println(
            "--> Failed to write failure diagnostics: " + e.getMessage()
        );
    }
}



private void initializeDriverAndLogin() {
    String[] credentials = getCredentialsFromExcel();
    String memberId = credentials[0];
    String password = credentials[1];

    Assert.assertFalse(memberId == null || memberId.isBlank(),
            "Member ID is missing.");
    Assert.assertFalse(password == null || password.isBlank(),
            "Password is missing.");

    ChromeOptions options = new ChromeOptions();
    options.addArguments("--headless=new");
    options.addArguments("--no-sandbox");
    options.addArguments("--disable-dev-shm-usage");
    options.addArguments("--disable-gpu");
    options.addArguments("--window-size=1920,1080");

    driver = new ChromeDriver(options);
    driver.manage().timeouts().implicitlyWait(Duration.ZERO);
    driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(90));

    wait = new WebDriverWait(driver, WAIT_TIMEOUT);

    try {
        System.out.println("--> Opening application URL: " + APP_URL);
        driver.get(APP_URL);

        wait.until(webDriver ->
                ((JavascriptExecutor) webDriver)
                        .executeScript("return document.readyState")
                        .equals("complete"));

        // Allow the SPA to render, without a fixed sleep.
        By emailLocator = By.xpath(
                "//input[" +
                "@id='vaa-email' or " +
                "@id='email' or " +
                "@id='username' or " +
                "@type='email' or " +
                "contains(@name,'email') or " +
                "contains(@name,'user') or " +
                "contains(@placeholder,'email') or " +
                "contains(@autocomplete,'username')" +
                "]"
        );

        // Log the actual page state before waiting for the field.
        System.out.println("--> Current URL: " + driver.getCurrentUrl());
        System.out.println("--> Page title: " + driver.getTitle());

        // If the page requires opening a sign-in form, try that first.
        By signInLocator = By.xpath(
                "//button[contains(translate(normalize-space(.)," +
                "'SIGNINLOG','signinlog'),'sign in') or " +
                "contains(translate(normalize-space(.)," +
                "'LOGIN','login'),'login')]"
        );

        try {
            List<WebElement> signInButtons =
                    driver.findElements(signInLocator);

            for (WebElement button : signInButtons) {
                if (button.isDisplayed() && button.isEnabled()
                        && driver.findElements(emailLocator).isEmpty()) {
                    clickElement(button);
                    break;
                }
            }
        } catch (Exception ex) {
            System.out.println(
                    "--> Sign-in button was not clicked: "
                    + ex.getMessage());
        }

        System.out.println("--> Matching email fields: "
                + driver.findElements(emailLocator).size());

        WebElement memberInput = wait.until(
                ExpectedConditions.elementToBeClickable(emailLocator));

        memberInput.clear();
        memberInput.sendKeys(memberId);

        By passwordLocator = By.xpath(
                "//input[@id='vaa-pw' or " +
                "@id='password' or " +
                "@type='password' or " +
                "contains(@name,'password') or " +
                "contains(@autocomplete,'current-password')]"
        );

        WebElement passwordInput = wait.until(
                ExpectedConditions.elementToBeClickable(passwordLocator));

        passwordInput.clear();
        passwordInput.sendKeys(password);

        By loginButtonLocator = By.xpath(
                "//button[@id='vaa-submit' or " +
                "@type='submit' or " +
                "contains(translate(normalize-space(.)," +
                "'LOGIN','login'),'login') or " +
                "contains(translate(normalize-space(.)," +
                "'SIGN','sign'),'sign in')]"
        );

        WebElement loginButton = wait.until(
                ExpectedConditions.elementToBeClickable(
                        loginButtonLocator));

        clickElement(loginButton);
        System.out.println("--> Login button clicked.");

        waitForChatInput();
        System.out.println("--> Chatbot input is ready.");

    } catch (Exception e) {
        try {
            File debugFile = new File("target/login_debug.html");
            File parent = debugFile.getParentFile();

            if (parent != null) {
                parent.mkdirs();
            }

            try (FileWriter writer = new FileWriter(debugFile)) {
                writer.write(driver.getPageSource());
            }

            System.err.println(
                    "--> Saved failed page source to target/login_debug.html");
        } catch (Exception debugException) {
            System.err.println(
                    "--> Could not save page source: "
                    + debugException.getMessage());
        }

        throw new AssertionError(
                "Failed to initialize chatbot test setup: "
                + e.getMessage(), e);
    }
}

    private By getChatInputLocator() {
        return By.xpath("//input | //textarea | //*[@contenteditable='true']");
    }

    private WebElement waitForChatInput() {
        return wait.until(ExpectedConditions.elementToBeClickable(getChatInputLocator()));
    }

    private void clickElement(WebElement element) {
        try {
            element.click();
        } catch (Exception e) {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("arguments[0].scrollIntoView({block:'center'});", element);
            js.executeScript("arguments[0].click();", element);
        }
    }

    private List<WebElement> getChatMessages() {
        try {
            return driver.findElements(By.xpath("//div[contains(@class,'message')] | //div[contains(@class,'bot-response')] | //div[contains(@class,'chat-bubble')]"));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String getLastChatMessageText() {
        List<WebElement> messages = getChatMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            try {
                String text = messages.get(i).getText().trim();
                if (!text.isBlank()) {
                    return text;
                }
            } catch (StaleElementReferenceException ignored) {}
        }
        return "";
    }

    private String waitForChatbotResponse(String previousResponse) {
        WebDriverWait responseWait = new WebDriverWait(driver, RESPONSE_TIMEOUT);
        return responseWait.until(d -> {
            String currentResponse = getLastChatMessageText();
            if (currentResponse.isBlank()) return null;
            if (!currentResponse.equals(previousResponse)) return currentResponse;
            return null;
        });
    }

    private String sendQuestionToChatbot(String question, String questionType, String context) {
        // Handle conversational context / question type transitions if applicable
        if (questionType != null && (questionType.equalsIgnoreCase("Initial") || questionType.equalsIgnoreCase("Direct"))) {
            lastConversationContext = ""; // Reset context for initial/direct queries
        }

        String previousResponse = getLastChatMessageText();
        WebElement chatInput = waitForChatInput();
        try {
            chatInput.click();
            chatInput.clear();
        } catch (Exception ignored) {}

        chatInput.sendKeys(question);
        chatInput.sendKeys(Keys.ENTER);
        
        String response = waitForChatbotResponse(previousResponse);
        if (response != null && !response.isBlank()) {
            lastConversationContext = response; // Store for follow-ups
        }
        return response;
    }

    private void selectRole(String targetRole) {
        if (targetRole == null || targetRole.isBlank()) return;
        try {
            By roleDropdownLocator = By.xpath("//select[contains(@id,'role') or contains(@name,'role')] | //div[contains(@class,'role-select')]");
            List<WebElement> dropdowns = driver.findElements(roleDropdownLocator);
            if (!dropdowns.isEmpty()) {
                WebElement dropdown = dropdowns.get(0);
                dropdown.click();
                WebElement option = driver.findElement(By.xpath("//option[text()='" + targetRole + "'] | //li[contains(text(),'" + targetRole + "')]"));
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

            // Step: Send Question + Answer + Context to Chatbot using Selenium + Java
            System.out.println(
            	    "--> Running test: " + rowData.testCaseId
            	    + " | Sheet: " + rowData.sheetName
            	    + " | Question: " + rowData.question
            	);
            String chatbotResponse = sendQuestionToChatbot(rowData.question, rowData.questionType, rowData.context);
            rowData.actualResult = chatbotResponse;

            if (chatbotResponse == null || chatbotResponse.isBlank()) {
                rowData.status = "FAIL";
                rowData.reason = "Chatbot returned an empty response.";
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
        File localFile = new File(FRAMEWORK_EXCEL_FILE);
        try (InputStream is = localFile.exists() 
                ? new java.io.FileInputStream(localFile) 
                : openUrlStream("https://githubusercontent.com" + FRAMEWORK_EXCEL_FILE);
             Workbook workbook = new XSSFWorkbook(is);
             FileOutputStream outputStream = new FileOutputStream(OUTPUT_EXCEL_FILE)) {
            
            for (TestRowData result : results) {
                Sheet sheet = workbook.getSheet(result.sheetName);
                if (sheet == null) continue;
                Row row = sheet.getRow(result.rowIndex);
                if (row == null) continue;

                Row headerRow = sheet.getRow(0);
                if (headerRow == null) continue;

                for (Cell cell : headerRow) {
                    String header = getCellStringValue(cell).toLowerCase().trim();
                    int colIdx = cell.getColumnIndex();
                    
                    // Case-Insensitive alignment for tracking metrics across different structural layouts
                    if (header.contains("actual result") || header.contains("chatbot answer")) {
                        Cell c = row.getCell(colIdx, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        c.setCellValue(result.actualResult);
                    } else if (header.contains("pass / fail") || header.contains("status")) {
                        Cell c = row.getCell(colIdx, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        c.setCellValue(result.status);
                    }
                }
            }
            workbook.write(outputStream);
            System.out.println("--> [SUCCESS] Runtime metrics workbook populated successfully: " + OUTPUT_EXCEL_FILE);
        } catch (Exception e) {
            System.err.println("Critical Error updating POI Excel workbook matrix: " + e.getMessage());
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
