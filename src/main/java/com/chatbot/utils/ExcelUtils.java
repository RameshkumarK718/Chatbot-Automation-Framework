
package com.chatbot.utils;



import java.io.FileInputStream;

import java.io.FileOutputStream;

import java.util.*;



import org.apache.poi.ss.usermodel.*;



public class ExcelUtils {



    private ExcelUtils() {}



    public static List<Map<String, String>> getTestData(

            String excelPath, String sheetName) {



        List<Map<String, String>> data = new ArrayList<>();



        try (FileInputStream fis = new FileInputStream(excelPath);

             Workbook workbook = WorkbookFactory.create(fis)) {



            Sheet sheet = workbook.getSheet(sheetName);



            if (sheet == null) {

                throw new RuntimeException("Sheet not found: " + sheetName);

            }



            Row headerRow = sheet.getRow(0);



            if (headerRow == null) {

                throw new RuntimeException("Header row is missing.");

            }



            DataFormatter formatter = new DataFormatter();



            for (int r = 1; r <= sheet.getLastRowNum(); r++) {



                Row row = sheet.getRow(r);



                if (row == null) {

                    continue;

                }



                Map<String, String> rowData = new LinkedHashMap<>();



                for (int c = 0; c < headerRow.getLastCellNum(); c++) {



                    Cell headerCell = headerRow.getCell(c);



                    if (headerCell == null) {

                        continue;

                    }



                    String header = formatter

                            .formatCellValue(headerCell)

                            .trim();



                    String value = "";



                    Cell dataCell = row.getCell(c);



                    if (dataCell != null) {

                        value = formatter

                                .formatCellValue(dataCell)

                                .trim();

                    }



                    rowData.put(header, value);

                }



                data.add(rowData);

            }



        } catch (Exception e) {

            throw new RuntimeException(

                    "Unable to read Excel test data: " + excelPath, e);

        }



        return data;

    }



    public static String getCellData(

            String sheetName, int rowNum, int colNum) {



        throw new UnsupportedOperationException(

                "Use getTestData(excelPath, sheetName)");

    }



    public static void writeTestResult(

            String excelPath,

            String sheetName,

            int rowNum,

            String botResponse,

            String relevance,

            String quality,

            String reason) {



        try (FileInputStream fis = new FileInputStream(excelPath);

             Workbook workbook = WorkbookFactory.create(fis)) {



            Sheet sheet = workbook.getSheet(sheetName);



            if (sheet == null) {

                throw new RuntimeException("Sheet not found: " + sheetName);

            }



            Row headerRow = sheet.getRow(0);



            if (headerRow == null) {

                throw new RuntimeException("Header row is missing.");

            }



            Row row = sheet.getRow(rowNum);



            if (row == null) {

                row = sheet.createRow(rowNum);

            }



            DataFormatter formatter = new DataFormatter();



            Map<String, Integer> columns = new HashMap<>();



            for (int c = 0; c < headerRow.getLastCellNum(); c++) {



                Cell cell = headerRow.getCell(c);



                if (cell != null) {

                    columns.put(

                            formatter.formatCellValue(cell).trim().toLowerCase(),

                            c);

                }

            }



            writeColumn(row, columns,

                    "chatbot answer", botResponse);



            writeColumn(row, columns,

                    "chatbot response", botResponse);



            writeColumn(row, columns,

                    "relevance", relevance);



            writeColumn(row, columns,

                    "relevance status", relevance);



            writeColumn(row, columns,

                    "quality", quality);



            writeColumn(row, columns,

                    "quality status", quality);



            writeColumn(row, columns,

                    "status", quality);



            writeColumn(row, columns,

                    "result", quality);



            writeColumn(row, columns,

                    "reason", reason);



            writeColumn(row, columns,

                    "evaluation reason", reason);



            try (FileOutputStream fos = new FileOutputStream(excelPath)) {

                workbook.write(fos);

            }



        } catch (Exception e) {

            throw new RuntimeException(

                    "Unable to write test result to Excel.", e);

        }

    }



    private static void writeColumn(

            Row row,

            Map<String, Integer> columns,

            String columnName,

            String value) {



        Integer index = columns.get(columnName.toLowerCase());



        if (index != null) {

            Cell cell = row.getCell(index);



            if (cell == null) {

                cell = row.createCell(index);

            }



            cell.setCellValue(value == null ? "" : value);

        }

    }

}

