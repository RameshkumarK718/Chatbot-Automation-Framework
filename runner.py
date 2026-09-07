import os
import sys
# 1. Define PROJECT_ROOT and add to sys.path
PROJECT_ROOT = os.path.dirname(os.path.abspath(__file__))
if PROJECT_ROOT not in sys.path:
    sys.path.insert(0, PROJECT_ROOT)
# 2. Required Selenium & Module Imports
from selenium import webdriver
from pages.chatbot_page import ChatbotPage
from engine.ai_evaluator import AIEvaluator
from utils.excel_handler import ExcelHandler
def run_framework():
    EXCEL_PATH = os.path.join(PROJECT_ROOT, "data", "test_suite.xlsx")
    OPENAI_KEY = os.getenv("OPENAI_API_KEY", "YOUR_OPENAI_API_KEY")
    CHATBOT_URL = "https://rameshkumark718.github.io/Chatbot-Automation-Framework/"
    # Setup Chrome options
    options = webdriver.ChromeOptions()
    # Remove or comment out "--headless=new" if you want to watch the browser execute
    options.add_argument("--headless=new")    
    driver = webdriver.Chrome(options=options)
    driver.maximize_window()
    driver.get(CHATBOT_URL)
    # Initialize components
    chatbot = ChatbotPage(driver)
    evaluator = AIEvaluator(api_key=OPENAI_KEY)
    excel = ExcelHandler(EXCEL_PATH)
    # Ensure screenshot directory exists
    os.makedirs(os.path.join(PROJECT_ROOT, "reports", "screenshots"), exist_ok=True)
    # Execute test suite loop
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
            screenshot_path = os.path.join(PROJECT_ROOT, "reports", "screenshots", f"{tc['id']}_error.png")
            driver.save_screenshot(screenshot_path)
            excel.update_test_case(tc["row"], "ERROR", f"Failed to execute: {str(e)}")
        excel.save()
    driver.quit()
    print("Suite execution completed successfully.")
if __name__ == "__main__":
    run_framework()
