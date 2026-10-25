package com.agua.backend.importacion;

import com.agua.backend.usuario.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

@Service @RequiredArgsConstructor
public class ImportacionClientesService {
 private final JdbcTemplate jdbc;
 private final UsuarioRepository usuarios;
 private static final ZoneId ZONA=ZoneId.of("America/Argentina/Buenos_Aires");
 private static final UUID P12=UUID.fromString("00000000-0000-4000-8000-000000000001"), P20=UUID.fromString("00000000-0000-4000-8000-000000000002"), MAQ=UUID.fromString("00000000-0000-4000-8000-000000000004");
 public record Solicitud(String archivoBase64,LocalDate fechaCorte) { @Override public String toString(){return "Importacion[archivo omitido]";} }
 public record ErrorFila(String hoja,int fila,String mensaje) {}
 public record Abono(String codigo,UUID planId,String plan,long version,LocalDate mes,BigDecimal importe,int incluidos12,int consumidos12,int incluidos20,int consumidos20) {}
 public record Cliente(String codigo,String nombre,String direccion,String telefono,String observaciones,BigDecimal saldo,int envases12,int envases20,int maquinas) {}
 public record Vista(UUID id,LocalDate fechaCorte,String estado,List<Cliente> clientes,List<Abono> abonos,List<ErrorFila> errores) {}
 private record Guardado(UUID id,UUID usuario,LocalDate corte,byte[] archivo,String huella,OffsetDateTime creado,OffsetDateTime confirmado,int cantidad) {}

