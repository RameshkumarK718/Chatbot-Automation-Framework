
from selenium import webdriver

from pages.chatbot_page import ChatbotPage

from engine.ai_evaluator import AIEvaluator

from utils.excel_handler import ExcelHandler



def run_framework():

    EXCEL_PATH = "data/test_suite.xlsx"

    OPENAI_KEY = "YOUR_OPENAI_API_KEY"

    CHATBOT_URL = "https://your-chatbot-url.com"



    driver = webdriver.Chrome()

    driver.get(CHATBOT_URL)

    

    chatbot = ChatbotPage(driver)

    evaluator = AIEvaluator(api_key=OPENAI_KEY)

    excel = ExcelHandler(EXCEL_PATH)



    test_cases = excel.read_test_cases()



    for tc in test_cases:

        print(f"Executing {tc['id']}: {tc['question']}")

        

        chatbot.send_question(tc["question"])

        actual_answer = chatbot.get_latest_response()

        

        eval_result = evaluator.evaluate(

            question=tc["question"],

            q_type=tc["type"],

            context=tc["context"],

            expected=tc["expected"],

            actual=actual_answer

        )

        

        excel.update_test_case(tc["row"], actual_answer, eval_result)

        excel.save()



    driver.quit()

    print("Execution complete. Excel report updated.")



if __name__ == "__main__":

    run_framework()

