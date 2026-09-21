package com.chatbot.tests;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
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
import java.util.List;

@SuppressWarnings("unused")
public class Frame {
    // TEST DATA MODEL
    public static class TestRowData {

        public String sheetName;
        public int rowIndex;
        public String testCaseId;
        public String role;
        public String setName;
        public String question;
        public String expectedResult;
        public String chatbotAnswer;
        public String status;
        public String passFailureReason;
        public TestRowData(
                String sheetName,
                int rowIndex,
                String testCaseId,
                String role,
                String setName,
                String question,
                String expectedResult,
                String chatbotAnswer,
                String status,
                String passFailureReason) {
            this.sheetName = sheetName;
            this.rowIndex = rowIndex;
            this.testCaseId = testCaseId;
            this.role = role;
            this.setName = setName;
            this.question = question;
            this.expectedResult = expectedResult;
            this.chatbotAnswer = chatbotAnswer;
            this.status = status;
            this.passFailureReason = passFailureReason;
        }
    }
    // CONFIGURATION
    private static final String APP_URL =
            "https://dtqponlzcij0l.cloudfront.net/";
    private static final Duration WAIT_TIMEOUT =
            Duration.ofSeconds(45);
    private static final Duration RESPONSE_TIMEOUT =
            Duration.ofSeconds(45);
    private static final String FRAMEWORK_EXCEL_URL =
            "https://raw.githubusercontent.com/RameshkumarK718/EFIProject/main/EFI.xlsx";
    private static final String FRAMEWORK_EXCEL_FILE =
            "EFI.xlsx";
    private WebDriver driver;
    private WebDriverWait wait;
    // EXCEL DOWNLOAD
    @SuppressWarnings("deprecation")
	private InputStream openUrlStream(String fileUrl) throws Exception {
        URL url = new URL(fileUrl);
        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(60000);
        connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0");
        connection.setRequestProperty(
                "Accept",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
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
    // EXCEL CELL VALUE
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
    // FETCH QUESTION BANK
    private List<TestRowData> fetchExcelDataFromGitHub(
            String fileUrl) {
        List<TestRowData> dataList = new ArrayList<>();
        try (InputStream is = openUrlStream(fileUrl);
             Workbook workbook = new XSSFWorkbook(is)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new RuntimeException(
                        "Framework Excel contains no sheets.");
            }
            for (int i = 0;
                 i < workbook.getNumberOfSheets();
                 i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName();
                System.out.println(
                        "--> Reading sheet: " + sheetName);
                for (int r = 1;
                     r <= sheet.getLastRowNum();
                     r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) {
                        continue;
                    }
                    String role = "";
                    String setName = "";
                    String qNum = "";
                    String question = "";
                    String expectedResult = "";
                    // JIRA SHEET
                    if (sheetName
                            .toLowerCase()
                            .contains("jira")) {
                        qNum =
                                getCellStringValue(
                                        row.getCell(0));

                        question =
                                getCellStringValue(
                                        row.getCell(1));

                        expectedResult =
                                getCellStringValue(
                                        row.getCell(2));

                        role = "PMO";
                        setName = "Jira Queries";
                    }

                    // ------------------------------------------------
                    // STANDARD SHEETS
                    // ------------------------------------------------

                    else {

                        role =
                                getCellStringValue(
                                        row.getCell(0));

                        setName =
                                getCellStringValue(
                                        row.getCell(2));

                        qNum =
                                getCellStringValue(
                                        row.getCell(3));

                        question =
                                getCellStringValue(
                                        row.getCell(4));

                        expectedResult =
                                getCellStringValue(
                                        row.getCell(6));
                    }

                    if (question.isBlank()) {
                        continue;
                    }

                    String testCaseId =
                            String.format(
                                    "%s-Q%s",
                                    sheetName.replaceAll(
                                            "\\s+",
                                            ""),
                                    qNum.isEmpty()
                                            ? String.valueOf(r)
                                            : qNum);

                    dataList.add(
                            new TestRowData(
                                    sheetName,
                                    r,
                                    testCaseId,
                                    role.isBlank()
                                            ? sheetName
                                            : role,
                                    setName,
                                    question,
                                    expectedResult,
                                    "",
                                    "PENDING",
                                    ""));
                }
            }

            System.out.println(
                    "--> Successfully parsed "
                            + dataList.size()
                            + " test cases from workbook.");

        } catch (Exception e) {

            throw new RuntimeException(
                    "Error fetching Framework Excel: "
                            + e.getMessage(),
                    e);
        }

        return dataList;
    }

    // ============================================================
    // TEST SETUP
    // ============================================================

