package org.YanPl.model;

import java.util.ArrayList;
import java.util.List;

public class DialogueSession {
    private final List<Message> history = new ArrayList<>();
    private long lastActivityTime;

    public DialogueSession() {
        this.lastActivityTime = System.currentTimeMillis();
    }

    public void addMessage(String role, String content) {
        history.add(new Message(role, content));
        this.lastActivityTime = System.currentTimeMillis();
        
        if (history.size() > 20) {
            history.remove(0);
            history.remove(0);
        }
    }

    public List<Message> getHistory() {
        return history;
    }

    public int getEstimatedTokens() {
        int chars = 0;
        for (Message msg : history) {
            chars += msg.getContent().length();
        }
        return chars / 4;
    }

    public long getLastActivityTime() {
        return lastActivityTime;
    }

    public void updateActivity() {
        this.lastActivityTime = System.currentTimeMillis();
    }

    public void clearHistory() {
        history.clear();
    }

    public void removeLastMessage() {
        if (!history.isEmpty()) {
            history.remove(history.size() - 1);
        }
    }

    public static class Message {
        private final String role;
        private final String content;

        public Message(String role, String content) {
            this.role = role != null ? role : "user";
            this.content = content != null ? content : "";
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }
    }
}
