package unimet.avilaos.pruebas;

import unimet.avilaos.so.archivos.Permiso;

import java.io.File;
import unimet.avilaos.cluster.Cluster;
import unimet.avilaos.cluster.ConfiguracionProcesos;
import unimet.avilaos.cluster.EspecificacionES;
import unimet.avilaos.cluster.EspecificacionProceso;
import unimet.avilaos.estructuras.ListaEnlazada;
import unimet.avilaos.estructuras.Recorrido;
import unimet.avilaos.hardware.disco.TipoOperacionES;
import unimet.avilaos.json.ArchivoProcesos;
import unimet.avilaos.json.ArchivoSistemaArchivos;
import unimet.avilaos.json.ErrorJson;
import unimet.avilaos.so.SistemaOperativo;
import unimet.avilaos.so.archivos.ModoUsuario;
import unimet.avilaos.so.archivos.OperacionRechazada;
import unimet.avilaos.so.archivos.SistemaArchivos;
import unimet.avilaos.so.disco.TipoPoliticaDisco;
import unimet.avilaos.so.proceso.EstadoProceso;
import unimet.avilaos.so.planificacion.TipoPolitica;
import unimet.avilaos.so.proceso.PCB;
import unimet.avilaos.so.proceso.TipoProceso;

/**
 * Pruebas del sistema completo sin la GUI: se cargan los dos JSON, se
 * avanza el reloj paso a paso y se revisa que los procesos de E/S pasen
 * por las colas del computador y por la cola del disco.
 * Se corre como un main normal (Run File en NetBeans).
 */
public final class PruebasIntegracion {

    private static int fallas = 0;
    private static int total = 0;
    private static String carpeta = "datos";

    public static void main(String[] args) {
        if (args.length > 0) {
            carpeta = args[0];
        }
        casoDelEnunciado();
        cabezalLento();
        todasLasPoliticas();
        crudCompleto();
        rechazos();
        deadlineEnLaCola();
        jsonDeProcesosInvalido();
        continuidadProyecto1();
        suspensionPorElDisco();
        scanHastaElFinalDeArchivos();
        permisosYDuenosConProcesos();
        System.out.println();
        System.out.println(fallas == 0 ? "TODO BIEN: " + total + " verificaciones"
                : fallas + " de " + total + " verificaciones fallaron");
        System.exit(fallas > 0 ? 1 : 0);
    }

    private static Cluster armar(TipoPoliticaDisco politica) {
        return armar(politica, 0);
    }

    private static Cluster armar(TipoPoliticaDisco politica, int velocidadCabezal) {
        SistemaArchivos fs = ArchivoSistemaArchivos.cargar(new File(carpeta, "sistema_archivos.json"), 0, 1);
        ConfiguracionProcesos cfg = ArchivoProcesos.cargar(new File(carpeta, "procesos.json"));
        if (politica != null) {
            cfg.politicaDisco = politica;
        }
        if (velocidadCabezal > 0) {
            cfg.posicionesPorCiclo = velocidadCabezal;
        }
        return Cluster.crear(cfg, fs);
    }

    /** Avanza el reloj hasta que no quede ningun proceso vivo. Devuelve el log completo. */
    private static String correr(Cluster c, int esperados) {
        StringBuilder log = new StringBuilder();
        SistemaOperativo os = c.getComputadores()[0].getOs();
        for (int i = 0; i < 20000 && os.foto().terminados.length < esperados; i++) {
            c.getReloj().paso();
            Recorrido<String> r = c.getLog().vaciar().recorrido();
            while (r.haySiguiente()) {
                log.append(r.siguiente()).append('\n');
            }
        }
        return log.toString();
    }

    /** Bloques en el orden en que el disco los atendio, sacados del log. */
    private static String ordenDeAtencion(String log) {
        StringBuilder sb = new StringBuilder();
        String marca = " en el bloque ";
        for (String linea : log.split("\n")) {
            int i = linea.indexOf(marca);
            if (linea.contains("El disco atiende") && i >= 0) {
                String resto = linea.substring(i + marca.length());
                sb.append(sb.length() > 0 ? " " : "").append(resto, 0, resto.indexOf(' '));
            }
        }
        return sb.toString();
    }

