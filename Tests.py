from selenium import webdriver
from selenium.webdriver.common.by import By
from config import Config

def test_launch_chatbot():
    driver = webdriver.Chrome()
    driver.maximize_window()
    
    # Access the URL from config.py directly
    driver.get(Config.BASE_URL)
    
    assert "Gemini" in driver.title or "Chatbot" in driver.title
    driver.quit()