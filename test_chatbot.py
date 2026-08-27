
from selenium import webdriver

from selenium.webdriver.chrome.service import Service

from webdriver_manager.chrome import ChromeDriverManager

from config import Config



def test_launch_chatbot():

    # Automatically manages ChromeDriver versions

    driver = webdriver.Chrome(service=Service(ChromeDriverManager().install()))

    driver.maximize_window()

    

    # Access target UI

    driver.get(Config.BASE_URL)

    

    print("Page Title:", driver.title)

    print("Current URL:", driver.current_url)

    

    # Assert successful connection

    assert Config.BASE_URL in driver.current_url

    driver.quit()



if __name__ == "__main__":

    test_launch_chatbot()

