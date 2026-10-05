package io.qameta.allure.utils;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class JsonReporter {
    private static final Logger logger = Logger.getLogger(JsonReporter.class.getName());
    private static final List<TestResult> results = new ArrayList<>();

    public static class TestResult {
        public String testCaseId;
        public String question;
        public String expectedAnswer;
        public String actualAnswer;
        public String status;

        public TestResult(String testCaseId, String question, String expectedAnswer, String actualAnswer, String status) {
            this.testCaseId = testCaseId;
            this.question = question;
            this.expectedAnswer = expectedAnswer;
            this.actualAnswer = actualAnswer;
            this.status = status;
        }
    }

    public static synchronized void addTestResult(String testCaseId, String question, String expected, String actual, String status) {
        results.add(new TestResult(testCaseId, question, expected, actual, status));
    }

    public static synchronized void writeJsonReport() {
        try {
            File file = new File("results.json");
            
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("[\n");
            for (int i = 0; i < results.size(); i++) {
                TestResult r = results.get(i);
                jsonBuilder.append("  {\n");
                jsonBuilder.append("    \"testCaseId\": \"").append(escapeJson(r.testCaseId)).append("\",\n");
                jsonBuilder.append("    \"question\": \"").append(escapeJson(r.question)).append("\",\n");
                jsonBuilder.append("    \"expectedAnswer\": \"").append(escapeJson(r.expectedAnswer)).append("\",\n");
                jsonBuilder.append("    \"actualAnswer\": \"").append(escapeJson(r.actualAnswer)).append("\",\n");
                jsonBuilder.append("    \"status\": \"").append(escapeJson(r.status)).append("\"\n");
                jsonBuilder.append("  }");
                if (i < results.size() - 1) {
                    jsonBuilder.append(",");
                }
                jsonBuilder.append("\n");
            }
            jsonBuilder.append("]\n");

            try (FileWriter writer = new FileWriter(file)) {
                writer.write(jsonBuilder.toString());
            }
            logger.info("Successfully generated machine-to-machine contract: results.json");
        } catch (IOException e) {
            logger.severe("Failed to write results.json: " + e.getMessage());
        }
    }

    private static String escapeJson(String val) {
        if (val == null) return "";
        return val.replace("\"", "\\\"").replace("\n", " ");
    }
}
