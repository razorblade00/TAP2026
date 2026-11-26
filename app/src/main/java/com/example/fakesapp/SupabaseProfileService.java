package com.example.fakesapp;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.util.ArrayList;

public class SupabaseProfileService {

    private static final String SUPABASE_URL =
            BuildConfig.SUPABASE_URL;

    private static final String SUPABASE_KEY =
            BuildConfig.SUPABASE_PUBLISHABLE_KEY;

    private static final OkHttpClient CLIENT =
            new OkHttpClient();

    private static final MediaType JSON =
            MediaType.parse("application/json; charset=utf-8");

    private static final int MAX_IMAGE_SIZE = 5 * 1024 * 1024;

    // Resultado de las operaciones
    public interface ResultadoCallback {
        void onSuccess(String resultado);
        void onError(String error);
    }

    public interface PerfilCallback {
        void onSuccess(String nombre, String avatarUrl);
        void onError(String error);
    }

    // Paso 2: Interfaz para devolver una lista de objetos Usuario
    public interface UsuariosCallback {
        void onSuccess(ArrayList<Usuario> usuarios);
        void onError(String error);
    }

    // Quitar diagonal final de la URL si existe
    private static String obtenerUrlBase() {
        if (SUPABASE_URL.endsWith("/")) {
            return SUPABASE_URL.substring(
                    0, SUPABASE_URL.length() - 1
            );
        }

        return SUPABASE_URL;
    }

    // =========================================
    // 1. OBTENER USUARIO AUTENTICADO
    // =========================================

    public static void obtenerUsuarioActual(
            String accessToken,
            ResultadoCallback callback) {

        Request request = new Request.Builder()
                .url(obtenerUrlBase() + "/auth/v1/user")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        CLIENT.newCall(request).enqueue(new okhttp3.Callback() {

            @Override
            public void onFailure(
                    okhttp3.Call call,
                    IOException e) {

                callback.onError(
                        "Error al consultar el usuario: " + e.getMessage()
                );
            }

            @Override
            public void onResponse(
                    okhttp3.Call call,
                    Response response) throws IOException {

                try (Response res = response) {

                    ResponseBody body = res.body();

                    String contenido = body != null
                            ? body.string()
                            : "";

                    if (res.isSuccessful()) {

                        try {
                            JSONObject usuario =
                                    new JSONObject(contenido);

                            callback.onSuccess(
                                    usuario.getString("id")
                            );

                        } catch (JSONException e) {
                            callback.onError(
                                    "No se pudo obtener el ID del usuario."
                            );
                        }

                    } else {
                        callback.onError(
                                obtenerMensajeError(contenido)
                        );
                    }
                }
            }
        });
    }

    // =========================================
    // 2. SUBIR FOTOGRAFÍA A STORAGE
    // =========================================

    public static void subirFotografia(
            Context context,
            Uri imagen,
            String userId,
            String accessToken,
            ResultadoCallback callback) {

        new Thread(() -> {

            try {

                // Obtener el tipo de archivo
                String tipoImagen =
                        context.getContentResolver().getType(imagen);

                if (tipoImagen == null ||
                        !(tipoImagen.equals("image/jpeg") ||
                                tipoImagen.equals("image/png") ||
                                tipoImagen.equals("image/webp"))) {

                    callback.onError(
                            "Formato no permitido. Utiliza JPG, PNG o WEBP."
                    );
                    return;
                }

                // Leer los bytes de la imagen
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream();

                try (InputStream input =
                             context.getContentResolver()
                                     .openInputStream(imagen)) {

                    if (input == null) {
                        callback.onError(
                                "No se pudo leer la fotografía."
                        );
                        return;
                    }

                    byte[] buffer = new byte[8192];
                    int cantidad;
                    int total = 0;

                    while ((cantidad = input.read(buffer)) != -1) {

                        total += cantidad;

                        if (total > MAX_IMAGE_SIZE) {
                            callback.onError(
                                    "La fotografía supera el límite de 5 MB."
                            );
                            return;
                        }

                        output.write(buffer, 0, cantidad);
                    }
                }

                byte[] imagenBytes = output.toByteArray();

                // Determinar extensión
                String extension;

                if (tipoImagen.equals("image/png")) {
                    extension = "png";
                } else if (tipoImagen.equals("image/webp")) {
                    extension = "webp";
                } else {
                    extension = "jpg";
                }

                // Crear una ruta única para evitar sobrescribir
                String nombreArchivo =
                        "avatar_" + System.currentTimeMillis()
                                + "." + extension;

                String rutaArchivo =
                        userId + "/" + nombreArchivo;

                String url =
                        obtenerUrlBase()
                                + "/storage/v1/object/avatars/"
                                + rutaArchivo;

                // Preparar el archivo
                RequestBody archivoBody =
                        RequestBody.create(
                                MediaType.parse(tipoImagen),
                                imagenBytes
                        );

                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("apikey", SUPABASE_KEY)
                        .addHeader(
                                "Authorization",
                                "Bearer " + accessToken
                        )
                        .post(archivoBody)
                        .build();

                // Enviar fotografía
                try (Response response =
                             CLIENT.newCall(request).execute()) {

                    ResponseBody body = response.body();

                    String contenido = body != null
                            ? body.string()
                            : "";

                    if (response.isSuccessful()) {

                        String urlPublica =
                                obtenerUrlBase()
                                        + "/storage/v1/object/public/avatars/"
                                        + rutaArchivo;

                        callback.onSuccess(urlPublica);

                    } else {
                        callback.onError(
                                obtenerMensajeError(contenido)
                        );
                    }
                }

            } catch (Exception e) {

                callback.onError(
                        "Error al subir la fotografía: " + e.getMessage()
                );
            }

        }).start();
    }

