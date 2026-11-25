package com.example.fakesapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class RegistroActivity extends AppCompatActivity {

    private EditText etNombre;
    private EditText etCorreo;
    private EditText etContrasena;
    private Button btnRegistrar;
    private TextView tvVolverLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registro);

        // Relacionar los elementos XML con Java
        etNombre = findViewById(R.id.etNombre);
        etCorreo = findViewById(R.id.etCorreoRegistro);
        etContrasena = findViewById(R.id.etContrasenaRegistro);

        btnRegistrar = findViewById(R.id.btnRegistrar);
        tvVolverLogin = findViewById(R.id.tvVolverLogin);

        // Boton para registrar al usuario
        btnRegistrar.setOnClickListener(v -> registrarUsuario());

        // Regresar a la pantalla de inicio de sesion
        tvVolverLogin.setOnClickListener(v -> finish());
    }

    private void registrarUsuario() {

        String nombre = etNombre.getText().toString().trim();
        String correo = etCorreo.getText().toString().trim();
        String contrasena = etContrasena.getText().toString();

        // Validar campos vacios
        if (nombre.isEmpty() || correo.isEmpty() || contrasena.isEmpty()) {

            Toast.makeText(
                    this,
                    "Completa todos los campos.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        btnRegistrar.setEnabled(false);
        btnRegistrar.setText("Registrando...");

        // Comunicacion con Supabase Auth
        SupabaseAuthService.registrarUsuario(
                correo,
                contrasena,
                nombre,
                new SupabaseAuthService.AuthCallback() {

                    @Override
                    public void onSuccess(JSONObject response) {

                        runOnUiThread(() -> {

                            btnRegistrar.setEnabled(true);
                            btnRegistrar.setText("Crear cuenta");

                            String accessToken = response.optString(
                                    "access_token",
                                    ""
                            );

                            String mensaje;

                            if (accessToken.isEmpty()) {

                                mensaje = "Tu cuenta fue registrada. "
                                        + "Revisa tu correo electrónico "
                                        + "y confirma tu cuenta antes de iniciar sesión.";

                            } else {

                                mensaje = "Tu cuenta fue creada correctamente. "
                                        + "Ya puedes iniciar sesión.";
                            }

                            new AlertDialog.Builder(RegistroActivity.this)
                                    .setTitle("Registro completado")
                                    .setMessage(mensaje)
                                    .setPositiveButton("Aceptar", (dialog, which) -> {
                                        finish();
                                    })
                                    .show();
                        });
                    }

                    @Override
                    public void onError(String message) {

                        runOnUiThread(() -> {

                            btnRegistrar.setEnabled(true);
                            btnRegistrar.setText("Crear cuenta");

                            Toast.makeText(
                                    RegistroActivity.this,
                                    message,
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                }
        );
    }
}