    private static void casoDelEnunciado() {
        System.out.println("== Los dos JSON del enunciado con FIFO ==");
        Cluster c = armar(null);
        verificar("un solo computador", 1L, c.getComputadores().length);
        verificar("swap del JSON", 100L, c.getDisco().getTamanoSwap());
        verificar("politica inicial", "FIFO", c.getPlanificadorDisco().getTipoPolitica().toString());
        verificar("procesos en memoria", 8L * 16, c.getComputadores()[0].getRam().getUsada());
        String log = correr(c, 8);
        verificar("orden de atencion", "95 180 34 119 11 123 62 64", ordenDeAtencion(log));
        verificar("terminan los 8", 8L, c.getComputadores()[0].getOs().foto().terminados.length);
        verificar("se bloquean por E/S de disco", "true", String.valueOf(log.contains("se bloquea por E/S de disco")));
        verificar("vuelven a Listo", "true", String.valueOf(log.contains("se desbloquea (fin de E/S de disco)")));
        verificar("DELETE borro img1.png", "true", String.valueOf(c.getSistemaArchivos().buscar("/usuarios/img1.png") == null));
        verificar("se libero su bloque", 193L, c.getDisco().bloquesLibres());
        verificar("RAM devuelta", 0L, c.getComputadores()[0].getRam().getUsada());
        verificar("8 solicitudes atendidas", 8L, c.getPlanificadorDisco().getMetricas().getTotalAtendidas());
        verificar("cola de E/S vacia", 0L, c.getPlanificadorDisco().pendientes());
        verificar("ninguno murio por deadline", 8L, c.getComputadores()[0].getOs().getEstadisticas().getCompletados());
    }

    /**
     * Con el cabezal a 1 posicion por ciclo FIFO recorre 644 posiciones y
     * el deadline del ejemplo es 500: los ultimos procesos no llegan.
     */
    private static void cabezalLento() {
        System.out.println("== El mismo caso con el cabezal a 1 posicion por ciclo ==");
        Cluster c = armar(TipoPoliticaDisco.FIFO, 1);
        correr(c, 8);
        long bien = c.getComputadores()[0].getOs().getEstadisticas().getCompletados();
        verificar("FIFO pierde procesos por deadline", "true", String.valueOf(bien < 8));
        c = armar(TipoPoliticaDisco.SSTF, 1);
        correr(c, 8);
        verificar("SSTF si llega a tiempo", 8L, c.getComputadores()[0].getOs().getEstadisticas().getCompletados());
    }

    private static void todasLasPoliticas() {
        System.out.println("== Todas las politicas de disco terminan el mismo trabajo ==");
        for (TipoPoliticaDisco t : TipoPoliticaDisco.values()) {
            Cluster c = armar(t);
            correr(c, 8);
            verificar(t + " termina los 8", 8L, c.getComputadores()[0].getOs().getEstadisticas().getCompletados());
        }
        Cluster c = armar(TipoPoliticaDisco.FIFO);
        for (int i = 0; i < 10; i++) {
            c.getReloj().paso();
        }
        c.cambiarPoliticaDisco(TipoPoliticaDisco.SSTF);
        correr(c, 8);
        verificar("cambio de politica en caliente", "SSTF", c.getPlanificadorDisco().getTipoPolitica().toString());
        verificar("atendidas con las dos", 8L, c.getPlanificadorDisco().getMetricas().atendidasCon(TipoPoliticaDisco.FIFO)
                + c.getPlanificadorDisco().getMetricas().atendidasCon(TipoPoliticaDisco.SSTF));
    }

    private static EspecificacionES proceso(String nombre, TipoOperacionES op, String ruta, int bloques,
            String nuevoNombre, String usuario, ModoUsuario modo) {
        return new EspecificacionES(nombre, 16, 0, 500, 3, op, ruta, bloques, nuevoNombre, usuario, modo);
    }

