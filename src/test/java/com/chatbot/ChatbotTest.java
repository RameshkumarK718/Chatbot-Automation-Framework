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

public class ChatbotTest {

    public static class TestRowData {
        public String sheetName;
        public int rowIndex;
        public String testCaseId;
        public String role;
        public String question;
        public String expectedResult;
        public String runnable;
        public String actualResult;
        public String reason;
        public String status;

        public TestRowData(String sheetName, int rowIndex, String testCaseId, String role,
                            String question, String expectedResult, String runnable,
                            String actualResult, String reason, String status) {
            this.sheetName = sheetName;
            this.rowIndex = rowIndex;
            this.testCaseId = testCaseId;
            this.role = role;
            this.question = question;
            this.expectedResult = expectedResult;
            this.runnable = runnable;
            this.actualResult = actualResult;
            this.reason = reason;
            this.status = status;
        }
    }
3
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(45);

    private static final String APP_URL = System.getProperty("app.url", "https://dtqponlzcij0l.cloudfront.net/");
    private static final String CREDENTIALS_EXCEL_URL = System.getProperty("credentials.excel.url",
            "https://raw.githubusercontent.com/RameshkumarK718/Chatbot-Automation-Framework/main/credentials(2).xlsx");
    private static final String FRAMEWORK_EXCEL_URL = System.getProperty("framework.excel.url",
            "https://raw.githubusercontent.com/RameshkumarK718/Chatbot-Automation-Framework/main/Frameworks(EFI).xlsx");
    private static final String RESULT_EXCEL_URL = System.getProperty("result.excel.url",
            "https://raw.githubusercontent.com/RameshkumarK718/Chatbot-Automation-Framework/main/Frameworks-Result(EFI).xlsx");
    private static final String OUTPUT_EXCEL_FILE = "Frameworks-Result(EFI)_Output.xlsx";
    private static final String OUTPUT_JSON_FILE = "target/test_results.json";
    
    private WebDriver driver;
    private WebDriverWait wait;
    private final List<TestRowData> executedResults = new ArrayList<>();

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
        
