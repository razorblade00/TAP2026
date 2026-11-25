package com.example.fakesapp;

import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;

public class SupabaseAuthService {

    // =====================================
    // CONFIGURACION DE SUPABASE
    // =====================================

    private static final String SUPABASE_URL =
            BuildConfig.SUPABASE_URL;

    private static final String SUPABASE_KEY =
            BuildConfig.SUPABASE_PUBLISHABLE_KEY;

    // URL para regresar a nuestra aplicacion
    private static final String REDIRECT_URL =
            "com.example.fakesapp://auth-callback";

    // Formato de las peticiones JSON
    private static final MediaType JSON =
            MediaType.get("application/json; charset=utf-8");

    // Cliente HTTP
    private static final OkHttpClient client =
            new OkHttpClient();

    // =====================================
    // RESPUESTA DE LAS OPERACIONES
    // =====================================

    public interface AuthCallback {

        void onSuccess(JSONObject response);

        void onError(String message);

    }

    // =====================================
    // METODO PARA VALIDAR CONFIGURACION
    // =====================================

    private static boolean configuracionValida() {

        return SUPABASE_URL != null
                && !SUPABASE_URL.isEmpty()
                && SUPABASE_KEY != null
                && !SUPABASE_KEY.isEmpty();

    }

    // =====================================
    // METODO PARA OBTENER URL BASE
    // =====================================

    private static String obtenerUrlBase() {

        if (SUPABASE_URL.endsWith("/")) {

            return SUPABASE_URL.substring(
                    0,
                    SUPABASE_URL.length() - 1
            );

        }

        return SUPABASE_URL;

    }

    // =====================================
    // 1. REGISTRAR USUARIO
    // =====================================

    public static void registrarUsuario(
            String email,
            String password,
            String displayName,
            AuthCallback callback
    ) {

        if (!configuracionValida()) {

            callback.onError(
                    "La configuracion de Supabase esta incompleta."
            );

            return;
        }

        if (email == null || email.trim().isEmpty()
                || password == null || password.isEmpty()
                || displayName == null || displayName.trim().isEmpty()) {

            callback.onError(
                    "Todos los campos son obligatorios."
            );

            return;
        }

        try {

            // Crear objeto JSON
            JSONObject datos = new JSONObject();

            datos.put("email", email.trim());

            datos.put("password", password);

            // Datos adicionales del usuario
            JSONObject metadata = new JSONObject();

            metadata.put("display_name", displayName.trim());

            datos.put("data", metadata);

            // Crear cuerpo de la peticion
            RequestBody body = RequestBody.create(
                    datos.toString(),
                    JSON
            );

            // Construir URL de registro
            String url = obtenerUrlBase()
                    + "/auth/v1/signup?redirect_to=com.example.fakesapp%3A%2F%2Fauth-callback";

            // Construir solicitud
            Request request = new Request.Builder()

                    .url(url)

                    .addHeader("apikey", SUPABASE_KEY)

                    .addHeader("Accept", "application/json")

                    .post(body)

                    .build();

            // Enviar solicitud
            ejecutarPeticion(request, callback);

        } catch (JSONException e) {

            callback.onError(
                    "Error al preparar los datos del registro."
            );

        }

    }

    // =====================================
    // 2. INICIAR SESION
    // =====================================

    public static void iniciarSesion(
            String email,
            String password,
            AuthCallback callback
    ) {

        if (!configuracionValida()) {

            callback.onError(
                    "La configuracion de Supabase esta incompleta."
            );

            return;
        }

        if (email == null || email.trim().isEmpty()
                || password == null || password.isEmpty()) {

            callback.onError(
                    "Ingresa tu correo y contrasena."
            );

            return;
        }

        try {

            // Crear objeto JSON
            JSONObject datos = new JSONObject();

            datos.put("email", email.trim());

            datos.put("password", password);

            // Crear cuerpo de la peticion
            RequestBody body = RequestBody.create(
                    datos.toString(),
                    JSON
            );

            // URL del inicio de sesion
            String url = obtenerUrlBase()
                    + "/auth/v1/token?grant_type=password";

            // Construir solicitud
            Request request = new Request.Builder()

                    .url(url)

                    .addHeader("apikey", SUPABASE_KEY)

                    .addHeader("Accept", "application/json")

                    .post(body)

                    .build();

            // Enviar solicitud
            ejecutarPeticion(request, callback);

        } catch (JSONException e) {

            callback.onError(
                    "Error al preparar las credenciales."
            );

        }

    }
    // =====================================
// 3. RENOVAR SESION
// =====================================

    public static void renovarSesion(
            String refreshToken,
            AuthCallback callback
    ) {

        if (!configuracionValida()) {

            callback.onError(
                    "La configuracion de Supabase esta incompleta."
            );

            return;
        }

        if (refreshToken == null || refreshToken.isEmpty()) {

            callback.onError(
                    "No existe un refresh token."
            );

            return;
        }

        try {

            JSONObject datos = new JSONObject();

            datos.put("refresh_token", refreshToken);

            RequestBody body = RequestBody.create(
                    datos.toString(),
                    JSON
            );

            String url = obtenerUrlBase()
                    + "/auth/v1/token?grant_type=refresh_token";

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Accept", "application/json")
                    .post(body)
                    .build();

            ejecutarPeticion(request, callback);

        } catch (JSONException e) {

            callback.onError(
                    "Error al preparar la renovacion de sesion."
            );
        }
    }
    // =====================================
// 4. CERRAR SESION EN SUPABASE
// =====================================

