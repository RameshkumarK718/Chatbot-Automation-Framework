package com.chatbot.tests;

import org.openqa.selenium.*;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

public class ChatbotPage {
    private WebDriver driver;
    private WebDriverWait wait;

    @FindBy(xpath = "//input[@placeholder='Ask a question...'] | //textarea[@placeholder='Ask a question...'] | //input[contains(@placeholder,'Ask')] | //textarea[contains(@placeholder,'Ask')] | //div[@contenteditable='true']")
    private WebElement chatInput;

    @FindBy(xpath = "//button[@type='submit'] | //button[contains(@aria-label, 'Send')] | //button[.//svg] | //button[contains(@class, 'send')]")
    private WebElement sendButton;

    public ChatbotPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(60));
        PageFactory.initElements(driver, this);
    }

    public void sendMessage(String question) {
        wait.until(ExpectedConditions.elementToBeClickable(chatInput));
        try {
            chatInput.click();
            chatInput.clear();
        } catch (Exception ignored) {}
        
        chatInput.sendKeys(question);
        
        try {
            sendButton.click();
        } catch (Exception e) {
            chatInput.sendKeys(Keys.ENTER);
        }
    }

    public String waitForStreamingToComplete() {
        String previousText = "";
        long startTime = System.currentTimeMillis();
        
        while ((System.currentTimeMillis() - startTime) < 45000) {
            String currentText = getLastBotMessage();
            if (!currentText.isBlank() && currentText.equals(previousText)) {
                return currentText; // Streaming complete and stable
            }
            previousText = currentText;
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }
        return previousText;
    }

    private String getLastBotMessage() {
        try {
            List<WebElement> messages = driver.findElements(By.xpath(
                "//div[contains(@class,'message')] | //div[contains(@class,'bot-response')] | //div[contains(@class,'markdown')]"
            ));
            if (!messages.isEmpty()) {
                return messages.get(messages.size() - 1).getText().trim();
            }
        } catch (StaleElementReferenceException ignored) {}
        return "";
    }
}