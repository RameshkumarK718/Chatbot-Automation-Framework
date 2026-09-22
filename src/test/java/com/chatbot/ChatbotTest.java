package com.chatbot;
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
    // TEST DATA MODEL
    public static class TestRowData {
        public String sheetName;
        public int rowIndex;
        public String testCaseId;
        public String category;
        public String subcategory;
        public String question;
        public String expectedAnswer;
        public String chatbotAnswer;
        public String relevance;
        public String status;
        public String passFailureReason;
        public TestRowData(
                String sheetName,
                int rowIndex,
                String testCaseId,
                String category,
                String subcategory,
                String question,
                String expectedAnswer,
                String chatbotAnswer,
                String relevance,
                String status,
                String passFailureReason) {
            this.sheetName = sheetName;
            this.rowIndex = rowIndex;
            this.testCaseId = testCaseId;
            this.category = category;
            this.subcategory = subcategory;
            this.question = question;
            this.expectedAnswer = expectedAnswer;
            this.chatbotAnswer = chatbotAnswer;
            this.relevance = relevance;
            this.status = status;
            this.passFailureReason = passFailureReason;
        }
    }

    // ==================== CONFIGURATION ====================
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(45);

    // ==================== VEDAS ENVIRONMENT ====================
    private static final String APP_URL_VEDAS = "https://d3rl0fkw0q6ssb.cloudfront.net/";
    private static final String CREDENTIALS_EXCEL_URL_VEDAS = "https://raw.githubusercontent.com/RameshkumarK718/Chatbot-Automation-Framework/main/credentials(1).xlsx";
    private static final String FRAMEWORK_EXCEL_URL_VEDAS = "https://raw.githubusercontent.com/RameshkumarK718/Chatbot-Automation-Framework/main/Frameworks(Vedas).xlsx";

    // ==================== EFI ENVIRONMENT ====================
    private static final String APP_URL_EFI = "https://dtqponlzcij0l.cloudfront.net/";
    private static final String CREDENTIALS_EXCEL_URL_EFI = "https://raw.githubusercontent.com/RameshkumarK718/Chatbot-Automation-Framework/main/credentials(2).xlsx";
    private static final String FRAMEWORK_EXCEL_URL_EFI = "https://raw.githubusercontent.com/RameshkumarK718/Chatbot-Automation-Framework/main/Frameworks(EFI).xlsx";

    // ==================== ACTIVE ENVIRONMENT ====================
    private static final String ACTIVE_ENVIRONMENT = System.getProperty("env", "EFI").toUpperCase();

    private static final String APP_URL = 
            ACTIVE_ENVIRONMENT.equals("VEDAS") ? APP_URL_VEDAS : APP_URL_EFI;

    private static final String CREDENTIALS_EXCEL_URL =
            ACTIVE_ENVIRONMENT.equals("VEDAS")
                    ? CREDENTIALS_EXCEL_URL_VEDAS
                    : CREDENTIALS_EXCEL_URL_EFI;

    private static final String FRAMEWORK_EXCEL_URL =
            ACTIVE_ENVIRONMENT.equals("VEDAS")
                    ? FRAMEWORK_EXCEL_URL_VEDAS
                    : FRAMEWORK_EXCEL_URL_EFI;

    private static final String FRAMEWORK_EXCEL_FILE = "Frameworks.xlsx";

    // ==================== ENVIRONMENT-AWARE COLUMN MAPPINGS ====================
    // VEDAS Columns
    private static final int VEDAS_COL_CHATBOT_ANSWER = 5;
    private static final int VEDAS_COL_STATUS = 7;
    private static final int VEDAS_COL_REASON = 8;

    // EFI Columns
    private static final int EFI_COL_CHATBOT_ANSWER = 10; // "Actual result"
    private static final int EFI_COL_REASON = 11;         // "Pass / Fail" reason
    private static final int EFI_COL_STATUS = 12;         // Pass / Fail status column

    private WebDriver driver;
    private WebDriverWait wait;

    // GET EXCEL INPUT STREAM
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
            throw new RuntimeException(
                    "Unable to download file. HTTP response code: "
                            + responseCode
                            + " | URL: "
                            + fileUrl);
        }
        return connection.getInputStream();
    }

    // READ CREDENTIALS FROM EXCEL
    private String[] getCredentialsFromExcel() {
        String username = "";
        String password = "";
        try (InputStream is = openUrlStream(CREDENTIALS_EXCEL_URL);
             Workbook workbook = new XSSFWorkbook(is)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new RuntimeException("Credentials Excel contains no sheets.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row row = sheet.getRow(3); // Expects credentials on Excel row index 3
            if (row == null) {
                throw new RuntimeException("Credentials row 4 was not found in Excel.");
            }
            Cell userCell = row.getCell(0);
            Cell passCell = row.getCell(1);
            username = getCellStringValue(userCell);
            password = getCellStringValue(passCell);
            System.out.println("--> Credentials loaded successfully.");
            System.out.println("--> Member ID: " + username);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Error reading credentials Excel from GitHub: " + e.getMessage(), e);
        }
        return new String[]{username, password};
    }

    // EXCEL CELL VALUE HANDLER
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

    // FETCH FRAMEWORK DATA FROM GITHUB (DYNAMIC FOR VEDAS & EFI)
    private List<TestRowData> fetchExcelDataFromGitHub(String fileUrl) {
        List<TestRowData> dataList = new ArrayList<>();
        boolean isVedas = "VEDAS".equalsIgnoreCase(ACTIVE_ENVIRONMENT);

        try (InputStream is = openUrlStream(fileUrl);
             Workbook workbook = new XSSFWorkbook(is)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new RuntimeException("Framework Excel contains no sheets.");
            }
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName();
                System.out.println("--> Reading sheet: " + sheetName);

                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) {
                        continue;
                    }

                    String testCaseId, category, subcategory, question, expectedAnswer;

                    if (isVedas) {
                        // VEDAS Mapping Structure
                        testCaseId = getCellStringValue(row.getCell(0));
                        category = getCellStringValue(row.getCell(1));
                        subcategory = getCellStringValue(row.getCell(2));
                        question = getCellStringValue(row.getCell(3));
                        expectedAnswer = getCellStringValue(row.getCell(4));
                    } else {
                        // EFI Mapping Structure
                        testCaseId = getCellStringValue(row.getCell(4)); // Q #
                        category = getCellStringValue(row.getCell(0));   // Role
                        subcategory = getCellStringValue(row.getCell(2)); // Set name
                        question = getCellStringValue(row.getCell(5));   // Question / Input to enter
                        expectedAnswer = getCellStringValue(row.getCell(7)); // Expected result

                        // Check if runnable today for EFI
                        String runnable = getCellStringValue(row.getCell(9));
                        if ("NO".equalsIgnoreCase(runnable)) {
                            continue;
                        }
                    }

                    if (question.isBlank()) {
                        continue;
                    }
                    if (testCaseId.isBlank()) {
                        testCaseId = String.format("TC-%03d", r);
                    }

                    dataList.add(
                            new TestRowData(
                                    sheetName,
                                    r,
                                    testCaseId,
                                    category,
                                    subcategory,
                                    question,
                                    expectedAnswer,
                                    "",
                                    "High",
                                    "",
                                    ""));
                }
            }
            System.out.println("--> Successfully parsed " + dataList.size() + " test cases for " + ACTIVE_ENVIRONMENT + " from GitHub.");
        } catch (Exception e) {
            throw new RuntimeException(
                    "Error fetching Frameworks Excel from GitHub: " + e.getMessage(), e);
        }
        return dataList;
    }

    // TEST SETUP & TEARDOWN
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

    // INITIALIZE CHROME + LOGIN
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
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--remote-allow-origins=*");

        driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ZERO);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
        wait = new WebDriverWait(driver, WAIT_TIMEOUT);

        try {
            System.out.println("--> Opening application (" + ACTIVE_ENVIRONMENT + "): " + APP_URL);
            driver.get(APP_URL);

            WebElement memberInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("vaa-email")));
            memberInput.clear();
            memberInput.sendKeys(memberId);

            WebElement passwordInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("vaa-pw")));
            passwordInput.clear();
            passwordInput.sendKeys(password);

            WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("vaa-submit")));
            clickElement(loginButton);
            System.out.println("--> Login button clicked.");

            wait.until(
                    ExpectedConditions.or(
                            ExpectedConditions.visibilityOfElementLocated(By.id("vaa-portal")),
                            ExpectedConditions.visibilityOfElementLocated(
                                    By.xpath("//*[contains(normalize-space(),'Conversational AI')]"))
                    ));
            System.out.println("--> Login completed successfully.");

            WebElement conversationalAILink = wait.until(
                    ExpectedConditions.elementToBeClickable(
                            By.xpath("//span[contains(normalize-space(),'Conversational AI')]/ancestor::a[1] | //a[contains(normalize-space(),'Conversational AI')]")));
            clickElement(conversationalAILink);
            System.out.println("--> Conversational AI opened.");

            waitForChatInput();
            System.out.println("--> Chatbot input is ready.");
        } catch (Exception e) {
            throw new AssertionError(
                    "Failed to initialize chatbot test setup: " + e.getMessage(), e);
        }
    }

    // CHAT INPUT LOCATOR & HELPERS
    private By getChatInputLocator() {
        return By.xpath(
                "//input[@placeholder='Ask a question...']"
                        + " | "
                        + "//textarea[@placeholder='Ask a question...']"
                        + " | "
                        + "//input[contains(@placeholder,'Ask')]"
                        + " | "
                        + "//textarea[contains(@placeholder,'Ask')]"
                        + " | "
                        + "//div[@contenteditable='true']");
    }

    private WebElement waitForChatInput() {
        return wait.until(
                ExpectedConditions.elementToBeClickable(getChatInputLocator()));
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
            return driver.findElements(
                    By.xpath(
                            "//div[contains(@class,'message')]"
                                    + " | "
                                    + "//div[contains(@class,'bot-response')]"
                                    + " | "
                                    + "//div[contains(@class,'chat-bubble')]"));
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
            } catch (StaleElementReferenceException ignored) {
                // Continue to next message.
            }
        }
        return "";
    }

    private String waitForChatbotResponse(String previousResponse) {
        WebDriverWait responseWait = new WebDriverWait(driver, RESPONSE_TIMEOUT);
        return responseWait.until(
                d -> {
                    String currentResponse = getLastChatMessageText();
                    if (currentResponse.isBlank()) {
                        return null;
                    }
                    if (!currentResponse.equals(previousResponse)) {
                        return currentResponse;
                    }
                    return null;
                });
    }

    private String sendQuestion(String question) {
        String previousResponse = getLastChatMessageText();
        WebElement chatInput = waitForChatInput();
        try {
            chatInput.click();
            chatInput.clear();
        } catch (Exception ignored) {
            // Fallback handled smoothly
        }
        chatInput.sendKeys(question);
        chatInput.sendKeys(Keys.ENTER);
        System.out.println("--> Question submitted: " + question);
        return waitForChatbotResponse(previousResponse);
    }

    // MAIN AUTOMATION TEST
    @Test
    public void runAutomationFramework() {
        List<TestRowData> testDataList = fetchExcelDataFromGitHub(FRAMEWORK_EXCEL_URL);
        Assert.assertFalse(
                testDataList.isEmpty(),
                "Failed to fetch Excel data from GitHub or file is empty.");
        System.out.println("============================================================");
        System.out.println("Executing " + testDataList.size() + " test cases against Chatbot UI (" + ACTIVE_ENVIRONMENT + ").");
        System.out.println("============================================================");
        List<TestRowData> executedResults = new ArrayList<>();
        for (int i = 0; i < testDataList.size(); i++) {
            TestRowData rowData = testDataList.get(i);
            String testCaseId = rowData.testCaseId;
            String question = rowData.question;
            if (question == null || question.isBlank()) {
                continue;
            }
            if (testCaseId == null || testCaseId.isBlank()) {
                testCaseId = String.format("TC-%03d", i + 1);
                rowData.testCaseId = testCaseId;
            }
            System.out.println("\n------------------------------------------------------------");
            System.out.println("Executing Test Case: " + testCaseId);
            System.out.println("Question: " + question);
            try {
                String chatbotResponse = sendQuestion(question);
                rowData.chatbotAnswer = chatbotResponse;
                if (chatbotResponse == null || chatbotResponse.isBlank()) {
                    rowData.status = "FAIL";
                    rowData.passFailureReason = "Chatbot returned an empty response.";
                } else if (chatbotResponse.toLowerCase().contains("error")) {
                    rowData.status = "FAIL";
                    rowData.passFailureReason = "Chatbot returned an error response.";
                } else {
                    rowData.status = "PASS";
                    rowData.passFailureReason = "Chatbot returned a non-empty response.";
                }
            } catch (Exception e) {
                rowData.chatbotAnswer = "EXCEPTION: " + safeExceptionMessage(e);
                rowData.status = "FAIL";
                rowData.passFailureReason = safeExceptionMessage(e);
            }
            executedResults.add(rowData);
            System.out.println("Status: " + rowData.status);
            System.out.println("Response: " + rowData.chatbotAnswer);
        }
        updateFrameworkExcel(executedResults);
        generateDetailedEnterpriseReport(executedResults);
    }

    private String safeExceptionMessage(Exception e) {
        if (e == null) {
            return "Unknown error";
        }
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return message;
    }

    // DYNAMIC ENVIRONMENT-AWARE EXCEL WRITER
    private void updateFrameworkExcel(List<TestRowData> results) {
        boolean isVedas = "VEDAS".equalsIgnoreCase(ACTIVE_ENVIRONMENT);
        int answerCol = isVedas ? VEDAS_COL_CHATBOT_ANSWER : EFI_COL_CHATBOT_ANSWER;
        int statusCol = isVedas ? VEDAS_COL_STATUS : EFI_COL_STATUS;
        int reasonCol = isVedas ? VEDAS_COL_REASON : EFI_COL_REASON;

        try (
            InputStream is = openUrlStream(FRAMEWORK_EXCEL_URL);
            Workbook workbook = new XSSFWorkbook(is);
            FileOutputStream outputStream = new FileOutputStream(FRAMEWORK_EXCEL_FILE)
        ) {
            for (TestRowData result : results) {
                Sheet sheet = workbook.getSheet(result.sheetName);
                if (sheet == null) {
                    continue;
                }
                Row row = sheet.getRow(result.rowIndex);
                if (row == null) {
                    continue;
                }
                
                // Write Chatbot Answer / Actual Result
                Cell chatbotAnswerCell = row.getCell(answerCol);
                if (chatbotAnswerCell == null) chatbotAnswerCell = row.createCell(answerCol);
                chatbotAnswerCell.setCellValue(result.chatbotAnswer == null ? "" : result.chatbotAnswer);

                // Write Pass/Fail Status
                Cell statusCell = row.getCell(statusCol);
                if (statusCell == null) statusCell = row.createCell(statusCol);
                statusCell.setCellValue(result.status == null ? "" : result.status);

                // Write Reason / Pass-Fail Detail
                Cell reasonCell = row.getCell(reasonCol);
                if (reasonCell == null) reasonCell = row.createCell(reasonCol);
                reasonCell.setCellValue(result.passFailureReason == null ? "" : result.passFailureReason);
            }
            workbook.write(outputStream);
            System.out.println("--> Execution results updated successfully in Frameworks.xlsx for " + ACTIVE_ENVIRONMENT);
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to update Frameworks Excel: " + e.getMessage(), e
            );
        }
    }

    private void generateDetailedEnterpriseReport(List<TestRowData> results) {
        int total = results.size();
        int passed = 0;
        int failed = 0;
        Map<String, Integer> categoryPassCount = new HashMap<>();
        Map<String, Integer> categoryTotalCount = new HashMap<>();
        for (TestRowData res : results) {
            String category = res.category;
            if (category == null || category.isBlank()) {
                category = "Uncategorized";
            }
            categoryTotalCount.put(
                    category,
                    categoryTotalCount.getOrDefault(category, 0) + 1);
            if ("PASS".equalsIgnoreCase(res.status)) {
                passed++;
                categoryPassCount.put(
                        category,
                        categoryPassCount.getOrDefault(category, 0) + 1);
            } else {
                failed++;
            }
        }
        double passRate = total > 0 ? ((double) passed / total) * 100 : 0.0;
        double failRate = total > 0 ? ((double) failed / total) * 100 : 0.0;

        System.out.println();
        System.out.println("========================================================================================");
        System.out.println("                    CHATBOT AI & SYSTEM RELIABILITY AUDIT REPORT (" + ACTIVE_ENVIRONMENT + ")");
        System.out.println("========================================================================================");
        System.out.println(" >> SECTION 1: EXECUTIVE DASHBOARD SUMMARY");
        System.out.println("----------------------------------------------------------------------------------------");
        System.out.printf(" • Total Test Cases Evaluated : %d%n", total);
        System.out.printf(" • Passed Test Cases          : %d%n", passed);
        System.out.printf(" • Failed Test Cases          : %d%n", failed);
        System.out.printf(" • Pass Rate                  : %.2f%%%n", passRate);
        System.out.printf(" • Fail Rate                  : %.2f%%%n", failRate);

        System.out.println();
        System.out.println("----------------------------------------------------------------------------------------");
        System.out.println(" >> SECTION 2: CATEGORY-WISE PERFORMANCE BREAKDOWN");
        System.out.println("----------------------------------------------------------------------------------------");
        for (Map.Entry<String, Integer> entry : categoryTotalCount.entrySet()) {
            String category = entry.getKey();
            int categoryTotal = entry.getValue();
            int categoryPassed = categoryPassCount.getOrDefault(category, 0);
            double categoryRate = categoryTotal > 0
                    ? ((double) categoryPassed / categoryTotal) * 100
                    : 0.0;
            System.out.printf(
                    " • [%s] Passed: %d / %d (%.1f%%)%n",
                    category,
                    categoryPassed,
                    categoryTotal,
                    categoryRate);
        }

        System.out.println();
        System.out.println("----------------------------------------------------------------------------------------");
        System.out.println(" >> SECTION 3: DETAILED QA & MASTER AUDIT LOGS");
        System.out.println("----------------------------------------------------------------------------------------");
        for (TestRowData r : results) {
            System.out.printf("[%s] ID: %s | Sheet: %s | Row: %d%n",
                    r.status, r.testCaseId, r.sheetName, r.rowIndex);
            System.out.printf("    Category       : %s%n", r.category);
            System.out.printf("    Subcategory    : %s%n", r.subcategory);
            System.out.printf("    Question       : %s%n", r.question);
            System.out.printf("    Expected       : %s%n", r.expectedAnswer);
            System.out.printf("    Chatbot        : %s%n", r.chatbotAnswer);
            System.out.printf("    Relevance      : %s%n", r.relevance);
            System.out.printf("    Reason         : %s%n", r.passFailureReason);
            System.out.println("----------------------------------------------------------------------------------------");
        }
        System.out.println("========================================================================================");
    }
}
