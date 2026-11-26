
package com.example.fakesapp;

public class Usuario {

    private String id;
    private String username;
    private String displayName;
    private String avatarUrl;
    private String about;

    // Constructor
    public Usuario(
            String id,
            String username,
            String displayName,
            String avatarUrl,
            String about) {

        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.about = about;
    }

    // Obtener ID
    public String getId() {
        return id;
    }

    // Obtener nombre de usuario
    public String getUsername() {
        return username;
    }

    // Obtener nombre visible
    public String getDisplayName() {
        return displayName;
    }

    // Obtener fotografía
    public String getAvatarUrl() {
        return avatarUrl;
    }

    // Obtener descripción
    public String getAbout() {
        return about;
    }
}