    @BeforeMethod
    public void setUp() {

        initializeDriverAndLogin("End User");
    }

    // ============================================================
    // TEST TEARDOWN
    // ============================================================

    @AfterMethod
    public void tearDown() {

        if (driver != null) {

            try {

                driver.quit();

                System.out.println(
                        "--> WebDriver closed successfully.");

            } catch (Exception e) {

                System.err.println(
                        "--> Error closing WebDriver: "
                                + e.getMessage());
            }
        }
    }

    // ============================================================
    // INITIALIZE CHROME
    // ============================================================

    private void initializeDriverAndLogin(
            String roleName) {

        ChromeOptions options =
                new ChromeOptions();

        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-notifications");
        options.addArguments("--remote-allow-origins=*");

        driver = new ChromeDriver(options);

        driver.manage()
                .timeouts()
                .implicitlyWait(Duration.ZERO);

        driver.manage()
                .timeouts()
                .pageLoadTimeout(
                        Duration.ofSeconds(60));

        wait = new WebDriverWait(
                driver,
                WAIT_TIMEOUT);

        try {

            System.out.println(
                    "--> Opening application landing page: "
                            + APP_URL);

            driver.get(APP_URL);

            wait.until(
                    d -> ((JavascriptExecutor) d)
                            .executeScript(
                                    "return document.readyState")
                            .equals("complete"));

            System.out.println(
                    "--> Page loaded.");

            System.out.println(
                    "--> Current URL: "
                            + driver.getCurrentUrl());

            System.out.println(
                    "--> Page title: "
                            + driver.getTitle());

            // --------------------------------------------------------
            // SELECT MOCK ROLE
            // --------------------------------------------------------

            String buttonXpath =
                    getRoleButtonXpath(roleName);

            System.out.println(
                    "--> Looking for role: "
                            + roleName);

            System.out.println(
                    "--> Role XPath: "
                            + buttonXpath);

            WebElement roleButton =
                    wait.until(
                            ExpectedConditions
                                    .elementToBeClickable(
                                            By.xpath(
                                                    buttonXpath)));

            clickElement(roleButton);

            System.out.println(
                    "--> Selected mock role: "
                            + roleName);

            // --------------------------------------------------------
            // WAIT FOR CHATBOT
            // --------------------------------------------------------

            switchToChatFrameIfPresent();

            waitForChatInput();

            System.out.println(
                    "--> Chatbot interface is ready.");

        } catch (Exception e) {

            capturePageDiagnostics();

            throw new AssertionError(
                    "Failed to log in with mock role ["
                            + roleName
                            + "]: "
                            + e.getMessage(),
                    e);
        }
    }

    // ============================================================
    // ROLE BUTTON LOCATOR
    // ============================================================

    private String getRoleButtonXpath(
            String roleName) {

        if (roleName.equalsIgnoreCase("SDM")) {

            return
                    "//button[contains(normalize-space(), 'SDM')]"
                    + " | "
                    + "//button[contains(normalize-space(), 'Manager')]"
                    + " | "
                    + "//a[contains(normalize-space(), 'SDM')]"
                    + " | "
                    + "//a[contains(normalize-space(), 'Manager')]";
        }

        if (roleName.equalsIgnoreCase("PMO")) {

            return
                    "//button[contains(normalize-space(), 'PMO')]"
                    + " | "
                    + "//button[contains(normalize-space(), 'Leadership')]"
                    + " | "
                    + "//a[contains(normalize-space(), 'PMO')]"
                    + " | "
                    + "//a[contains(normalize-space(), 'Leadership')]";
        }

        if (roleName.equalsIgnoreCase("L1 Engineer")) {

            return
                    "//button[contains(normalize-space(), 'L1')]"
                    + " | "
                    + "//button[contains(normalize-space(), 'Engineer')]"
                    + " | "
                    + "//a[contains(normalize-space(), 'L1')]"
                    + " | "
                    + "//a[contains(normalize-space(), 'Engineer')]";
        }

        return
                "//button[contains(normalize-space(), 'End User')]"
                + " | "
                + "//button[contains(normalize-space(), 'EndUser')]"
                + " | "
                + "//button[contains(normalize-space(), 'User')]"
                + " | "
                + "//a[contains(normalize-space(), 'End User')]"
                + " | "
                + "//a[contains(normalize-space(), 'EndUser')]"
                + " | "
                + "//a[contains(normalize-space(), 'User')]";
    }

    // ============================================================
    // CHAT INPUT LOCATOR
    // ============================================================