    public static void cerrarSesion(
            String accessToken,
            AuthCallback callback
    ) {

        if (!configuracionValida()) {

            callback.onError(
                    "La configuracion de Supabase esta incompleta."
            );

            return;
        }

        if (accessToken == null || accessToken.isEmpty()) {

            callback.onError(
                    "No existe un access token."
            );

            return;
        }

        String url = obtenerUrlBase()
                + "/auth/v1/logout?scope=local";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .post(RequestBody.create("{}", JSON))
                .build();

        ejecutarPeticion(request, callback);
    }
    // =====================================
// 5. SOLICITAR RECUPERACION DE CONTRASENA
// =====================================

    public static void solicitarRecuperacion(
            String email,
            AuthCallback callback
    ) {

        if (!configuracionValida()) {

            callback.onError(
                    "La configuracion de Supabase esta incompleta."
            );

            return;
        }

        if (email == null || email.trim().isEmpty()) {

            callback.onError(
                    "Ingresa tu correo electronico."
            );

            return;
        }

        try {

            // Crear el JSON con el correo
            JSONObject datos = new JSONObject();

            datos.put("email", email.trim());

            // Crear el cuerpo de la peticion
            RequestBody body = RequestBody.create(
                    datos.toString(),
                    JSON
            );

            // Solicitar recuperacion y regresar a Fakesapp
            String url = obtenerUrlBase()
                    + "/auth/v1/recover?redirect_to="
                    + "com.example.fakesapp%3A%2F%2Fauth-callback";

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Accept", "application/json")
                    .post(body)
                    .build();

            ejecutarPeticion(request, callback);

        } catch (JSONException e) {

            callback.onError(
                    "Error al preparar la solicitud de recuperacion."
            );
        }
    }
    // =====================================
// 6. ACTUALIZAR CONTRASENA
// =====================================

    public static void actualizarContrasena(
            String accessToken,
            String nuevaContrasena,
            AuthCallback callback
    ) {

        if (!configuracionValida()) {

            callback.onError(
                    "La configuracion de Supabase esta incompleta."
            );

            return;
        }

        if (accessToken == null || accessToken.isEmpty()) {

            callback.onError(
                    "No existe una sesion de recuperacion valida."
            );

            return;
        }

        if (nuevaContrasena == null || nuevaContrasena.isEmpty()) {

            callback.onError(
                    "Ingresa una nueva contrasena."
            );

            return;
        }

        try {

            JSONObject datos = new JSONObject();

            datos.put("password", nuevaContrasena);

            RequestBody body = RequestBody.create(
                    datos.toString(),
                    JSON
            );

            String url = obtenerUrlBase()
                    + "/auth/v1/user";

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Accept", "application/json")
                    .put(body)
                    .build();

            ejecutarPeticion(request, callback);

        } catch (JSONException e) {

            callback.onError(
                    "Error al preparar la nueva contrasena."
            );
        }
    }
    // =====================================
    // 3. EJECUTAR PETICIONES HTTP
    // =====================================

    private static void ejecutarPeticion(
            Request request,
            AuthCallback callback
    ) {

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(
                    Call call,
                    IOException e
            ) {

                callback.onError(
                        "Error de conexion: " + e.getMessage()
                );

            }

            @Override
            public void onResponse(
                    Call call,
                    Response response
            ) throws IOException {

                String respuestaServidor = "";

                int codigo = response.code();

                boolean exitoso = response.isSuccessful();

                if (response.body() != null) {

                    respuestaServidor = response.body().string();

                }

                response.close();

                try {

                    JSONObject resultado = new JSONObject(
                            respuestaServidor
                    );

                    if (exitoso) {

                        callback.onSuccess(resultado);

                    } else {

                        callback.onError(
                                obtenerMensajeError(
                                        resultado,
                                        codigo
                                )
                        );

                    }

                } catch (JSONException e) {

                    if (exitoso) {

                        callback.onSuccess(new JSONObject());

                    } else {

                        callback.onError(
                                "Error HTTP " + codigo
                        );

                    }

                }

            }

        });

    }

    // =====================================
    // 4. INTERPRETAR ERRORES DE SUPABASE
    // =====================================

    private static String obtenerMensajeError(
            JSONObject resultado,
            int codigo
    ) {

        String[] campos = {
                "msg",
                "message",
                "error_description",
                "error"
        };

        for (String campo : campos) {

            String mensaje = resultado.optString(
                    campo,
                    ""
            );

            if (!mensaje.isEmpty()) {

                return mensaje;

            }

        }

        return "Error de autenticacion. Codigo HTTP: " + codigo;

    }

}