    private static void crudCompleto() {
        System.out.println("== Crear, leer, renombrar y eliminar con procesos ==");
        Cluster c = armar(null);
        correr(c, 8);
        ModoUsuario adm = ModoUsuario.ADMINISTRADOR;
        c.crearProcesoES(proceso("crear dir", TipoOperacionES.CREAR, "/docs", 0, null, "admin", adm));
        correr(c, 9);
        PCB p = c.crearProcesoES(proceso("crear", TipoOperacionES.CREAR, "/docs/tarea.txt", 5, null, "admin", adm));
        correr(c, 10);
        verificar("archivo creado con 5 bloques", 5L, c.getSistemaArchivos().buscar("/docs/tarea.txt").getTamanoBloques());
        verificar("guarda el proceso que lo creo", p.getEtiqueta(), c.getSistemaArchivos().buscar("/docs/tarea.txt").getProcesoCreador());
        verificar("el proceso ejecuto sus 3 instrucciones", 3L, p.getContadorPrograma());
        verificar("termino", EstadoProceso.TERMINADO.name(), p.getEstado().name());
        c.crearProcesoES(proceso("leer", TipoOperacionES.LEER, "/docs/tarea.txt", 0, null, "admin", adm));
        c.crearProcesoES(proceso("renombrar", TipoOperacionES.ACTUALIZAR, "/docs/tarea.txt", 0, "final.txt", "admin", adm));
        correr(c, 12);
        verificar("renombrado", "true", String.valueOf(c.getSistemaArchivos().buscar("/docs/final.txt") != null));
        c.crearProcesoES(proceso("borrar dir", TipoOperacionES.ELIMINAR, "/docs", 0, null, "admin", adm));
        correr(c, 13);
        verificar("directorio eliminado", "true", String.valueOf(c.getSistemaArchivos().buscar("/docs") == null));
        verificar("bloques devueltos", 193L, c.getDisco().bloquesLibres());
    }

    private static void rechazos() {
        System.out.println("== Operaciones rechazadas ==");
        Cluster c = armar(null);
        correr(c, 8);
        String motivo = null;
        try {
            c.crearProcesoES(proceso("ajeno", TipoOperacionES.ELIMINAR, "/sistema/boot.bin", 0, null,
                    "user2", ModoUsuario.USUARIO));
        } catch (OperacionRechazada e) {
            motivo = e.getMessage();
        }
        anotar("modo usuario no crea el proceso -> " + motivo, motivo != null);
        verificar("no entro ningun proceso", 0L, c.getComputadores()[0].getOs().getCarga());
        c.crearProcesoES(proceso("propio", TipoOperacionES.LEER, "/usuarios/data.csv", 0, null,
                "user2", ModoUsuario.USUARIO));
        correr(c, 9);
        verificar("modo usuario lee lo suyo", 9L, c.getPlanificadorDisco().getMetricas().getTotalAtendidas());
        // en modo administrador el error aparece cuando el proceso ejecuta la llamada
        PCB p = c.crearProcesoES(proceso("fantasma", TipoOperacionES.LEER, "/no/existe", 0, null,
                "admin", ModoUsuario.ADMINISTRADOR));
        String log = correr(c, 10);
        verificar("se registra el rechazo", "true", String.valueOf(log.contains("se rechaza READ /no/existe")));
        verificar("no llega al disco", 9L, c.getPlanificadorDisco().getMetricas().getTotalAtendidas());
        verificar("el proceso termina igual", EstadoProceso.TERMINADO.name(), p.getEstado().name());
    }

    private static void deadlineEnLaCola() {
        System.out.println("== Un proceso que muere por deadline esperando al disco ==");
        Cluster c = armar(null);
        // es el noveno en la cola de listos y el cabezal tarda en llegar: el deadline vence antes
        PCB p = c.crearProcesoES(new EspecificacionES("apurado", 16, 0, 25, 2, TipoOperacionES.LEER,
                "/usuarios/video.mp4", 0, null, "admin", ModoUsuario.ADMINISTRADOR));
        correr(c, 9);
        verificar("vencio", "true", String.valueOf(p.isTerminadoPorDeadline()));
        verificar("los otros 8 terminan bien", 8L, c.getComputadores()[0].getOs().getEstadisticas().getCompletados());
        verificar("su solicitud salio de la cola", 0L, c.getPlanificadorDisco().pendientes());
        verificar("RAM devuelta", 0L, c.getComputadores()[0].getRam().getUsada());
    }