    public static void consultarPerfil(
            String accessToken,
            String userId,
            PerfilCallback callback) {

        String url =
                obtenerUrlBase()
                        + "/rest/v1/profiles?select=display_name,avatar_url&id=eq."
                        + userId;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        CLIENT.newCall(request).enqueue(new okhttp3.Callback() {

            @Override
            public void onFailure(
                    okhttp3.Call call,
                    IOException e) {

                callback.onError(
                        "Error al consultar el perfil: " + e.getMessage()
                );
            }

            @Override
            public void onResponse(
                    okhttp3.Call call,
                    Response response) throws IOException {

                try (Response res = response) {

                    ResponseBody body = res.body();

                    String contenido = body != null
                            ? body.string()
                            : "";

                    if (res.isSuccessful()) {

                        try {

                            JSONArray perfiles =
                                    new JSONArray(contenido);

                            if (perfiles.length() == 0) {
                                callback.onError(
                                        "No se encontró el perfil del usuario."
                                );
                                return;
                            }

                            JSONObject perfil =
                                    perfiles.getJSONObject(0);

                            String nombre =
                                    perfil.optString(
                                            "display_name",
                                            "Usuario"
                                    );

                            String avatarUrl = null;

                            if (!perfil.isNull("avatar_url")) {
                                avatarUrl =
                                        perfil.getString("avatar_url");
                            }

                            callback.onSuccess(nombre, avatarUrl);

                        } catch (JSONException e) {

                            callback.onError(
                                    "No se pudieron interpretar los datos del perfil."
                            );
                        }

                    } else {

                        callback.onError(
                                obtenerMensajeError(contenido)
                        );
                    }
                }
            }
        });
    }

    // =========================================
    // 3. ACTUALIZAR PERFIL EN POSTGRESQL
    // =========================================

    public static void actualizarPerfil(
            String accessToken,
            String userId,
            String nombre,
            String avatarUrl,
            ResultadoCallback callback) {

        try {

            JSONObject datos = new JSONObject();

            datos.put("display_name", nombre);

            // Actualizar fecha de modificación
            SimpleDateFormat formato =
                    new SimpleDateFormat(
                            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                            Locale.US
                    );

            formato.setTimeZone(TimeZone.getTimeZone("UTC"));

            datos.put("updated_at", formato.format(new Date()));

            // Si no se seleccionó foto, no modificamos avatar_url
            if (avatarUrl != null) {
                datos.put("avatar_url", avatarUrl);
            }

            String url =
                    obtenerUrlBase()
                            + "/rest/v1/profiles?id=eq."
                            + userId
                            + "&select=id";

            RequestBody body =
                    RequestBody.create(JSON, datos.toString());

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader(
                            "Authorization",
                            "Bearer " + accessToken
                    )
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .patch(body)
                    .build();

            CLIENT.newCall(request).enqueue(new okhttp3.Callback() {

                @Override
                public void onFailure(
                        okhttp3.Call call,
                        IOException e) {

                    callback.onError(
                            "Error al actualizar el perfil: "
                                    + e.getMessage()
                    );
                }

                @Override
                public void onResponse(
                        okhttp3.Call call,
                        Response response) throws IOException {

                    try (Response res = response) {

                        ResponseBody responseBody = res.body();

                        String contenido = responseBody != null
                                ? responseBody.string()
                                : "";

                        if (res.isSuccessful()) {

                            try {
                                JSONArray resultados =
                                        new JSONArray(contenido);

                                if (resultados.length() > 0) {
                                    callback.onSuccess(
                                            "Perfil actualizado correctamente."
                                    );
                                } else {
                                    callback.onError(
                                            "No se encontró el perfil para actualizar."
                                    );
                                }

                            } catch (JSONException e) {
                                callback.onError(
                                        "No se pudo confirmar la actualización."
                                );
                            }

                        } else {
                            callback.onError(
                                    obtenerMensajeError(contenido)
                            );
                        }
                    }
                }
            });

        } catch (Exception e) {

            callback.onError(
                    "Error al preparar los datos: " + e.getMessage()
            );
        }
    }

