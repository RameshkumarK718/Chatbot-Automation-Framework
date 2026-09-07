package com.chatbot.pages;
import java.time.Duration;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class ChatPage {
    private final WebDriverWait wait;

    // CHATBOT LOCATORS
    // Replace these with the actual locators from your chatbot
    // Chat input box
    private final By chatInput = By.xpath(
            "//textarea[@placeholder='Type your message']"
    );
    // Chatbot response
    private final By botResponse = By.xpath(
            "//div[contains(@class,'bot-response')]"
    );
    // CONSTRUCTOR
    public ChatPage(WebDriver driver) {
        this.wait = new WebDriverWait(
                driver,
                Duration.ofSeconds(30)
        );
        PageFactory.initElements(driver, this);
    }
    // SEND MESSAGE TO CHATBOT
    public void sendMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Chatbot message cannot be null or empty"
            );
        }
        WebElement input = wait.until(
                ExpectedConditions.elementToBeClickable(chatInput)
        );
        input.click();
        input.clear();
        input.sendKeys(message);
        input.sendKeys(Keys.ENTER);
    }
    // CAPTURE CHATBOT RESPONSE
    public String captureBotResponse() {
        WebElement response = wait.until(
                ExpectedConditions.visibilityOfElementLocated(botResponse)
        );
        return response.getText().trim();
    }
}
