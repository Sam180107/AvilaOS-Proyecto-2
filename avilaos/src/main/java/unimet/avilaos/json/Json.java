package unimet.avilaos.json;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Lector de JSON hecho por nosotros, sin librerias ni colecciones de Java.
 * Sirve tanto para el JSON del sistema de archivos como para el de procesos.
 */
public final class Json {

    private final String s;
    private int pos = 0;

    private Json(String s) {
        this.s = s;
    }

    public static ValorJson leer(String texto) {
        if (texto == null) {
            throw new ErrorJson("No hay nada que leer");
        }
        Json lector = new Json(texto);
        lector.saltarBlancos();
        ValorJson v = lector.valor();
        lector.saltarBlancos();
        if (lector.pos < lector.s.length()) {
            throw lector.error("Sobra texto despues del JSON");
        }
        return v;
    }

    public static ValorJson leerArchivo(File archivo) {
        try {
            return leer(Files.readString(archivo.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ErrorJson("No se pudo leer " + archivo.getName() + ": " + e.getMessage());
        }
    }

    public static void escribirArchivo(File archivo, String contenido) {
        try {
            Files.writeString(archivo.toPath(), contenido, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ErrorJson("No se pudo guardar " + archivo.getName() + ": " + e.getMessage());
        }
    }

    /** Texto entre comillas con los caracteres especiales escapados. */
    public static String comillas(String texto) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private ValorJson valor() {
        if (pos >= s.length()) {
            throw error("El JSON termina antes de tiempo");
        }
        char c = s.charAt(pos);
        if (c == '{') {
            return objeto();
        }
        if (c == '[') {
            return arreglo();
        }
        if (c == '"') {
            return ValorJson.texto(cadena());
        }
        if (c == '-' || (c >= '0' && c <= '9')) {
            return numero();
        }
        if (s.startsWith("true", pos)) {
            pos += 4;
            return ValorJson.booleano(true);
        }
        if (s.startsWith("false", pos)) {
            pos += 5;
            return ValorJson.booleano(false);
        }
        if (s.startsWith("null", pos)) {
            pos += 4;
            return ValorJson.nulo();
        }
        throw error("Caracter inesperado '" + c + "'");
    }

    private ValorJson objeto() {
        ValorJson obj = ValorJson.objeto();
        pos++; // {
        saltarBlancos();
        if (mirar() == '}') {
            pos++;
            return obj;
        }
        while (true) {
            saltarBlancos();
            if (mirar() != '"') {
                throw error("Se esperaba el nombre de un campo entre comillas");
            }
            String clave = cadena();
            saltarBlancos();
            esperar(':');
            saltarBlancos();
            obj.poner(clave, valor());
            saltarBlancos();
            if (mirar() == ',') {
                pos++;
            } else {
                esperar('}');
                return obj;
            }
        }
    }

    private ValorJson arreglo() {
        ValorJson arr = ValorJson.arreglo();
        pos++; // [
        saltarBlancos();
        if (mirar() == ']') {
            pos++;
            return arr;
        }
        while (true) {
            saltarBlancos();
            arr.agregar(valor());
            saltarBlancos();
            if (mirar() == ',') {
                pos++;
            } else {
                esperar(']');
                return arr;
            }
        }
    }

    private String cadena() {
        StringBuilder sb = new StringBuilder();
        pos++; // comilla que abre
        while (true) {
            if (pos >= s.length()) {
                throw error("Falta cerrar una comilla");
            }
            char c = s.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (pos >= s.length()) {
                throw error("Escape incompleto");
            }
            char e = s.charAt(pos++);
            switch (e) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'u' -> {
                    if (pos + 4 > s.length()) {
                        throw error("Escape unicode incompleto");
                    }
                    try {
                        sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw error("Escape unicode invalido");
                    }
                    pos += 4;
                }
                case '"', '\\', '/' -> sb.append(e);
                default -> throw error("Escape desconocido \\" + e);
            }
        }
    }

    private ValorJson numero() {
        int inicio = pos;
        while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) {
            pos++;
        }
        try {
            return ValorJson.numero(Double.parseDouble(s.substring(inicio, pos)));
        } catch (NumberFormatException e) {
            throw error("Numero invalido: " + s.substring(inicio, pos));
        }
    }

    private char mirar() {
        return pos < s.length() ? s.charAt(pos) : '\0';
    }

    private void esperar(char c) {
        if (mirar() != c) {
            throw error("Se esperaba '" + c + "'");
        }
        pos++;
    }

    private void saltarBlancos() {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
            pos++;
        }
    }

    private ErrorJson error(String mensaje) {
        int linea = 1;
        for (int i = 0; i < pos && i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                linea++;
            }
        }
        return new ErrorJson(mensaje + " (linea " + linea + ")");
    }
}