    private static void jsonDeProcesosInvalido() {
        System.out.println("== JSON de procesos invalido ==");
        String base = "{\"globalConfig\":{\"cycleDurationMs\":300,\"swapSizeBlocks\":100,\"initialDiskPolicy\":\"%s\"},"
                + "\"computer\":{\"ramCapacity\":64,\"policy\":\"%s\",\"quantum\":0},\"processes\":[%s]}";
        String ok = "{\"name\":\"A\",\"memory\":16,\"deadline\":50,\"instructions\":2,"
                + "\"targetPath\":\"/a\",\"operation\":\"READ\",\"fileSizeBlocks\":0}";
        verificar("uno valido", 1L, ArchivoProcesos.desdeTexto(String.format(base, "C-SCAN", "RR", ok)).procesos.tamano());
        errorJson("politica de disco rara", String.format(base, "ZIGZAG", "FCFS", ok));
        errorJson("politica de CPU rara", String.format(base, "FIFO", "LOTERIA", ok));
        errorJson("operacion rara", String.format(base, "FIFO", "FCFS", ok.replace("READ", "COPY")));
        errorJson("no cabe en la RAM", String.format(base, "FIFO", "FCFS", ok.replace("16", "999")));
        errorJson("instrucciones en cero", String.format(base, "FIFO", "FCFS", ok.replace("\"instructions\":2", "\"instructions\":0")));
        errorJson("falta la ruta", String.format(base, "FIFO", "FCFS", ok.replace("\"targetPath\":\"/a\",", "")));
        errorJson("sin processes", "{\"globalConfig\":{\"cycleDurationMs\":300,\"swapSizeBlocks\":1,"
                + "\"initialDiskPolicy\":\"FIFO\"},\"computer\":{\"ramCapacity\":64,\"policy\":\"FCFS\"}}");
    }

    /** Lo del Proyecto 1 sigue igual: procesos normales, sin disco y junto a los de E/S. */
    private static void continuidadProyecto1() {
        System.out.println("== Continuidad con el Proyecto 1 ==");
        Cluster sinDisco = new Cluster(new int[]{256, 256}, new TipoPolitica[]{TipoPolitica.ROUND_ROBIN,
            TipoPolitica.EDF}, 100, 3, 2);
        sinDisco.crearProceso(new EspecificacionProceso("cpu", TipoProceso.CPU_BOUND, 10, 32, 1, 200, 1, 0, 0, "", 0, 0));
        sinDisco.crearProceso(new EspecificacionProceso("io", TipoProceso.IO_BOUND, 10, 32, 1, 200, 2, 3, 4, "", 0, 0));
        for (int i = 0; i < 300; i++) {
            sinDisco.getReloj().paso();
        }
        verificar("dos computadores sin disco", 2L, sinDisco.getComputadores()[0].getOs().getEstadisticas().getCompletados()
                + sinDisco.getComputadores()[1].getOs().getEstadisticas().getCompletados());

        Cluster c = armar(null);
        c.getComputadores()[0].getOs().cambiarPolitica(TipoPolitica.ROUND_ROBIN);
        c.crearProceso(new EspecificacionProceso("cpu", TipoProceso.CPU_BOUND, 30, 32, 1, 400, 1, 0, 0, "", 0, 0));
        c.crearProceso(new EspecificacionProceso("io", TipoProceso.IO_BOUND, 20, 32, 1, 400, 1, 4, 5, "", 0, 0));
        correr(c, 10);
        verificar("mezclados con los de E/S de disco", 10L, c.getComputadores()[0].getOs().getEstadisticas().getCompletados());
        String rechazo = null;
        try {
            c.crearProceso(new EspecificacionProceso("x", TipoProceso.ES_ARCHIVO, 5, 16, 1, 100, 1, 0, 0, "", 0, 0));
        } catch (IllegalArgumentException e) {
            rechazo = e.getMessage();
        }
        anotar("el dialogo viejo no crea procesos de disco -> " + rechazo, rechazo != null);
    }

    // ---------- utilidades ----------

    private static void errorJson(String nombre, String json) {
        String motivo = null;
        try {
            ArchivoProcesos.desdeTexto(json);
        } catch (ErrorJson e) {
            motivo = e.getMessage();
        }
        anotar(nombre + " -> " + motivo, motivo != null);
    }

