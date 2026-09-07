<<<<<<< HEAD
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
public class ChatbotTest {
    private static final int COL_TESTCASE = 0;
    private static final int COL_QUESTION = 1;
    private static final int COL_CHATBOT_ANSWER = 2;
    private static final int TOTAL_COLUMNS = 7;
    private static final String APP_URL = "https://d3rl0fkw0q6ssb.cloudfront.net/";
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(60);    
    private final String credentialsExcelPath = "C:\\Users\\rames\\OneDrive\\Documents\\My Data Sources\\credentials.xlsx";  
    private final String githubCsvUrl = "https://raw.githubusercontent.com/RameshkumarK718/Automation-Framework/refs/heads/main/mismatch.csv";
    private final String localCsvPath = "C:\\Users\\rames\\OneDrive\\Documents\\My Data Sources\\mismatch.csv";     
    @SuppressWarnings("unused")
    private static int currentCredentialRow = 4;
    private WebDriver driver;
    private WebDriverWait wait;
    private String[] getCredentialsFromExcel() {
        String username = "";
        String password = "";
        File file = new File(credentialsExcelPath);     
        if (!file.exists()) {
            System.err.println("Credentials file not found at path: " + credentialsExcelPath);
            return new String[]{username, password};
        }   
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {          
            Sheet sheet = workbook.getSheetAt(0);        
            Row row = sheet.getRow(4); 
            if (row != null) {
                Cell userCell = row.getCell(0); 
                Cell passCell = row.getCell(1);                           
                username = userCell != null ? getCellStringValue(userCell) : "";
                password = passCell != null ? getCellStringValue(passCell) : "";  
            }     
            System.out.println("--> Using Credentials from Excel Row: 5 | Member ID: " + username);                 
        } catch (IOException e) {
            System.err.println("Error reading credentials Excel file: " + e.getMessage());
        }     
        return new String[]{username, password};
    }
    private String getCellStringValue(Cell cell) {
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
        Assert.assertFalse(memberId.isBlank(), "Member ID is missing from Excel.");
        Assert.assertFalse(password.isBlank(), "Password is missing from Excel.");                          
        ChromeOptions options = new ChromeOptions();
        options.setCapability("se:cdpEnabled", false);    
        options.addArguments("--guest"); // Integrated your extra options here safely     
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
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("vaa-portal")));                
            WebElement conversationalAILink = wait.until(
                ExpectedConditions.elementToBeClickable(
                    By.xpath("//span[contains(normalize-space(), 'Conversational AI')]/ancestor::a | //a[contains(., 'Conversational AI')] | //a[contains(@href, 'javascript:void(0)')]")
                )
            );                     
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("arguments[0].click();", conversationalAILink);                
            System.out.println("Successfully logged in using Member ID: " + memberId + " and opened Conversational AI window.");                
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
        List<String[]> csvData = fetchCSVFromGitHub(githubCsvUrl);
        Assert.assertFalse(csvData.isEmpty(), "Failed to fetch CSV data from GitHub or file is empty!");
        System.out.println("Successfully fetched questions from GitHub mismatch.csv. Total rows: " + csvData.size());                
        File localFile = new File(localCsvPath);                  
        for (int i = 1; i < csvData.size(); i++) {      
            if (driver != null) {
                driver.quit();
            }
            initializeDriverAndLogin();
            String[] row = csvData.get(i);      
            String testCase = row[COL_TESTCASE];
            String question = row[COL_QUESTION];              
            if (question == null || question.trim().isEmpty()) {
                System.out.println("--> Skipping row " + (i + 1) + ": Question column is empty.");
                continue;
            }
            if (testCase == null || testCase.trim().isEmpty()) {
                testCase = String.format("TC-%03d", i);
                row[COL_TESTCASE] = testCase;
            }             
            System.out.println("\n--- Processing [" + testCase + "] (Row " + (i + 1) + " of " + (csvData.size() - 1) + ") ---");
            System.out.println("Question: " + question);                
            String chatbotResponse = askQuestion(question);             
            row[COL_CHATBOT_ANSWER] = chatbotResponse;
            System.out.println("[Chatbot Response Captured & Assigned]");                  
            writeCSVWithRetry(localFile, csvData);                    
            if (i < csvData.size() - 1) {
                sleep(2000); 
            }
        }
        System.out.println("\n All questions processed sequentially with rotating Member IDs and results saved successfully!");
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
    @SuppressWarnings("deprecation")
    private List<String[]> fetchCSVFromGitHub(String urlString) {
        List<String[]> data = new ArrayList<>();
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.connect();
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String[] parts = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
                        String[] paddedRow = new String[TOTAL_COLUMNS];
                        Arrays.fill(paddedRow, "");                              
                        for (int i = 0; i < parts.length && i < TOTAL_COLUMNS; i++) {
                            paddedRow[i] = parts[i].replaceAll("^\"|\"$", "").replace("\"\"", "\"").trim();
                        }
                        data.add(paddedRow);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Exception while reading CSV from GitHub: " + e.getMessage());
        }
        return data;
    }
    private void writeCSVWithRetry(File file, List<String[]> data) {
        int retries = 3;
        while (retries > 0) {
            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                for (String[] row : data) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < row.length; i++) {
                        String cell = row[i] == null ? "" : row[i];
                        if (cell.contains(",") || cell.contains("\n") || cell.contains("\"")) {
                            cell = "\"" + cell.replace("\"", "\"\"") + "\"";
                        }
                        sb.append(cell);
                        if (i < row.length - 1) {
                            sb.append(",");
                        }
                    }
                    bw.write(sb.toString());
                    bw.newLine();
                }
                return; 
            } catch (IOException e) {
                retries--;
                sleep(2000);
            }
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
=======

>>>>>>> c031d6491f9250e73b4e1a2127843bae877a4ff5
