package com.agua.backend.carga;

import com.agua.backend.camioneta.CamionetaRepository;
import com.agua.backend.jornada.JornadaService;
import com.agua.backend.usuario.Rol;
import com.agua.backend.usuario.Usuario;
import com.agua.backend.usuario.UsuarioRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// PostgreSQL de pruebas explícito: cada prueba usa y elimina su propio esquema aleatorio.
// No lee application.yaml ni requiere levantar el backend completo.
@EnabledIfEnvironmentVariable(named = "AGUA_TEST_DB_URL", matches = ".+")
class CargaServicePostgresTest {
    private static final UUID BIDON = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID COPA = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private JdbcTemplate jdbc;
    private JdbcTemplate admin;
    private AnnotationConfigApplicationContext context;
    private CargaService service;
    private UUID jornadaId;
    private String schema;

    @Configuration
    @EnableTransactionManagement
    static class Transacciones {}

    @BeforeEach
    void preparar() throws Exception {
        var ds = new DriverManagerDataSource(System.getenv("AGUA_TEST_DB_URL"),
                System.getenv("AGUA_TEST_DB_USER"), System.getenv("AGUA_TEST_DB_PASSWORD"));
        admin = new JdbcTemplate(ds);
        schema = "cargas_test_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE SCHEMA " + schema);
        var props = new Properties();
        props.setProperty("currentSchema", schema);
        ds.setConnectionProperties(props);
        jdbc = new JdbcTemplate(ds);
        var recursos = new PathMatchingResourcePatternResolver()
                .getResources("classpath:db/migration/V*.sql");
        Arrays.sort(recursos, Comparator.comparing(r -> r.getFilename()));
        try (var connection = ds.getConnection()) {
            for (var recurso : recursos) ScriptUtils.executeSqlScript(connection, recurso);
        }
        var usuario = new Usuario("repartidor", "Repartidor", "test", Rol.REPARTIDOR);
        var otro = new Usuario("otro", "Otro repartidor", "test", Rol.REPARTIDOR);
        var repo = mock(UsuarioRepository.class);
        when(repo.findByUsername("repartidor")).thenReturn(Optional.of(usuario));
        when(repo.findByUsername("otro")).thenReturn(Optional.of(otro));
        jdbc.update("INSERT INTO usuarios (id, username, nombre, password_hash, rol) VALUES (?, ?, ?, ?, ?)",
                usuario.getId(), "repartidor", "Repartidor", "test", "REPARTIDOR");
        UUID camioneta = UUID.randomUUID();
        jdbc.update("INSERT INTO camionetas (id, nombre, patente) VALUES (?, 'Test', 'TEST123')", camioneta);
        jornadaId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO jornadas (id, fecha, camioneta_id, repartidor_id, efectivo_inicial, abierta_por)
                VALUES (?, CURRENT_DATE, ?, ?, 24000, ?)
                """, jornadaId, camioneta, usuario.getId(), usuario.getId());
        jdbc.update("UPDATE stock_fabrica SET cantidad = 100 WHERE producto_id = ? AND estado = 'LLENO'", BIDON);
        context = new AnnotationConfigApplicationContext();
        context.register(Transacciones.class);
        context.registerBean(JdbcTemplate.class, () -> jdbc);
        context.registerBean(DataSourceTransactionManager.class, () -> new DataSourceTransactionManager(ds));
        context.registerBean(UsuarioRepository.class, () -> repo);
        context.registerBean(CamionetaRepository.class, () -> mock(CamionetaRepository.class));
        context.registerBean(JornadaService.class);
        context.registerBean(CargaService.class);
        context.refresh();
        service = context.getBean(CargaService.class);
    }

    @AfterEach
    void limpiar() {
        if (context != null) context.close();
        if (admin != null && schema != null) admin.execute("DROP SCHEMA " + schema + " CASCADE");
    }

    private CrearCargaRequest pedido(UUID id, int cantidad) {
        return new CrearCargaRequest(id, List.of(new CrearCargaRequest.Item(BIDON, cantidad)));
    }

    private long stockFabrica() {
        return jdbc.queryForObject("SELECT cantidad FROM stock_fabrica WHERE producto_id = ? AND estado = 'LLENO'",
                Long.class, BIDON);
    }

    @Test
    void cargaRecargaYReintentoConservanStock() {
        var request = pedido(UUID.randomUUID(), 30);
        var inicial = service.cargar(jornadaId, request, "repartidor");
        assertEquals("INICIAL", inicial.tipo());
        assertEquals(inicial, service.cargar(jornadaId, request, "repartidor"));
        var recarga = service.cargar(jornadaId, pedido(UUID.randomUUID(), 20), "repartidor");
        assertEquals("RECARGA", recarga.tipo());
        assertEquals(2, recarga.numero());
        assertEquals(50, stockFabrica());
        assertEquals(50, service.stock(jornadaId, "repartidor").getFirst().cantidad());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM movimientos_stock_fabrica WHERE tipo = 'CARGA'", Integer.class));
    }

    @Test
    void fallaEnSegundoProductoRevierteTodaLaCarga() {
        var request = new CrearCargaRequest(UUID.randomUUID(), List.of(
                new CrearCargaRequest.Item(BIDON, 30), new CrearCargaRequest.Item(COPA, 1)));
        var error = assertThrows(ResponseStatusException.class,
                () -> service.cargar(jornadaId, request, "repartidor"));
        assertEquals(409, error.getStatusCode().value());
        assertEquals(100, stockFabrica());
        for (String tabla : List.of("cargas_jornada", "detalles_carga_jornada", "stock_jornada", "movimientos_stock_fabrica")) {
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM " + tabla, Integer.class));
        }
    }

    @Test
    void rechazaReutilizacionConOtroContenido() {
        UUID id = UUID.randomUUID();
        service.cargar(jornadaId, pedido(id, 30), "repartidor");
        var error = assertThrows(ResponseStatusException.class,
                () -> service.cargar(jornadaId, pedido(id, 31), "repartidor"));
        assertEquals(409, error.getStatusCode().value());
        assertEquals(70, stockFabrica());
    }

    @Test
    void protegeJornadaAjenaYCerradaPeroPermiteReintentoConfirmado() {
        var request = pedido(UUID.randomUUID(), 10);
        var carga = service.cargar(jornadaId, request, "repartidor");
        var ajena = assertThrows(ResponseStatusException.class,
                () -> service.cargar(jornadaId, pedido(UUID.randomUUID(), 10), "otro"));
        assertEquals(403, ajena.getStatusCode().value());
        jdbc.update("UPDATE jornadas SET estado = 'CERRADA', cerrada_en = CURRENT_TIMESTAMP WHERE id = ?", jornadaId);
        assertEquals(carga, service.cargar(jornadaId, request, "repartidor"));
        var cerrada = assertThrows(ResponseStatusException.class,
                () -> service.cargar(jornadaId, pedido(UUID.randomUUID(), 10), "repartidor"));
        assertEquals(409, cerrada.getStatusCode().value());
        assertEquals(90, stockFabrica());
    }

    @Test
    void reintentoConProductosReordenadosDevuelveLaMismaCarga() {
        jdbc.update("UPDATE stock_fabrica SET cantidad = 10 WHERE producto_id = ?", COPA);
        UUID id = UUID.randomUUID();
        var a = new CrearCargaRequest.Item(BIDON, 5);
        var b = new CrearCargaRequest.Item(COPA, 2);
        var carga = service.cargar(jornadaId, new CrearCargaRequest(id, List.of(a, b)), "repartidor");
        assertEquals(carga, service.cargar(jornadaId, new CrearCargaRequest(id, List.of(b, a)), "repartidor"));
        assertEquals(95, stockFabrica());
    }

    @Test
    void dosCargasDistintasNoPuedenGastarElMismoStock() throws Exception {
        var salida = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> cargar = () -> {
                salida.await();
                try {
                    service.cargar(jornadaId, pedido(UUID.randomUUID(), 60), "repartidor");
                    return true;
                } catch (ResponseStatusException error) {
                    assertEquals(409, error.getStatusCode().value());
                    return false;
                }
            };
            var uno = pool.submit(cargar);
            var dos = pool.submit(cargar);
            salida.countDown();
            assertNotEquals(uno.get(15, TimeUnit.SECONDS), dos.get(15, TimeUnit.SECONDS));
            assertEquals(40, stockFabrica());
            assertEquals(60, service.stock(jornadaId, "repartidor").getFirst().cantidad());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM cargas_jornada", Integer.class));
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(15, TimeUnit.SECONDS);
        }
    }

    @Test
    void dosReintentosSimultaneosNoDuplicanLaCarga() throws Exception {
        var request = pedido(UUID.randomUUID(), 60);
        var salida = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var uno = pool.submit(() -> { salida.await(); return service.cargar(jornadaId, request, "repartidor"); });
            var dos = pool.submit(() -> { salida.await(); return service.cargar(jornadaId, request, "repartidor"); });
            salida.countDown();
            assertEquals(uno.get(15, TimeUnit.SECONDS), dos.get(15, TimeUnit.SECONDS));
            assertEquals(40, stockFabrica());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM cargas_jornada", Integer.class));
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(15, TimeUnit.SECONDS);
        }
    }
}
