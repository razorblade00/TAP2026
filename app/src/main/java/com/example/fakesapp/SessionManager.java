package com.example.fakesapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

public class SessionManager {

    private static final String PREFS_NAME = "fakesapp_session";
    private static final String SESSION_KEY = "encrypted_session";
    private static final String KEY_ALIAS = "FakesappSessionKey";

    // =====================================
    // OBTENER O CREAR CLAVE DE ANDROID
    // =====================================

    private static SecretKey obtenerClave() throws Exception {

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        if (keyStore.containsAlias(KEY_ALIAS)) {

            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }

        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
        );

        KeyGenParameterSpec especificacion =
                new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT
                                | KeyProperties.PURPOSE_DECRYPT
                )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(
                                KeyProperties.ENCRYPTION_PADDING_NONE
                        )
                        .setKeySize(256)
                        .build();

        keyGenerator.init(especificacion);

        return keyGenerator.generateKey();
    }

    // =====================================
    // CIFRAR INFORMACION
    // =====================================

    private static String cifrar(String texto) throws Exception {

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        cipher.init(Cipher.ENCRYPT_MODE, obtenerClave());

        byte[] iv = cipher.getIV();

        byte[] datosCifrados = cipher.doFinal(
                texto.getBytes("UTF-8")
        );

        // Unir IV y contenido cifrado
        ByteBuffer buffer = ByteBuffer.allocate(
                iv.length + datosCifrados.length
        );

        buffer.put(iv);
        buffer.put(datosCifrados);

        return Base64.encodeToString(
                buffer.array(),
                Base64.NO_WRAP
        );
    }

    // =====================================
    // DESCIFRAR INFORMACION
    // =====================================

    private static String descifrar(String textoCifrado)
            throws Exception {

        byte[] datos = Base64.decode(
                textoCifrado,
                Base64.NO_WRAP
        );

        ByteBuffer buffer = ByteBuffer.wrap(datos);

        byte[] iv = new byte[12];
        buffer.get(iv);

        byte[] datosCifrados = new byte[buffer.remaining()];
        buffer.get(datosCifrados);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        GCMParameterSpec especificacion =
                new GCMParameterSpec(128, iv);

        cipher.init(
                Cipher.DECRYPT_MODE,
                obtenerClave(),
                especificacion
        );

        byte[] resultado = cipher.doFinal(datosCifrados);

        return new String(resultado, "UTF-8");
    }

    // =====================================
    // GUARDAR SESION
    // =====================================

    public static void guardarSesion(
            Context context,
            JSONObject respuesta
    ) throws Exception {

        String accessToken = respuesta.optString(
                "access_token",
                ""
        );

        String refreshToken = respuesta.optString(
                "refresh_token",
                ""
        );

        if (accessToken.isEmpty() || refreshToken.isEmpty()) {

            throw new Exception(
                    "Supabase no devolvió los tokens de sesión."
            );
        }

        long ahora = System.currentTimeMillis() / 1000;

        long tiempoExpiracion = respuesta.optLong(
                "expires_at",
                0
        );

        if (tiempoExpiracion <= 0) {

            long duracion = respuesta.optLong(
                    "expires_in",
                    3600
            );

            tiempoExpiracion = ahora + duracion;
        }

        JSONObject usuario = respuesta.optJSONObject("user");

        String correo = "";

        if (usuario != null) {

            correo = usuario.optString("email", "");
        }

        JSONObject sesion = new JSONObject();

        sesion.put("access_token", accessToken);
        sesion.put("refresh_token", refreshToken);
        sesion.put("expires_at", tiempoExpiracion);
        sesion.put("email", correo);

        // Cifrar toda la informacion de la sesion
        String sesionCifrada = cifrar(sesion.toString());

        SharedPreferences preferencias =
                context.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );

        preferencias.edit()
                .putString(SESSION_KEY, sesionCifrada)
                .apply();
    }

    // =====================================
    // RECUPERAR SESION
    // =====================================

    public static JSONObject obtenerSesion(Context context) {

        SharedPreferences preferencias =
                context.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );

        String sesionCifrada = preferencias.getString(
                SESSION_KEY,
                null
        );

        if (sesionCifrada == null) {
            return null;
        }

        try {

            String sesionDescifrada = descifrar(sesionCifrada);

            return new JSONObject(sesionDescifrada);

        } catch (Exception e) {

            borrarSesion(context);

            return null;
        }
    }

    // =====================================
    // COMPROBAR SI EL TOKEN SIGUE VIGENTE
    // =====================================

    public static boolean accessTokenVigente(JSONObject sesion) {

        if (sesion == null) {
            return false;
        }

        long expiracion = sesion.optLong("expires_at", 0);

        long ahora = System.currentTimeMillis() / 1000;

        // Renovar un minuto antes de expirar
        return expiracion > ahora + 60;
    }

    // =====================================
    // ELIMINAR SESION LOCAL
    // =====================================

    public static void borrarSesion(Context context) {

        SharedPreferences preferencias =
                context.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );

        preferencias.edit()
                .remove(SESSION_KEY)
                .apply();
    }
}