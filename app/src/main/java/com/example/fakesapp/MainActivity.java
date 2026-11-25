package com.example.fakesapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private EditText etCorreo;
    private EditText etContrasena;
    private Button btnIniciarSesion;
    private TextView tvIrRegistro;
    private TextView tvRecuperarContrasena;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etCorreo = findViewById(R.id.etCorreo);
        etContrasena = findViewById(R.id.etContrasena);
        btnIniciarSesion = findViewById(R.id.btnIniciarSesion);
        tvIrRegistro = findViewById(R.id.tvIrRegistro);
        tvRecuperarContrasena = findViewById(R.id.tvRecuperarContrasena);

        tvRecuperarContrasena.setOnClickListener(v -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    RecuperarContrasenaActivity.class
            );
            startActivity(intent);
        });

        btnIniciarSesion.setOnClickListener(v -> iniciarSesion());

        tvIrRegistro.setOnClickListener(v -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    RegistroActivity.class
            );
            startActivity(intent);
        });

        // Primero comprobar si llegamos desde el correo
        if (procesarEnlaceRecuperacion(getIntent())) {
            return;
        }

        // Si no llegamos desde recuperacion, comprobar sesion normal
        comprobarSesionGuardada();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        procesarEnlaceRecuperacion(intent);
    }

    // =====================================
    // INICIAR SESION
    // =====================================

    private void iniciarSesion() {

        String correo = etCorreo.getText().toString().trim();
        String contrasena = etContrasena.getText().toString();

        if (correo.isEmpty() || contrasena.isEmpty()) {

            Toast.makeText(
                    this,
                    "Ingresa tu correo y contraseña.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        btnIniciarSesion.setEnabled(false);
        btnIniciarSesion.setText("Ingresando...");

        SupabaseAuthService.iniciarSesion(
                correo,
                contrasena,
                new SupabaseAuthService.AuthCallback() {

                    @Override
                    public void onSuccess(JSONObject response) {

                        runOnUiThread(() -> {

                            btnIniciarSesion.setEnabled(true);
                            btnIniciarSesion.setText("Iniciar sesión");

                            try {

                                // Guardar tokens de manera cifrada
                                SessionManager.guardarSesion(
                                        MainActivity.this,
                                        response
                                );

                                abrirPantallaPrincipal();

                            } catch (Exception e) {

                                Toast.makeText(
                                        MainActivity.this,
                                        "No se pudo guardar la sesión.",
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {

                        runOnUiThread(() -> {

                            btnIniciarSesion.setEnabled(true);
                            btnIniciarSesion.setText("Iniciar sesión");

                            Toast.makeText(
                                    MainActivity.this,
                                    message,
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                }
        );
    }

    // =====================================
    // COMPROBAR SESION GUARDADA
    // =====================================

    private void comprobarSesionGuardada() {

        JSONObject sesion = SessionManager.obtenerSesion(this);

        if (sesion == null) {
            return;
        }

        // Si el access token sigue vigente
        if (SessionManager.accessTokenVigente(sesion)) {

            abrirPantallaPrincipal();
            return;
        }

        // Si expiro, utilizar el refresh token
        String refreshToken = sesion.optString(
                "refresh_token",
                ""
        );

        if (refreshToken.isEmpty()) {
            SessionManager.borrarSesion(this);
            return;
        }

        btnIniciarSesion.setEnabled(false);
        btnIniciarSesion.setText("Restaurando sesión...");

        SupabaseAuthService.renovarSesion(
                refreshToken,
                new SupabaseAuthService.AuthCallback() {

                    @Override
                    public void onSuccess(JSONObject response) {

                        runOnUiThread(() -> {

                            try {

                                // Sustituir los tokens anteriores
                                // por los nuevos tokens recibidos
                                SessionManager.guardarSesion(
                                        MainActivity.this,
                                        response
                                );

                                abrirPantallaPrincipal();

                            } catch (Exception e) {

                                btnIniciarSesion.setEnabled(true);
                                btnIniciarSesion.setText("Iniciar sesión");

                                Toast.makeText(
                                        MainActivity.this,
                                        "No se pudo restaurar la sesión.",
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {

                        runOnUiThread(() -> {

                            btnIniciarSesion.setEnabled(true);
                            btnIniciarSesion.setText("Iniciar sesión");

                            Toast.makeText(
                                    MainActivity.this,
                                    "No se pudo restaurar la sesión. "
                                            + "Inicia sesión nuevamente.",
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                }
        );
    }

    // =====================================
    // PROCESAR ENLACE DE RECUPERACION
    // =====================================

    private boolean procesarEnlaceRecuperacion(Intent intent) {

        if (intent == null) {
            return false;
        }

        Uri enlace = intent.getData();

        if (enlace == null) {
            return false;
        }

        // Comprobar que el enlace pertenece a Fakesapp
        if (!"com.example.fakesapp".equals(enlace.getScheme())
                || !"auth-callback".equals(enlace.getHost())) {

            return false;
        }

        // Obtener los parametros del enlace
        String error = obtenerParametroEnlace(
                enlace,
                "error_description"
        );

        if (!error.isEmpty()) {

            Toast.makeText(
                    this,
                    "No se pudo validar el enlace: " + error,
                    Toast.LENGTH_LONG
            ).show();

            return true;
        }

        String tipo = obtenerParametroEnlace(
                enlace,
                "type"
        );

        // Comprobar que sea un enlace de recuperacion
        if (!"recovery".equals(tipo)) {
            return false;
        }

        String accessToken = obtenerParametroEnlace(
                enlace,
                "access_token"
        );

        String refreshToken = obtenerParametroEnlace(
                enlace,
                "refresh_token"
        );

        if (accessToken.isEmpty() || refreshToken.isEmpty()) {

            Toast.makeText(
                    this,
                    "El enlace no contiene una sesión válida.",
                    Toast.LENGTH_LONG
            ).show();

            return true;
        }

        try {

            // Crear objeto con los tokens recibidos
            JSONObject sesion = new JSONObject();

            sesion.put("access_token", accessToken);
            sesion.put("refresh_token", refreshToken);

            String expiracion = obtenerParametroEnlace(
                    enlace,
                    "expires_in"
            );

            sesion.put(
                    "expires_in",
                    expiracion.isEmpty() ? 3600 : Long.parseLong(expiracion)
            );

            // Guardar la sesion cifrada
            SessionManager.guardarSesion(
                    this,
                    sesion
            );

            // Abrir pantalla para establecer nueva contrasena
            Intent nuevaPantalla = new Intent(
                    MainActivity.this,
                    ActualizarContrasenaActivity.class
            );

            startActivity(nuevaPantalla);

            finish();

            return true;

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "No fue posible procesar el enlace de recuperación.",
                    Toast.LENGTH_LONG
            ).show();

            return true;
        }
    }

    // =====================================
    // OBTENER PARAMETROS DEL ENLACE
    // =====================================

    private String obtenerParametroEnlace(
            Uri enlace,
            String parametro
    ) {

        // Primero buscar parametros normales
        String valor = enlace.getQueryParameter(parametro);

        if (valor != null) {
            return valor;
        }

        // Despues buscar parametros dentro del fragmento #
        String fragmento = enlace.getFragment();

        if (fragmento != null) {

            Uri parametros = Uri.parse(
                    "https://fakesapp.local/?" + fragmento
            );

            valor = parametros.getQueryParameter(parametro);
        }

        return valor == null ? "" : valor;
    }

    // =====================================
    // ABRIR PANTALLA PRINCIPAL
    // =====================================

    private void abrirPantallaPrincipal() {

        Intent intent = new Intent(
                MainActivity.this,
                HomeActivity.class
        );

        startActivity(intent);

        finish();
    }
}