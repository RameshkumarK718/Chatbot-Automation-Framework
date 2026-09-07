package com.chatbot.ChatbotAutomationFramework;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExcelUtils {

    // Method to read test cases from Excel into a List of HashMaps for easy data mapping
    @SuppressWarnings("deprecation")
	public static List<Map<String, String>> getTestData(String excelFilePath, String sheetName) {
        List<Map<String, String>> testDataList = new ArrayList<>();
        
        try (FileInputStream fis = new FileInputStream(excelFilePath);
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheet(sheetName);
            Row headerRow = sheet.getRow(0);
            int totalRows = sheet.getPhysicalNumberOfRows();
            
            for (int i = 1; i < totalRows; i++) {
                Row currentRow = sheet.getRow(i);
                if (currentRow == null) continue;
                
                Map<String, String> rowData = new HashMap<>();
                for (int j = 0; j < headerRow.getPhysicalNumberOfCells(); j++) {
                    Cell headerCell = headerRow.getCell(j);
                    Cell dataCell = currentRow.getCell(j, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    
                    String key = headerCell.getStringCellValue().trim();
                    dataCell.setCellType(CellType.STRING);
                    String value = dataCell.getStringCellValue().trim();
                    
                    rowData.put(key, value);
                }
                testDataList.add(rowData);
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return testDataList;
    }

    // Method to write back actual bot responses and results to the specific Excel row
    public static void writeTestResult(String excelFilePath, String sheetName, int rowNum, String actualResponse, String relevanceStatus, String qualityStatus, String evaluationReason) {
        try (FileInputStream fis = new FileInputStream(excelFilePath);
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheet(sheetName);
            Row row = sheet.getRow(rowNum);
            
            row.createCell(6, CellType.STRING).setCellValue(actualResponse);
            row.createCell(7, CellType.STRING).setCellValue(relevanceStatus);
            row.createCell(8, CellType.STRING).setCellValue(qualityStatus);
            row.createCell(9, CellType.STRING).setCellValue(evaluationReason);
            
            try (FileOutputStream fos = new FileOutputStream(excelFilePath)) {
                workbook.write(fos);
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}