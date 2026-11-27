package com.example.fakesapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeActivity extends AppCompatActivity {

    private View barraSuperiorHome;
    private ImageView ivPerfilPropio;
    private EditText etBuscarEnHome;
    private TextView tvEstadoHome;
    private RecyclerView rvConversaciones;
    private RecyclerView rvResultadosBusqueda;
    private FloatingActionButton fabDirectorio;

    private ConversationAdapter conversacionAdapter;
    private UsuarioAdapter busquedaContactoAdapter;
    private ConversationRepository conversationRepository;

    private final ExecutorService avatarExecutor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable busquedaPendiente;
    private int numeroSolicitud = 0;

    private String accessToken = "";
    private String currentUserId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // 1. Vincular vistas
        barraSuperiorHome = findViewById(R.id.barraSuperiorHome);
        ivPerfilPropio = findViewById(R.id.ivPerfilPropio);
        etBuscarEnHome = findViewById(R.id.etBuscarEnHome);
        tvEstadoHome = findViewById(R.id.tvEstadoHome);
        rvConversaciones = findViewById(R.id.rvConversaciones);
        rvResultadosBusqueda = findViewById(R.id.rvResultadosBusqueda);
        fabDirectorio = findViewById(R.id.fabDirectorio);

        // 2. Respetar barra de estado superior (status bar / notch)
        configurarInsetsBarraSuperior();

        // 3. Validar sesión local
        JSONObject sesion = SessionManager.obtenerSesion(this);
        if (sesion == null) {
            regresarLogin();
            return;
        }

        accessToken = sesion.optString("access_token", "");
        if (accessToken.isEmpty()) {
            regresarLogin();
            return;
        }

        conversationRepository = new ConversationRepository();

        // 4. Configurar RecyclerViews
        rvConversaciones.setLayoutManager(new LinearLayoutManager(this));
        rvResultadosBusqueda.setLayoutManager(new LinearLayoutManager(this));

        busquedaContactoAdapter = new UsuarioAdapter(this, this::iniciarConversacionConContacto);
        rvResultadosBusqueda.setAdapter(busquedaContactoAdapter);

        // 5. Menú desplegable en foto de perfil propia
        ivPerfilPropio.setOnClickListener(this::mostrarMenuPerfil);

        // 6. Botón flotante hacia el directorio completo
        fabDirectorio.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, DirectorioActivity.class);
            startActivity(intent);
        });

        // 7. Configurar buscador superior
        configurarBuscador();

        // 8. Identificar usuario y cargar perfil + conversaciones
        inicializarUsuario();
    }

    private void configurarInsetsBarraSuperior() {
        if (barraSuperiorHome == null) {
            return;
        }

        final int paddingLeftOriginal = barraSuperiorHome.getPaddingLeft();
        final int paddingTopOriginal = barraSuperiorHome.getPaddingTop();
        final int paddingRightOriginal = barraSuperiorHome.getPaddingRight();
        final int paddingBottomOriginal = barraSuperiorHome.getPaddingBottom();
        final int margenExtraTopPx = (int) (8 * getResources().getDisplayMetrics().density);

        ViewCompat.setOnApplyWindowInsetsListener(barraSuperiorHome, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    paddingLeftOriginal,
                    paddingTopOriginal + systemBars.top + margenExtraTopPx,
                    paddingRightOriginal,
                    paddingBottomOriginal
            );
            return insets;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentUserId != null && !accessToken.isEmpty()) {
            cargarPerfilPropio();
            if (etBuscarEnHome.getText().toString().trim().isEmpty()) {
                cargarConversaciones();
            }
        }
    }

    // =========================================
    // IDENTIFICAR USUARIO Y PERFIL PROPIO
    // =========================================

    private void inicializarUsuario() {
        tvEstadoHome.setText("Cargando conversaciones...");

        SupabaseProfileService.obtenerUsuarioActual(
                accessToken,
                new SupabaseProfileService.ResultadoCallback() {
                    @Override
                    public void onSuccess(String id) {
                        runOnUiThread(() -> {
                            currentUserId = id;

                            conversacionAdapter = new ConversationAdapter(currentUserId, conversacion ->
                                    abrirChat(
                                            conversacion.getConversationId(),
                                            conversacion.getOtherDisplayName(),
                                            conversacion.getOtherAvatarUrl()
                                    )
                            );
                            rvConversaciones.setAdapter(conversacionAdapter);

                            cargarPerfilPropio();
                            cargarConversaciones();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> tvEstadoHome.setText("Error de sesión: " + error));
                    }
                }
        );
    }

    private void cargarPerfilPropio() {
        if (currentUserId == null) {
            return;
        }

        SupabaseProfileService.consultarPerfil(
                accessToken,
                currentUserId,
                new SupabaseProfileService.PerfilCallback() {
                    @Override
                    public void onSuccess(String nombre, String avatarUrl) {
                        if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
                            cargarFotografiaPerfil(avatarUrl);
                        } else {
                            runOnUiThread(() ->
                                    ivPerfilPropio.setImageResource(android.R.drawable.ic_menu_camera)
                            );
                        }
                    }

                    @Override
                    public void onError(String error) {
                        // Mantiene el ícono por defecto si no se puede cargar el perfil
                    }
                }
        );
    }

    private void cargarFotografiaPerfil(String urlString) {
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
                                ivPerfilPropio.setImageBitmap(bitmap);
                            }
                        });
                    }
                }
            } catch (Exception ignored) {
                // Mantiene el ícono por defecto si falla la descarga
            } finally {
                if (conexion != null) {
                    conexion.disconnect();
                }
            }
        });
    }

    // =========================================
    // MENU DESPLEGABLE EN FOTO DE PERFIL
    // =========================================

    private void mostrarMenuPerfil(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "Editar perfil");
        popup.getMenu().add(0, 2, 1, "Cerrar sesión");

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                startActivity(new Intent(HomeActivity.this, EditarPerfilActivity.class));
                return true;
            } else if (item.getItemId() == 2) {
                cerrarSesion();
                return true;
            }
            return false;
        });

        popup.show();
    }

    // =========================================
    // BUSCADOR SUPERIOR DE CONTACTOS
    // =========================================

    private void configurarBuscador() {
        etBuscarEnHome.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (busquedaPendiente != null) {
                    handler.removeCallbacks(busquedaPendiente);
                }

                String termino = s.toString().trim();

                if (termino.isEmpty()) {
                    rvResultadosBusqueda.setVisibility(View.GONE);
                    rvConversaciones.setVisibility(View.VISIBLE);
                    tvEstadoHome.setText("Chats recientes");
                    cargarConversaciones();
                    return;
                }

                rvConversaciones.setVisibility(View.GONE);
                rvResultadosBusqueda.setVisibility(View.VISIBLE);
                tvEstadoHome.setText("Buscando contactos...");

                busquedaPendiente = () -> buscarContactos(termino);
                handler.postDelayed(busquedaPendiente, 350);
            }
        });
    }

    private void buscarContactos(String termino) {
        if (currentUserId == null) {
            return;
        }

        final int solicitudActual = ++numeroSolicitud;

        SupabaseProfileService.consultarUsuarios(
                accessToken,
                currentUserId,
                termino,
                new SupabaseProfileService.UsuariosCallback() {
                    @Override
                    public void onSuccess(ArrayList<Usuario> usuarios) {
                        runOnUiThread(() -> {
                            if (solicitudActual != numeroSolicitud || isFinishing() || isDestroyed()) {
                                return;
                            }
                            busquedaContactoAdapter.actualizarLista(usuarios);
                            if (usuarios.isEmpty()) {
                                tvEstadoHome.setText("No se encontraron contactos con \"" + termino + "\"");
                            } else {
                                tvEstadoHome.setText(
                                        "Contactos (" + usuarios.size() + ") — Toca para chatear"
                                );
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            if (solicitudActual != numeroSolicitud || isFinishing() || isDestroyed()) {
                                return;
                            }
                            tvEstadoHome.setText("Error al buscar: " + error);
                        });
                    }
                }
        );
    }

    private void iniciarConversacionConContacto(Usuario contacto) {
        if (contacto == null || contacto.getId() == null || contacto.getId().isEmpty()) {
            Toast.makeText(this, "Contacto inválido.", Toast.LENGTH_SHORT).show();
            return;
        }

        tvEstadoHome.setText("Preparando conversación con " + contacto.getDisplayName() + "...");

        conversationRepository.getOrCreateDirectConversation(
                accessToken,
                contacto.getId(),
                new ConversationRepository.CreateConversationCallback() {
                    @Override
                    public void onSuccess(String conversationId) {
                        etBuscarEnHome.setText("");
                        abrirChat(
                                conversationId,
                                contacto.getDisplayName(),
                                contacto.getAvatarUrl()
                        );
                    }

                    @Override
                    public void onError(String errorMessage) {
                        tvEstadoHome.setText("Error al crear conversación: " + errorMessage);
                        Toast.makeText(HomeActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    // =========================================
    // CARGAR CONVERSACIONES (FASE 5)
    // =========================================

    private void cargarConversaciones() {
        if (conversacionAdapter == null) {
            return;
        }

        conversationRepository.fetchUserConversations(
                accessToken,
                new ConversationRepository.FetchConversationsCallback() {
                    @Override
                    public void onSuccess(List<ConversationItem> conversaciones) {
                        conversacionAdapter.actualizarLista(conversaciones);
                        if (conversaciones.isEmpty()) {
                            tvEstadoHome.setText(
                                    "Aún no tienes chats. Busca un contacto arriba o abre el directorio."
                            );
                        } else {
                            tvEstadoHome.setText("Chats recientes (" + conversaciones.size() + ")");
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        tvEstadoHome.setText("Error al cargar chats: " + errorMessage);
                    }
                }
        );
    }

    private void abrirChat(String conversationId, String otherUserName, String otherUserAvatar) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            Toast.makeText(this, "No se pudo abrir la conversación.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(HomeActivity.this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conversationId);
        intent.putExtra(ChatActivity.EXTRA_OTHER_USER_NAME, otherUserName);
        intent.putExtra(ChatActivity.EXTRA_OTHER_USER_AVATAR, otherUserAvatar);
        startActivity(intent);
    }

    // =========================================
    // CERRAR SESION
    // =========================================

    private void cerrarSesion() {
        JSONObject sesion = SessionManager.obtenerSesion(this);
        String tokenRevocacion = accessToken;
        if (sesion != null && !sesion.optString("access_token", "").isEmpty()) {
            tokenRevocacion = sesion.optString("access_token", "");
        }

        SessionManager.borrarSesion(this);
        regresarLogin();

        Toast.makeText(
                this,
                "Sesión cerrada en este dispositivo.",
                Toast.LENGTH_SHORT
        ).show();

        if (!tokenRevocacion.isEmpty()) {
            SupabaseAuthService.cerrarSesion(
                    tokenRevocacion,
                    new SupabaseAuthService.AuthCallback() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            // Supabase confirmó el cierre de sesión.
                        }

                        @Override
                        public void onError(String message) {
                            runOnUiThread(() -> Toast.makeText(
                                    HomeActivity.this,
                                    "La sesión se cerró localmente, "
                                            + "pero no se pudo confirmar "
                                            + "la revocación en Supabase.",
                                    Toast.LENGTH_LONG
                            ).show());
                        }
                    }
            );
        }
    }

    private void regresarLogin() {
        Intent intent = new Intent(HomeActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (busquedaPendiente != null) {
            handler.removeCallbacks(busquedaPendiente);
        }
        avatarExecutor.shutdownNow();
        super.onDestroy();
    }
}