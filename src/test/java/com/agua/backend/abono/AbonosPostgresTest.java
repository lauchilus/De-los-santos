package com.agua.backend.abono;

import com.agua.backend.camioneta.CamionetaRepository;
import com.agua.backend.entrega.*;
import com.agua.backend.jornada.JornadaService;
import com.agua.backend.maquina.MaquinaService;
import com.agua.backend.historial.*;
import com.agua.backend.usuario.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Esquema aislado: no utiliza las credenciales ni los datos de application.yaml.
@EnabledIfEnvironmentVariable(named="AGUA_TEST_DB_URL",matches=".+")
class AbonosPostgresTest {
    static final UUID BIDON=UUID.fromString("00000000-0000-4000-8000-000000000002");
    static final UUID MAQUINA=UUID.fromString("00000000-0000-4000-8000-000000000004");
    JdbcTemplate jdbc,admin;
    AnnotationConfigApplicationContext context;
    EntregaService entregas;
    AbonoService abonos;
    PrecioClienteService precios;
    MaquinaService maquinas;
    UUID cliente,jornada;
    String schema;
    AbonoService.Plan plan;
    LocalDate mes=LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).withDayOfMonth(1);
    @Configuration @EnableTransactionManagement static class Transacciones {}
    @BeforeEach void preparar() throws Exception {
        var ds=new DriverManagerDataSource(System.getenv("AGUA_TEST_DB_URL"),System.getenv("AGUA_TEST_DB_USER"),System.getenv("AGUA_TEST_DB_PASSWORD")) {
            @Override public java.sql.Connection getConnection() throws java.sql.SQLException {
                var connection=super.getConnection();
                if(schema!=null) connection.setSchema(schema);
                return connection;
            }
        };
        admin=new JdbcTemplate(ds);
        schema="abonos_test_"+UUID.randomUUID().toString().replace("-","");
        admin.execute("CREATE SCHEMA "+schema);
        var props=new Properties();props.setProperty("currentSchema",schema);ds.setConnectionProperties(props);
        jdbc=new JdbcTemplate(ds);
        var recursos=new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*.sql");
        Arrays.sort(recursos,Comparator.comparingInt(r->Integer.parseInt(r.getFilename().substring(1,r.getFilename().indexOf("__")))));
        try(var connection=ds.getConnection()){for(var r:recursos)ScriptUtils.executeSqlScript(connection,r);}
        var usuario=new Usuario("admin","Admin","test",Rol.ADMIN);
        var rep=new Usuario("otro","Otro","test",Rol.REPARTIDOR);
        var repo=mock(UsuarioRepository.class);
        when(repo.findByUsername("admin")).thenReturn(Optional.of(usuario));
        when(repo.findByUsername("otro")).thenReturn(Optional.of(rep));
        for(var u:List.of(usuario,rep))jdbc.update("INSERT INTO usuarios(id,username,nombre,password_hash,rol) VALUES(?,?,?,'test',?)",u.getId(),u.getUsername(),u.getNombre(),u.getRol().name());
        UUID camioneta=UUID.randomUUID();cliente=UUID.randomUUID();jornada=UUID.randomUUID();
        jdbc.update("INSERT INTO camionetas(id,nombre,patente) VALUES(?,'Test','TEST')",camioneta);
        jdbc.update("INSERT INTO clientes(id,nombre,direccion,telefono) VALUES(?,'Cliente','Prueba 123','123456')",cliente);
        jdbc.update("INSERT INTO jornadas(id,fecha,camioneta_id,repartidor_id,efectivo_inicial,abierta_por) VALUES(?,CURRENT_DATE,?,?,0,?)",jornada,camioneta,usuario.getId(),usuario.getId());
        jdbc.update("UPDATE productos SET precio_venta=5000 WHERE id=?",BIDON);
        jdbc.update("INSERT INTO stock_jornada(jornada_id,producto_id,estado,cantidad) VALUES(?,?,'LLENO',20)",jornada,BIDON);
        jdbc.update("INSERT INTO stock_jornada(jornada_id,producto_id,estado,cantidad) VALUES(?,?,'DISPONIBLE',3)",jornada,MAQUINA);
        context=new AnnotationConfigApplicationContext();context.register(Transacciones.class);
        context.registerBean(JdbcTemplate.class,()->jdbc);
        context.registerBean(DataSourceTransactionManager.class,()->new DataSourceTransactionManager(ds));
        context.registerBean(UsuarioRepository.class,()->repo);
        context.registerBean(CamionetaRepository.class,()->mock(CamionetaRepository.class));
        context.registerBean(JornadaService.class);context.registerBean(AbonoService.class);
        context.registerBean(PrecioClienteService.class);context.registerBean(EntregaService.class);context.registerBean(MaquinaService.class);
        context.registerBean(ResumenVentasService.class);context.registerBean(ResumenAbonosService.class);context.registerBean(HistorialClienteService.class);
        context.refresh();
        abonos=context.getBean(AbonoService.class);entregas=context.getBean(EntregaService.class);
        precios=context.getBean(PrecioClienteService.class);maquinas=context.getBean(MaquinaService.class);
        plan=abonos.guardar(null,new AbonoService.PlanRequest("Cuatro",new BigDecimal("18000"),true,null,List.of(new AbonoService.ItemPlan(BIDON,4))),"admin");
    }
    @AfterEach void limpiar(){if(context!=null)context.close();if(admin!=null && schema!=null)admin.execute("DROP SCHEMA "+schema+" CASCADE");}
    CrearEntregaRequest request(int cantidad,boolean cargar){return new CrearEntregaRequest(UUID.randomUUID(),cliente,
            cantidad==0?List.of():List.of(new CrearEntregaRequest.Item(BIDON,cantidad,0,0,0L)),
            cargar?new AbonoService.Carga(plan.id(),plan.version(),mes):null);}
    EntregaResponse registrar(CrearEntregaRequest r){return entregas.registrar(jornada,r,"admin");}
    void dinero(String esperado,BigDecimal real){assertEquals(0,new BigDecimal(esperado).compareTo(real));}
    long scalar(String sql){return jdbc.queryForObject(sql,Long.class);}
    @Test void abonoExtrasReintentoYReportes(){
        var r=request(6,true);var e=registrar(r);
        dinero("28000",e.total());assertEquals(4,e.items().getFirst().incluidosAbono());assertEquals(2,e.items().getFirst().facturados());
        assertEquals(e,registrar(r));assertEquals(4,scalar("SELECT SUM(consumidos) FROM cupos_abono"));
        dinero("28000",jdbc.queryForObject("SELECT saldo FROM cuentas_clientes WHERE cliente_id=?",BigDecimal.class,cliente));
        var ventas=context.getBean(ResumenVentasService.class).consultar(mes,mes.plusMonths(1).minusDays(1),null,null,null);
        dinero("10000",ventas.totales().importeVendido());dinero("4",ventas.totales().unidadesIncluidasAbono());
        dinero("18000",context.getBean(ResumenAbonosService.class).consultar(mes,mes.plusMonths(1).minusDays(1)).importeAbonos());
        assertEquals("ENTREGA_CON_ABONO",context.getBean(HistorialClienteService.class).listar(cliente,mes,mes.plusMonths(1).minusDays(1),0,20).contenido().getFirst().tipo());
    }
    @Test void cargoSinBidonesYDuplicadoConOtroUuid(){
        var e=registrar(request(0,true));dinero("18000",e.total());assertTrue(e.items().isEmpty());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->registrar(request(0,true))).getStatusCode().value());
        assertEquals(1,scalar("SELECT COUNT(*) FROM entregas"));assertEquals(1,scalar("SELECT COUNT(*) FROM abonos_cliente"));
    }
    @Test void falloStockRevierteCargoCupoYCuenta(){
        assertThrows(ResponseStatusException.class,()->registrar(request(21,true)));
        assertEquals(0,scalar("SELECT COUNT(*) FROM abonos_cliente"));assertEquals(0,scalar("SELECT COUNT(*) FROM entregas"));
        assertEquals(0,scalar("SELECT COUNT(*) FROM cuentas_clientes"));assertEquals(20,scalar("SELECT cantidad FROM stock_jornada WHERE estado='LLENO'"));
    }
    @Test void falloPosteriorNoConsumeCupoExistente(){
        registrar(request(0,true));
        assertThrows(ResponseStatusException.class,()->registrar(request(21,false)));
        assertEquals(0,scalar("SELECT SUM(consumidos) FROM cupos_abono"));assertEquals(0,scalar("SELECT COUNT(*) FROM consumos_abono"));
    }
    @Test void devolucionNoRecargaCupo(){
        registrar(request(2,true));
        var r=new CrearEntregaRequest(UUID.randomUUID(),cliente,List.of(new CrearEntregaRequest.Item(BIDON,0,1,0,null)));
        dinero("0",registrar(r).total());assertEquals(2,scalar("SELECT SUM(consumidos) FROM cupos_abono"));
    }
    @Test void precioEspecialExcepcionalYVersion(){
        precios.guardar(cliente,BIDON,new PrecioClienteService.Request(new BigDecimal("4500"),true,null,"Convenio"),"admin");
        assertEquals(409,assertThrows(ResponseStatusException.class,()->registrar(request(1,false))).getStatusCode().value());
        var r=new CrearEntregaRequest(UUID.randomUUID(),cliente,List.of(new CrearEntregaRequest.Item(BIDON,2,0,0,0L,0L,new BigDecimal("4000"),"Atención comercial")));
        var e=registrar(r);dinero("8000",e.total());assertEquals("EXCEPCIONAL",e.items().getFirst().origenPrecio());
        precios.guardar(cliente,BIDON,new PrecioClienteService.Request(new BigDecimal("4700"),true,0L,"Nuevo convenio"),"admin");
        assertEquals(e,registrar(r));
        var cambiado=new CrearEntregaRequest(r.operacionId(),cliente,List.of(new CrearEntregaRequest.Item(BIDON,2,0,0,0L,0L,new BigDecimal("3900"),"Atención comercial")));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->registrar(cambiado)).getStatusCode().value());
    }
    @Test void mesAnteriorNoSeAcumula(){
        registrar(request(0,true));jdbc.update("UPDATE abonos_cliente SET periodo=?",mes.minusMonths(1));
        dinero("5000",registrar(request(1,false)).total());assertEquals(0,scalar("SELECT SUM(consumidos) FROM cupos_abono"));
        dinero("18000",registrar(request(0,true)).total());assertEquals(2,scalar("SELECT COUNT(*) FROM abonos_cliente"));
    }
    @Test void cierreYPermisos(){
        var r=request(1,true);var e=registrar(r);
        jdbc.update("UPDATE jornadas SET estado='CERRADA',cerrada_en=CURRENT_TIMESTAMP WHERE id=?",jornada);
        assertEquals(e,registrar(r));assertEquals(409,assertThrows(ResponseStatusException.class,()->registrar(request(1,false))).getStatusCode().value());
        assertEquals(403,assertThrows(ResponseStatusException.class,()->entregas.obtener(jornada,e.id(),"otro")).getStatusCode().value());
    }
    @Test void editarPlanNoModificaCargosNiCuposAnteriores(){
        var r=request(0,true);var e=registrar(r);
        abonos.guardar(plan.id(),new AbonoService.PlanRequest("Seis",new BigDecimal("25000"),true,0L,List.of(new AbonoService.ItemPlan(BIDON,6))),"admin");
        assertEquals(e,registrar(r));assertEquals(4,scalar("SELECT SUM(incluidos) FROM cupos_abono"));
        dinero("18000",jdbc.queryForObject("SELECT importe FROM abonos_cliente",BigDecimal.class));
    }
    @Test void repartidorPuedeAplicarExcepcionPeroNoCambiarConvenio(){
        UUID rep=jdbc.queryForObject("SELECT id FROM usuarios WHERE username='otro'",UUID.class);
        jdbc.update("UPDATE jornadas SET repartidor_id=? WHERE id=?",rep,jornada);
        var r=new CrearEntregaRequest(UUID.randomUUID(),cliente,List.of(new CrearEntregaRequest.Item(BIDON,1,0,0,0L,null,new BigDecimal("4200"),"Precio puntual")));
        dinero("4200",entregas.registrar(jornada,r,"otro").total());
        assertEquals(403,assertThrows(ResponseStatusException.class,()->precios.guardar(cliente,BIDON,new PrecioClienteService.Request(new BigDecimal("4200"),true,null,"Convenio"),"otro")).getStatusCode().value());
    }
    @Test void maquinasSinCargoYRetiroIdempotente(){
        registrar(request(0,true));
        var r=new MaquinaService.Request(UUID.randomUUID(),cliente,MAQUINA,2,0);
        var m=maquinas.registrar(jornada,r,"admin");assertEquals(2,m.saldoCliente());assertEquals(m,maquinas.registrar(jornada,r,"admin"));
        var retiro=new MaquinaService.Request(UUID.randomUUID(),cliente,MAQUINA,0,1);
        var resultado=maquinas.registrar(jornada,retiro,"admin");assertEquals(1,resultado.saldoCliente());assertEquals(2,resultado.saldoJornada());
        assertEquals(resultado,maquinas.registrar(jornada,retiro,"admin"));
        dinero("18000",jdbc.queryForObject("SELECT saldo FROM cuentas_clientes WHERE cliente_id=?",BigDecimal.class,cliente));
    }
}
