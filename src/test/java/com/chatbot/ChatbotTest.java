package com.chatbot.tests;
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

    // ==================== TEST DATA MODEL ====================

    // FLEXIBLE TEST DATA MODEL SUPPORTING DYNAMIC COLUMNS
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

    // ==================== CONFIGURATIONS & VARIABLES ====================

    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(60);

    // Dynamic configuration via system properties or defaults
    private static final String APP_URL = System.getProperty("app.url", "https://dtqponlzcij0l.cloudfront.net/");
    private static final String CREDENTIALS_EXCEL_URL = System.getProperty("credentials.excel.url", 
            "https://raw.githubusercontent.com/RameshkumarK718/Chatbot-Automation-Framework/main/credentials(2).xlsx");
    private static final String FRAMEWORK_EXCEL_URL = System.getProperty("framework.excel.url", 
            "https://raw.githubusercontent.com/RameshkumarK718/Chatbot-Automation-Framework/main/Frameworks(EFI).xlsx");

    private static final String OUTPUT_EXCEL_FILE = "Frameworks_Output.xlsx";

    private WebDriver driver;
    private WebDriverWait wait;

    // ==================== EXCEL UTILITIES & DATA FETCHING ====================

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

    private java.util.List<String> getRolesFromExcel() {

        java.util.List<String> roles = new java.util.ArrayList<>();



        try (InputStream is = openUrlStream(CREDENTIALS_EXCEL_URL);

             Workbook workbook = new XSSFWorkbook(is)) {



            if (workbook.getNumberOfSheets() == 0) {

                throw new RuntimeException("Role Excel contains no sheets.");

            }



            Sheet sheet = workbook.getSheetAt(0);



            // Row 1 is the header.

            // Roles are stored in Column A from Row 2 onward.

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {

                Row row = sheet.getRow(r);



                if (row == null) {

                    continue;

                }



                String role = getCellStringValue(row.getCell(0)).trim();



                if (!role.isBlank()) {

                    roles.add(role);

                }

            }



            System.out.println("--> Roles loaded successfully: " + roles);



        } catch (Exception e) {

            throw new RuntimeException(

                    "Error reading role Excel: " + e.getMessage(), e

            );

        }



        return roles;

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

    // DYNAMIC EXCEL DATA FETCHER (Reads headers automatically)
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

                    // Dynamically locate columns based on common names or header variants
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

                    // Find index for Actual result, Pass/Fail columns to write back later
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

    // ==================== TEST SETUP & TEARDOWN ====================

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

    // ==================== DRIVER INITIALIZATION & LOGIN ====================

    private void initializeDriverAndLogin() {



        java.util.List<String> configuredRoles = getRolesFromExcel();



        if (configuredRoles.isEmpty()) {

            throw new IllegalStateException(

                    "No roles found in credentials Excel."

            );

        }



        ChromeOptions options = new ChromeOptions();



        options.addArguments("--headless=new");

        options.addArguments("--no-sandbox");

        options.addArguments("--disable-dev-shm-usage");

        options.addArguments("--disable-gpu");

        options.addArguments("--window-size=1920,1080");

        options.addArguments("--remote-allow-origins=*");



        driver = new ChromeDriver(options);



        driver.manage().timeouts().implicitlyWait(Duration.ZERO);

        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));



        wait = new WebDriverWait(driver, WAIT_TIMEOUT);



        String role = configuredRoles.get(0).trim();



        System.out.println("--> Selected role from Excel: " + role);

        System.out.println("--> Opening application URL: " + APP_URL);



        driver.get(APP_URL);



        String roleKey = role

                .replaceAll("////s+", " ")

                .trim()

                .toLowerCase();



        By roleButtonLocator = By.xpath(

                "//button[" +

                "translate(normalize-space(.)," +

                "'ABCDEFGHIJKLMNOPQRSTUVWXYZ'," +

                "'abcdefghijklmnopqrstuvwxyz')" +

                "='" + roleKey + "'" +

                "]"

        );



        WebElement roleButton = wait.until(

                ExpectedConditions.elementToBeClickable(roleButtonLocator)

        );



        clickElement(roleButton);



        System.out.println("--> Role selected: " + role);



        // EFI requires a name after selecting the role.

        By nameInputLocator = By.cssSelector(

                "input[placeholder='Your name']"

        );



        WebElement nameInput = wait.until(

                ExpectedConditions.elementToBeClickable(nameInputLocator)

        );



        String testUserName = System.getProperty(

                "efi.user.name",

                System.getenv().getOrDefault(

                        "EFI_USER_NAME",

                        System.getProperty("user.name", "Automation User")

                )

        );



        if (testUserName == null || testUserName.isBlank()) {

            throw new IllegalStateException(

                    "EFI user name is empty."

            );

        }



        nameInput.clear();

        nameInput.sendKeys(testUserName);



        System.out.println(

                "--> Name entered for EFI session."

        );



        By continueButtonLocator = By.xpath(

                "//button[@type='submit' and normalize-space()='Continue']"

        );



        WebElement continueButton = wait.until(

                ExpectedConditions.elementToBeClickable(

                        continueButtonLocator

                )

        );



        clickElement(continueButton);



        System.out.println(

                "--> Continue clicked."

        );



        waitForChatInput();



        System.out.println(

                "--> Chatbot input is ready."

        );



        System.out.println("--> Chatbot input is ready.");



    }



    // ==================== CHATBOT INTERACTION HELPERS ====================

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

    // ==================== TEST EXECUTION & REPORTING ====================

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
                } else if (chatbotResponse.toLowerCase().contains("error")) {
                    rowData.status = "FAIL";
                    rowData.reason = "Chatbot returned an error message.";
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
            Assert.fail("Test suite completed with " + failedCount + " failing test case(s). Check Frameworks_Output.xlsx for details.");
        }
    }

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

                // Find 'Actual result' and 'Pass / Fail' columns dynamically by header
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
}
