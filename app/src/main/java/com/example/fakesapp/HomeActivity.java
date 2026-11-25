package com.example.fakesapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class HomeActivity extends AppCompatActivity {

    private TextView tvBienvenida;
    private Button btnCerrarSesion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        tvBienvenida = findViewById(R.id.tvBienvenida);
        btnCerrarSesion = findViewById(R.id.btnCerrarSesion);

        // Recuperar informacion de la sesion
        JSONObject sesion = SessionManager.obtenerSesion(this);

        if (sesion == null) {

            regresarLogin();
            return;
        }

        String correo = sesion.optString(
                "email",
                "Usuario"
        );

        tvBienvenida.setText(
                "¡Bienvenido a Fakesapp!\n\n" + correo
        );

        btnCerrarSesion.setOnClickListener(v -> cerrarSesion());
    }

    private void cerrarSesion() {

        JSONObject sesion = SessionManager.obtenerSesion(this);

        String accessToken = "";

        if (sesion != null) {

            accessToken = sesion.optString(
                    "access_token",
                    ""
            );
        }

        // Eliminar primero la sesion del dispositivo
        SessionManager.borrarSesion(this);

        // Regresar a la pantalla de acceso
        regresarLogin();

        Toast.makeText(
                this,
                "Sesión cerrada en este dispositivo.",
                Toast.LENGTH_SHORT
        ).show();

        // Solicitar a Supabase la revocacion de la sesion
        if (!accessToken.isEmpty()) {

            SupabaseAuthService.cerrarSesion(
                    accessToken,
                    new SupabaseAuthService.AuthCallback() {

                        @Override
                        public void onSuccess(JSONObject response) {
                            // Supabase confirmo el cierre de sesion.
                        }

                        @Override
                        public void onError(String message) {

                            runOnUiThread(() -> {

                                Toast.makeText(
                                        HomeActivity.this,
                                        "La sesión se cerró localmente, "
                                                + "pero no se pudo confirmar "
                                                + "la revocación en Supabase.",
                                        Toast.LENGTH_LONG
                                ).show();
                            });
                        }
                    }
            );
        }
    }

    private void regresarLogin() {

        Intent intent = new Intent(
                HomeActivity.this,
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