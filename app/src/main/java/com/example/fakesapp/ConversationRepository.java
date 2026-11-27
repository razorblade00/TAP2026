package com.example.fakesapp;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ConversationRepository {

    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface CreateConversationCallback {
        void onSuccess(String conversationId);
        void onError(String errorMessage);
    }

    public interface FetchConversationsCallback {
        void onSuccess(List<ConversationItem> conversaciones);
        void onError(String errorMessage);
    }

    public void getOrCreateDirectConversation(
            String accessToken,
            String targetUserId,
            CreateConversationCallback callback
    ) {
        executor.execute(() -> {
            try {
                JSONObject bodyJson = new JSONObject();
                bodyJson.put("p_target_user_id", targetUserId);

                RequestBody body = RequestBody.create(
                        bodyJson.toString(),
                        JSON_MEDIA_TYPE
                );

                String url = BuildConfig.SUPABASE_URL
                        + "/rest/v1/rpc/get_or_create_direct_conversation";

                Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .addHeader("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .addHeader("Content-Type", "application/json")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String responseBody = response.body() != null
                            ? response.body().string()
                            : "";

                    if (!response.isSuccessful()) {
                        mainHandler.post(() -> callback.onError(
                                "Error HTTP " + response.code() + ": " + responseBody
                        ));
                        return;
                    }

                    String conversationId = responseBody.replace("\"", "").trim();
                    mainHandler.post(() -> callback.onSuccess(conversationId));
                }
            } catch (Exception e) {
                String mensaje = e.getMessage() != null
                        ? e.getMessage()
                        : "Error al crear o abrir la conversación.";
                mainHandler.post(() -> callback.onError(mensaje));
            }
        });
    }

    public void fetchUserConversations(
            String accessToken,
            FetchConversationsCallback callback
    ) {
        executor.execute(() -> {
            try {
                String url = BuildConfig.SUPABASE_URL
                        + "/rest/v1/rpc/get_user_conversations";

                RequestBody emptyBody = RequestBody.create("{}", JSON_MEDIA_TYPE);

                Request request = new Request.Builder()
                        .url(url)
                        .post(emptyBody)
                        .addHeader("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .addHeader("Content-Type", "application/json")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String responseBody = response.body() != null
                            ? response.body().string()
                            : "[]";

                    if (!response.isSuccessful()) {
                        mainHandler.post(() -> callback.onError(
                                "Error HTTP " + response.code() + ": " + responseBody
                        ));
                        return;
                    }

                    List<ConversationItem> lista = parseConversations(responseBody);
                    mainHandler.post(() -> callback.onSuccess(lista));
                }
            } catch (Exception e) {
                String mensaje = e.getMessage() != null
                        ? e.getMessage()
                        : "Error al cargar las conversaciones.";
                mainHandler.post(() -> callback.onError(mensaje));
            }
        });
    }

    private List<ConversationItem> parseConversations(String jsonString) throws Exception {
        List<ConversationItem> items = new ArrayList<>();
        JSONArray array = new JSONArray(jsonString);

        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);

            items.add(new ConversationItem(
                    obj.getString("conversation_id"),
                    obj.optString("conversation_type", "direct"),
                    obj.isNull("other_user_id") ? null : obj.getString("other_user_id"),
                    obj.isNull("other_display_name")
                            ? "Usuario"
                            : obj.getString("other_display_name"),
                    obj.isNull("other_avatar_url")
                            ? null
                            : obj.getString("other_avatar_url"),
                    obj.isNull("last_message_content")
                            ? null
                            : obj.getString("last_message_content"),
                    obj.isNull("last_message_created_at")
                            ? null
                            : obj.getString("last_message_created_at"),
                    obj.isNull("last_message_sender_id")
                            ? null
                            : obj.getString("last_message_sender_id"),
                    obj.isNull("updated_at") ? null : obj.getString("updated_at")
            ));
        }

        return items;
    }
}