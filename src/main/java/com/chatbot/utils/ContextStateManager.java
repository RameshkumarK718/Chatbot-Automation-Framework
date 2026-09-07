
package com.chatbot.utils;



import java.util.ArrayList;

import java.util.List;

import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;



public class ContextStateManager {



    private static final Map<String, List<String>> CONTEXT =

            new ConcurrentHashMap<>();



    private ContextStateManager() {

    }



    public static void addContext(String conversationId, String message) {



        CONTEXT.computeIfAbsent(

                conversationId,

                key -> new ArrayList<>()

        ).add(message == null ? "" : message);

    }



    public static List<String> getContext(String conversationId) {



        return new ArrayList<>(

                CONTEXT.getOrDefault(

                        conversationId,

                        new ArrayList<>()

                )

        );

    }



    public static void setState(String key, String value) {



        List<String> state = new ArrayList<>();

        state.add(value == null ? "" : value);



        CONTEXT.put(key, state);

    }



    public static List<String> getState(String key) {



        return getContext(key);

    }



    public static void clearContext() {



        CONTEXT.clear();

    }

}

