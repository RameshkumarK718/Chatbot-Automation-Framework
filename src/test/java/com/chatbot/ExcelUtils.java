package com.chatbot;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ExcelUtils {
    // Local file path for offline/local execution
    private static final String LOCAL_FILE_PATH = "Frameworks(EFI).xlsx";
    
    // Remote fallback URL if local file isn't found
    private static final String EXCEL_URL = "https://raw.githubusercontent.com/RameshkumarK718/Chatbot-Automation-Framework/main/Frameworks(EFI).xlsx";
    private static final String OUTPUT_FILE = "Frameworks_Output.xlsx";

    public static Object[][] getTestDataArray() {
        List<Object[]> testRows = new ArrayList<>();
        try (InputStream is = openExcelStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String question = getCellVal(row.getCell(4)); // Adjusted per your sheet layout ('Question / Input to enter')
                String expected = getCellVal(row.getCell(6)); // Adjusted per your sheet layout ('Expected result')
                String context = getCellVal(row.getCell(7));  // Adjusted per your sheet layout ('Expected source')

                if (!question.isBlank()) {
                    testRows.add(new Object[]{sheet.getSheetName(), r, "TC" + r, question, expected, context});
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read Excel test data: " + e.getMessage(), e);
        }
        return testRows.toArray(new Object[0][0]);
    }

    public static synchronized void writeResult(String sheetName, int rowIndex, String actualResult, String status) {
        try (InputStream is = openExcelStream();
             Workbook workbook = new XSSFWorkbook(is);
             FileOutputStream fos = new FileOutputStream(OUTPUT_FILE)) {

            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                sheet = workbook.createSheet(sheetName);
            }
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                row = sheet.createRow(rowIndex);
            }

            Cell actualCell = row.getCell(9, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK); // 'Actual result' column index
            actualCell.setCellValue(actualResult);

            Cell statusCell = row.getCell(10, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK); // 'Pass / Fail' column index
            statusCell.setCellValue(status);

            workbook.write(fos);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Tries to open the Excel file locally first. If not found, falls back to downloading it from GitHub URL.
     */
    private static InputStream openExcelStream() throws Exception {
        File localFile = new File(LOCAL_FILE_PATH);
        if (localFile.exists()) {
            System.out.println("Loading test Excel file locally from: " + localFile.getAbsolutePath());
            return new FileInputStream(localFile);
        } else {
            System.out.println("Local file not found. Downloading from GitHub URL: " + EXCEL_URL);
            URL url = new URL(EXCEL_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            return conn.getInputStream();
        }
    }

    private static String getCellVal(Cell cell) {
        if (cell == null) return "";
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }
}
