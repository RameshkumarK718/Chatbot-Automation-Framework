import os
import time
from selenium import webdriver
from pages.chatbot_page import ChatbotPage
from engine.ai_evaluator import AIEvaluator
from utils.excel_handler import ExcelHandler

def run_framework():
    EXCEL_PATH = "data/test_suite.xlsx"
    OPENAI_KEY = os.getenv("OPENAI_API_KEY", "YOUR_OPENAI_API_KEY")
    CHATBOT_URL = "https://rameshkumark718.github.io/Chatbot-Automation-Framework/"

    options = webdriver.ChromeOptions()
    options.add_argument("--headless=new")  # Enable for headless CI runs
    driver = webdriver.Chrome(options=options)
    driver.maximize_window()
    driver.get(CHATBOT_URL)

    chatbot = ChatbotPage(driver)
    evaluator = AIEvaluator(api_key=OPENAI_KEY)
    excel = ExcelHandler(EXCEL_PATH)

    os.makedirs("reports/screenshots", exist_ok=True)

    for tc in excel.read_test_cases():
        print(f"[RUNNING] {tc['id']}: {tc['question']}")
        
        try:
            chatbot.send_question(tc["question"])
            actual_answer = chatbot.get_latest_response()

            eval_res = evaluator.evaluate_advanced(
                question=tc["question"],
                expected=tc["expected"],
                actual=actual_answer,
                context=tc["context"]
            )

            status = "PASS" if eval_res.get("pass") else "FAIL"
            excel.update_test_case(tc["row"], actual_answer, f"{status} | {eval_res['feedback']}")

        except Exception as e:
            screenshot_path = f"reports/screenshots/{tc['id']}_error.png"
            driver.save_screenshot(screenshot_path)
            excel.update_test_case(tc["row"], "ERROR", f"Failed to execute: {str(e)}")

        excel.save()

    driver.quit()
    print("Suite execution completed successfully.")

if __name__ == "__main__":
    run_framework()