    /** Los JSON del enunciado pero con poca RAM, para obligar al planificador a mediano plazo. */
    private static Cluster armarConPocaRam(int ram) {
        try {
            String texto = java.nio.file.Files.readString(new File(carpeta, "procesos.json").toPath())
                    .replace("\"ramCapacity\": 1024", "\"ramCapacity\": " + ram)
                    .replace("\"deadline\": 500", "\"deadline\": 50000");
            SistemaArchivos fs = ArchivoSistemaArchivos.cargar(new File(carpeta, "sistema_archivos.json"), 0, 1);
            return Cluster.crear(ArchivoProcesos.desdeTexto(texto), fs);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void suspensionPorElDisco() {
        System.out.println("== Suspension y reactivacion pasan por la cola del disco ==");
        Cluster c = armarConPocaRam(40);
        SistemaOperativo os = c.getComputadores()[0].getOs();
        verificar("solo caben 2 al principio", 32L, c.getComputadores()[0].getRam().getUsada());
        verificar("los otros esperan como Nuevos", 6L, os.foto().nuevos.length);
        StringBuilder log = new StringBuilder();
        boolean vioListoSuspendido = false;
        boolean vioBloqueadoSuspendido = false;
        boolean ramSeRespeta = true;
        for (int i = 0; i < 60000 && os.foto().terminados.length < 8; i++) {
            c.getReloj().paso();
            Recorrido<String> r = c.getLog().vaciar().recorrido();
            while (r.haySiguiente()) {
                log.append(r.siguiente()).append('\n');
            }
            vioListoSuspendido |= os.foto().listosSuspendidos.length > 0;
            vioBloqueadoSuspendido |= os.foto().bloqueadosSuspendidos.length > 0;
            ramSeRespeta &= c.getComputadores()[0].getRam().getUsada() <= 40;
        }
        String l = log.toString();
        verificar("terminan los 8 sin vencer", 8L, os.getEstadisticas().getCompletados());
        anotar("hubo suspensiones (" + os.getSuspensiones() + ")", os.getSuspensiones() > 0);
        anotar("hubo reactivaciones (" + os.getReactivaciones() + ")", os.getReactivaciones() > 0);
        verificar("todo lo suspendido se reactivo", os.getSuspensiones(), os.getReactivaciones());
        verificar("se suspende para admitir", "true", String.valueOf(l.contains("Se suspende al proceso")
                && l.contains("para admitir a")));
        verificar("SUSPENDER entra a la cola del disco", "true", String.valueOf(l.contains("encola Suspender sobre swap/")));
        verificar("REACTIVAR entra a la cola del disco", "true", String.valueOf(l.contains("encola Reactivar sobre swap/")));
        verificar("el disco atiende el swap", "true", String.valueOf(l.contains("El disco termino Suspender")
                && l.contains("El disco termino Reactivar")));
        anotar("se vio la cola Listo/Suspendido", vioListoSuspendido);
        anotar("se vio la cola Bloqueado/Suspendido", vioBloqueadoSuspendido);
        anotar("nunca se paso de la RAM", ramSeRespeta);
        verificar("RAM devuelta", 0L, c.getComputadores()[0].getRam().getUsada());
        verificar("swap vacio al final", 100L, c.getDisco().swapLibre());
        long atendidas = c.getPlanificadorDisco().getMetricas().getTotalAtendidas();
        verificar("solicitudes = 8 de archivos + suspensiones + reactivaciones",
                8 + os.getSuspensiones() + os.getReactivaciones(), atendidas);
    }

    /** Casos P2 a P4: se detiene el disco hasta que las 8 solicitudes esten en la cola. */
    private static String conColaLlena(TipoPoliticaDisco politica) {
        Cluster c = armar(politica);
        c.detenerDisco(true);
        for (int i = 0; i < 200 && c.getPlanificadorDisco().pendientes() < 8; i++) {
            c.getReloj().paso();
        }
        verificar(politica.getEtiqueta() + ": las 8 solicitudes esperan en la cola", 8L,
                c.getPlanificadorDisco().pendientes());
        verificar("el cabezal no se movio", 50L, c.getDisco().getCabezal());
        c.detenerDisco(false);
        return correr(c, 8);
    }

    private static void scanHastaElFinalDeArchivos() {
        System.out.println("== Casos P2 a P4 en la simulacion completa (disco detenido hasta llenar la cola) ==");
        String log = conColaLlena(TipoPoliticaDisco.SSTF);
        verificar("SSTF: orden del caso P2", "62 64 34 11 95 119 123 180", ordenDeAtencion(log));
        log = conColaLlena(TipoPoliticaDisco.SCAN);
        verificar("SCAN: orden del caso P3", "62 64 95 119 123 180 34 11", ordenDeAtencion(log));
        verificar("SCAN llega al 199 y regresa (no entra al swap)", "true",
                String.valueOf(log.contains("El cabezal va al bloque 199 (SCAN)")));
        log = conColaLlena(TipoPoliticaDisco.C_SCAN);
        verificar("C-SCAN: orden del caso P4", "62 64 95 119 123 180 11 34", ordenDeAtencion(log));
        verificar("C-SCAN va al 199 y salta al 0", "true",
                String.valueOf(log.contains("El cabezal va al bloque 199 y salta al 0")));
    }

    private static void permisosYDuenosConProcesos() {
        System.out.println("== Permisos y duenos tambien con procesos de E/S ==");
        Cluster c = armar(null);
        correr(c, 8);
        ModoUsuario adm = ModoUsuario.ADMINISTRADOR;
        ModoUsuario usr = ModoUsuario.USUARIO;
        c.crearProcesoES(new EspecificacionES("dir user1", 16, 0, 500, 2, TipoOperacionES.CREAR,
                "/usuarios/user1", 0, null, "admin", adm, Permiso.PRIVADO, "user1"));
        correr(c, 9);
        verificar("el admin crea un directorio de user1", "user1",
                c.getSistemaArchivos().buscar("/usuarios/user1").getDueno());
        c.crearProcesoES(new EspecificacionES("archivo user1", 16, 0, 500, 2, TipoOperacionES.CREAR,
                "/usuarios/user1/tarea.txt", 2, null, "user1", usr, Permiso.PUBLICO, null));
        correr(c, 10);
        verificar("user1 crea dentro de lo suyo", "user1",
                c.getSistemaArchivos().buscar("/usuarios/user1/tarea.txt").getDueno());
        verificar("con el permiso que eligio", Permiso.PUBLICO.name(),
                c.getSistemaArchivos().buscar("/usuarios/user1/tarea.txt").getPermiso().name());
        String motivo = null;
        try {
            c.crearProcesoES(new EspecificacionES("dir", 16, 0, 500, 2, TipoOperacionES.CREAR,
                    "/usuarios/user1/sub", 0, null, "user1", usr));
        } catch (OperacionRechazada e) {
            motivo = e.getMessage();
        }
        anotar("modo usuario no crea directorios -> " + motivo, motivo != null);
        c.crearProcesoES(new EspecificacionES("permiso", 16, 0, 500, 2, TipoOperacionES.ACTUALIZAR,
                "/usuarios/user1/tarea.txt", 0, null, "user1", usr, Permiso.PRIVADO, null));
        verificar("el permiso no cambia hasta que el disco atiende", Permiso.PUBLICO.name(),
                c.getSistemaArchivos().buscar("/usuarios/user1/tarea.txt").getPermiso().name());
        String log = correr(c, 11);
        verificar("cambio de permiso hecho por el proceso", Permiso.PRIVADO.name(),
                c.getSistemaArchivos().buscar("/usuarios/user1/tarea.txt").getPermiso().name());
        verificar("queda en el log", "true", String.valueOf(log.contains("Permiso de /usuarios/user1/tarea.txt cambiado a")));
        motivo = null;
        try {
            c.crearProcesoES(new EspecificacionES("ajeno", 16, 0, 500, 2, TipoOperacionES.ACTUALIZAR,
                    "/usuarios/data.csv", 0, null, "user1", usr, Permiso.PRIVADO, null));
        } catch (OperacionRechazada e) {
            motivo = e.getMessage();
        }
        anotar("no cambia permisos ajenos -> " + motivo, motivo != null);
    }

    private static void verificar(String nombre, String esperado, String obtenido) {
        boolean ok = esperado.equals(obtenido);
        anotar(nombre + (ok ? "" : ": esperaba [" + esperado + "] y salio [" + obtenido + "]"), ok);
    }

    private static void verificar(String nombre, long esperado, long obtenido) {
        verificar(nombre, String.valueOf(esperado), String.valueOf(obtenido));
    }

    private static void anotar(String linea, boolean ok) {
        total++;
        if (!ok) {
            fallas++;
        }
        System.out.println((ok ? "  ok    " : "  FALLA ") + (linea.length() > 110 ? linea.substring(0, 110) + "..." : linea));
    }
}
