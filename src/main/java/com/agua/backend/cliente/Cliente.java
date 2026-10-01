package com.agua.backend.cliente;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clientes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cliente {

    @Id
    private UUID id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, length = 250)
    private String direccion;

    @Column(nullable = false, length = 50)
    private String telefono;

    @Column(columnDefinition = "text")
    private String observaciones;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @Version
    @Column(nullable = false)
    private Long version;

    public Cliente(
            String nombre,
            String direccion,
            String telefono,
            String observaciones
    ) {
        this.id = UUID.randomUUID();
        this.nombre = nombre.strip();
        this.direccion = direccion.strip();
        this.telefono = telefono.strip();
        this.observaciones = observaciones == null
                ? null : observaciones.strip();
        this.activo = true;
    }

    @PrePersist
    void antesDeCrear() {
        Instant ahora = Instant.now();
        this.creadoEn = ahora;
        this.actualizadoEn = ahora;
    }

    @PreUpdate
    void antesDeActualizar() {
        this.actualizadoEn = Instant.now();
    }
}