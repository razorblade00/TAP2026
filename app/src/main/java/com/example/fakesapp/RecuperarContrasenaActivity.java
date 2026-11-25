package com.example.fakesapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class RecuperarContrasenaActivity extends AppCompatActivity {

    private EditText etCorreo;
    private Button btnEnviar;
    private TextView tvVolver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recuperar_contrasena);

        etCorreo = findViewById(R.id.etCorreoRecuperacion);
        btnEnviar = findViewById(R.id.btnEnviarRecuperacion);
        tvVolver = findViewById(R.id.tvVolverLoginRecuperacion);

        btnEnviar.setOnClickListener(v -> solicitarRecuperacion());

        tvVolver.setOnClickListener(v -> finish());
    }

    private void solicitarRecuperacion() {

        String correo = etCorreo.getText().toString().trim();

        if (correo.isEmpty()) {

            Toast.makeText(
                    this,
                    "Ingresa tu correo electrónico.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        btnEnviar.setEnabled(false);
        btnEnviar.setText("Enviando...");

        SupabaseAuthService.solicitarRecuperacion(
                correo,
                new SupabaseAuthService.AuthCallback() {

                    @Override
                    public void onSuccess(JSONObject response) {

                        runOnUiThread(() -> {

                            btnEnviar.setEnabled(true);
                            btnEnviar.setText("Enviar enlace");

                            Toast.makeText(
                                    RecuperarContrasenaActivity.this,
                                    "Si el correo está registrado, "
                                            + "recibirás un enlace de recuperación.",
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }

                    @Override
                    public void onError(String message) {

                        runOnUiThread(() -> {

                            btnEnviar.setEnabled(true);
                            btnEnviar.setText("Enviar enlace");

                            Toast.makeText(
                                    RecuperarContrasenaActivity.this,
                                    message,
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                }
        );
    }
}