    private By getChatInputLocator() {

        return By.xpath(

                "//input["
                        + "contains(translate(@placeholder,"
                        + "'ABCDEFGHIJKLMNOPQRSTUVWXYZ',"
                        + "'abcdefghijklmnopqrstuvwxyz'),"
                        + "'ask')"
                        + "]"

                        + " | "

                        + "//textarea["
                        + "contains(translate(@placeholder,"
                        + "'ABCDEFGHIJKLMNOPQRSTUVWXYZ',"
                        + "'abcdefghijklmnopqrstuvwxyz'),"
                        + "'ask')"
                        + "]"

                        + " | "

                        + "//input["
                        + "contains(translate(@aria-label,"
                        + "'ABCDEFGHIJKLMNOPQRSTUVWXYZ',"
                        + "'abcdefghijklmnopqrstuvwxyz'),"
                        + "'ask')"
                        + "]"

                        + " | "

                        + "//textarea["
                        + "contains(translate(@aria-label,"
                        + "'ABCDEFGHIJKLMNOPQRSTUVWXYZ',"
                        + "'abcdefghijklmnopqrstuvwxyz'),"
                        + "'ask')"
                        + "]"

                        + " | "

                        + "//div[@contenteditable='true']"

                        + " | "

                        + "//div[@role='textbox']"

                        + " | "

                        + "//input[@type='text']"

                        + " | "

                        + "//textarea");
    }

    // ============================================================
    // WAIT FOR CHAT INPUT
    // ============================================================

    private WebElement waitForChatInput() {

        try {

            return wait.until(
                    ExpectedConditions
                            .visibilityOfElementLocated(
                                    getChatInputLocator()));

        } catch (Exception e) {

            capturePageDiagnostics();

            throw new RuntimeException(
                    "Chatbot input was not found after "
                            + WAIT_TIMEOUT.getSeconds()
                            + " seconds. "
                            + "Check role navigation, iframe, "
                            + "or application page state.",
                    e);
        }
    }

    // ============================================================
    // IFRAME SUPPORT
    // ============================================================

    private void switchToChatFrameIfPresent() {

        driver.switchTo().defaultContent();

        List<WebElement> frames =
                driver.findElements(
                        By.tagName("iframe"));

        System.out.println(
                "--> Iframes found: "
                        + frames.size());

        if (frames.isEmpty()) {
            return;
        }

        for (int i = 0;
             i < frames.size();
             i++) {

            try {

                driver.switchTo().defaultContent();

                driver.switchTo().frame(
                        frames.get(i));

                List<WebElement> inputs =
                        driver.findElements(
                                getChatInputLocator());

                if (!inputs.isEmpty()) {

                    System.out.println(
                            "--> Chat input found inside iframe #"
                                    + i);

                    return;
                }

            } catch (Exception ignored) {
            }
        }

        driver.switchTo().defaultContent();

        System.out.println(
                "--> Chat input not found in available iframes.");
    }

    // ============================================================
    // CLICK HELPER
    // ============================================================

    private void clickElement(
            WebElement element) {

        try {

            element.click();

        } catch (Exception e) {

            JavascriptExecutor js =
                    (JavascriptExecutor) driver;

            js.executeScript(
                    "arguments[0].scrollIntoView({block:'center'});",
                    element);

            js.executeScript(
                    "arguments[0].click();",
                    element);
        }
    }

    // ============================================================
    // CHAT MESSAGES
    // ============================================================

    private List<WebElement> getChatMessages() {

        try {

            return driver.findElements(
                    By.xpath(
                            "//div[contains(@class,'message')]"
                                    + " | "
                                    + "//div[contains(@class,'bot-response')]"
                                    + " | "
                                    + "//div[contains(@class,'chat-bubble')]"
                                    + " | "
                                    + "//*[contains(@class,'assistant')]"
                                    + " | "
                                    + "//*[contains(@class,'bot')]"));

        } catch (Exception e) {

            return new ArrayList<>();
        }
    }

    // ============================================================
    // LAST CHAT MESSAGE
    // ============================================================

    private String getLastChatMessageText() {

        List<WebElement> messages =
                getChatMessages();

        for (int i = messages.size() - 1;
             i >= 0;
             i--) {

            try {

                String text =
                        messages.get(i)
                                .getText()
                                .trim();

                if (!text.isBlank()) {
                    return text;
                }

            } catch (StaleElementReferenceException ignored) {
            }
        }

        return "";
    }

    // ============================================================
    // WAIT FOR CHATBOT RESPONSE
    // ============================================================

