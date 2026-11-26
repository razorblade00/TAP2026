
package com.example.fakesapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class EditarPerfilActivity extends AppCompatActivity {

    private ImageView imgAvatar;
    private EditText etNombrePerfil;
    private Button btnSeleccionarFoto;
    private Button btnGuardarPerfil;

    private Uri imagenSeleccionada;

    private ActivityResultLauncher<String> selectorImagen;

    private String accessToken;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editar_perfil);

        // 1. Relacionar elementos XML
        imgAvatar = findViewById(R.id.imgAvatar);
        etNombrePerfil = findViewById(R.id.etNombrePerfil);
        btnSeleccionarFoto = findViewById(R.id.btnSeleccionarFoto);
        btnGuardarPerfil = findViewById(R.id.btnGuardarPerfil);

        // 2. Configurar selector de imágenes
        selectorImagen = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        imagenSeleccionada = uri;
                        imgAvatar.setImageURI(uri);
                    }
                }
        );

        // 3. Abrir galería
        btnSeleccionarFoto.setOnClickListener(v -> {
            selectorImagen.launch("image/*");
        });

        // 4. Guardar perfil
        btnGuardarPerfil.setOnClickListener(v -> {
            guardarPerfil();
        });

        // 5. Cargar información existente
        cargarPerfilActual();
    }

    // =========================================
    // CARGAR PERFIL EXISTENTE
    // =========================================

    private void cargarPerfilActual() {

        JSONObject sesion =
                SessionManager.obtenerSesion(this);

        if (sesion == null) {
            mostrarError("No existe una sesión iniciada.");
            return;
        }

        accessToken =
                sesion.optString("access_token", "");

        if (accessToken.isEmpty()) {
            mostrarError("No se encontró el token de acceso.");
            return;
        }

        btnGuardarPerfil.setEnabled(false);

        // Obtener UUID del usuario autenticado
        SupabaseProfileService.obtenerUsuarioActual(
                accessToken,
                new SupabaseProfileService.ResultadoCallback() {

                    @Override
                    public void onSuccess(String id) {

                        userId = id;

                        // Consultar información del perfil
                        SupabaseProfileService.consultarPerfil(
                                accessToken,
                                userId,
                                new SupabaseProfileService.PerfilCallback() {

                                    @Override
                                    public void onSuccess(
                                            String nombre,
                                            String avatarUrl) {

                                        runOnUiThread(() -> {

                                            etNombrePerfil.setText(nombre);

                                            // Cargar fotografía si existe
                                            if (avatarUrl != null &&
                                                    !avatarUrl.isEmpty()) {

                                                cargarFotografia(avatarUrl);
                                            }

                                            btnGuardarPerfil.setEnabled(true);
                                        });
                                    }

                                    @Override
                                    public void onError(String error) {
                                        mostrarError(error);
                                    }
                                }
                        );
                    }

                    @Override
                    public void onError(String error) {
                        mostrarError(error);
                    }
                }
        );
    }

    // =========================================
    // DESCARGAR Y MOSTRAR FOTOGRAFÍA
    // =========================================

    private void cargarFotografia(String url) {

        new Thread(() -> {

            HttpURLConnection conexion = null;

            try {

                URL direccion = new URL(url);

                conexion = (HttpURLConnection)
                        direccion.openConnection();

                conexion.setConnectTimeout(10000);
                conexion.setReadTimeout(10000);
                conexion.setRequestMethod("GET");

                try (InputStream entrada =
                             conexion.getInputStream()) {

                    Bitmap bitmap =
                            BitmapFactory.decodeStream(entrada);

                    if (bitmap != null) {

                        runOnUiThread(() -> {

                            if (!isFinishing()) {
                                imgAvatar.setImageBitmap(bitmap);
                            }

                        });
                    }
                }

            } catch (Exception e) {

                mostrarError(
                        "No se pudo cargar la fotografía."
                );

            } finally {

                if (conexion != null) {
                    conexion.disconnect();
                }
            }

        }).start();
    }

    // =========================================
    // GUARDAR PERFIL
    // =========================================

    private void guardarPerfil() {

        String nombre =
                etNombrePerfil.getText().toString().trim();

        if (nombre.isEmpty()) {

            etNombrePerfil.setError("Ingresa tu nombre");
            etNombrePerfil.requestFocus();

            return;
        }

        // Comprobar sesión
        if (accessToken == null || accessToken.isEmpty() ||
                userId == null || userId.isEmpty()) {

            mostrarError("No se ha podido cargar la sesión del usuario.");
            return;
        }

        final Uri fotoFinal = imagenSeleccionada;
        final String nombreFinal = nombre;

        btnGuardarPerfil.setEnabled(false);
        btnSeleccionarFoto.setEnabled(false);

        // Si seleccionó una fotografía, primero subirla
        if (fotoFinal != null) {

            SupabaseProfileService.subirFotografia(
                    getApplicationContext(),
                    fotoFinal,
                    userId,
                    accessToken,
                    new SupabaseProfileService.ResultadoCallback() {

                        @Override
                        public void onSuccess(String avatarUrl) {

                            actualizarDatos(
                                    nombreFinal,
                                    avatarUrl
                            );
                        }

                        @Override
                        public void onError(String error) {
                            mostrarError(error);
                        }
                    }
            );

        } else {

            // Si no seleccionó foto, conservar la anterior
            actualizarDatos(
                    nombreFinal,
                    null
            );
        }
    }

    // =========================================
    // ACTUALIZAR INFORMACIÓN EN SUPABASE
    // =========================================

    private void actualizarDatos(
            String nombre,
            String avatarUrl) {

        SupabaseProfileService.actualizarPerfil(
                accessToken,
                userId,
                nombre,
                avatarUrl,
                new SupabaseProfileService.ResultadoCallback() {

                    @Override
                    public void onSuccess(String resultado) {

                        runOnUiThread(() -> {

                            btnGuardarPerfil.setEnabled(true);
                            btnSeleccionarFoto.setEnabled(true);

                            Toast.makeText(
                                    EditarPerfilActivity.this,
                                    resultado,
                                    Toast.LENGTH_LONG
                            ).show();

                        });
                    }

                    @Override
                    public void onError(String error) {
                        mostrarError(error);
                    }
                }
        );
    }

    // =========================================
    // MOSTRAR ERRORES
    // =========================================

    private void mostrarError(String error) {

        runOnUiThread(() -> {

            btnGuardarPerfil.setEnabled(true);
            btnSeleccionarFoto.setEnabled(true);

            Toast.makeText(
                    EditarPerfilActivity.this,
                    error,
                    Toast.LENGTH_LONG
            ).show();

        });
    }
}
