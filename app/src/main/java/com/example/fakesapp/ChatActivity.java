package com.example.fakesapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_CONVERSATION_ID = "conversation_id";
    public static final String EXTRA_OTHER_USER_NAME = "other_user_name";
    public static final String EXTRA_OTHER_USER_AVATAR = "other_user_avatar";

    private View barraSuperiorChat;
    private View barraInferiorChat;
    private ImageButton btnVolverChat;
    private ImageView ivAvatarContactoChat;
    private TextView tvNombreContactoChat;
    private TextView tvEstadoChat;
    private RecyclerView rvMensajes;
    private EditText etMensaje;
    private ImageButton btnEnviarMensaje;

    private MessageRepository messageRepository;
    private MessageAdapter messageAdapter;

    private final ExecutorService avatarExecutor = Executors.newSingleThreadExecutor();

    private String accessToken = "";
    private String currentUserId = null;
    private String conversationId = "";
    private String otherUserName = "Chat";
    private String otherUserAvatar = "";
    private boolean enviando = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        barraSuperiorChat = findViewById(R.id.barraSuperiorChat);
        barraInferiorChat = findViewById(R.id.barraInferiorChat);
        btnVolverChat = findViewById(R.id.btnVolverChat);
        ivAvatarContactoChat = findViewById(R.id.ivAvatarContactoChat);
        tvNombreContactoChat = findViewById(R.id.tvNombreContactoChat);
        tvEstadoChat = findViewById(R.id.tvEstadoChat);
        rvMensajes = findViewById(R.id.rvMensajes);
        etMensaje = findViewById(R.id.etMensaje);
        btnEnviarMensaje = findViewById(R.id.btnEnviarMensaje);

        configurarInsets();

        conversationId = getIntent().getStringExtra(EXTRA_CONVERSATION_ID);
        if (conversationId == null) {
            conversationId = "";
        }

        otherUserName = getIntent().getStringExtra(EXTRA_OTHER_USER_NAME);
        if (otherUserName == null || otherUserName.trim().isEmpty()) {
            otherUserName = "Chat";
        }

        otherUserAvatar = getIntent().getStringExtra(EXTRA_OTHER_USER_AVATAR);
        if (otherUserAvatar == null) {
            otherUserAvatar = "";
        }

        tvNombreContactoChat.setText(otherUserName);
        btnVolverChat.setOnClickListener(v -> finish());

        if (!otherUserAvatar.trim().isEmpty()) {
            cargarAvatarContacto(otherUserAvatar);
        }

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMensajes.setLayoutManager(layoutManager);

        messageRepository = new MessageRepository();

        JSONObject sesion = SessionManager.obtenerSesion(this);
        if (sesion == null) {
            tvEstadoChat.setText("Sin sesión activa");
            btnEnviarMensaje.setEnabled(false);
            return;
        }

        accessToken = sesion.optString("access_token", "");
        if (accessToken.isEmpty()) {
            tvEstadoChat.setText("Token de sesión no disponible");
            btnEnviarMensaje.setEnabled(false);
            return;
        }

        btnEnviarMensaje.setOnClickListener(v -> intentarEnviarMensaje());

        inicializarUsuarioYMensajes();
    }

    private void configurarInsets() {
        if (barraSuperiorChat != null) {
            final int left = barraSuperiorChat.getPaddingLeft();
            final int top = barraSuperiorChat.getPaddingTop();
            final int right = barraSuperiorChat.getPaddingRight();
            final int bottom = barraSuperiorChat.getPaddingBottom();

            ViewCompat.setOnApplyWindowInsetsListener(barraSuperiorChat, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(left, top + bars.top, right, bottom);
                return insets;
            });
        }

        if (barraInferiorChat != null) {
            final int left = barraInferiorChat.getPaddingLeft();
            final int top = barraInferiorChat.getPaddingTop();
            final int right = barraInferiorChat.getPaddingRight();
            final int bottom = barraInferiorChat.getPaddingBottom();

            ViewCompat.setOnApplyWindowInsetsListener(barraInferiorChat, (v, insets) -> {
                Insets bars = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime()
                );
                v.setPadding(left, top, right, bottom + bars.bottom);
                return insets;
            });
        }
    }

    private void inicializarUsuarioYMensajes() {
        tvEstadoChat.setText("Conectando...");

        SupabaseProfileService.obtenerUsuarioActual(
                accessToken,
                new SupabaseProfileService.ResultadoCallback() {
                    @Override
                    public void onSuccess(String id) {
                        runOnUiThread(() -> {
                            currentUserId = id;
                            messageAdapter = new MessageAdapter(currentUserId);
                            rvMensajes.setAdapter(messageAdapter);
                            cargarMensajes();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> tvEstadoChat.setText("Error: " + error));
                    }
                }
        );
    }

    private void cargarMensajes() {
        if (messageAdapter == null) {
            return;
        }

        tvEstadoChat.setText("Cargando mensajes...");

        messageRepository.fetchMessages(
                accessToken,
                conversationId,
                new MessageRepository.FetchMessagesCallback() {
                    @Override
                    public void onSuccess(List<MessageItem> messages) {
                        messageAdapter.actualizarMensajes(messages);
                        if (messages.isEmpty()) {
                            tvEstadoChat.setText("Sin mensajes aún");
                        } else {
                            tvEstadoChat.setText("En conversación");
                            rvMensajes.scrollToPosition(messageAdapter.getItemCount() - 1);
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        tvEstadoChat.setText("Error al cargar historial");
                        Toast.makeText(ChatActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void intentarEnviarMensaje() {
        if (enviando || currentUserId == null || currentUserId.isEmpty()) {
            return;
        }

        String texto = etMensaje.getText().toString().trim();
        if (texto.isEmpty()) {
            return;
        }

        enviando = true;
        btnEnviarMensaje.setEnabled(false);

        messageRepository.sendMessage(
                accessToken,
                conversationId,
                currentUserId,
                texto,
                new MessageRepository.SendMessageCallback() {
                    @Override
                    public void onSuccess(MessageItem sentMessage) {
                        enviando = false;
                        btnEnviarMensaje.setEnabled(true);
                        etMensaje.setText("");
                        if (messageAdapter != null) {
                            messageAdapter.agregarMensaje(sentMessage);
                            rvMensajes.scrollToPosition(messageAdapter.getItemCount() - 1);
                        }
                        tvEstadoChat.setText("En conversación");
                    }

                    @Override
                    public void onError(String errorMessage) {
                        enviando = false;
                        btnEnviarMensaje.setEnabled(true);
                        Toast.makeText(ChatActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void cargarAvatarContacto(String urlString) {
        avatarExecutor.execute(() -> {
            HttpURLConnection conexion = null;
            try {
                URL direccion = new URL(urlString);
                conexion = (HttpURLConnection) direccion.openConnection();
                conexion.setConnectTimeout(10000);
                conexion.setReadTimeout(10000);
                conexion.setRequestMethod("GET");

                try (InputStream entrada = conexion.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(entrada);
                    if (bitmap != null) {
                        runOnUiThread(() -> {
                            if (!isFinishing() && !isDestroyed()) {
                                ivAvatarContactoChat.setImageBitmap(bitmap);
                            }
                        });
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (conexion != null) {
                    conexion.disconnect();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        avatarExecutor.shutdownNow();
        super.onDestroy();
    }
}