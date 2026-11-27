package com.example.fakesapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder> {

    public interface OnConversacionClickListener {
        void onConversacionClick(ConversationItem conversacion);
    }

    private final List<ConversationItem> conversaciones = new ArrayList<>();
    private final String currentUserId;
    private final OnConversacionClickListener listener;
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ConversationAdapter(String currentUserId, OnConversacionClickListener listener) {
        this.currentUserId = currentUserId;
        this.listener = listener;
    }

    public void actualizarLista(List<ConversationItem> nuevaLista) {
        conversaciones.clear();
        if (nuevaLista != null) {
            conversaciones.addAll(nuevaLista);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation, parent, false);
        return new ConversationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder holder, int position) {
        ConversationItem item = conversaciones.get(position);

        holder.tvNombre.setText(item.getOtherDisplayName());

        String ultimoMensaje = item.getLastMessageContent();
        if (ultimoMensaje == null || ultimoMensaje.trim().isEmpty()) {
            holder.tvUltimoMensaje.setText("Toca para enviar un mensaje");
        } else if (currentUserId != null && currentUserId.equals(item.getLastMessageSenderId())) {
            holder.tvUltimoMensaje.setText("Tú: " + ultimoMensaje);
        } else {
            holder.tvUltimoMensaje.setText(ultimoMensaje);
        }

        String fechaCruda = item.getLastMessageCreatedAt() != null
                ? item.getLastMessageCreatedAt()
                : item.getUpdatedAt();
        holder.tvHora.setText(formatearHora(fechaCruda));

        holder.imgAvatar.setImageResource(android.R.drawable.ic_menu_camera);
        String avatarUrl = item.getOtherAvatarUrl();
        if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
            cargarAvatar(holder, avatarUrl, position);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onConversacionClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return conversaciones.size();
    }

    private void cargarAvatar(ConversationViewHolder holder, String urlString, int posicionEsperada) {
        imageExecutor.execute(() -> {
            HttpURLConnection conexion = null;
            try {
                URL url = new URL(urlString);
                conexion = (HttpURLConnection) url.openConnection();
                conexion.setConnectTimeout(8000);
                conexion.setReadTimeout(8000);
                conexion.setRequestMethod("GET");

                try (InputStream entrada = conexion.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(entrada);
                    if (bitmap != null) {
                        mainHandler.post(() -> {
                            int posActual = holder.getBindingAdapterPosition();
                            if (posActual != RecyclerView.NO_POSITION && posActual == posicionEsperada) {
                                holder.imgAvatar.setImageBitmap(bitmap);
                            }
                        });
                    }
                }
            } catch (Exception ignored) {
                // Mantiene el ícono por defecto si no se puede descargar
            } finally {
                if (conexion != null) {
                    conexion.disconnect();
                }
            }
        });
    }

    private String formatearHora(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.length() < 16) {
            return "";
        }
        try {
            String trimmed = isoTimestamp.substring(0, 19);
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = parser.parse(trimmed);

            SimpleDateFormat formatter = new SimpleDateFormat("HH:mm", Locale.getDefault());
            return date != null ? formatter.format(date) : "";
        } catch (Exception e) {
            return "";
        }
    }

    static class ConversationViewHolder extends RecyclerView.ViewHolder {
        final ImageView imgAvatar;
        final TextView tvNombre;
        final TextView tvHora;
        final TextView tvUltimoMensaje;

        ConversationViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAvatar = itemView.findViewById(R.id.imgConversacionAvatar);
            tvNombre = itemView.findViewById(R.id.tvConversacionNombre);
            tvHora = itemView.findViewById(R.id.tvConversacionHora);
            tvUltimoMensaje = itemView.findViewById(R.id.tvConversacionUltimoMensaje);
        }
    }
}