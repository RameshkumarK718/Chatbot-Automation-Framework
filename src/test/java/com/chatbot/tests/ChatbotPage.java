package com.chatbot.tests;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;
import java.util.List;

public class ChatbotPage {
    private final WebDriver driver;
    private final WebDriverWait wait;

    // Specific locators targeting bot responses only
    private final By chatInput = By.xpath("//textarea[contains(@placeholder,'Ask')]");
    private final By sendBtn = By.xpath("//button[@type='submit' or contains(@aria-label,'Send')]");
    private final By botMessages = By.xpath("//div[contains(@class,'bot-message') or contains(@data-author,'assistant')]");
    private final By streamingIndicator = By.xpath("//div[contains(@class,'streaming') or contains(@class,'typing')]");

    public ChatbotPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(30));
    }

    public void sendQuestion(String question) {
        WebElement input = wait.until(ExpectedConditions.elementToBeClickable(chatInput));
        input.clear();
        input.sendKeys(question);
        driver.findElement(sendBtn).click();
    }

    public String getLatestResponse() {
        // Wait until typing indicator vanishes
        try {
            wait.until(ExpectedConditions.invisibilityOfElementLocated(streamingIndicator));
        } catch (Exception ignored) {}

        List<WebElement> msgList = driver.findElements(botMessages);
        if (!msgList.isEmpty()) {
            return msgList.get(msgList.size() - 1).getText().trim();
        }
        return "";
    }
}