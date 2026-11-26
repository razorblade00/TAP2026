package com.example.fakesapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UsuarioAdapter
        extends RecyclerView.Adapter<UsuarioAdapter.UsuarioViewHolder> {

    private final Context context;
    private final ArrayList<Usuario> listaUsuarios;

    // Descargar imágenes sin bloquear la interfaz
    private final ExecutorService ejecutor =
            Executors.newFixedThreadPool(3);
    private final OnUsuarioClickListener listener;
    public interface OnUsuarioClickListener {
        void onUsuarioClick(Usuario usuario);
    }

    public UsuarioAdapter(
            Context context,
            OnUsuarioClickListener listener) {

        this.context = context;
        this.listener = listener;
        this.listaUsuarios = new ArrayList<>();
    }

    // =========================================
    // CREAR CADA FILA
    // =========================================

    @NonNull
    @Override
    public UsuarioViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {

        View vista = LayoutInflater.from(context)
                .inflate(R.layout.item_usuario, parent, false);

        return new UsuarioViewHolder(vista);
    }

    // =========================================
    // ASIGNAR DATOS A CADA FILA
    // =========================================

    @Override
    public void onBindViewHolder(
            @NonNull UsuarioViewHolder holder,
            int position) {

        Usuario usuario = listaUsuarios.get(position);

        holder.tvNombre.setText(usuario.getDisplayName());

        // Mostrar username
        String username = usuario.getUsername();

        if (username == null || username.isEmpty()) {
            holder.tvUsername.setText("Sin nombre de usuario");
        } else {
            holder.tvUsername.setText("@" + username);
        }

        // Mostrar descripción
        String about = usuario.getAbout();

        if (about == null || about.isEmpty()) {
            holder.tvAbout.setText("Sin descripción");
        } else {
            holder.tvAbout.setText(about);
        }

        // Fotografía del usuario
        String avatarUrl = usuario.getAvatarUrl();

        holder.imgUsuario.setImageResource(
                android.R.drawable.ic_menu_camera
        );

        holder.imgUsuario.setTag(avatarUrl);

        if (avatarUrl != null && !avatarUrl.isEmpty()) {

            ejecutor.execute(() -> {

                Bitmap imagen = descargarImagen(avatarUrl);

                if (imagen != null) {

                    holder.imgUsuario.post(() -> {

                        // Evitar mostrar una imagen en otra fila reciclada
                        if (avatarUrl.equals(
                                holder.imgUsuario.getTag())) {

                            holder.imgUsuario.setImageBitmap(imagen);
                        }
                    });
                }
            });

        }

        // Detectar cuando se toca un usuario
        holder.itemView.setOnClickListener(v -> {

            if (listener != null) {
                listener.onUsuarioClick(usuario);
            }

        });
    }

    // =========================================
    // DESCARGAR FOTOGRAFÍA
    // =========================================

    private Bitmap descargarImagen(String urlImagen) {

        HttpURLConnection conexion = null;

        try {

            URL url = new URL(urlImagen);

            conexion = (HttpURLConnection)
                    url.openConnection();

            conexion.setConnectTimeout(10000);
            conexion.setReadTimeout(10000);
            conexion.setRequestMethod("GET");

            try (InputStream entrada =
                         conexion.getInputStream()) {

                return BitmapFactory.decodeStream(entrada);
            }

        } catch (Exception e) {

            return null;

        } finally {

            if (conexion != null) {
                conexion.disconnect();
            }
        }
    }

    // =========================================
    // ACTUALIZAR LISTA DE USUARIOS
    // =========================================

    public void actualizarLista(List<Usuario> usuarios) {

        listaUsuarios.clear();
        listaUsuarios.addAll(usuarios);

        notifyDataSetChanged();
    }

    // =========================================
    // CANTIDAD DE USUARIOS
    // =========================================

    @Override
    public int getItemCount() {
        return listaUsuarios.size();
    }

    // =========================================
    // VIEW HOLDER
    // =========================================

    public static class UsuarioViewHolder
            extends RecyclerView.ViewHolder {

        ImageView imgUsuario;
        TextView tvNombre;
        TextView tvUsername;
        TextView tvAbout;

        public UsuarioViewHolder(@NonNull View itemView) {
            super(itemView);

            imgUsuario = itemView.findViewById(R.id.imgUsuario);
            tvNombre = itemView.findViewById(R.id.tvNombreUsuario);
            tvUsername = itemView.findViewById(R.id.tvUsername);
            tvAbout = itemView.findViewById(R.id.tvAbout);
        }
    }

    // Liberar los hilos al retirar el adaptador
    @Override
    public void onDetachedFromRecyclerView(
            @NonNull RecyclerView recyclerView) {

        super.onDetachedFromRecyclerView(recyclerView);
        ejecutor.shutdownNow();
    }
}