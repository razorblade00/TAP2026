package com.example.fakesapp;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class MessageRepository {

    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String SUPABASE_ANON_KEY = BuildConfig.SUPABASE_PUBLISHABLE_KEY;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ExecutorService executorService;
    private final ScheduledExecutorService heartbeatExecutor;
    private final Handler mainHandler;

    private WebSocket activeWebSocket;
    private ScheduledFuture<?> heartbeatFuture;
    private String activeTopic;
    private String activeUserId;
    private int messageRefCounter = 1;
    private final Set<String> onlineUserIds = new HashSet<>();

    public interface FetchMessagesCallback {
        void onSuccess(List<MessageItem> messages);
        void onError(String errorMessage);
    }

    public interface SendMessageCallback {
        void onSuccess(MessageItem sentMessage);
        void onError(String errorMessage);
    }

    public interface RealtimeChatCallback {
        void onNewMessage(MessageItem message);
        void onPeerTypingChanged(boolean isTyping);
        void onPeerPresenceChanged(boolean isOnline);
        void onError(String errorMessage);
    }

    public MessageRepository() {
        this.httpClient = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
        this.executorService = Executors.newCachedThreadPool();
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
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

    public synchronized void connectRealtime(
            String accessToken,
            String conversationId,
            String currentUserId,
            RealtimeChatCallback callback
    ) {
        disconnectRealtime();

        if (accessToken == null || accessToken.trim().isEmpty()
                || conversationId == null || conversationId.trim().isEmpty()
                || currentUserId == null || currentUserId.trim().isEmpty()) {
            return;
        }

        this.activeUserId = currentUserId.trim();
        this.activeTopic = "realtime:chat:" + conversationId.trim();
        this.onlineUserIds.clear();

        String wsBaseUrl = SUPABASE_URL.trim()
                .replaceFirst("^https://", "wss://")
                .replaceFirst("^http://", "ws://");
        if (wsBaseUrl.endsWith("/")) {
            wsBaseUrl = wsBaseUrl.substring(0, wsBaseUrl.length() - 1);
        }

        String wsUrl = wsBaseUrl + "/realtime/v1/websocket?apikey=" + SUPABASE_ANON_KEY + "&vsn=1.0.0";

        Request request = new Request.Builder()
                .url(wsUrl)
                .build();

        activeWebSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                enviarJoinCanal(webSocket, accessToken, conversationId.trim());
                iniciarHeartbeat(webSocket);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                procesarMensajeRealtime(text, callback);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                if (callback != null) {
                    String msg = t != null && t.getMessage() != null ? t.getMessage() : "Conexión interrumpida";
                    mainHandler.post(() -> {
                        callback.onPeerPresenceChanged(false);
                        callback.onPeerTypingChanged(false);
                        callback.onError("Realtime: " + msg);
                    });
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                if (callback != null) {
                    mainHandler.post(() -> {
                        callback.onPeerPresenceChanged(false);
                        callback.onPeerTypingChanged(false);
                    });
                }
            }
        });
    }

    public synchronized void sendTypingStatus(boolean isTyping) {
        if (activeWebSocket == null || activeTopic == null || activeUserId == null) {
            return;
        }

        try {
            JSONObject payloadInner = new JSONObject();
            payloadInner.put("user_id", activeUserId);
            payloadInner.put("typing", isTyping);

            JSONObject payload = new JSONObject();
            payload.put("type", "broadcast");
            payload.put("event", "typing");
            payload.put("payload", payloadInner);

            JSONObject frame = new JSONObject();
            frame.put("topic", activeTopic);
            frame.put("event", "broadcast");
            frame.put("payload", payload);
            frame.put("ref", String.valueOf(messageRefCounter++));

            activeWebSocket.send(frame.toString());
        } catch (Exception ignored) {
        }
    }

    public synchronized void disconnectRealtime() {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(true);
            heartbeatFuture = null;
        }
        if (activeWebSocket != null) {
            try {
                activeWebSocket.close(1000, "Chat cerrado");
            } catch (Exception ignored) {
            }
            activeWebSocket = null;
        }
        onlineUserIds.clear();
        activeTopic = null;
        activeUserId = null;
    }

    private void enviarJoinCanal(WebSocket webSocket, String accessToken, String conversationId) {
        try {
            JSONObject insertBinding = new JSONObject();
            insertBinding.put("event", "INSERT");
            insertBinding.put("schema", "public");
            insertBinding.put("table", "messages");
            insertBinding.put("filter", "conversation_id=eq." + conversationId);

            JSONArray postgresChanges = new JSONArray();
            postgresChanges.put(insertBinding);

            JSONObject broadcastConfig = new JSONObject();
            broadcastConfig.put("ack", false);
            broadcastConfig.put("self", false);

            JSONObject presenceConfig = new JSONObject();
            presenceConfig.put("key", activeUserId);

            JSONObject config = new JSONObject();
            config.put("postgres_changes", postgresChanges);
            config.put("broadcast", broadcastConfig);
            config.put("presence", presenceConfig);

            JSONObject payload = new JSONObject();
            payload.put("config", config);
            payload.put("access_token", accessToken);

            JSONObject joinFrame = new JSONObject();
            joinFrame.put("topic", activeTopic);
            joinFrame.put("event", "phx_join");
            joinFrame.put("payload", payload);
            joinFrame.put("ref", String.valueOf(messageRefCounter++));

            webSocket.send(joinFrame.toString());
            enviarPresenceTrack(webSocket);
        } catch (Exception ignored) {
        }
    }

    private void enviarPresenceTrack(WebSocket webSocket) {
        if (activeTopic == null || activeUserId == null) {
            return;
        }
        try {
            JSONObject userState = new JSONObject();
            userState.put("user_id", activeUserId);
            userState.put("online", true);

            JSONObject payload = new JSONObject();
            payload.put("type", "presence");
            payload.put("event", "track");
            payload.put("payload", userState);

            JSONObject frame = new JSONObject();
            frame.put("topic", activeTopic);
            frame.put("event", "presence");
            frame.put("payload", payload);
            frame.put("ref", String.valueOf(messageRefCounter++));

            webSocket.send(frame.toString());
        } catch (Exception ignored) {
        }
    }

    private void iniciarHeartbeat(WebSocket webSocket) {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(true);
        }
        heartbeatFuture = heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                JSONObject hb = new JSONObject();
                hb.put("topic", "phoenix");
                hb.put("event", "heartbeat");
                hb.put("payload", new JSONObject());
                hb.put("ref", String.valueOf(messageRefCounter++));
                webSocket.send(hb.toString());
            } catch (Exception ignored) {
            }
        }, 25, 25, TimeUnit.SECONDS);
    }

    private void procesarMensajeRealtime(String rawText, RealtimeChatCallback callback) {
        if (rawText == null || callback == null) {
            return;
        }
        try {
            JSONObject root = new JSONObject(rawText);
            String event = root.optString("event", "");
            JSONObject payload = root.optJSONObject("payload");
            if (payload == null) {
                return;
            }

            if ("postgres_changes".equals(event)) {
                JSONObject data = payload.optJSONObject("data");
                if (data != null && "INSERT".equals(data.optString("type", ""))) {
                    JSONObject record = data.optJSONObject("record");
                    if (record != null) {
                        MessageItem item = parseMessage(record);
                        mainHandler.post(() -> callback.onNewMessage(item));
                    }
                }
            } else if ("broadcast".equals(event)) {
                String bEvent = payload.optString("event", "");
                if ("typing".equals(bEvent)) {
                    JSONObject inner = payload.optJSONObject("payload");
                    if (inner != null) {
                        String sender = inner.optString("user_id", "");
                        boolean typing = inner.optBoolean("typing", false);
                        if (!sender.isEmpty() && !sender.equals(activeUserId)) {
                            mainHandler.post(() -> callback.onPeerTypingChanged(typing));
                        }
                    }
                }
            } else if ("presence_state".equals(event)) {
                onlineUserIds.clear();
                agregarClavesPresencia(payload);
                notificarPresenciaOtroUsuario(callback);
            } else if ("presence_diff".equals(event)) {
                JSONObject joins = payload.optJSONObject("joins");
                if (joins != null) {
                    agregarClavesPresencia(joins);
                }
                JSONObject leaves = payload.optJSONObject("leaves");
                if (leaves != null) {
                    quitarClavesPresencia(leaves);
                }
                notificarPresenciaOtroUsuario(callback);
            }
        } catch (Exception ignored) {
        }
    }

    private void agregarClavesPresencia(JSONObject obj) {
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key != null && !key.isEmpty()) {
                onlineUserIds.add(key);
            }
        }
    }

    private void quitarClavesPresencia(JSONObject obj) {
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key != null) {
                onlineUserIds.remove(key);
            }
        }
    }

    private void notificarPresenciaOtroUsuario(RealtimeChatCallback callback) {
        boolean peerOnline = false;
        for (String uid : onlineUserIds) {
            if (uid != null && !uid.equals(activeUserId)) {
                peerOnline = true;
                break;
            }
        }
        final boolean finalPeerOnline = peerOnline;
        mainHandler.post(() -> callback.onPeerPresenceChanged(finalPeerOnline));
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