from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

class ChatbotPage:
    def __init__(self, driver):
        self.driver = driver
        # Uses generic HTML tags if specific IDs are missing
        self.input_box = (By.TAG_NAME, "input")
        self.send_button = (By.TAG_NAME, "button")
        self.latest_response = (By.CLASS_NAME, "message") 

    def send_question(self, question):
        wait = WebDriverWait(self.driver, 15)
        
        # Wait until input box is visible before typing
        element = wait.until(EC.visibility_of_element_located(self.input_box))
        element.clear()
        element.send_keys(question)
        
        # Click send button
        btn = wait.until(EC.element_to_be_clickable(self.send_button))
        btn.click()

    def get_latest_response(self):
        wait = WebDriverWait(self.driver, 15)
        responses = wait.until(EC.presence_of_all_elements_located(self.latest_response))
        return responses[-1].text