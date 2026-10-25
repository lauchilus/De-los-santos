package com.agua.backend.importacion;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
public class ExcelClientesTest {
 public static byte[] excel(Map<String,String[][]> hojas) throws Exception {
  var bytes=new ByteArrayOutputStream();
  try(var zip=new ZipOutputStream(bytes)) {
   StringBuilder libro=new StringBuilder("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>"),rels=new StringBuilder("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");int n=0;
   for(var h:hojas.entrySet()) {n++;libro.append("<sheet name=\"").append(h.getKey()).append("\" r:id=\"rId").append(n).append("\"/>");rels.append("<Relationship Id=\"rId").append(n).append("\" Target=\"worksheets/sheet").append(n).append(".xml\"/>");
    StringBuilder xml=new StringBuilder("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");int row=0;
    for(String[] valores:h.getValue()){row++;xml.append("<row r=\"").append(row).append("\">");for(int c=0;c<valores.length;c++)xml.append("<c r=\"").append((char)('A'+c)).append(row).append("\" t=\"inlineStr\"><is><t>").append(valores[c].replace("&","&amp;").replace("<","&lt;")).append("</t></is></c>");xml.append("</row>");}
    xml.append("</sheetData></worksheet>");entry(zip,"xl/worksheets/sheet"+n+".xml",xml.toString());
   }
   entry(zip,"xl/workbook.xml",libro.append("</sheets></workbook>").toString());entry(zip,"xl/_rels/workbook.xml.rels",rels.append("</Relationships>").toString());
  }return bytes.toByteArray();
 }
 private static void entry(ZipOutputStream z,String name,String body)throws Exception{z.putNextEntry(new ZipEntry(name));z.write(body.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
 public static Map<String,String[][]> datos(String mes) {
  Map<String,String[][]> d=new LinkedHashMap<>();
  d.put("clientes",new String[][]{{"codigo","nombre","direccion","telefono","observaciones"},{"CLI-001","Persona nueva","Calle 321","0111234","Timbre"}});
  d.put("saldos",new String[][]{{"codigo","deuda","saldo_a_favor"},{"CLI-001","15000","0"}});
  d.put("envases",new String[][]{{"codigo","bidones_12","bidones_20"},{"CLI-001","1","4"}});
  d.put("maquinas",new String[][]{{"codigo","cantidad"},{"CLI-001","1"}});
  d.put("abonos",new String[][]{{"codigo","plan","mes","importe","incluidos_12","consumidos_12","incluidos_20","consumidos_20"},{"CLI-001","Cuatro",mes,"18000","0","0","4","2"}});
  return d;
 }
 @Test void leeColumnasTelefonosTextoYCincoHojas()throws Exception {var r=ExcelClientes.leer(excel(datos("2026-10")));assertEquals(5,r.size());assertEquals("0111234",r.get("clientes").filas().getFirst().get("telefono"));assertEquals(2,r.get("clientes").filas().getFirst().numero());}
 @Test void rechazaArchivoInvalidoYTamanio(){assertThrows(IllegalArgumentException.class,()->ExcelClientes.leer(new byte[]{1,2}));assertThrows(IllegalArgumentException.class,()->ExcelClientes.leer(new byte[2_000_001]));}
 @Test void rechazaHojaNoReconocida()throws Exception {byte[] b=excel(Map.of("otra",new String[][]{{"codigo"}}));assertThrows(IllegalArgumentException.class,()->ExcelClientes.leer(b));}
 @Test void rechazaEncabezadosDuplicados()throws Exception {byte[] b=excel(Map.of("clientes",new String[][]{{"codigo","codigo"}}));assertThrows(IllegalArgumentException.class,()->ExcelClientes.leer(b));}
 @Test void rechazaXmlConEntidadExterna()throws Exception {var b=new ByteArrayOutputStream();try(var z=new ZipOutputStream(b)){entry(z,"xl/workbook.xml","<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><workbook>&e;</workbook>");}assertThrows(IllegalArgumentException.class,()->ExcelClientes.leer(b.toByteArray()));}
}
