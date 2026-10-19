package com.chatbot.utils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ExcelUtils {

    private ExcelUtils() {
        // Private constructor for utility class
    }

    /**
     * Reads test data from an Excel sheet for TestNG DataProvider.
     *
     * @param filePath  Path to the .xlsx file
     * @param sheetName Name of the worksheet
     * @return 2D Object array containing [TestCaseId, Question, ExpectedAnswer]
     */
    public static Object[][] getDataFromExcel(String filePath, String sheetName) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Excel file path cannot be null or empty.");
        }

        if (sheetName == null || sheetName.trim().isEmpty()) {
            throw new IllegalArgumentException("Sheet name cannot be null or empty.");
        }

        File excelFile = new File(filePath);
        if (!excelFile.exists() || !excelFile.isFile()) {
            throw new IllegalArgumentException("Excel file not found at: " + excelFile.getAbsolutePath());
        }

        List<Object[]> dataList = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.US);

        try (FileInputStream inputStream = new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                sheet = workbook.getSheetAt(0); // Fallback to first sheet
            }

            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                return new Object[0][0];
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return new Object[0][0];
            }

            Map<String, Integer> columns = getHeaderColumns(headerRow, formatter);

            int testCaseIdCol = findColumn(columns, "Test Case ID", 0);
            int questionCol = findColumn(columns, "User Question", 3);
            int expectedAnswerCol = findColumn(columns, "Expected Answer", 4);

            int lastRowNum = sheet.getLastRowNum();
            for (int rowIndex = 1; rowIndex <= lastRowNum; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                String testCaseId = getCellString(row.getCell(testCaseIdCol), formatter);
                String question = getCellString(row.getCell(questionCol), formatter);
                String expectedAnswer = getCellString(row.getCell(expectedAnswerCol), formatter);

                // Skip completely blank rows
                if (testCaseId.isEmpty() && question.isEmpty() && expectedAnswer.isEmpty()) {
                    continue;
                }

                dataList.add(new Object[]{testCaseId, question, expectedAnswer});
            }

        } catch (IOException e) {
            throw new IllegalStateException("Failed to read Excel file at " + excelFile.getAbsolutePath(), e);
        }

        return dataList.toArray(new Object[0][]);
    }

    private static Map<String, Integer> getHeaderColumns(Row headerRow, DataFormatter formatter) {
        Map<String, Integer> columns = new HashMap<>();
        for (Cell cell : headerRow) {
            String val = getCellString(cell, formatter).toLowerCase(Locale.ROOT);
            if (!val.isEmpty()) {
                columns.put(val, cell.getColumnIndex());
            }
        }
        return columns;
    }

    private static int findColumn(Map<String, Integer> columns, String columnName, int defaultIndex) {
        Integer index = columns.get(columnName.toLowerCase(Locale.ROOT));
        return index != null ? index : defaultIndex;
    }

    private static String getCellString(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell).trim();
    }
}
