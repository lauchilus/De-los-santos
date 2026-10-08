package com.agua.backend.cliente;
import com.agua.backend.usuario.*;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service @RequiredArgsConstructor
public class GestionClienteService {
 private final JdbcTemplate jdbc; private final UsuarioRepository usuarios;
 public record Solicitud(@NotNull UUID operacionId,@NotBlank @Size(max=150) String nombre,@NotBlank @Size(max=250) String direccion,@NotBlank @Size(max=50) String telefono,@Size(max=2000) String observaciones,@PositiveOrZero Long version){}
 public record Pagina(List<ClienteResponse> contenido,int pagina,boolean hayMas){}
 private static final RowMapper<ClienteResponse> MAPPER=(rs,n)->new ClienteResponse(rs.getObject("id",UUID.class),rs.getString("nombre"),rs.getString("direccion"),rs.getString("telefono"),rs.getString("observaciones"),rs.getBoolean("activo"),rs.getTimestamp("creado_en").toInstant(),rs.getTimestamp("actualizado_en").toInstant(),rs.getLong("version"));
 private Usuario actor(String username){var u=usuarios.findByUsername(username).filter(Usuario::isActivo).orElseThrow(()->error(401,"Usuario no disponible"));if(u.getRol()!=Rol.ADMIN&&u.getRol()!=Rol.REPARTIDOR)throw error(403,"Sin permiso");return u;}
 private static ResponseStatusException error(int status,String message){return new ResponseStatusException(HttpStatus.valueOf(status),message);}
 @Transactional(readOnly=true) public Pagina listar(String texto,int pagina,String username){actor(username);if(pagina<0||pagina>100000||texto.length()>250)throw error(400,"Filtro no válido");var list=jdbc.query("SELECT * FROM clientes WHERE position(lower(?) in lower(concat_ws(' ',nombre,direccion,telefono)))>0 ORDER BY nombre,id LIMIT 21 OFFSET ?",MAPPER,texto.strip(),pagina*20);return new Pagina(List.copyOf(list.subList(0,Math.min(20,list.size()))),pagina,list.size()>20);}
 @Transactional public ClienteResponse guardar(UUID destino,Solicitud r,String username){
  var u=actor(username);if(r.operacionId()==null||r.nombre()==null||r.nombre().isBlank()||r.direccion()==null||r.direccion().isBlank()||r.telefono()==null||r.telefono().isBlank())throw error(400,"Completá los datos obligatorios");
  if((destino==null&&r.version()!=null)||(destino!=null&&(r.version()==null||r.version()<0)))throw error(400,"Versión no válida");
  String nombre=r.nombre().strip(),direccion=r.direccion().strip(),telefono=r.telefono().strip(),obs=r.observaciones()==null?null:r.observaciones().strip();if(obs!=null&&obs.isEmpty())obs=null;
  StringBuilder fingerprint=new StringBuilder();for(Object value:new Object[]{destino,r.version(),nombre,direccion,telefono,obs}){String s=value==null?null:value.toString();fingerprint.append(s==null?-1:s.length()).append(':').append(s==null?"":s);}
  String solicitud=fingerprint.toString();jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",rs->{},r.operacionId().toString());
  var existing=jdbc.queryForList("SELECT usuario_id,solicitud FROM operaciones_gestion_cliente WHERE id=?",r.operacionId());
  if(!existing.isEmpty()){var old=existing.getFirst();if(!u.getId().equals(old.get("usuario_id"))||!solicitud.equals(old.get("solicitud")))throw error(409,"Identificador utilizado con otros datos");return respuesta(r.operacionId());}
  UUID id=destino==null?r.operacionId():destino;
  if(destino==null){int n=jdbc.update("INSERT INTO clientes(id,nombre,direccion,telefono,observaciones) VALUES(?,?,?,?,?) ON CONFLICT(id) DO NOTHING",id,nombre,direccion,telefono,obs);if(n==0)throw error(409,"El identificador de cliente ya existe");}
  else {int n=jdbc.update("UPDATE clientes SET nombre=?,direccion=?,telefono=?,observaciones=?,version=version+1,actualizado_en=CURRENT_TIMESTAMP WHERE id=? AND version=?",nombre,direccion,telefono,obs,id,r.version());if(n==0)throw error(409,"El cliente cambió o ya no está disponible. Consultalo nuevamente antes de editar.");}
  jdbc.update("INSERT INTO operaciones_gestion_cliente(id,cliente_id,usuario_id,solicitud,respuesta) SELECT ?,id,?,?,to_jsonb(c) FROM clientes c WHERE id=?",r.operacionId(),u.getId(),solicitud,id);
  return respuesta(r.operacionId());
 }
 private ClienteResponse respuesta(UUID operacion){return jdbc.queryForObject("SELECT c.* FROM operaciones_gestion_cliente o CROSS JOIN LATERAL jsonb_populate_record(NULL::clientes,o.respuesta) c WHERE o.id=?",MAPPER,operacion);}
}
