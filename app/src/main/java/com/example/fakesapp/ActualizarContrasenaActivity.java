package com.example.fakesapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class ActualizarContrasenaActivity extends AppCompatActivity {

    private EditText etNuevaContrasena;
    private EditText etConfirmarContrasena;
    private Button btnActualizar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_actualizar_contrasena);

        etNuevaContrasena = findViewById(R.id.etNuevaContrasena);
        etConfirmarContrasena = findViewById(R.id.etConfirmarContrasena);
        btnActualizar = findViewById(R.id.btnActualizarContrasena);

        btnActualizar.setOnClickListener(v -> actualizarContrasena());

        // Comprobar que exista una sesion de recuperacion
        JSONObject sesion = SessionManager.obtenerSesion(this);

        if (sesion == null
                || sesion.optString("access_token", "").isEmpty()) {

            Toast.makeText(
                    this,
                    "No existe una sesión de recuperación válida.",
                    Toast.LENGTH_LONG
            ).show();

            regresarLogin();
        }
    }

    private void actualizarContrasena() {

        String nuevaContrasena = etNuevaContrasena.getText().toString();

        String confirmarContrasena =
                etConfirmarContrasena.getText().toString();

        // Comprobar que los campos esten completos
        if (nuevaContrasena.isEmpty()
                || confirmarContrasena.isEmpty()) {

            Toast.makeText(
                    this,
                    "Completa ambos campos.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        // Comprobar longitud minima definida para Fakesapp
        if (nuevaContrasena.length() < 8) {

            Toast.makeText(
                    this,
                    "La contraseña debe tener al menos 8 caracteres.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        // Comprobar que las contrasenas coincidan
        if (!nuevaContrasena.equals(confirmarContrasena)) {

            Toast.makeText(
                    this,
                    "Las contraseñas no coinciden.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        JSONObject sesion = SessionManager.obtenerSesion(this);

        if (sesion == null) {
            regresarLogin();
            return;
        }

        String accessToken = sesion.optString(
                "access_token",
                ""
        );

        btnActualizar.setEnabled(false);
        btnActualizar.setText("Actualizando...");

        // Enviar la nueva contrasena a Supabase
        SupabaseAuthService.actualizarContrasena(
                accessToken,
                nuevaContrasena,
                new SupabaseAuthService.AuthCallback() {

                    @Override
                    public void onSuccess(JSONObject response) {

                        runOnUiThread(() -> {

                            // Eliminar la sesion de recuperacion local
                            SessionManager.borrarSesion(
                                    ActualizarContrasenaActivity.this
                            );

                            Toast.makeText(
                                    ActualizarContrasenaActivity.this,
                                    "La contraseña se actualizó correctamente.",
                                    Toast.LENGTH_LONG
                            ).show();

                            regresarLogin();
                        });
                    }

                    @Override
                    public void onError(String message) {

                        runOnUiThread(() -> {

                            btnActualizar.setEnabled(true);
                            btnActualizar.setText("Cambiar contraseña");

                            Toast.makeText(
                                    ActualizarContrasenaActivity.this,
                                    message,
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                }
        );
    }

    private void regresarLogin() {

        Intent intent = new Intent(
                ActualizarContrasenaActivity.this,
                MainActivity.class
        );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);

        finish();
    }
}