        File localFile = new File("credentials(2).xlsx");
        InputStream is = null;
        try {
            if (localFile.exists()) {
                is = new java.io.FileInputStream(localFile);
                System.out.println("--> Loading credentials from local file.");
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
                System.out.println("--> Credentials loaded successfully: " + username);
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

    private List<TestRowData> fetchExcelDataFromGitHub(String fileUrl) {
        List<TestRowData> dataList = new ArrayList<>();
        File localFile = new File("Frameworks(EFI).xlsx");
        
        try (InputStream is = localFile.exists() 
                ? new java.io.FileInputStream(localFile) 
                : openUrlStream(fileUrl);
             Workbook workbook = new XSSFWorkbook(is)) {

            if (workbook.getNumberOfSheets() == 0) {
                throw new RuntimeException("Framework Excel contains no sheets.");
            }

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName();
                System.out.println("--> Reading sheet: " + sheetName);

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
                    String question = getCellByExactOrPartialHeader(row, colMap, "question / input to enter", "question");
                    String expectedResult = getCellByExactOrPartialHeader(row, colMap, "expected result", "what it tests / expected answer");

                    if (question.isBlank()) {
                        continue;
                    }

                    String testCaseId = String.format("%s-TC%03d", sheetName.replaceAll("\\s+", ""), r);
                    dataList.add(new TestRowData(
                            sheetName, r, testCaseId, role, question, expectedResult,
                            "", "", "", ""
                    ));
                }
            }
            System.out.println("--> Successfully parsed " + dataList.size() + " test cases across all sheets.");
        } catch (Exception e) {
            throw new RuntimeException("Error fetching Frameworks Excel: " + e.getMessage(), e);
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
        if (result.getStatus() == ITestResult.FAILURE && driver != null) {
            String testName = result.getName() + "_" + System.currentTimeMillis();
            try {
                File htmlFile = new File("target/diagnostics/html/" + testName + ".html");
                htmlFile.getParentFile().mkdirs();
                try (FileWriter writer = new FileWriter(htmlFile)) {
                    writer.write(driver.getPageSource());
                }

                File logFile = new File("target/diagnostics/logs/" + testName + ".log");
                logFile.getParentFile().mkdirs();
                try (FileWriter writer = new FileWriter(logFile)) {
                    driver.manage().logs().get(LogType.BROWSER).forEach(entry -> {
                        try {
                            writer.write(entry.getMessage() + "\n");
                        } catch (Exception ignored) {}
                    });
                }
            } catch (Exception e) {
                System.err.println("--> Failed to write failure diagnostics: " + e.getMessage());
            }
        }
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
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);

        driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ZERO);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(90));
        wait = new WebDriverWait(driver, WAIT_TIMEOUT);

        try {
            System.out.println("--> Opening application URL: " + APP_URL);
            driver.get(APP_URL);

            // Wait for page ready state
            wait.until(webDriver -> ((JavascriptExecutor) webDriver).executeScript("return document.readyState").equals("complete"));

            // STEP 1: Handle potential landing page sign-in buttons or modals
            try {
                WebElement signinBtn = driver.findElement(By.xpath("//button[contains(translate(text(),'SIGN','sign'),'sign') or contains(translate(text(),'LOGIN','login'),'login') or contains(@class,'login')]"));
                if (signinBtn.isDisplayed()) {
                    System.out.println("--> Clicking landing page sign-in button...");
                    clickElement(signinBtn);
                    Thread.sleep(1500); // Brief wait for form expansion
                }
            } catch (Exception ignored) {
                // No extra button needed, form is assumed to be visible
            }

            // STEP 2: Find and fill Email / Username field using multi-fallback XPath
            WebElement memberInput = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//input[@id='vaa-email' or @id='email' or @id='username' or @type='email' or contains(@name,'email') or contains(@name,'user') or contains(@placeholder,'email')]")
            ));
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", memberInput);
            memberInput.clear();
            memberInput.sendKeys(memberId);
            System.out.println("--> Email entered.");

            // STEP 3: Find and fill Password field
            WebElement passwordInput = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//input[@id='vaa-pw' or @id='password' or @type='password' or contains(@name,'password') or contains(@placeholder,'password')]")
            ));
            passwordInput.clear();
            passwordInput.sendKeys(password);
            System.out.println("--> Password entered.");

            // STEP 4: Submit Login
            WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[@id='vaa-submit' or @type='submit' or contains(translate(text(),'LOGIN','login'),'login') or contains(translate(text(),'SIGN','sign'),'sign')]")
            ));
            clickElement(loginButton);
            System.out.println("--> Login submitted.");

            // STEP 5: Wait for Conversational AI navigation link / chat interface
            By conversationalAILocator = By.xpath(
                "//span[contains(normalize-space(),'Conversational AI')]/ancestor::a[1] | //a[contains(normalize-space(),'Conversational AI')] | //*[contains(normalize-space(), 'Conversational AI')]"
            );
            WebElement conversationalAI = wait.until(ExpectedConditions.elementToBeClickable(conversationalAILocator));
            clickElement(conversationalAI);
            System.out.println("--> Conversational AI clicked.");

            waitForChatInput();
            System.out.println("--> Chatbot input is ready.");

        } catch (Exception e) {
            System.out.println("--> CRITICAL FAILURE URL: " + driver.getCurrentUrl());
            System.out.println("--> CRITICAL FAILURE TITLE: " + driver.getTitle());
            throw new AssertionError("Failed to initialize chatbot test setup: " + e.getMessage(), e);
        }}

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

    private String sendQuestion(String question) {
        String previousResponse = getLastChatMessageText();
        WebElement chatInput = waitForChatInput();
        try {
            chatInput.click();
            chatInput.clear();
        } catch (Exception ignored) {}
        chatInput.sendKeys(question);
        chatInput.sendKeys(Keys.ENTER);
        return waitForChatbotResponse(previousResponse);
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
        List<TestRowData> dataList = fetchExcelDataFromGitHub(FRAMEWORK_EXCEL_URL);
        // Make sure there is NO subList or limit here!
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
                System.out.println("--> Driver session lost. Re-initializing session for " + rowData.testCaseId);
                initializeDriverAndLogin();
            } else {
                waitForChatInput();
            }
            selectRole(rowData.role);

            String chatbotResponse = sendQuestion(rowData.question);
            rowData.actualResult = chatbotResponse;

            if (chatbotResponse == null || chatbotResponse.isBlank()) {
                rowData.status = "FAIL";
                rowData.reason = "Chatbot returned an empty response.";
            } else {
                rowData.status = "PASS";
                rowData.reason = "Success. Response received.";
            }
        } catch (Exception e) {
            rowData.actualResult = "EXCEPTION: " + e.getMessage();
            rowData.status = "FAIL";
            rowData.reason = e.getMessage();
            
            if (e.getMessage() != null && (e.getMessage().contains("invalid session id") || e.getMessage().contains("Session ID is null"))) {
                driver = null; 
            }
        }

        synchronized (executedResults) {
            executedResults.add(rowData);
        }

        Assert.assertEquals(rowData.status, "PASS", "Test failed for TC " + rowData.testCaseId + ": " + rowData.reason);
    }
    
    private void updateFrameworkExcel(List<TestRowData> results) {
        try (InputStream is = openUrlStream(RESULT_EXCEL_URL);
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
                    String header = getCellStringValue(cell).toLowerCase();
                    int colIdx = cell.getColumnIndex();
                    if (header.contains("actual result")) {
                        Cell c = row.getCell(colIdx);
                        if (c == null) c = row.createCell(colIdx);
                        c.setCellValue(result.actualResult);
                    } else if (header.contains("pass / fail") || header.contains("status")) {
                        Cell c = row.getCell(colIdx);
                        if (c == null) c = row.createCell(colIdx);
                        c.setCellValue(result.status);
                    }
                }
            }
            workbook.write(outputStream);
            System.out.println("--> Execution results written to " + OUTPUT_EXCEL_FILE);
        } catch (Exception e) {
            System.err.println("Failed to update Excel: " + e.getMessage());
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
                            "    \"question\": \"%s\",\n" +
                            "    \"expectedResult\": \"%s\",\n" +
                            "    \"actualResult\": \"%s\",\n" +
                            "    \"status\": \"%s\"\n" +
                            "  }%s\n",
                            escapeJson(r.testCaseId),
                            escapeJson(r.role),
                            escapeJson(r.question),
                            escapeJson(r.expectedResult),
                            escapeJson(r.actualResult),
                            escapeJson(r.status),
                            (i < results.size() - 1) ? "," : ""
                    ));
                }
                writer.write("]\n");
            }
            System.out.println("--> Execution contract written to " + file.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Failed to export JSON results: " + e.getMessage());
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
