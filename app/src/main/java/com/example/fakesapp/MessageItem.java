package com.example.fakesapp;

public class MessageItem {

    private final String id;
    private final String conversationId;
    private final String senderId;
    private final String content;
    private final String createdAt;

    public MessageItem(
            String id,
            String conversationId,
            String senderId,
            String content,
            String createdAt
    ) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.content = content;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getContent() {
        return content;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public boolean isSentByMe(String currentUserId) {
        return currentUserId != null && currentUserId.equals(senderId);
    }
}