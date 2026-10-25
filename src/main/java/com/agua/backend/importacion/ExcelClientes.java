package com.agua.backend.importacion;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Lector acotado al formato tabular de importación. No evalúa fórmulas. */
public final class ExcelClientes {
    private ExcelClientes() {}
    public record Fila(int numero, Map<String,String> valores) {
        public String get(String columna) { return valores.getOrDefault(columna, "").strip(); }
    }
    public record Hoja(List<String> columnas, List<Fila> filas) {}
    public static Map<String,Hoja> leer(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > 2_000_000) throw new IllegalArgumentException("El Excel debe pesar entre 1 byte y 2 MB");
        try {
            Map<String,byte[]> archivos = new HashMap<>(); int total=0, entradas=0;
            try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                ZipEntry e;
                while ((e=zip.getNextEntry())!=null) {
                    if (++entradas>200) throw new IllegalArgumentException("Demasiadas entradas en el archivo");
                    byte[] contenido=zip.readNBytes(10_000_001); total+=contenido.length;
                    if(total>10_000_000) throw new IllegalArgumentException("El contenido descomprimido supera 10 MB");
                    if(archivos.put(e.getName(),contenido)!=null) throw new IllegalArgumentException("Entrada duplicada en el Excel");
                }
            }
            var libro=xml(archivos.get("xl/workbook.xml"));
            var props=libro.getElementsByTagNameNS("*","workbookPr");
            boolean fecha1904=props.getLength()>0 && Set.of("1","true").contains(((Element)props.item(0)).getAttribute("date1904"));
            // Evitar interpretar el sistema de fechas alternativo como una fecha incorrecta.
            if(fecha1904) throw new IllegalArgumentException("Guardá el Excel con el sistema de fechas 1900, no 1904");
            List<String> textos=new ArrayList<>();
            if(archivos.containsKey("xl/sharedStrings.xml")) {
                var si=xml(archivos.get("xl/sharedStrings.xml")).getElementsByTagNameNS("*","si");
                for(int i=0;i<si.getLength();i++) textos.add(textos((Element)si.item(i)));
            }
            Map<String,String> relaciones=new HashMap<>();
            var rels=xml(archivos.get("xl/_rels/workbook.xml.rels")).getElementsByTagNameNS("*","Relationship");
            for(int i=0;i<rels.getLength();i++) {
                var r=(Element)rels.item(i); if("External".equals(r.getAttribute("TargetMode"))) continue;
                String ruta=r.getAttribute("Target");
                if(ruta.contains("..")) throw new IllegalArgumentException("Ruta no admitida en el Excel");
                relaciones.put(r.getAttribute("Id"),ruta.startsWith("/")?ruta.substring(1):"xl/"+ruta);
            }
            Map<String,Hoja> resultado=new LinkedHashMap<>();
            var sheets=libro.getElementsByTagNameNS("*","sheet");
            for(int i=0;i<sheets.getLength();i++) {
                var sheet=(Element)sheets.item(i); String nombre=sheet.getAttribute("name").strip().toLowerCase(Locale.ROOT);
                if(!Set.of("clientes","saldos","envases","maquinas","abonos").contains(nombre)) throw new IllegalArgumentException("Hoja no reconocida: "+nombre);
                var doc=xml(archivos.get(relaciones.get(sheet.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships","id"))));
                if(doc.getElementsByTagNameNS("*","mergeCell").getLength()>0) throw new IllegalArgumentException(nombre+": no se admiten celdas combinadas");
                var rows=doc.getElementsByTagNameNS("*","row"); List<String> cabecera=new ArrayList<>(); List<Fila> filas=new ArrayList<>();
                for(int ri=0;ri<rows.getLength();ri++) {
                    var row=(Element)rows.item(ri); int numero=Integer.parseInt(row.getAttribute("r"));
                    if(numero>1001) throw new IllegalArgumentException(nombre+": máximo 1000 filas de datos");
                    Map<Integer,String> valores=new TreeMap<>(); var cells=row.getElementsByTagNameNS("*","c");
                    for(int ci=0;ci<cells.getLength();ci++) {
                        var c=(Element)cells.item(ci);String ref=c.getAttribute("r");int columna=0;
                        for(char letra:ref.toCharArray()) { if(letra<'A'||letra>'Z') break; columna=columna*26+letra-'A'+1; }
                        if(columna<1||columna>20) throw new IllegalArgumentException(nombre+" fila "+numero+": demasiadas columnas");
                        if(c.getElementsByTagNameNS("*","f").getLength()>0) throw new IllegalArgumentException(nombre+"!"+ref+": reemplazá la fórmula por su valor");
                        String tipo=c.getAttribute("t"), valor=texto(c,"v");
                        if("s".equals(tipo)) valor=textos.get(Integer.parseInt(valor));
                        else if("inlineStr".equals(tipo)) valor=textos(c);
                        else if("e".equals(tipo)) throw new IllegalArgumentException(nombre+"!"+ref+": celda con error");
                        else if(!valor.isBlank() && (tipo.isEmpty()||"n".equals(tipo))) valor=new BigDecimal(valor).stripTrailingZeros().toPlainString();
                        if(valor.length()>2000) throw new IllegalArgumentException(nombre+"!"+ref+": texto demasiado largo");
                        if(!valor.isBlank()) valores.put(columna,valor.strip());
                    }
                    if(numero==1) {
                        for(int c=1;c<=valores.size();c++) {String h=valores.getOrDefault(c,"").toLowerCase(Locale.ROOT);if(h.isEmpty()||cabecera.contains(h)) throw new IllegalArgumentException(nombre+": encabezados vacíos o repetidos");cabecera.add(h);}
                    } else if(!valores.isEmpty()) {
                        Map<String,String> datos=new LinkedHashMap<>();
                        for(var v:valores.entrySet()) {if(v.getKey()>cabecera.size()) throw new IllegalArgumentException(nombre+" fila "+numero+": columna sin encabezado");datos.put(cabecera.get(v.getKey()-1),v.getValue());}
                        filas.add(new Fila(numero,datos));
                    }
                }
                if(resultado.put(nombre,new Hoja(cabecera,filas))!=null) throw new IllegalArgumentException("Hoja repetida: "+nombre);
            }
            return resultado;
        } catch(IllegalArgumentException e) {throw e;} catch(Exception e) {throw new IllegalArgumentException("No se pudo leer el XLSX. Guardalo nuevamente como Libro de Excel (.xlsx)");}
    }
    private static Document xml(byte[] bytes) throws Exception {
        if(bytes==null) throw new IllegalArgumentException("Estructura XLSX incompleta");
        var f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }
    private static String texto(Element e,String tag) {var n=e.getElementsByTagNameNS("*",tag);return n.getLength()==0?"":n.item(0).getTextContent();}
    private static String textos(Element e) {var n=e.getElementsByTagNameNS("*","t");var b=new StringBuilder();for(int i=0;i<n.getLength();i++)b.append(n.item(i).getTextContent());return b.toString();}
}