    private String waitForChatbotResponse(
            String previousResponse) {

        WebDriverWait responseWait =
                new WebDriverWait(
                        driver,
                        RESPONSE_TIMEOUT);

        return responseWait.until(d -> {

            String currentResponse =
                    getLastChatMessageText();

            if (currentResponse.isBlank()) {
                return null;
            }

            if (!currentResponse.equals(
                    previousResponse)) {

                return currentResponse;
            }

            return null;
        });
    }

    // ============================================================
    // SEND QUESTION
    // ============================================================

    private String sendQuestion(
            String question) {

        String previousResponse =
                getLastChatMessageText();

        switchToChatFrameIfPresent();

        WebElement chatInput =
                waitForChatInput();

        try {

            chatInput.click();

            chatInput.clear();

        } catch (Exception ignored) {
        }

        chatInput.sendKeys(question);

        chatInput.sendKeys(Keys.ENTER);

        System.out.println(
                "--> Question submitted: "
                        + question);

        return waitForChatbotResponse(
                previousResponse);
    }

    // ============================================================
    // PAGE DIAGNOSTICS
    // ============================================================

    @SuppressWarnings("deprecation")
	private void capturePageDiagnostics() {
        System.out.println();
        System.out.println("========== PAGE DIAGNOSTICS ==========");
        try {
            System.out.println("Current URL: " + driver.getCurrentUrl());
            System.out.println("Page Title: " + driver.getTitle());

            // Check if chat is inside an iframe
            List<WebElement> iframes = driver.findElements(By.tagName("iframe"));
            System.out.println("Iframes found: " + iframes.size());
            for (int i = 0; i < iframes.size(); i++) {
                System.out.println("IFRAME[" + i + "] id=" + iframes.get(i).getAttribute("id") + " name=" + iframes.get(i).getAttribute("name") + " src=" + iframes.get(i).getAttribute("src"));
            }

            // Check all inputs, textareas, and contenteditables
            List<WebElement> inputs = driver.findElements(
                    By.xpath("//input | //textarea | //*[@contenteditable='true'] | //*[@role='textbox']"));
            System.out.println("Possible input/textbox elements: " + inputs.size());
            for (int i = 0; i < inputs.size(); i++) {
                try {
                    WebElement element = inputs.get(i);
                    System.out.println(
                            "INPUT[" + i + "]"
                                    + " tag=" + element.getTagName()
                                    + " type=" + element.getAttribute("type")
                                    + " placeholder=" + element.getAttribute("placeholder")
                                    + " aria-label=" + element.getAttribute("aria-label")
                                    + " role=" + element.getAttribute("role")
                                    + " contenteditable=" + element.getAttribute("contenteditable")
                                    + " displayed=" + element.isDisplayed());
                } catch (Exception ignored) {
                }
            }

            // Check buttons
            List<WebElement> buttons = driver.findElements(By.tagName("button"));
            System.out.println("Buttons found: " + buttons.size());
            for (int i = 0; i < buttons.size(); i++) {
                try {
                    String buttonText = buttons.get(i).getText().trim();
                    if (!buttonText.isBlank()) {
                        System.out.println("BUTTON[" + i + "]: " + buttonText);
                    }
                } catch (Exception ignored) {
                }
            }

        } catch (Exception diagnosticException) {
            System.out.println("Diagnostic collection failed: " + diagnosticException.getMessage());
        }
        System.out.println("========== END DIAGNOSTICS ==========");
        System.out.println();
    }

    // ============================================================
    // MAIN AUTOMATION TEST
    // ============================================================

