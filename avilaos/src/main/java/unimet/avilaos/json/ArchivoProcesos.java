package unimet.avilaos.json;

import java.io.File;
import unimet.avilaos.cluster.ConfiguracionProcesos;
import unimet.avilaos.cluster.EspecificacionES;
import unimet.avilaos.estructuras.Recorrido;
import unimet.avilaos.hardware.disco.TipoOperacionES;
import unimet.avilaos.so.archivos.ModoUsuario;
import unimet.avilaos.so.archivos.SistemaArchivos;
import unimet.avilaos.so.disco.TipoPoliticaDisco;
import unimet.avilaos.so.planificacion.TipoPolitica;

/**
 * Lee el JSON de procesos y configuracion del enunciado
 * (globalConfig + computer + processes).
 *
 * Campos opcionales que no estan en el enunciado: headSpeed en globalConfig
 * (posiciones que recorre el cabezal por ciclo, 4 si no viene) y, por
 * proceso, priority, user, mode (ADMIN o USER) y newName para renombrar.
 */
public final class ArchivoProcesos {

    private ArchivoProcesos() {
    }

    public static ConfiguracionProcesos cargar(File archivo) {
        return desdeValor(Json.leerArchivo(archivo));
    }

    /** Si algo esta mal lanza ErrorJson con un mensaje que se le puede mostrar al usuario. */
    public static ConfiguracionProcesos desdeTexto(String json) {
        return desdeValor(Json.leer(json));
    }

    private static ConfiguracionProcesos desdeValor(ValorJson raiz) {
        if (raiz.getTipo() != TipoJson.OBJETO) {
            throw new ErrorJson("El JSON de procesos debe ser un objeto");
        }
        ConfiguracionProcesos cfg = new ConfiguracionProcesos();

        ValorJson global = raiz.objetoEn("globalConfig");
        cfg.duracionCiclo = minimo(global.enteroEn("cycleDurationMs"), 1, "cycleDurationMs");
        cfg.tamanoSwap = minimo(global.enteroEn("swapSizeBlocks"), 0, "swapSizeBlocks");
        cfg.posicionesPorCiclo = minimo(global.enteroEn("headSpeed", cfg.posicionesPorCiclo), 1, "headSpeed");
        try {
            cfg.politicaDisco = TipoPoliticaDisco.desdeTexto(global.textoEn("initialDiskPolicy"));
        } catch (IllegalArgumentException e) {
            throw new ErrorJson("initialDiskPolicy: " + e.getMessage());
        }

        ValorJson computador = raiz.objetoEn("computer");
        cfg.ram = minimo(computador.enteroEn("ramCapacity"), 1, "ramCapacity");
        cfg.politica = politicaCpu(computador.textoEn("policy"));
        cfg.quantum = Math.max(1, minimo(computador.enteroEn("quantum", 0), 0, "quantum"));

        Recorrido<ValorJson> r = raiz.arregloEn("processes").elementos();
        while (r.haySiguiente()) {
            ValorJson p = r.siguiente();
            if (p.getTipo() != TipoJson.OBJETO) {
                throw new ErrorJson("Cada entrada de processes debe ser un objeto");
            }
            String nombre = p.textoEn("name");
            try {
                cfg.procesos.agregarAlFinal(proceso(nombre, p, cfg.ram));
            } catch (IllegalArgumentException e) {
                throw new ErrorJson(nombre + ": " + e.getMessage());
            }
        }
        return cfg;
    }

    private static EspecificacionES proceso(String nombre, ValorJson p, int ram) {
        if (nombre.trim().isEmpty()) {
            throw new ErrorJson("Hay un proceso sin nombre");
        }
        int memoria = minimo(p.enteroEn("memory"), 1, nombre + ": memory");
        if (memoria > ram) {
            throw new ErrorJson(nombre + ": pide " + memoria + " de memoria y la RAM es de " + ram);
        }
        TipoOperacionES operacion = TipoOperacionES.desdeClave(p.textoEn("operation"));
        if (!operacion.esDeArchivo()) {
            throw new ErrorJson(nombre + ": la operacion debe ser CREATE, READ, UPDATE o DELETE");
        }
        String ruta = p.textoEn("targetPath");
        if (!ruta.startsWith("/")) {
            throw new ErrorJson(nombre + ": targetPath debe empezar con /");
        }
        return new EspecificacionES(nombre,
                memoria,
                minimo(p.enteroEn("priority", 0), 0, nombre + ": priority"),
                minimo(p.enteroEn("deadline"), 1, nombre + ": deadline"),
                minimo(p.enteroEn("instructions"), 1, nombre + ": instructions"),
                operacion, ruta,
                minimo(p.enteroEn("fileSizeBlocks", 0), 0, nombre + ": fileSizeBlocks"),
                p.textoEn("newName", null),
                p.textoEn("user", SistemaArchivos.ADMIN),
                modo(p.textoEn("mode", "ADMIN")));
    }

    private static int minimo(int valor, int minimo, String campo) {
        if (valor < minimo) {
            throw new ErrorJson(campo + " debe ser al menos " + minimo + " (vino " + valor + ")");
        }
        return valor;
    }

    private static TipoPolitica politicaCpu(String texto) {
        String t = texto.trim().toUpperCase().replace("-", "").replace("_", "").replace(" ", "");
        return switch (t) {
            case "FCFS", "FIFO" -> TipoPolitica.FCFS;
            case "EDF" -> TipoPolitica.EDF;
            case "RR", "ROUNDROBIN" -> TipoPolitica.ROUND_ROBIN;
            case "PRIORIDADES", "PRIORIDAD", "PRIORITY", "PRIORITIES" -> TipoPolitica.PRIORIDADES;
            default -> throw new ErrorJson("Politica de CPU desconocida: " + texto);
        };
    }

    private static ModoUsuario modo(String texto) {
        String t = texto.trim().toUpperCase();
        if (t.startsWith("ADMIN")) {
            return ModoUsuario.ADMINISTRADOR;
        }
        if (t.startsWith("USER") || t.startsWith("USUARIO")) {
            return ModoUsuario.USUARIO;
        }
        throw new ErrorJson("Modo de usuario desconocido: " + texto);
    }
}
