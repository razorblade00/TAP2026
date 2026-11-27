package com.example.fakesapp;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.List;

public class ConversacionesActivity extends AppCompatActivity {

    private TextView tvEstadoConversaciones;
    private RecyclerView rvConversaciones;

    private ConversationRepository repository;
    private ConversationAdapter adapter;

    private String accessToken = "";
    private String currentUserId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversaciones);

        tvEstadoConversaciones = findViewById(R.id.tvEstadoConversaciones);
        rvConversaciones = findViewById(R.id.rvConversaciones);

        rvConversaciones.setLayoutManager(new LinearLayoutManager(this));
        repository = new ConversationRepository();

        inicializarSesion();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentUserId != null && !accessToken.isEmpty()) {
            cargarConversaciones();
        }
    }

    private void inicializarSesion() {
        JSONObject sesion = SessionManager.obtenerSesion(this);
        if (sesion == null) {
            tvEstadoConversaciones.setText("No existe una sesión iniciada.");
            return;
        }

        accessToken = sesion.optString("access_token", "");
        if (accessToken.isEmpty()) {
            tvEstadoConversaciones.setText("No se encontró el token de acceso.");
            return;
        }

        tvEstadoConversaciones.setText("Identificando usuario...");

        SupabaseProfileService.obtenerUsuarioActual(
                accessToken,
                new SupabaseProfileService.ResultadoCallback() {
                    @Override
                    public void onSuccess(String id) {
                        runOnUiThread(() -> {
                            currentUserId = id;
                            adapter = new ConversationAdapter(currentUserId, conversacion -> {
                                Toast.makeText(
                                        ConversacionesActivity.this,
                                        "Conversación con " + conversacion.getOtherDisplayName(),
                                        Toast.LENGTH_SHORT
                                ).show();
                            });
                            rvConversaciones.setAdapter(adapter);
                            cargarConversaciones();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> tvEstadoConversaciones.setText("Error: " + error));
                    }
                }
        );
    }

    private void cargarConversaciones() {
        if (adapter == null) {
            return;
        }

        tvEstadoConversaciones.setText("Cargando conversaciones...");

        repository.fetchUserConversations(
                accessToken,
                new ConversationRepository.FetchConversationsCallback() {
                    @Override
                    public void onSuccess(List<ConversationItem> conversaciones) {
                        adapter.actualizarLista(conversaciones);
                        if (conversaciones.isEmpty()) {
                            tvEstadoConversaciones.setText("No tienes conversaciones aún.");
                        } else {
                            tvEstadoConversaciones.setText(
                                    "Conversaciones: " + conversaciones.size()
                            );
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        tvEstadoConversaciones.setText("Error al cargar: " + errorMessage);
                    }
                }
        );
    }
}