package com.chatbot.ChatbotAutomationFramework;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContextStateManager {
    
    // In-memory map to store conversation turns: Key = Conversation_ID, Value = List of messages/context
    private static Map<String, List<String>> conversationStore = new HashMap<>();

    // Add a message/response to a specific conversation thread
    public static void addContext(String conversationId, String message) {
        conversationStore.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(message);
    }

    // Retrieve the full conversation history for a given conversation ID
    public static List<String> getContext(String conversationId) {
        return conversationStore.getOrDefault(conversationId, new ArrayList<>());
    }

    // Clear context if a conversation thread is completed or resetting
    public static void clearContext(String conversationId) {
        conversationStore.remove(conversationId);
    }
}