    // =========================================
    // 4. CONSULTAR USUARIOS EN POSTGRESQL
    // =========================================


    public static void consultarUsuarios(
            String accessToken,
            String userId,
            UsuariosCallback callback) {

        // Consultar todos los usuarios, sin término de búsqueda
        consultarUsuarios(accessToken, userId, "", callback);
    }

// =========================================
// CONSULTAR Y BUSCAR USUARIOS
// =========================================

    public static void consultarUsuarios(
            String accessToken,
            String userId,
            String termino,
            UsuariosCallback callback) {

        String url =
                obtenerUrlBase()
                        + "/rest/v1/profiles"
                        + "?select=id,username,display_name,avatar_url,about"
                        + "&id=neq." + userId
                        + "&order=display_name.asc"
                        + "&limit=100";

        // Agregar filtro cuando se escribe algo
        if (termino != null && !termino.trim().isEmpty()) {

            // Permitir letras, números, espacios, guiones y puntos
            String terminoSeguro = termino.trim().replaceAll(
                    "[^\\p{L}\\p{N} _.-]",
                    ""
            );

            if (terminoSeguro.isEmpty()) {
                callback.onSuccess(new ArrayList<Usuario>());
                return;
            }

            // Buscar en nombre visible o username
            String filtro =
                    "(display_name.ilike.*" + terminoSeguro
                            + "*,username.ilike.*" + terminoSeguro + "*)";

            url += "&or=" + Uri.encode(filtro);
        }

        Request request = new Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        CLIENT.newCall(request).enqueue(new okhttp3.Callback() {

            @Override
            public void onFailure(
                    okhttp3.Call call,
                    IOException e) {

                callback.onError(
                        "Error al consultar usuarios: " + e.getMessage()
                );
            }

            @Override
            public void onResponse(
                    okhttp3.Call call,
                    Response response) throws IOException {

                try (Response res = response) {

                    ResponseBody body = res.body();

                    String contenido = body != null
                            ? body.string()
                            : "";

                    if (res.isSuccessful()) {

                        try {

                            JSONArray resultados =
                                    new JSONArray(contenido);

                            ArrayList<Usuario> usuarios =
                                    new ArrayList<>();

                            for (int i = 0; i < resultados.length(); i++) {

                                JSONObject perfil =
                                        resultados.getJSONObject(i);

                                String id =
                                        perfil.optString("id", "");

                                String username =
                                        perfil.isNull("username")
                                                ? ""
                                                : perfil.optString("username", "");

                                String displayName =
                                        perfil.optString(
                                                "display_name",
                                                "Usuario"
                                        );

                                String avatarUrl =
                                        perfil.isNull("avatar_url")
                                                ? null
                                                : perfil.optString("avatar_url", null);

                                String about =
                                        perfil.isNull("about")
                                                ? ""
                                                : perfil.optString("about", "");

                                Usuario usuario = new Usuario(
                                        id,
                                        username,
                                        displayName,
                                        avatarUrl,
                                        about
                                );

                                usuarios.add(usuario);
                            }

                            callback.onSuccess(usuarios);

                        } catch (JSONException e) {

                            callback.onError(
                                    "Error al interpretar los usuarios."
                            );
                        }

                    } else {

                        callback.onError(
                                obtenerMensajeError(contenido)
                        );
                    }
                }
            }
        });
    }

    // =========================================
    // OBTENER MENSAJE DE ERROR
    // =========================================

    private static String obtenerMensajeError(String contenido) {

        try {
            JSONObject error = new JSONObject(contenido);

            if (error.has("message")) {
                return error.getString("message");
            }

            if (error.has("msg")) {
                return error.getString("msg");
            }

            if (error.has("error_description")) {
                return error.getString("error_description");
            }

            if (error.has("error")) {
                return error.getString("error");
            }

        } catch (JSONException e) {
            // Se devuelve el contenido original si no es JSON
        }

        return contenido.isEmpty()
                ? "Ocurrió un error desconocido."
                : contenido;
    }
}