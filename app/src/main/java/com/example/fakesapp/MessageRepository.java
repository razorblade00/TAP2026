package com.example.fakesapp;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MessageRepository {

    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String SUPABASE_ANON_KEY = BuildConfig.SUPABASE_PUBLISHABLE_KEY;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    public interface FetchMessagesCallback {
        void onSuccess(List<MessageItem> messages);
        void onError(String errorMessage);
    }

    public interface SendMessageCallback {
        void onSuccess(MessageItem sentMessage);
        void onError(String errorMessage);
    }

    public MessageRepository() {
        this.httpClient = new OkHttpClient();
        this.executorService = Executors.newCachedThreadPool();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void fetchMessages(
            String accessToken,
            String conversationId,
            FetchMessagesCallback callback
    ) {
        if (accessToken == null || accessToken.trim().isEmpty()) {
            postErrorFetch(callback, "Sesión no válida.");
            return;
        }
        if (conversationId == null || conversationId.trim().isEmpty()) {
            postErrorFetch(callback, "Conversación no válida.");
            return;
        }

        executorService.execute(() -> {
            HttpUrl url = HttpUrl.parse(SUPABASE_URL + "/rest/v1/messages")
                    .newBuilder()
                    .addQueryParameter("select", "id,conversation_id,sender_id,content,created_at")
                    .addQueryParameter("conversation_id", "eq." + conversationId)
                    .addQueryParameter("order", "created_at.asc")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .get()
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    postErrorFetch(callback, "Error de red al cargar mensajes: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String body = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        postErrorFetch(callback, "Error (" + response.code() + "): " + body);
                        return;
                    }

                    try {
                        JSONArray array = new JSONArray(body);
                        List<MessageItem> items = new ArrayList<>();
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject obj = array.optJSONObject(i);
                            if (obj != null) {
                                items.add(parseMessage(obj));
                            }
                        }
                        mainHandler.post(() -> callback.onSuccess(items));
                    } catch (Exception e) {
                        postErrorFetch(callback, "Error al leer mensajes: " + e.getMessage());
                    }
                }
            });
        });
    }

    public void sendMessage(
            String accessToken,
            String conversationId,
            String senderId,
            String content,
            SendMessageCallback callback
    ) {
        if (accessToken == null || accessToken.trim().isEmpty()) {
            postErrorSend(callback, "Sesión no válida.");
            return;
        }
        if (conversationId == null || conversationId.trim().isEmpty()) {
            postErrorSend(callback, "Conversación no válida.");
            return;
        }
        if (senderId == null || senderId.trim().isEmpty()) {
            postErrorSend(callback, "Remitente no válido.");
            return;
        }
        if (content == null || content.trim().isEmpty()) {
            postErrorSend(callback, "El mensaje no puede estar vacío.");
            return;
        }

        executorService.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("conversation_id", conversationId);
                payload.put("sender_id", senderId);
                payload.put("content", content.trim());

                RequestBody requestBody = RequestBody.create(payload.toString(), JSON);

                Request request = new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/messages")
                        .addHeader("apikey", SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=representation")
                        .post(requestBody)
                        .build();

                httpClient.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        postErrorSend(callback, "Error de red al enviar mensaje: " + e.getMessage());
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        String body = response.body() != null ? response.body().string() : "";
                        if (!response.isSuccessful()) {
                            postErrorSend(callback, "Error (" + response.code() + "): " + body);
                            return;
                        }

                        try {
                            JSONArray array = new JSONArray(body);
                            if (array.length() > 0) {
                                MessageItem sent = parseMessage(array.getJSONObject(0));
                                mainHandler.post(() -> callback.onSuccess(sent));
                            } else {
                                postErrorSend(callback, "Respuesta vacía al enviar el mensaje.");
                            }
                        } catch (Exception e) {
                            postErrorSend(callback, "Error al procesar el mensaje enviado: " + e.getMessage());
                        }
                    }
                });
            } catch (Exception e) {
                postErrorSend(callback, "Error al preparar el envío: " + e.getMessage());
            }
        });
    }

    private MessageItem parseMessage(JSONObject obj) {
        return new MessageItem(
                obj.optString("id", ""),
                obj.optString("conversation_id", ""),
                obj.optString("sender_id", ""),
                obj.optString("content", ""),
                obj.optString("created_at", "")
        );
    }

    private void postErrorFetch(FetchMessagesCallback callback, String message) {
        mainHandler.post(() -> callback.onError(message));
    }

    private void postErrorSend(SendMessageCallback callback, String message) {
        mainHandler.post(() -> callback.onError(message));
    }
}