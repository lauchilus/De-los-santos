package com.agua.backend.usuario;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "usuarios")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Usuario {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    public Usuario(
            String username,
            String nombre,
            String passwordHash,
            Rol rol
    ) {
        this.id = UUID.randomUUID();
        this.username = username.strip().toLowerCase(Locale.ROOT);
        this.nombre = nombre.strip();
        this.passwordHash = passwordHash;
        this.rol = rol;
        this.activo = true;
        this.creadoEn = Instant.now();
    }
}