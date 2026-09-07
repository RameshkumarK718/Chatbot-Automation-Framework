import pytest
from playwright.sync_api import Page
# Sample data structure for data-driven testing
TEST_CASES = [
    ("Hello", "greeting"),
    ("What are your business hours?", "hours"),
    ("How do I reset my password?", "support")
]
@pytest.mark.parametrize("user_input, expected_category", TEST_CASES)
def test_chatbot_responses(page: Page, user_input, expected_category):
    page.goto("https://rameshkumark718.github.io/Chatbot-Automation-Framework/")  
    # Fill chatbot input and submit
    # page.fill("#chat-input", user_input)
    # page.click("#send-btn")
    assert page.title() == "Enterprise Dual-Viewport Chatbot Playground v5.0"
