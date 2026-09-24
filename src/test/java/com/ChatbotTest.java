package com;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.StaleElementReferenceException;
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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatbotTest {

    // =========================================================================
    // 1. DATA MODEL SUPPORTING DYNAMIC EXCEL COLUMNS
    // =========================================================================
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

    // =========================================================================
    // 2. CONFIGURATION & TIMEOUT CONSTANTS
    // =========================================================================
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(90);

    private static final String APP_URL = System.getProperty("app.url", "https://dtqponlzcij0l.cloudfront.net/");
    private static final String CREDENTIALS_EXCEL_URL = System.getProperty("credentials.excel.url", 
            "https://raw.githubusercontent.com/RameshkumarK718/Chatbot-Automation-Framework/main/credentials(2).xlsx");
    private static final String FRAMEWORK_EXCEL_URL = System.getProperty("framework.excel.url", 
            "https://raw.githubusercontent.com/RameshkumarK718/Chatbot-Automation-Framework/main/Frameworks(EFI).xlsx");

    private static final String OUTPUT_EXCEL_FILE = "Frameworks_Output.xlsx";

    private WebDriver driver;
    private WebDriverWait wait;

    // =========================================================================
    // 3. HTTP STREAM & EXCEL PARSING UTILITIES
    // =========================================================================
    @SuppressWarnings("deprecation")
    private InputStream openUrlStream(String fileUrl) throws Exception {
        URL url = new URL(fileUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(60000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setRequestProperty("Accept", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("Unable to download file. HTTP response code: " + responseCode + " | URL: " + fileUrl);
        }
        return connection.getInputStream();
    }

    private String[] getCredentialsFromExcel() {
        String username = "";
        String password = "";
        try (InputStream is = openUrlStream(CREDENTIALS_EXCEL_URL);
             Workbook workbook = new XSSFWorkbook(is)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new RuntimeException("Credentials Excel contains no sheets.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row row = sheet.getRow(1);
            if (row == null) {
                row = sheet.getRow(3);
            }
            if (row == null) {
                throw new RuntimeException("Credentials row was not found in Excel.");
            }
            username = getCellStringValue(row.getCell(0));
            password = getCellStringValue(row.getCell(1));
            System.out.println("--> Credentials loaded successfully.");
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

        try (InputStream is = openUrlStream(fileUrl);
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
                    colMap.put(getCellStringValue(cell).toLowerCase(), cell.getColumnIndex());
                }

                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;

                    String role = getCellByAnyHeader(row, colMap, "role", "set #");
                    String question = getCellByAnyHeader(row, colMap, "question / input to enter", "question");
                    String expectedResult = getCellByAnyHeader(row, colMap, "expected result", "what it tests / expected answer");
                    String runnable = getCellByAnyHeader(row, colMap, "runnable for this role today?");
                    
                    if (question.isBlank()) {
                        continue;
                    }
                    if ("no".equalsIgnoreCase(runnable)) {
                        continue;
                    }

                    String testCaseId = String.format("%s-TC%03d", sheetName.replaceAll("\\s+", ""), r);

                    dataList.add(new TestRowData(
                            sheetName, r, testCaseId, role, question, expectedResult,
                            runnable, "", "", ""
                    ));
                }
            }
            System.out.println("--> Successfully parsed " + dataList.size() + " test cases dynamically.");
        } catch (Exception e) {
            throw new RuntimeException("Error fetching Frameworks Excel: " + e.getMessage(), e);
        }
        return dataList;
    }

    private String getCellByAnyHeader(Row row, Map<String, Integer> colMap, String... possibleHeaders) {
        for (String header : possibleHeaders) {
            for (Map.Entry<String, Integer> entry : colMap.entrySet()) {
                if (entry.getKey().contains(header)) {
                    return getCellStringValue(row.getCell(entry.getValue()));
                }
            }
        }
        return "";
    }

    // =========================================================================
    // 4. TESTNG LIFECYCLE HOOKS (SETUP & TEARDOWN)
    // =========================================================================
    @BeforeMethod
    public void setUp() {
        initializeDriverAndLogin();
    }

    @AfterMethod
    public void tearDown() {
        if (driver != null) {
            try {
                driver.quit();
                System.out.println("--> WebDriver closed successfully.");
            } catch (Exception e) {
                System.err.println("--> Error closing WebDriver: " + e.getMessage());
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
<<<<<<< HEAD
        options.addArguments("--headless=new"); 
=======
        // Automatically enable headless mode if running in a CI/CD environment (like GitHub Actions)
        // Or you can uncomment the line below directly:
        options.addArguments("--headless=new"); 
        
>>>>>>> ada0b09caae208b3b8e56dec7ebec7bce73d92d6
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--remote-allow-origins=*");
        
        // Prevent CloudFront/WAF from blocking headless browser detection
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

            // Ensure document is fully loaded
            wait.until(webDriver -> ((JavascriptExecutor) webDriver).executeScript("return document.readyState").equals("complete"));

            // 1. Wait for and fill the email/member ID field
            WebElement memberInput;
            try {
<<<<<<< HEAD
                memberInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("vaa-email")));
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", memberInput);
                wait.until(ExpectedConditions.elementToBeClickable(memberInput));
=======
                memberInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("vaa-email")));
>>>>>>> ada0b09caae208b3b8e56dec7ebec7bce73d92d6
            } catch (org.openqa.selenium.TimeoutException e) {
                System.out.println("--> TIMEOUT! Current URL: " + driver.getCurrentUrl());
                System.out.println("--> TIMEOUT! Page Title: " + driver.getTitle());
                System.out.println("--> PAGE SOURCE:\n" + driver.getPageSource());
                throw e;
            }
            memberInput.clear();
            memberInput.sendKeys(memberId);
            System.out.println("--> Email entered.");

            // 2. Wait for and fill the password field
<<<<<<< HEAD
            WebElement passwordInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("vaa-pw")));
