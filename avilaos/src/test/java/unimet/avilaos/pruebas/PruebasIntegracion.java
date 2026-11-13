package unimet.avilaos.pruebas;

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
