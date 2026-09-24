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
            Sheet sheet = workbook.getSheetAt(0);
            Row row = sheet.getRow(1);
            if (row == null) row = sheet.getRow(3);
            username = getCellStringValue(row.getCell(0));
            password = getCellStringValue(row.getCell(1));
            System.out.println("--> Credentials loaded successfully.");
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

    private List<TestRowData> fetchExcelDataFromGitHub(String fileUrl) {
        List<TestRowData> dataList = new ArrayList<>();
        try (InputStream is = openUrlStream(fileUrl);
             Workbook workbook = new XSSFWorkbook(is)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName();
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
                    
                    if (question.isBlank() || "no".equalsIgnoreCase(runnable)) continue;

                    String testCaseId = String.format("%s-TC%03d", sheetName.replaceAll("\\s+", ""), r);
                    dataList.add(new TestRowData(sheetName, r, testCaseId, role, question, expectedResult, runnable, "", "", ""));
                }
            }
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

    @BeforeMethod
    public void setUp() {
        initializeDriverAndLogin();
    }

    @AfterMethod
    public void tearDown() {
        if (driver != null) {
            try { driver.quit(); } catch (Exception ignored) {}
        }
    }

    private void initializeDriverAndLogin() {
        String[] credentials = getCredentialsFromExcel();
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
        wait = new WebDriverWait(driver, WAIT_TIMEOUT);

        driver.get(APP_URL);
        WebElement memberInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("vaa-email")));
        memberInput.sendKeys(credentials[0]);

        WebElement passwordInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("vaa-pw")));
        passwordInput.sendKeys(credentials[1]);

        WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("vaa-submit")));
        loginButton.click();

        WebElement conversationalAI = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
                "//span[contains(normalize-space(),'Conversational AI')]/ancestor::a[1] | //a[contains(normalize-space(),'Conversational AI')] | //*[contains(normalize-space(), 'Conversational AI')]"
        )));
        conversationalAI.click();
    }

    private WebElement waitForChatInput() {
        return wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//input[@placeholder='Ask a question...'] | //textarea[@placeholder='Ask a question...'] | //input[contains(@placeholder,'Ask')] | //textarea[contains(@placeholder,'Ask')] | //div[@contenteditable='true']")));
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
                if (!text.isBlank()) return text;
            } catch (StaleElementReferenceException ignored) {}
        }
        return "";
    }

    private String sendQuestion(String question) {
        String previousResponse = getLastChatMessageText();
        WebElement chatInput = waitForChatInput();
        chatInput.clear();
        chatInput.sendKeys(question);
        chatInput.sendKeys(Keys.ENTER);
        
        return new WebDriverWait(driver, RESPONSE_TIMEOUT).until(d -> {
            String currentResponse = getLastChatMessageText();
            if (!currentResponse.isBlank() && !currentResponse.equals(previousResponse)) {
                return currentResponse;
            }
            return null;
        });
    }

    @Test
    public void runAutomationFramework() {
        List<TestRowData> testDataList = fetchExcelDataFromGitHub(FRAMEWORK_EXCEL_URL);
        for (TestRowData rowData : testDataList) {
            try {
                rowData.actualResult = sendQuestion(rowData.question);
                rowData.status = (rowData.actualResult != null && !rowData.actualResult.isBlank()) ? "PASS" : "FAIL";
            } catch (Exception e) {
                rowData.actualResult = "EXCEPTION: " + e.getMessage();
                rowData.status = "FAIL";
            }
        }
        updateFrameworkExcel(testDataList);
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
                for (Cell cell : headerRow) {
                    String header = getCellStringValue(cell).toLowerCase();
                    if (header.contains("actual result")) {
                        Cell c = row.getCell(cell.getColumnIndex(), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        c.setCellValue(result.actualResult);
                    } else if (header.contains("pass / fail") || header.contains("status")) {
                        Cell c = row.getCell(cell.getColumnIndex(), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        c.setCellValue(result.status);
                    }
                }
            }
            workbook.write(outputStream);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update Excel: " + e.getMessage(), e);
        }
    }
<<<<<<< HEAD
}
=======
}
>>>>>>> cb6f8e8cca5a2a7e53107107b5b13168a5988b22