=======
            WebElement passwordInput = wait.until(ExpectedConditions.elementToBeClickable(
                By.id("vaa-pw")
            ));
>>>>>>> ada0b09caae208b3b8e56dec7ebec7bce73d92d6
            passwordInput.clear();
            passwordInput.sendKeys(password);
            System.out.println("--> Password entered.");

            // 3. Click Login
<<<<<<< HEAD
            WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("vaa-submit")));
=======
            WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(
                By.id("vaa-submit")
            ));
>>>>>>> ada0b09caae208b3b8e56dec7ebec7bce73d92d6
            clickElement(loginButton);
            System.out.println("--> Login submitted.");

            // 4. Wait for post-login view
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.visibilityOfElementLocated(By.id("vaa-portal")),
                    ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(normalize-space(), 'Conversational AI')]"))
            ));

            System.out.println("--> Post-login page loaded.");

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
        }
    }
    // =========================================================================
    // 5. CHATBOT INTERACTION & LOCATOR METHODS
    // =========================================================================
    private By getChatInputLocator() {
        return By.xpath("//input[@placeholder='Ask a question...'] | //textarea[@placeholder='Ask a question...'] | //input[contains(@placeholder,'Ask')] | //textarea[contains(@placeholder,'Ask')] | //div[@contenteditable='true']");
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

    // =========================================================================
    // 6. MAIN TEST EXECUTION METHOD
    // =========================================================================
    @Test
    public void runAutomationFramework() {
        List<TestRowData> testDataList = fetchExcelDataFromGitHub(FRAMEWORK_EXCEL_URL);
        Assert.assertFalse(testDataList.isEmpty(), "Failed to fetch Excel data or file is empty.");
        
        System.out.println("============================================================");
        System.out.println("Executing " + testDataList.size() + " test cases against target Chatbot.");
        System.out.println("============================================================");
        
        List<TestRowData> executedResults = new ArrayList<>();
        int failedCount = 0;

        for (int i = 0; i < testDataList.size(); i++) {
            TestRowData rowData = testDataList.get(i);
            try {
                String chatbotResponse = sendQuestion(rowData.question);
                rowData.actualResult = chatbotResponse;
                
                if (chatbotResponse == null || chatbotResponse.isBlank()) {
                    rowData.status = "FAIL";
                    rowData.reason = "Chatbot returned an empty response.";
                    failedCount++;
                } else {
                    rowData.status = "PASS";
                    rowData.reason = "Success. Response received.";
                }
            } catch (Exception e) {
                rowData.actualResult = "EXCEPTION: " + e.getMessage();
                rowData.status = "FAIL";
                rowData.reason = e.getMessage();
                failedCount++;
            }
            executedResults.add(rowData);
        }
        
        updateFrameworkExcel(executedResults);

        if (failedCount > 0) {
            System.out.println("--> Warning: Test suite completed with " + failedCount + " failing test case(s). Results written to " + OUTPUT_EXCEL_FILE);
        }
    }

    // =========================================================================
    // 7. EXCEL RESULT WRITER (OUTPUT GENERATION)
    // =========================================================================
    private void updateFrameworkExcel(List<TestRowData> results) {
        try (InputStream is = openUrlStream(FRAMEWORK_EXCEL_URL);
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
            System.out.println("--> Execution results safely written dynamically to output file.");
        } catch (Exception e) {
            throw new RuntimeException("Failed to update Excel: " + e.getMessage(), e);
        }
    }
<<<<<<< HEAD
}
=======
}
>>>>>>> ada0b09caae208b3b8e56dec7ebec7bce73d92d6
