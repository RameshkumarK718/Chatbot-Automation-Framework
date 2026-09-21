import os
import openpyxl

EXCEL_FILE = "EFI.xlsx"

def evaluate_chatbot_results():
    if not os.path.exists(EXCEL_FILE):
        print(f"Error: {EXCEL_FILE} not found.")
        return

    wb = openpyxl.load_workbook(EXCEL_FILE)
    print(f"Loaded workbook sheets: {wb.sheetnames}")

    total_evaluated = 0
    passed_count = 0

    for sheet_name in wb.sheetnames:
        sheet = wb[sheet_name]
        print(f"\nEvaluating sheet: {sheet_name}")
        
        is_jira = "jira" in sheet_name.lower()
        actual_col = 4 if is_jira else 10
        status_col = 5 if is_jira else 11

        for row in range(2, sheet.max_row + 1):
            status_val = sheet.cell(row=row, column=status_col).value
            answer_val = sheet.cell(row=row, column=actual_col).value
            
            if status_val:
                total_evaluated += 1
                if str(status_val).upper() == "PASS":
                    passed_count += 1
                print(f"Row {row} [{status_val}]: Response length -> {len(str(answer_val)) if answer_val else 0} chars")

    print(f"\n========================================")
    print(f" AI EVALUATION SUMMARY: {passed_count}/{total_evaluated} Passed")
    print(f"========================================")

if __name__ == "__main__":
    evaluate_chatbot_results()
