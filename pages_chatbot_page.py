import time
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
class ChatbotPage:
    def __init__(self, driver):
        self.driver = driver
        # Robust locators for the chatbot interface
        self.input_field = (By.ID, "chat-input")
        self.send_button = (By.ID, "send-button")
        self.response_bubbles = (By.CSS_SELECTOR, "#chat-scroller .prose")
        self.initial_count = 0
    def send_question(self, question: str):
        wait = WebDriverWait(self.driver, 10)        
        # Track total bubbles BEFORE sending the question to detect new responses later
        self.initial_count = len(self.driver.find_elements(*self.response_bubbles))          
        # Wait for input field, clear, and type the question
        field = wait.until(EC.element_to_be_clickable(self.input_field))
        field.clear()
        field.send_keys(question)             
        # Click the send button
        button = wait.until(EC.element_to_be_clickable(self.send_button))
        button.click()
    def get_latest_response(self, timeout=15) -> str:
        wait = WebDriverWait(self.driver, timeout)        
        # Explicit wait until NEW response bubbles appear in the DOM
        wait.until(
            lambda d: len(d.find_elements(*self.response_bubbles)) > self.initial_count
        )         
        # Brief pause to let live text-streaming finish rendering fully
        time.sleep(1.5)       
        bubbles = self.driver.find_elements(*self.response_bubbles)
        if bubbles:
            return bubbles[-1].text.strip()
        return ""
