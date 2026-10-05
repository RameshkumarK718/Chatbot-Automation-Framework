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

    // 1. Specific Locators (Update these to match your exact DOM attributes like data-testid or specific classes)
    private By chatInput = By.xpath("//textarea[@id='chat-input' or @placeholder='Type a message...']");
    private By sendButton = By.xpath("//button[@data-testid='send-button' or @type='submit']");
    
    // Targeted specifically at bot/assistant response containers (avoiding user messages)
    private By botMessages = By.xpath("//div[@data-sender='bot' or contains(@class, 'bot-message') or contains(@class, 'assistant-response')]");

    public ChatbotPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(20));
    }

    public void sendMessage(String question) {
        WebElement inputElement = wait.until(ExpectedConditions.elementToBeClickable(chatInput));
        inputElement.clear();
        inputElement.sendKeys(question);
        
        WebElement sendBtn = wait.until(ExpectedConditions.elementToBeClickable(sendButton));
        sendBtn.click();
    }

    /**
     * 2. Streaming Stabilization Loop
     * Polls the response text at intervals until it stops changing, 
     * ensuring you capture the final fully-streamed answer.
     */
    public String getLatestResponse() {
        wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(botMessages, 0));

        WebElement latestBotMsg = null;
        String previousText = "";
        String currentText = "";
        
        int maxAttempts = 30; // Max timeout protection (~15 seconds)
        int stableCount = 0;   // Consecutive identical checks required to confirm completion

        for (int i = 0; i < maxAttempts; i++) {
            List<WebElement> messages = driver.findElements(botMessages);
            if (messages.isEmpty()) {
                sleep(500);
                continue;
            }

            latestBotMsg = messages.get(messages.size() - 1);
            currentText = latestBotMsg.getText().trim();

            if (!currentText.isEmpty() && currentText.equals(previousText)) {
                stableCount++;
                if (stableCount >= 2) { // Text has stopped changing for ~1 second
                    break;
                }
            } else {
                stableCount = 0; // Still streaming/updating
            }

            previousText = currentText;
            sleep(500);
        }

        return currentText;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
