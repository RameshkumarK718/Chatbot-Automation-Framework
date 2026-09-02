import pytest
from selenium import webdriver
BASE_URL = "https://rameshkumark718.github.io/Chatbot-Automation-Framework/"
@pytest.fixture
def driver():
    driver = webdriver.Chrome()
    driver.maximize_window()
    driver.get(BASE_URL)  # Automatically navigates to your site before each test
    yield driver
    driver.quit()
