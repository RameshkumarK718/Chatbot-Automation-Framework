package io.qameta.allure.utils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ExcelUtils {

    public static Object[][] getDataFromExcel(String filePath, String sheetName) {
        List<Object[]> dataList = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(new File(filePath));
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                sheet = workbook.getSheetAt(0); // Fallback to first sheet
            }

            int rowCount = sheet.getPhysicalNumberOfRows();
            for (int i = 1; i < rowCount; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String testCaseId = getCellString(row.getCell(0));
                String question = getCellString(row.getCell(3)); // Adjust column index if needed
                String expected = getCellString(row.getCell(4)); // Adjust column index if needed

                dataList.add(new Object[]{testCaseId, question, expected});
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return dataList.toArray(new Object[0][0]);
    }

    private static String getCellString(Cell cell) {
        if (cell == null) return "";
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue().trim();
    }
}
