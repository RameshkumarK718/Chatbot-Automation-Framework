package io.qameta.allure.utils;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
        // Utility class; do not instantiate.
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

            // Row 0 is assumed to contain column headings.
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
                    "Unable to read Excel test data from: " + filePath, e);
        }

        return testData.toArray(new Object[0][]);
    }

    public static synchronized void writeResult(
            String sheetName,
            int rowIndex,
            String actualAnswer,
            String status) {

        Path outputPath = Paths.get(RESULTS_PATH);
        Path tempPath = null;

        try {
            Path parent = outputPath.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            String sourcePath = Files.exists(outputPath)
                    ? outputPath.toString()
                    : FRAMEWORK_URL;

            Workbook workbook;

            // Close the input stream before attempting any file replacement.
            try (InputStream input = openInput(sourcePath)) {
                workbook = WorkbookFactory.create(input);
            }

            try (Workbook workbookToClose = workbook) {
                writeCells(
                        workbookToClose,
                        sheetName,
                        rowIndex,
                        actualAnswer,
                        status);

                Path tempParent = outputPath.toAbsolutePath().getParent();
                tempPath = Files.createTempFile(
                        tempParent, "excel-results-", ".xlsx");

                try (OutputStream output = Files.newOutputStream(tempPath)) {
                    workbookToClose.write(output);
                }
            }

            // Replace the output only after the workbook has been closed.
            try {
                Files.move(
                        tempPath,
                        outputPath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(
                        tempPath,
                        outputPath,
                        StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to write test result for sheet '"
                    + sheetName + "', row " + rowIndex, e);
        } finally {
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (Exception ignored) {
                    // Best-effort cleanup of the temporary file.
                }
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

        // Column F = Actual Answer; column G = Status.
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
        if (cell == null) {
            return "";
        }

        return FORMATTER.formatCellValue(cell).trim();
    }

    private static InputStream openInput(String path) throws Exception {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(
                    "Excel file path must not be empty.");
        }

        if (path.startsWith("https://") || path.startsWith("http://")) {
            URLConnection connection = new URL(path).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            return connection.getInputStream();
        }

        return new FileInputStream(new File(path));
    }
}
