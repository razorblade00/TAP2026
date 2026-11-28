package com.example.fakesapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;

    private final String currentUserId;
    private final List<MessageItem> messages = new ArrayList<>();

    public MessageAdapter(String currentUserId) {
        this.currentUserId = currentUserId;
    }

    @Override
    public int getItemViewType(int position) {
        MessageItem item = messages.get(position);
        return item.isSentByMe(currentUserId) ? VIEW_TYPE_SENT : VIEW_TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = (viewType == VIEW_TYPE_SENT)
                ? R.layout.item_message_sent
                : R.layout.item_message_received;

        View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        MessageItem item = messages.get(position);
        holder.tvMensajeTexto.setText(item.getContent());
        holder.tvMensajeHora.setText(formatearHora(item.getCreatedAt()));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void actualizarMensajes(List<MessageItem> nuevosMensajes) {
        messages.clear();
        if (nuevosMensajes != null) {
            for (MessageItem item : nuevosMensajes) {
                if (item != null && !contieneMensaje(item.getId())) {
                    messages.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    public boolean agregarMensaje(MessageItem nuevoMensaje) {
        if (nuevoMensaje == null) {
            return false;
        }
        if (contieneMensaje(nuevoMensaje.getId())) {
            return false;
        }
        messages.add(nuevoMensaje);
        notifyItemInserted(messages.size() - 1);
        return true;
    }

    public boolean contieneMensaje(String messageId) {
        if (messageId == null || messageId.trim().isEmpty()) {
            return false;
        }
        for (MessageItem existing : messages) {
            if (messageId.equals(existing.getId())) {
                return true;
            }
        }
        return false;
    }

    private String formatearHora(String isoDate) {
        if (isoDate == null || isoDate.trim().isEmpty()) {
            return "";
        }

        String[] patrones = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'"
        };

        for (String patron : patrones) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(patron, Locale.US);
                if (patron.endsWith("'Z'")) {
                    parser.setTimeZone(TimeZone.getTimeZone("UTC"));
                }
                Date fecha = parser.parse(isoDate.trim());
                if (fecha != null) {
                    SimpleDateFormat salida = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    return salida.format(fecha);
                }
            } catch (Exception ignored) {
            }
        }

        int idxT = isoDate.indexOf('T');
        if (idxT != -1 && isoDate.length() >= idxT + 6) {
            return isoDate.substring(idxT + 1, idxT + 6);
        }
        return "";
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {

        final TextView tvMensajeTexto;
        final TextView tvMensajeHora;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMensajeTexto = itemView.findViewById(R.id.tvMensajeTexto);
            tvMensajeHora = itemView.findViewById(R.id.tvMensajeHora);
        }
    }
}