 @Transactional
 public Vista preparar(Solicitud r,String username) {
  var actor=admin(username);
  if(r==null||r.archivoBase64()==null||r.archivoBase64().length()>2_666_668) throw error(400,"Archivo requerido; máximo 2 MB");
  if(r.fechaCorte()==null||r.fechaCorte().isAfter(LocalDate.now(ZONA))||r.fechaCorte().isBefore(LocalDate.of(2000,1,1))) throw error(400,"Fecha de corte inválida o futura");
  byte[] bytes;try{bytes=Base64.getDecoder().decode(r.archivoBase64());}catch(IllegalArgumentException e){throw error(400,"Archivo inválido");}
  Vista vista=validar(bytes,r.fechaCorte());
  if(!vista.errores().isEmpty()) return vista;
  UUID id=UUID.randomUUID();
  jdbc.update("INSERT INTO importaciones_clientes(id,usuario_id,fecha_corte,archivo,huella,cantidad) VALUES(?,?,?,?,?,?)",id,actor.getId(),r.fechaCorte(),bytes,huella(vista),vista.clientes().size());
  return new Vista(id,r.fechaCorte(),"PREPARADA",vista.clientes(),vista.abonos(),List.of());
 }
 @Transactional(readOnly=true)
 public Vista consultar(UUID id,String username) {
  admin(username);var g=cargar(id,false);
  if(g.confirmado()!=null) return new Vista(id,g.corte(),"CONFIRMADA",jdbc.query("SELECT i.codigo,c.nombre,c.direccion,c.telefono,c.observaciones,i.saldo_inicial,i.envases_12,i.envases_20,i.maquinas FROM clientes_importados i JOIN clientes c ON c.id=i.cliente_id WHERE i.importacion_id=? ORDER BY i.codigo",(rs,n)->new Cliente(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getBigDecimal(6),rs.getInt(7),rs.getInt(8),rs.getInt(9)),id),jdbc.query("""
    SELECT i.codigo,a.plan_id,a.plan_nombre,a.plan_version,a.periodo,a.importe,
     COALESCE((SELECT incluidos FROM cupos_abono WHERE abono_id=a.id AND producto_id=?),0),
     COALESCE((SELECT consumidos FROM cupos_abono WHERE abono_id=a.id AND producto_id=?),0),
     COALESCE((SELECT incluidos FROM cupos_abono WHERE abono_id=a.id AND producto_id=?),0),
     COALESCE((SELECT consumidos FROM cupos_abono WHERE abono_id=a.id AND producto_id=?),0)
    FROM abonos_cliente a JOIN clientes_importados i ON i.cliente_id=a.cliente_id
    WHERE a.importacion_id=? ORDER BY i.codigo,a.plan_nombre
    """,(rs,n)->new Abono(rs.getString(1),rs.getObject(2,UUID.class),rs.getString(3),rs.getLong(4),rs.getObject(5,LocalDate.class),rs.getBigDecimal(6),rs.getInt(7),rs.getInt(8),rs.getInt(9),rs.getInt(10)),P12,P12,P20,P20,id),List.of());
  var v=validar(g.archivo(),g.corte());
  var errores=new ArrayList<>(v.errores());
  if(g.creado().plusHours(24).isBefore(OffsetDateTime.now())) errores.add(new ErrorFila("archivo",0,"La vista previa venció. Volvé a cargar el archivo"));
  if(!huella(v).equals(g.huella())) errores.add(new ErrorFila("archivo",0,"Los datos o planes cambiaron. Generá otra vista previa"));
  return new Vista(id,g.corte(),"PREPARADA",v.clientes(),v.abonos(),errores);
 }
 @Transactional
 public Vista confirmar(UUID id,String username) {
  var actor=admin(username);
  // Serializa confirmaciones de importación. Los códigos únicos son la segunda defensa.
  jdbc.execute("SELECT pg_advisory_xact_lock(170029)");
  var g=cargar(id,true);
  if(g.confirmado()!=null) return consultar(id,username);
  if(!g.usuario().equals(actor.getId())) throw error(403,"La importación debe confirmarla el administrador que la preparó");
  jdbc.query("SELECT id FROM planes_abono ORDER BY id FOR SHARE",(rs,n)->rs.getObject(1));
  var v=consultar(id,username);
  if(!v.errores().isEmpty()) throw error(409,"La vista previa cambió o venció. Volvé a cargar el archivo y revisá los errores");
  Map<String,UUID> ids=new HashMap<>();
  for(var c:v.clientes()) {
   UUID cid=UUID.randomUUID();ids.put(c.codigo(),cid);
   jdbc.update("INSERT INTO clientes(id,nombre,direccion,telefono,observaciones) VALUES(?,?,?,?,?)",cid,c.nombre(),c.direccion(),c.telefono(),c.observaciones());
   jdbc.update("INSERT INTO cuentas_clientes(cliente_id,saldo) VALUES(?,?)",cid,c.saldo());
   jdbc.update("INSERT INTO clientes_importados(codigo,cliente_id,importacion_id,saldo_inicial,envases_12,envases_20,maquinas) VALUES(?,?,?,?,?,?,?)",c.codigo(),cid,id,c.saldo(),c.envases12(),c.envases20(),c.maquinas());
   jdbc.update("INSERT INTO envases_cliente(cliente_id,producto_id,cantidad) VALUES(?,?,?),(?,?,?)",cid,P12,c.envases12(),cid,P20,c.envases20());
   jdbc.update("INSERT INTO maquinas_cliente(cliente_id,producto_id,cantidad) VALUES(?,?,?)",cid,MAQ,c.maquinas());
  }
  for(var a:v.abonos()) {
   UUID aid=UUID.randomUUID();
   jdbc.update("INSERT INTO abonos_cliente(id,cliente_id,plan_id,periodo,plan_nombre,plan_version,importe,importacion_id) VALUES(?,?,?,?,?,?,?,?)",aid,ids.get(a.codigo()),a.planId(),a.mes(),a.plan(),a.version(),a.importe(),id);
   if(a.incluidos12()>0) jdbc.update("INSERT INTO cupos_abono VALUES(?,?,?,?)",aid,P12,a.incluidos12(),a.consumidos12());
   if(a.incluidos20()>0) jdbc.update("INSERT INTO cupos_abono VALUES(?,?,?,?)",aid,P20,a.incluidos20(),a.consumidos20());
  }
  jdbc.update("UPDATE importaciones_clientes SET confirmado_en=CURRENT_TIMESTAMP WHERE id=?",id);
  return new Vista(id,g.corte(),"CONFIRMADA",v.clientes(),v.abonos(),List.of());
 }
 private Guardado cargar(UUID id,boolean lock) {
  var rows=jdbc.query("SELECT * FROM importaciones_clientes WHERE id=?"+(lock?" FOR UPDATE":""),(rs,n)->new Guardado(id,rs.getObject("usuario_id",UUID.class),rs.getObject("fecha_corte",LocalDate.class),rs.getBytes("archivo"),rs.getString("huella"),rs.getObject("creado_en",OffsetDateTime.class),rs.getObject("confirmado_en",OffsetDateTime.class),rs.getInt("cantidad")),id);
  if(rows.isEmpty()) throw error(404,"Importación no encontrada");return rows.getFirst();
 }
 private Vista validar(byte[] bytes,LocalDate corte) {
  List<ErrorFila> errores=new ArrayList<>();List<Cliente> clientes=new ArrayList<>();List<Abono> abonos=new ArrayList<>();
  Map<String,ExcelClientes.Hoja> hojas;
  try{hojas=ExcelClientes.leer(bytes);}catch(IllegalArgumentException e){return new Vista(null,corte,"INVALIDA",List.of(),List.of(),List.of(new ErrorFila("archivo",0,e.getMessage())));}
  Map<String,List<String>> columnas=new LinkedHashMap<>();
  columnas.put("clientes",List.of("codigo","nombre","direccion","telefono","observaciones"));columnas.put("saldos",List.of("codigo","deuda","saldo_a_favor"));columnas.put("envases",List.of("codigo","bidones_12","bidones_20"));columnas.put("maquinas",List.of("codigo","cantidad"));columnas.put("abonos",List.of("codigo","plan","mes","importe","incluidos_12","consumidos_12","incluidos_20","consumidos_20"));
  for(var col:columnas.entrySet()) {
   var hoja=hojas.get(col.getKey());
   if(hoja==null) {if(!col.getKey().equals("abonos")) errores.add(new ErrorFila(col.getKey(),1,"Falta la hoja requerida"));}
   else if(!new HashSet<>(hoja.columnas()).equals(new HashSet<>(col.getValue()))) errores.add(new ErrorFila(col.getKey(),1,"Columnas esperadas: "+String.join(", ",col.getValue())));
  }
  if(!errores.isEmpty()) return new Vista(null,corte,"INVALIDA",clientes,abonos,errores);
  Map<String,Map<String,ExcelClientes.Fila>> indices=new HashMap<>();
  for(String hoja:List.of("clientes","saldos","envases","maquinas")) {
   Map<String,ExcelClientes.Fila> indice=new LinkedHashMap<>();indices.put(hoja,indice);
   for(var fila:hojas.get(hoja).filas()) {
    String codigo=fila.get("codigo").toUpperCase(Locale.ROOT);
    if(!codigo.matches("[A-Z0-9_-]{1,50}")) errores.add(new ErrorFila(hoja,fila.numero(),"Código requerido: letras, números, guion o guion bajo; máximo 50"));
    if(indice.putIfAbsent(codigo,fila)!=null) errores.add(new ErrorFila(hoja,fila.numero(),"Código repetido: "+codigo));
   }
  }
  var principales=indices.get("clientes");
  if(principales.isEmpty()) errores.add(new ErrorFila("clientes",2,"Agregá al menos un cliente"));
  for(String hoja:List.of("saldos","envases","maquinas")) {
   for(var f:indices.get(hoja).entrySet()) if(!principales.containsKey(f.getKey())) errores.add(new ErrorFila(hoja,f.getValue().numero(),"Código sin cliente: "+f.getKey()));
   for(String codigo:principales.keySet()) if(!indices.get(hoja).containsKey(codigo)) errores.add(new ErrorFila(hoja,0,"Falta la fila de "+codigo+". Completá ceros explícitos si no tiene saldo o tenencias"));
  }
  for(var entry:principales.entrySet()) {
   String codigo=entry.getKey();var f=entry.getValue();
   if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM clientes_importados WHERE codigo=?)",Boolean.class,codigo))) errores.add(new ErrorFila("clientes",f.numero(),"El código ya fue importado: "+codigo));
   if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM clientes WHERE lower(btrim(nombre))=lower(?) AND lower(btrim(direccion))=lower(?))",Boolean.class,f.get("nombre"),f.get("direccion")))) errores.add(new ErrorFila("clientes",f.numero(),"Ya existe un cliente con ese nombre y dirección. Revisalo antes de importar"));
   String nombre=texto(f,"nombre",150,true,errores),direccion=texto(f,"direccion",250,true,errores),telefono=texto(f,"telefono",50,true,errores),obs=texto(f,"observaciones",2000,false,errores);
   var s=indices.get("saldos").get(codigo);var e=indices.get("envases").get(codigo);var m=indices.get("maquinas").get(codigo);
   if(s==null||e==null||m==null) continue;
   BigDecimal deuda=numero(s,"deuda","saldos",false,errores),favor=numero(s,"saldo_a_favor","saldos",false,errores);
   if(deuda.signum()>0&&favor.signum()>0) errores.add(new ErrorFila("saldos",s.numero(),"No puede tener deuda y saldo a favor simultáneamente"));
   clientes.add(new Cliente(codigo,nombre,direccion,telefono,obs,deuda.subtract(favor),numero(e,"bidones_12","envases",true,errores).intValue(),numero(e,"bidones_20","envases",true,errores).intValue(),numero(m,"cantidad","maquinas",true,errores).intValue()));
  }
  Set<String> nombres=new HashSet<>();for(var c:clientes)if(!nombres.add(c.nombre().toLowerCase(Locale.ROOT)+"\n"+c.direccion().toLowerCase(Locale.ROOT))) errores.add(new ErrorFila("clientes",0,"Nombre y dirección repetidos en el archivo: "+c.nombre()));
  Set<String> claves=new HashSet<>();
  if(hojas.containsKey("abonos")) for(var f:hojas.get("abonos").filas()) {
   String codigo=f.get("codigo").toUpperCase(Locale.ROOT);
   if(!principales.containsKey(codigo)) errores.add(new ErrorFila("abonos",f.numero(),"Código sin cliente: "+codigo));
   var planes=jdbc.query("SELECT id,nombre,version FROM planes_abono WHERE lower(btrim(nombre))=lower(?) AND activo=true",(rs,n)->new Object[]{rs.getObject(1,UUID.class),rs.getString(2),rs.getLong(3)},f.get("plan"));
   if(planes.isEmpty()) {errores.add(new ErrorFila("abonos",f.numero(),"Creá primero el plan activo con el nombre exacto: "+f.get("plan")));continue;}
   LocalDate mes;
   try {
    String valor=f.get("mes");
    mes=valor.matches("[0-9]{4}-[0-9]{2}")?YearMonth.parse(valor).atDay(1):valor.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")?LocalDate.parse(valor):LocalDate.of(1899,12,30).plusDays(new BigDecimal(valor).longValueExact());
    if(mes.getDayOfMonth()!=1||!YearMonth.from(mes).equals(YearMonth.from(corte))) throw new IllegalArgumentException();
   }catch(Exception ex){errores.add(new ErrorFila("abonos",f.numero(),"El mes debe coincidir con la fecha de corte (AAAA-MM o primer día del mes)"));continue;}
   int i12=numero(f,"incluidos_12","abonos",true,errores).intValue(),c12=numero(f,"consumidos_12","abonos",true,errores).intValue(),i20=numero(f,"incluidos_20","abonos",true,errores).intValue(),c20=numero(f,"consumidos_20","abonos",true,errores).intValue();
   if(i12+i20==0||c12>i12||c20>i20) errores.add(new ErrorFila("abonos",f.numero(),"Cupo inválido: incluidos > 0 y consumidos no puede superar incluidos"));
   Object[] p=planes.getFirst();if(!claves.add(codigo+"/"+p[0]+"/"+mes)) errores.add(new ErrorFila("abonos",f.numero(),"Abono repetido para cliente, plan y mes"));
   abonos.add(new Abono(codigo,(UUID)p[0],(String)p[1],(Long)p[2],mes,numero(f,"importe","abonos",false,errores),i12,c12,i20,c20));
  }
  return new Vista(null,corte,errores.isEmpty()?"PREPARADA":"INVALIDA",clientes,abonos,errores);
 }
 private String texto(ExcelClientes.Fila f,String campo,int max,boolean requerido,List<ErrorFila> errores) {
  String v=f.get(campo);if(v.length()>max||(requerido&&v.isEmpty())) errores.add(new ErrorFila("clientes",f.numero(),campo+": "+(requerido?"obligatorio, ":"")+"máximo "+max+" caracteres"));return v;
 }
 private BigDecimal numero(ExcelClientes.Fila f,String campo,String hoja,boolean entero,List<ErrorFila> errores) {
  try {var v=new BigDecimal(f.get(campo));if(v.signum()<0||v.compareTo(new BigDecimal(entero?"1000000":"9999999999.99"))>0||(entero?v.stripTrailingZeros().scale()>0:v.stripTrailingZeros().scale()>2))throw new IllegalArgumentException();return v;}
  catch(Exception e){errores.add(new ErrorFila(hoja,f.numero(),campo+": completá un número no negativo"+(entero?" entero":" con hasta 2 decimales")+"; no dejes vacío"));return BigDecimal.ZERO;}
 }
 private String huella(Vista v) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((v.fechaCorte()+"\n"+v.clientes()+"\n"+v.abonos()).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private Usuario admin(String username) {var u=usuarios.findByUsername(username).filter(Usuario::isActivo).orElseThrow(()->error(401,"Usuario no disponible"));if(u.getRol()!=Rol.ADMIN)throw error(403,"Se requiere administrador");return u;}
 private ResponseStatusException error(int status,String message){return new ResponseStatusException(HttpStatus.valueOf(status),message);}
}
