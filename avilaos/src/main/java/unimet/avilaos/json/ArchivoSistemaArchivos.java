package unimet.avilaos.json;

import java.io.File;
import unimet.avilaos.estructuras.ListaEnlazada;
import unimet.avilaos.estructuras.Recorrido;
import unimet.avilaos.hardware.disco.Disco;
import unimet.avilaos.so.archivos.NodoArchivo;
import unimet.avilaos.so.archivos.OperacionRechazada;
import unimet.avilaos.so.archivos.Permiso;
import unimet.avilaos.so.archivos.SistemaArchivos;
import unimet.avilaos.so.archivos.TipoNodo;

/**
 * Carga y guarda el estado del sistema de archivos en el formato JSON
 * del enunciado (diskConfig + fileSystem).
 *
 * Al guardar se agrega por archivo un campo opcional "chain" con la
 * cadena completa de bloques, para que al cargar quede igual.
 */
public final class ArchivoSistemaArchivos {

    private ArchivoSistemaArchivos() {
    }

    public static SistemaArchivos cargar(File archivo, int tamanoSwap, int posicionesPorCiclo) {
        return desdeValor(Json.leerArchivo(archivo), tamanoSwap, posicionesPorCiclo);
    }

    /**
     * Arma el disco y el arbol a partir del JSON. Si algo esta mal lanza
     * ErrorJson con un mensaje que se le puede mostrar al usuario.
     */
    public static SistemaArchivos desdeTexto(String json, int tamanoSwap, int posicionesPorCiclo) {
        return desdeValor(Json.leer(json), tamanoSwap, posicionesPorCiclo);
    }

    private static SistemaArchivos desdeValor(ValorJson raiz, int tamanoSwap, int posicionesPorCiclo) {
        if (raiz.getTipo() != TipoJson.OBJETO) {
            throw new ErrorJson("El JSON del sistema de archivos debe ser un objeto");
        }
        ValorJson config = raiz.objetoEn("diskConfig");
        Disco disco;
        try {
            disco = new Disco(config.enteroEn("totalBlocks"), tamanoSwap,
                    config.enteroEn("initialHead", 0), posicionesPorCiclo);
        } catch (IllegalArgumentException e) {
            throw new ErrorJson("diskConfig: " + e.getMessage());
        }
        SistemaArchivos fs = new SistemaArchivos(disco);
        ValorJson entradas = raiz.arregloEn("fileSystem");

        // dos pasadas: primero los directorios, para que el orden del JSON no importe
        for (int pasada = 0; pasada < 2; pasada++) {
            Recorrido<ValorJson> r = entradas.elementos();
            while (r.haySiguiente()) {
                ValorJson e = r.siguiente();
                if (e.getTipo() != TipoJson.OBJETO) {
                    throw new ErrorJson("Cada entrada de fileSystem debe ser un objeto");
                }
                String ruta = e.textoEn("path");
                try {
                    TipoNodo tipo = TipoNodo.desdeClave(e.textoEn("type"));
                    String dueno = e.textoEn("owner", SistemaArchivos.ADMIN);
                    Permiso permiso = Permiso.desdeClave(e.textoEn("permissions", "public"));
                    if (tipo == TipoNodo.DIRECTORIO && pasada == 0) {
                        crearConPadres(fs, ruta, dueno, permiso);
                    } else if (tipo == TipoNodo.ARCHIVO && pasada == 1) {
                        fs.restaurarArchivo(ruta, e.enteroEn("startBlock"), e.enteroEn("blocks"),
                                cadena(e), dueno, permiso, e.textoEn("createdBy", "-"));
                    }
                } catch (OperacionRechazada | IllegalArgumentException ex) {
                    throw new ErrorJson(ruta + ": " + ex.getMessage());
                }
            }
        }
        return fs;
    }

    /** Crea el directorio y, si hacen falta, los de arriba. */
    private static void crearConPadres(SistemaArchivos fs, String ruta, String dueno, Permiso permiso) {
        NodoArchivo ya = fs.buscar(ruta);
        if (ya != null) {
            if (ya.esDirectorio()) {
                return; // lo creo antes un hijo que venia primero en el JSON
            }
            throw new OperacionRechazada("Ya existe " + ruta);
        }
        int corte = ruta.lastIndexOf('/');
        if (corte > 0 && fs.buscar(ruta.substring(0, corte)) == null) {
            crearConPadres(fs, ruta.substring(0, corte), SistemaArchivos.ADMIN, Permiso.PUBLICO);
        }
        fs.crearDirectorio(ruta, dueno, permiso);
    }

    private static int[] cadena(ValorJson entrada) {
        ValorJson c = entrada.obtener("chain");
        if (c == null || c.getTipo() != TipoJson.ARREGLO) {
            return null;
        }
        int[] out = new int[c.cantidad()];
        int i = 0;
        Recorrido<ValorJson> r = c.elementos();
        while (r.haySiguiente()) {
            out[i++] = r.siguiente().comoEntero("chain");
        }
        return out;
    }

    public static void guardar(SistemaArchivos fs, File archivo) {
        Json.escribirArchivo(archivo, aTexto(fs));
    }

    /** El estado actual en el mismo formato en que se carga. */
    public static String aTexto(SistemaArchivos fs) {
        Disco disco = fs.getDisco();
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"diskConfig\": {\n");
        sb.append("    \"totalBlocks\": ").append(disco.getTotalBloques()).append(",\n");
        // si el cabezal quedo en el area de intercambio, se guarda en el ultimo bloque de archivos
        int cabezal = Math.min(disco.getCabezal(), disco.getTotalBloques() - 1);
        sb.append("    \"initialHead\": ").append(cabezal).append("\n  },\n");
        sb.append("  \"fileSystem\": [");
        ListaEnlazada<NodoArchivo> nodos = fs.listarTodo();
        Recorrido<NodoArchivo> r = nodos.recorrido();
        boolean primero = true;
        while (r.haySiguiente()) {
            NodoArchivo n = r.siguiente();
            sb.append(primero ? "\n" : ",\n");
            primero = false;
            sb.append("    {\n");
            sb.append("      \"path\": ").append(Json.comillas(n.getRuta())).append(",\n");
            sb.append("      \"type\": ").append(Json.comillas(n.getTipo().getClave())).append(",\n");
            if (!n.esDirectorio()) {
                sb.append("      \"startBlock\": ").append(n.getPrimerBloque()).append(",\n");
                sb.append("      \"blocks\": ").append(n.getTamanoBloques()).append(",\n");
                sb.append("      \"chain\": [");
                int[] cadena = disco.cadena(n.getPrimerBloque());
                for (int i = 0; i < cadena.length; i++) {
                    sb.append(i > 0 ? ", " : "").append(cadena[i]);
                }
                sb.append("],\n");
                sb.append("      \"createdBy\": ").append(Json.comillas(n.getProcesoCreador())).append(",\n");
            }
            sb.append("      \"owner\": ").append(Json.comillas(n.getDueno())).append(",\n");
            sb.append("      \"permissions\": ").append(Json.comillas(n.getPermiso().getClave())).append("\n");
            sb.append("    }");
        }
        sb.append(primero ? "]\n}\n" : "\n  ]\n}\n");
        return sb.toString();
    }
}
