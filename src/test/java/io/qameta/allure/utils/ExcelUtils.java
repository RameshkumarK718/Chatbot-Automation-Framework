package io.qameta.allure.utils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

public final class ExcelUtils {

    private static final String RESULTS_PATH =
            "target/Frameworks-Result(Vedas).xlsx";

    private static final String FRAMEWORK_URL =
            "https://raw.githubusercontent.com/"
            + "RameshkumarK718/Chatbot-Automation-Framework/"
            + "main/Frameworks(Vedas).xlsx";

    private static final DataFormatter FORMATTER = new DataFormatter();

    private ExcelUtils() {
        // Prevent instantiation.
    }

    public static Object[][] getDataFromExcel(
            String filePath, String sheetName) {

        List<Object[]> testData = new ArrayList<>();

        try (InputStream input = openInput(filePath);
             Workbook workbook = WorkbookFactory.create(input)) {

            Sheet sheet = workbook.getSheet(sheetName);

            if (sheet == null) {
                throw new IllegalArgumentException(
                        "Worksheet not found: " + sheetName);
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);

                if (row == null) {
                    continue;
                }

                String testCaseId = getCellString(row.getCell(0));
                String question = getCellString(row.getCell(3));
                String expectedAnswer = getCellString(row.getCell(4));

                if (question.isBlank()) {
                    continue;
                }

                testData.add(new Object[] {
                        sheetName,
                        i,
                        testCaseId,
                        question,
                        expectedAnswer
                });
            }

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to read test data from " + filePath, e);
        }

        return testData.toArray(new Object[0][]);
    }

    public static synchronized void writeResult(
            String sheetName,
            int rowIndex,
            String actualAnswer,
            String status) {

        Path outputPath = Paths.get(RESULTS_PATH);

        try {
            Files.createDirectories(outputPath.getParent());

            if (Files.exists(outputPath)) {
                updateWorkbook(
                        outputPath, sheetName, rowIndex,
                        actualAnswer, status);
            } else {
                try (InputStream input = openInput(FRAMEWORK_URL);
                     Workbook workbook = WorkbookFactory.create(input)) {

                    writeCells(
                            workbook, sheetName, rowIndex,
                            actualAnswer, status);

                    try (FileOutputStream output =
                                 new FileOutputStream(outputPath.toFile())) {
                        workbook.write(output);
                    }
                }
            }

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to write test result for row " + rowIndex, e);
        }
    }

    private static void updateWorkbook(
            Path path,
            String sheetName,
            int rowIndex,
            String actualAnswer,
            String status) throws Exception {

        try (InputStream input = new FileInputStream(path.toFile());
             Workbook workbook = WorkbookFactory.create(input)) {

            writeCells(
                    workbook, sheetName, rowIndex,
                    actualAnswer, status);

            try (FileOutputStream output =
                         new FileOutputStream(path.toFile())) {
                workbook.write(output);
            }
        }
    }

    private static void writeCells(
            Workbook workbook,
            String sheetName,
            int rowIndex,
            String actualAnswer,
            String status) {

        Sheet sheet = workbook.getSheet(sheetName);

        if (sheet == null) {
            throw new IllegalArgumentException(
                    "Worksheet not found: " + sheetName);
        }

        Row row = sheet.getRow(rowIndex);

        if (row == null) {
            row = sheet.createRow(rowIndex);
        }

        // F = Actual Answer; G = Status.
        setCellValue(row, 5, actualAnswer);
        setCellValue(row, 6, status);
    }

    private static void setCellValue(
            Row row, int columnIndex, String value) {

        Cell cell = row.getCell(columnIndex);

        if (cell == null) {
            cell = row.createCell(columnIndex);
        }

        cell.setCellValue(value == null ? "" : value);
    }

    private static String getCellString(Cell cell) {
        return cell == null
                ? ""
                : FORMATTER.formatCellValue(cell).trim();
    }

    private static InputStream openInput(String path) throws Exception {
        if (path.startsWith("https://") || path.startsWith("http://")) {
            return new URL(path).openStream();
        }

        return new FileInputStream(new File(path));
    }
}
