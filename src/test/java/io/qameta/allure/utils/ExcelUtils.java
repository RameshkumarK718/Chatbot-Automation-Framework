package io.qameta.allure.utils;
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
        // Utility class; prevent object creation.
    }

    /**
     * Reads test case ID, user question, and expected answer
     * from an Excel sheet.
     *
     * Expected headers:
     * Test Case ID, User Question, Expected Answer
     *
     * Returns Object[][] suitable for TestNG DataProvider.
     */
    public static Object[][] getDataFromExcel(
            String filePath, String sheetName) {

        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Excel file path cannot be empty.");
        }

        if (sheetName == null || sheetName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Excel sheet name cannot be empty.");
        }

        File excelFile = new File(filePath);

        if (!excelFile.isFile()) {
            throw new IllegalArgumentException(
                    "Excel file not found: "
                            + excelFile.getAbsolutePath());
        }

        List<Object[]> dataList = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.US);

        try (FileInputStream inputStream =
                     new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheet(sheetName);

            if (sheet == null) {
                throw new IllegalArgumentException(
                        "Sheet '" + sheetName
                                + "' was not found in "
                                + excelFile.getName());
            }

            Row headerRow = sheet.getRow(0);

            if (headerRow == null) {
                throw new IllegalArgumentException(
                        "The Excel sheet has no header row: "
                                + sheetName);
            }

            Map<String, Integer> columns =
                    getHeaderColumns(headerRow, formatter);

            int testCaseIdColumn =
                    findColumn(columns, "Test Case ID");

            int questionColumn =
                    findColumn(columns, "User Question");

            int expectedAnswerColumn =
                    findColumn(columns, "Expected Answer");

            if (testCaseIdColumn < 0
                    || questionColumn < 0
                    || expectedAnswerColumn < 0) {

                throw new IllegalArgumentException(
                        "Required headers are missing in sheet '"
                                + sheetName
                                + "'. Required: Test Case ID, "
                                + "User Question, Expected Answer. "
                                + "Found: " + columns.keySet());
            }

            // Start at row 1 because row 0 contains the headers.
            for (int rowIndex = 1;
                 rowIndex <= sheet.getLastRowNum();
                 rowIndex++) {

                Row row = sheet.getRow(rowIndex);

                if (row == null) {
                    continue;
                }

                String testCaseId = getCellString(
                        row.getCell(testCaseIdColumn), formatter);

                String question = getCellString(
                        row.getCell(questionColumn), formatter);

                String expectedAnswer = getCellString(
                        row.getCell(expectedAnswerColumn), formatter);

                // Skip completely empty test rows.
                if (testCaseId.isEmpty()
                        && question.isEmpty()
                        && expectedAnswer.isEmpty()) {
                    continue;
                }

                // A question is required to execute a test.
                if (question.isEmpty()) {
                    System.err.println(
                            "Skipping Excel row " + (rowIndex + 1)
                                    + ": User Question is empty.");
                    continue;
                }

                dataList.add(new Object[]{
                        testCaseId,
                        question,
                        expectedAnswer
                });
            }

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to read Excel file: "
                            + excelFile.getAbsolutePath(),
                    e);
        }

        if (dataList.isEmpty()) {
            System.err.println(
                    "Warning: No runnable test cases found in sheet '"
                            + sheetName + "'.");
        }

        return dataList.toArray(new Object[0][]);
    }

    /**
     * Creates a map of normalized header names to column indexes.
     */
    private static Map<String, Integer> getHeaderColumns(
            Row headerRow, DataFormatter formatter) {

        Map<String, Integer> columns = new HashMap<>();

        for (Cell cell : headerRow) {
            String header = getCellString(cell, formatter)
                    .trim()
                    .toLowerCase(Locale.ROOT);

            if (!header.isEmpty()) {
                columns.put(header, cell.getColumnIndex());
            }
        }

        return columns;
    }

    /**
     * Finds a column using a case-insensitive header name.
     */
    private static int findColumn(
            Map<String, Integer> columns, String headerName) {

        Integer columnIndex = columns.get(
                headerName.toLowerCase(Locale.ROOT));

        return columnIndex == null ? -1 : columnIndex;
    }

    /**
     * Safely reads string, numeric, date, and formula cell values.
     */
    private static String getCellString(
            Cell cell, DataFormatter formatter) {

        if (cell == null) {
            return "";
        }

        return formatter.formatCellValue(cell).trim();
    }
}
