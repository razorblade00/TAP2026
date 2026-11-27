package com.example.fakesapp;

public class ConversationItem {

    private final String conversationId;
    private final String conversationType;
    private final String otherUserId;
    private final String otherDisplayName;
    private final String otherAvatarUrl;
    private final String lastMessageContent;
    private final String lastMessageCreatedAt;
    private final String lastMessageSenderId;
    private final String updatedAt;

    public ConversationItem(
            String conversationId,
            String conversationType,
            String otherUserId,
            String otherDisplayName,
            String otherAvatarUrl,
            String lastMessageContent,
            String lastMessageCreatedAt,
            String lastMessageSenderId,
            String updatedAt
    ) {
        this.conversationId = conversationId;
        this.conversationType = conversationType;
        this.otherUserId = otherUserId;
        this.otherDisplayName = otherDisplayName;
        this.otherAvatarUrl = otherAvatarUrl;
        this.lastMessageContent = lastMessageContent;
        this.lastMessageCreatedAt = lastMessageCreatedAt;
        this.lastMessageSenderId = lastMessageSenderId;
        this.updatedAt = updatedAt;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getConversationType() {
        return conversationType;
    }

    public String getOtherUserId() {
        return otherUserId;
    }

    public String getOtherDisplayName() {
        return otherDisplayName;
    }

    public String getOtherAvatarUrl() {
        return otherAvatarUrl;
    }

    public String getLastMessageContent() {
        return lastMessageContent;
    }

    public String getLastMessageCreatedAt() {
        return lastMessageCreatedAt;
    }

    public String getLastMessageSenderId() {
        return lastMessageSenderId;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }
}