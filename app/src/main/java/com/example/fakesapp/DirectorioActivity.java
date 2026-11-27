package com.example.fakesapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.ArrayList;

import androidx.appcompat.app.AlertDialog;

public class DirectorioActivity extends AppCompatActivity {

    private EditText etBuscarUsuarios;
    private TextView tvEstadoDirectorio;
    private RecyclerView rvUsuarios;

    private UsuarioAdapter adapter;

    private String accessToken;
    private String userId;

    private Handler handler =
            new Handler(Looper.getMainLooper());

    private Runnable busquedaPendiente;

    // Identificador para distinguir las solicitudes de búsqueda
    private int numeroSolicitud = 0;

    // Usuario seleccionado como destinatario
    private Usuario destinatarioSeleccionado;

    private String destinatarioId;
    private String destinatarioNombre;
    private String destinatarioFoto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_directorio);

        // 1. Relacionar elementos XML
        etBuscarUsuarios =
                findViewById(R.id.etBuscarUsuarios);

        tvEstadoDirectorio =
                findViewById(R.id.tvEstadoDirectorio);

        rvUsuarios =
                findViewById(R.id.rvUsuarios);

        // 2. Configurar RecyclerView
        rvUsuarios.setLayoutManager(
                new LinearLayoutManager(this)
        );

        adapter = new UsuarioAdapter(
                this,
                usuario -> seleccionarDestinatario(usuario)
        );
        rvUsuarios.setAdapter(adapter);

        // 3. Configurar búsqueda
        configurarBusqueda();

        // 4. Obtener sesión y cargar directorio
        obtenerSesionUsuario();
    }

    // =========================================
    // OBTENER SESIÓN Y USUARIO ACTUAL
    // =========================================

    private void obtenerSesionUsuario() {

        JSONObject sesion =
                SessionManager.obtenerSesion(this);

        if (sesion == null) {
            mostrarEstado("No existe una sesión iniciada.");
            return;
        }

        accessToken =
                sesion.optString("access_token", "");

        if (accessToken.isEmpty()) {
            mostrarEstado("No se encontró el token de acceso.");
            return;
        }

        tvEstadoDirectorio.setText("Identificando usuario...");

        // Consultar UUID en Supabase Auth
        SupabaseProfileService.obtenerUsuarioActual(
                accessToken,
                new SupabaseProfileService.ResultadoCallback() {

                    @Override
                    public void onSuccess(String id) {

                        runOnUiThread(() -> {

                            userId = id;

                            // Consultar perfiles
                            cargarUsuarios("");
                        });
                    }

                    @Override
                    public void onError(String error) {
                        mostrarEstado(error);
                    }
                }
        );
    }

    // =========================================
    // CONFIGURAR BÚSQUEDA
    // =========================================

    private void configurarBusqueda() {

        etBuscarUsuarios.addTextChangedListener(
                new TextWatcher() {

                    @Override
                    public void beforeTextChanged(
                            CharSequence s,
                            int start,
                            int count,
                            int after) {
                    }

                    @Override
                    public void onTextChanged(
                            CharSequence s,
                            int start,
                            int before,
                            int count) {
                    }

                    @Override
                    public void afterTextChanged(Editable s) {

                        // Cancelar búsqueda pendiente
                        if (busquedaPendiente != null) {
                            handler.removeCallbacks(
                                    busquedaPendiente
                            );
                        }

                        String termino =
                                s.toString().trim();

                        // Esperar un poco antes de consultar
                        busquedaPendiente = () -> {

                            if (userId != null) {
                                cargarUsuarios(termino);
                            }
                        };

                        handler.postDelayed(
                                busquedaPendiente,
                                400
                        );
                    }
                }
        );
    }

    // =========================================
    // CONSULTAR USUARIOS
    // =========================================

    private void cargarUsuarios(String termino) {

        if (userId == null || accessToken == null) {
            return;
        }

        final int solicitudActual = ++numeroSolicitud;

        tvEstadoDirectorio.setText("Buscando usuarios...");

        SupabaseProfileService.consultarUsuarios(
                accessToken,
                userId,
                termino,
                new SupabaseProfileService.UsuariosCallback() {

                    @Override
                    public void onSuccess(
                            ArrayList<Usuario> usuarios) {

                        runOnUiThread(() -> {

                            // Ignorar resultados de búsquedas anteriores
                            if (solicitudActual != numeroSolicitud
                                    || isFinishing()
                                    || isDestroyed()) {
                                return;
                            }

                            adapter.actualizarLista(usuarios);

                            if (usuarios.isEmpty()) {

                                tvEstadoDirectorio.setText(
                                        "No se encontraron usuarios."
                                );

                            } else {

                                tvEstadoDirectorio.setText(
                                        "Usuarios encontrados: "
                                                + usuarios.size()
                                );
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {

                        runOnUiThread(() -> {

                            if (solicitudActual != numeroSolicitud
                                    || isFinishing()
                                    || isDestroyed()) {
                                return;
                            }

                            tvEstadoDirectorio.setText(
                                    "Error: " + error
                            );
                        });
                    }
                }
        );
    }
// =========================================
    // SELECCIONAR DESTINATARIO Y CREAR/OBTENER CHAT
    // =========================================

    private void seleccionarDestinatario(Usuario usuario) {

        new AlertDialog.Builder(this)
                .setTitle("Seleccionar destinatario")
                .setMessage(
                        "¿Deseas seleccionar a " + usuario.getDisplayName()+ " para iniciar una conversación?"
                )
                .setNegativeButton("Cancelar", (dialog, which) -> {
                    dialog.dismiss();
                })
                .setPositiveButton("Seleccionar", (dialog, which) -> {

                    destinatarioSeleccionado = usuario;
                    destinatarioId = usuario.getId();
                    destinatarioNombre = usuario.getDisplayName();
                    destinatarioFoto = usuario.getAvatarUrl();

                    if (accessToken == null || accessToken.isEmpty()) {
                        tvEstadoDirectorio.setText("Error: no hay token de sesión activo.");
                        return;
                    }

                    if (destinatarioId == null || destinatarioId.isEmpty()) {
                        tvEstadoDirectorio.setText("Error: el contacto no tiene un ID válido.");
                        return;
                    }

                    tvEstadoDirectorio.setText(
                            "Preparando conversación con " + destinatarioNombre + "..."
                    );

                    ConversationRepository repository = new ConversationRepository();
                    repository.getOrCreateDirectConversation(
                            accessToken,
                            destinatarioId,
                            new ConversationRepository.CreateConversationCallback() {

                                @Override
                                public void onSuccess(String conversationId) {
                                    tvEstadoDirectorio.setText(
                                            "Conversación lista con " + destinatarioNombre
                                    );

                                    android.widget.Toast.makeText(
                                            DirectorioActivity.this,
                                            "Conversación lista (" + conversationId + ")",
                                            android.widget.Toast.LENGTH_LONG
                                    ).show();
                                }

                                @Override
                                public void onError(String errorMessage) {
                                    tvEstadoDirectorio.setText(
                                            "Error al crear conversación: " + errorMessage
                                    );

                                    android.widget.Toast.makeText(
                                            DirectorioActivity.this,
                                            errorMessage,
                                            android.widget.Toast.LENGTH_LONG
                                    ).show();
                                }
                            }
                    );
                })
                .show();
    }
    // =========================================
    // MOSTRAR ESTADO
    // =========================================

    private void mostrarEstado(String mensaje) {

        runOnUiThread(() -> {
            tvEstadoDirectorio.setText(mensaje);
        });
    }

    @Override
    protected void onDestroy() {

        if (busquedaPendiente != null) {
            handler.removeCallbacks(busquedaPendiente);
        }

        super.onDestroy();
    }
}