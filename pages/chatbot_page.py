import time
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
class ChatbotPage:
    def __init__(self, driver):
        self.driver = driver
        self.input_box = (By.ID, "chat-input")
        self.send_button = (By.ID, "send-btn")
        self.last_response = (By.XPATH, "(//div[contains(@class, 'bot-message')])[last()]")
    def send_question(self, question: str):
        wait = WebDriverWait(self.driver, 10)
        element = wait.until(EC.element_to_be_clickable(self.input_box))
        element.clear()
        element.send_keys(question)
        element.send_keys(Keys.RETURN)
    def get_latest_response(self) -> str:
        time.sleep(2)
        wait = WebDriverWait(self.driver, 15)
        response_element = wait.until(EC.visibility_of_element_located(self.last_response))
        return response_element.text