    @Test
    public void runAutomationFramework() {

        List<TestRowData> testDataList =
                fetchExcelDataFromGitHub(
                        FRAMEWORK_EXCEL_URL);

        Assert.assertFalse(
                testDataList.isEmpty(),
                "Test data list is empty.");

        System.out.println(
                "============================================================");

        System.out.println(
                "Executing "
                        + testDataList.size()
                        + " test cases against Chatbot Portal.");

        System.out.println(
                "============================================================");

        List<TestRowData> executedResults =
                new ArrayList<>();

        String activeRole = "";

        for (int i = 0;
             i < testDataList.size();
             i++) {

            TestRowData rowData =
                    testDataList.get(i);

            String targetRole =
                    rowData.role;

            if (targetRole
                    .toLowerCase()
                    .contains("l1")) {

                targetRole = "L1 Engineer";

            } else if (targetRole
                    .toLowerCase()
                    .contains("sdm")) {

                targetRole = "SDM";

            } else if (targetRole
                    .toLowerCase()
                    .contains("pmo")) {

                targetRole = "PMO";

            } else {

                targetRole = "End User";
            }

            if (!targetRole.equals(activeRole)
                    || driver == null) {

                if (driver != null) {
                    driver.quit();
                }

                activeRole = targetRole;

                System.out.println(
                        "\n--> Switching Mock Role Context to: "
                                + activeRole);

                initializeDriverAndLogin(
                        activeRole);
            }

            System.out.println(
                    "\n------------------------------------------------------------");

            System.out.println(
                    "Executing Test: "
                            + rowData.testCaseId
                            + " ["
                            + rowData.sheetName
                            + "]");

            System.out.println(
                    "Question: "
                            + rowData.question);

            try {

                String chatbotResponse =
                        sendQuestion(
                                rowData.question);

                rowData.chatbotAnswer =
                        chatbotResponse;

                if (chatbotResponse == null
                        || chatbotResponse.isBlank()) {

                    rowData.status = "FAIL";

                    rowData.passFailureReason =
                            "Chatbot returned an empty response.";

                } else if (chatbotResponse
                        .toLowerCase()
                        .contains("error")) {

                    rowData.status = "FAIL";

                    rowData.passFailureReason =
                            "Chatbot returned an error response.";

                } else {

                    rowData.status = "PASS";

                    rowData.passFailureReason =
                            "Chatbot successfully generated a response.";
                }

            } catch (Exception e) {

                rowData.chatbotAnswer =
                        "EXCEPTION: "
                                + e.getMessage();

                rowData.status = "FAIL";

                rowData.passFailureReason =
                        e.getMessage();
            }

            executedResults.add(rowData);

            System.out.println(
                    "Status: "
                            + rowData.status);
        }

        updateFrameworkExcel(
                executedResults);

        generateDetailedEnterpriseReport(
                executedResults);
    }

    // ============================================================
    // UPDATE EXCEL
    // ============================================================

    private void updateFrameworkExcel(
            List<TestRowData> results) {

        try (InputStream is =
                     openUrlStream(
                             FRAMEWORK_EXCEL_URL);

             Workbook workbook =
                     new XSSFWorkbook(is);

             FileOutputStream outputStream =
                     new FileOutputStream(
                             FRAMEWORK_EXCEL_FILE)) {

            for (TestRowData result : results) {

                Sheet sheet =
                        workbook.getSheet(
                                result.sheetName);

                if (sheet == null) {
                    continue;
                }

                Row row =
                        sheet.getRow(
                                result.rowIndex);

                if (row == null) {
                    continue;
                }

                if (result.sheetName
                        .toLowerCase()
                        .contains("jira")) {

                    Cell actualCell =
                            row.getCell(3);

                    if (actualCell == null) {
                        actualCell =
                                row.createCell(3);
                    }

                    actualCell.setCellValue(
                            result.chatbotAnswer);

                    Cell statusCell =
                            row.getCell(4);

                    if (statusCell == null) {
                        statusCell =
                                row.createCell(4);
                    }

                    statusCell.setCellValue(
                            result.status);

                } else {

                    Cell actualCell =
                            row.getCell(9);

                    if (actualCell == null) {
                        actualCell =
                                row.createCell(9);
                    }

                    actualCell.setCellValue(
                            result.chatbotAnswer);

                    Cell statusCell =
                            row.getCell(10);

                    if (statusCell == null) {
                        statusCell =
                                row.createCell(10);
                    }

                    statusCell.setCellValue(
                            result.status);
                }
            }

            workbook.write(
                    outputStream);

            System.out.println(
                    "--> Execution results successfully saved to "
                            + FRAMEWORK_EXCEL_FILE);

        } catch (Exception e) {

            System.err.println(
                    "--> Failed to update Excel report: "
                            + e.getMessage());
        }
    }

    // ============================================================
    // ENTERPRISE REPORT
    // ============================================================

    private void generateDetailedEnterpriseReport(
            List<TestRowData> results) {

        int total = results.size();
        int passed = 0;
        int failed = 0;

        for (TestRowData res : results) {

            if ("PASS".equalsIgnoreCase(
                    res.status)) {

                passed++;

            } else {

                failed++;
            }
        }

        System.out.println( "\n========================================================================================");

        System.out.println(
                "                    EFI Framework AI & SYSTEM RELIABILITY AUDIT REPORT");

        System.out.println(
                "========================================================================================");

        System.out.printf(
                " • Total Test Cases Evaluated : %d%n",
                total);

        System.out.printf(
                " • Passed Test Cases          : %d%n",
                passed);

        System.out.printf(
                " • Failed Test Cases          : %d%n",
                failed);

        System.out.printf(
                " • Pass Rate                  : %.2f%%%n",
                total > 0
                        ? ((double) passed / total) * 100
                        : 0.0);

        System.out.println(
                "========================================================================================");
    }
}
