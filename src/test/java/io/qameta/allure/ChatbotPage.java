package io.qameta.allure.pages;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;
import java.util.List;

public class ChatbotPage {
    private WebDriver driver;
    private WebDriverWait wait;

    // Locators
    private By chatInput = By.xpath("//textarea[@placeholder='Type a message...' or @id='chat-input']"); // Update with your actual locator
    private By sendButton = By.xpath("//button[@type='submit' or contains(@class, 'send')]"); // Update with your actual locator
    private By chatbotMessages = By.xpath("//div[contains(@class, 'message-bubble') or contains(@class, 'bot-response')]"); // Update with your actual locator

    public ChatbotPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    public void sendMessage(String question) {
        WebElement inputElement = wait.until(ExpectedConditions.elementToBeClickable(chatInput));
        inputElement.clear();
        inputElement.sendKeys(question);
        
        WebElement sendBtn = wait.until(ExpectedConditions.elementToBeClickable(sendButton));
        sendBtn.click();
    }

    public void waitForResponse() {
        // Wait until at least one response message appears or updates
        wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(chatbotMessages, 0));
    }

    public void waitForStreamingToFinish() {
        // Optional: Add logic here if your chatbot streams text and you need to wait for it to stop changing
        try {
            Thread.sleep(2000); // Simple buffer or implement custom attribute check
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public String getLatestResponse() {
        waitForResponse();
        waitForStreamingToFinish();
        
        List<WebElement> messages = driver.findElements(chatbotMessages);
        if (!messages.isEmpty()) {
            return messages.get(messages.size() - 1).getText().trim();
        }
        return "";
    }
}
