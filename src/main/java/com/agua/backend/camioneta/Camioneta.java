package com.agua.backend.camioneta;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "camionetas")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Camioneta {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 15)
    private String patente;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @Version
    @Column(nullable = false)
    private Long version;

    public Camioneta(String nombre, String patente) {
        this.id = UUID.randomUUID();
        this.nombre = nombre.strip();
        this.patente = normalizarPatente(patente);
        this.activo = true;
    }

    public static String normalizarPatente(String patente) {
        return patente.replace(" ", "").toUpperCase(Locale.ROOT);
    }

    @PrePersist
    void antesDeCrear() {
        Instant ahora = Instant.now();
        creadoEn = ahora;
        actualizadoEn = ahora;
    }

    @PreUpdate
    void antesDeActualizar() {
        actualizadoEn = Instant.now();
    }
}