package com.chatbot;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ExcelUtils {
    private static final String EXCEL_URL = "https://raw.githubusercontent.com/RameshkumarK718/Chatbot-Automation-Framework/main/Frameworks(EFI).xlsx";
    private static final String OUTPUT_FILE = "Frameworks_Output.xlsx";

    public static Object[][] getTestDataArray() {
        List<Object[]> testRows = new ArrayList<>();
        try (InputStream is = openUrlStream(EXCEL_URL);
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String question = getCellVal(row.getCell(2)); // Adjust column index per your sheet layout
                String expected = getCellVal(row.getCell(3));
                String context = getCellVal(row.getCell(4));

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
        try (InputStream is = openUrlStream(EXCEL_URL);
             Workbook workbook = new XSSFWorkbook(is);
             FileOutputStream fos = new FileOutputStream(OUTPUT_FILE)) {

            Sheet sheet = workbook.getSheet(sheetName);
            Row row = sheet.getRow(rowIndex);

            // Assuming columns for Actual Result and Status
            Cell actualCell = row.getCell(5, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            actualCell.setCellValue(actualResult);

            Cell statusCell = row.getCell(6, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            statusCell.setCellValue(status);

            workbook.write(fos);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("deprecation")
	private static InputStream openUrlStream(String fileUrl) throws Exception {
        URL url = new URL(fileUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        return conn.getInputStream();
    }

    private static String getCellVal(Cell cell) {
        if (cell == null) return "";
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }
}