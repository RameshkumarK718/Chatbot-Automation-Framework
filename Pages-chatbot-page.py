<<<<<<< HEAD
=======
import time
>>>>>>> 98c8d0b (Add core framework files: Page objects, Excel handler, and AI evaluator)
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

class ChatbotPage:
    def __init__(self, driver):
        self.driver = driver
<<<<<<< HEAD
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
=======
        self.input_field = (By.ID, "chat-input")
        self.send_button = (By.ID, "send-button")
        self.response_bubbles = (By.CSS_SELECTOR, "#chat-scroller .prose")

    def send_question(self, question: str):
        wait = WebDriverWait(self.driver, 10)
        
        # Track total bubbles BEFORE sending question
        self.initial_count = len(self.driver.find_elements(*self.response_bubbles))
        
        field = wait.until(EC.element_to_be_clickable(self.input_field))
        field.clear()
        field.send_keys(question)
        
        button = wait.until(EC.element_to_be_clickable(self.send_button))
        button.click()

    def get_latest_response(self, timeout=15) -> str:
        wait = WebDriverWait(self.driver, timeout)
        
        # Wait until NEW response bubbles appear in the DOM
        wait.until(
            lambda d: len(d.find_elements(*self.response_bubbles)) > self.initial_count
        )
        
        # Give a short pause for live text-streaming to complete
        time.sleep(1.5)
        
        bubbles = self.driver.find_elements(*self.response_bubbles)
        if bubbles:
            return bubbles[-1].text
        return ""
>>>>>>> 98c8d0b (Add core framework files: Page objects, Excel handler, and AI evaluator)
