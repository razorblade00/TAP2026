package com.example.fakesapp;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class SupabaseClient {

    // Dirección de nuestro proyecto
    private static final String SUPABASE_URL =
            BuildConfig.SUPABASE_URL;

    // Clave pública de Supabase
    private static final String SUPABASE_KEY =
            BuildConfig.SUPABASE_PUBLISHABLE_KEY;

    // Cliente HTTP
    private static final OkHttpClient client = new OkHttpClient();

    // Método para comprobar la conexión
    public static void probarConexion(Callback callback) {

        if (SUPABASE_URL.isEmpty() || SUPABASE_KEY.isEmpty()) {
            throw new IllegalStateException(
                    "Faltan las credenciales de Supabase"
            );
        }

        String url = SUPABASE_URL;

        // Evitar una doble diagonal al construir la URL
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        // Construir la petición HTTP
        Request request = new Request.Builder()
                .url(url + "/auth/v1/health")
                .get()
                .addHeader("apikey", SUPABASE_KEY)
                .build();

        // Ejecutar la petición de forma asíncrona
        client.newCall(request).enqueue(callback);
    }
}