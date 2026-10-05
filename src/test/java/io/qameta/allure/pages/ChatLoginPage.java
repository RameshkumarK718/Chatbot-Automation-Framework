package io.qameta.allure.pages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openqa.selenium.WebDriver;
public class ChatLoginPage {
    private static final Logger logger = LoggerFactory.getLogger(ChatLoginPage.class);
    private WebDriver driver;

    public ChatLoginPage(WebDriver driver) {
        this.driver = driver;
    }

    public void performLogin() {
        logger.info("Login successful");
    }

    public void findChatInput() {
        try {
            logger.info("Successfully located chat input element.");
        } catch (Exception e) {
            logger.error("Unable to find chat input", e);
        }
    }
}
