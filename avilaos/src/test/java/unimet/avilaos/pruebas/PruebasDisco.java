package unimet.avilaos.pruebas;

import java.io.File;
import unimet.avilaos.hardware.disco.Bloque;
import unimet.avilaos.hardware.disco.Disco;
import unimet.avilaos.hardware.disco.SolicitudES;
import unimet.avilaos.hardware.disco.TipoOperacionES;
import unimet.avilaos.hardware.disco.UsoBloque;
import unimet.avilaos.json.ArchivoSistemaArchivos;
import unimet.avilaos.json.ErrorJson;
import unimet.avilaos.so.archivos.ModoUsuario;
import unimet.avilaos.so.archivos.OperacionRechazada;
import unimet.avilaos.so.archivos.Permiso;
import unimet.avilaos.so.archivos.SistemaArchivos;
import unimet.avilaos.so.disco.PlanificadorDisco;
import unimet.avilaos.so.disco.TipoPoliticaDisco;

/**
 * Pruebas del disco, el planificador y el sistema de archivos sin la GUI.
 * Se corre como un main normal (Run File en NetBeans). Usa el ejemplo del
 * anexo y los casos P1 a P4 del enunciado.
 */
public final class PruebasDisco {

    private static int fallas = 0;
    private static int total = 0;

    public static void main(String[] args) {
        anexo();
        casosDelEnunciado(args.length > 0 ? args[0] : "datos/sistema_archivos.json");
        politicasExtra();
        asignacionEncadenada();
        areaDeIntercambio();
        permisos();
        operacionesPorSolicitud();
        idaYVueltaJson();
        jsonInvalido();
        System.out.println();
        System.out.println(fallas == 0 ? "TODO BIEN: " + total + " verificaciones"
                : fallas + " de " + total + " verificaciones fallaron");
        if (fallas > 0) {
            System.exit(1);
        }
    }

    // ---------- planificacion ----------

    private static void anexo() {
        System.out.println("== Anexo: cabezal en 50, cola 82 17 43 140 24 16 190 ==");
        int[] cola = {82, 17, 43, 140, 24, 16, 190};
        politica(TipoPoliticaDisco.FIFO, cola, "82 17 43 140 24 16 190", 518);
        politica(TipoPoliticaDisco.SSTF, cola, "43 24 17 16 82 140 190", 208);
        politica(TipoPoliticaDisco.SCAN, cola, "82 140 190 43 24 17 16", 332);
        politica(TipoPoliticaDisco.C_SCAN, cola, "82 140 190 16 17 24 43", 192);
    }

    private static void politicasExtra() {
        System.out.println("== LOOK y C-LOOK con la cola del anexo ==");
        int[] cola = {82, 17, 43, 140, 24, 16, 190};
        politica(TipoPoliticaDisco.LOOK, cola, "82 140 190 43 24 17 16", 140 + 174);
        politica(TipoPoliticaDisco.C_LOOK, cola, "82 140 190 16 17 24 43", 140 + 174 + 27);
    }

    private static void politica(TipoPoliticaDisco tipo, int[] cola, String ordenEsperado, long desplazamiento) {
        Disco disco = new Disco(200, 0, 50, 1);
        PlanificadorDisco plan = new PlanificadorDisco(tipo, null);
        StringBuilder orden = new StringBuilder();
        plan.setOyente((s, ciclo) -> orden.append(orden.length() > 0 ? " " : "").append(s.getBloqueObjetivo()));
        disco.setControlador(plan);
        for (int b : cola) {
            plan.encolar(new SolicitudES(TipoOperacionES.LEER, "/b" + b, new int[]{b}, b, "P" + b, null, 0));
        }
        correr(disco, plan);
        verificar(tipo + " orden", ordenEsperado, orden.toString());
        verificar(tipo + " desplazamiento", desplazamiento, disco.getDesplazamientoTotal());
        verificar(tipo + " atendidas", (long) cola.length, plan.getMetricas().atendidasCon(tipo));
    }

    private static void casosDelEnunciado(String rutaJson) {
        System.out.println("== Casos P1 a P4 con " + rutaJson + " ==");
        String[] rutas = {"/sistema/config.sys", "/usuarios/video.mp4", "/usuarios/notas.txt",
            "/usuarios/img1.png", "/sistema/boot.bin", "/usuarios/data.csv", "/sistema/script.py",
            "/sistema/core.dll"};
        TipoPoliticaDisco[] tipos = {TipoPoliticaDisco.FIFO, TipoPoliticaDisco.SSTF,
            TipoPoliticaDisco.SCAN, TipoPoliticaDisco.C_SCAN};
        String[] esperado = {"95 180 34 119 11 123 62 64", "62 64 34 11 95 119 123 180",
            "62 64 95 119 123 180 34 11", "62 64 95 119 123 180 11 34"};
        for (int i = 0; i < tipos.length; i++) {
            // swap en 0 para que el disco termine en el 199 como en el enunciado
            SistemaArchivos fs = ArchivoSistemaArchivos.cargar(new File(rutaJson), 0, 1);
            PlanificadorDisco plan = new PlanificadorDisco(tipos[i], null);
            StringBuilder orden = new StringBuilder();
            plan.setOyente((s, c) -> orden.append(orden.length() > 0 ? " " : "").append(s.getBloqueObjetivo()));
            fs.getDisco().setControlador(plan);
            for (int k = 0; k < rutas.length; k++) {
                plan.encolar(fs.prepararSolicitud(TipoOperacionES.LEER, rutas[k], 0, null,
                        SistemaArchivos.ADMIN, ModoUsuario.ADMINISTRADOR, k, "Proc", null, 0));
            }
            correr(fs.getDisco(), plan);
            verificar("P" + (i + 1) + " " + tipos[i], esperado[i], orden.toString());
        }
    }

    private static void correr(Disco disco, PlanificadorDisco plan) {
        long ciclo = 0;
        while ((plan.pendientes() > 0 || disco.getSolicitudActual() != null) && ciclo < 100000) {
            disco.alTick(++ciclo);
        }
    }

    // ---------- disco ----------

    private static void asignacionEncadenada() {
        System.out.println("== Asignacion encadenada ==");
        Disco disco = new Disco(100, 0, 0, 1);
        SistemaArchivos fs = new SistemaArchivos(disco);
        fs.crearDirectorio("/docs", "admin", Permiso.PUBLICO);
        fs.crearArchivo("/docs/a.txt", 3, "admin", Permiso.PUBLICO, "P1");
        fs.crearArchivo("/docs/b.txt", 2, "admin", Permiso.PUBLICO, "P2");
        fs.eliminar("/docs/a.txt");
        // c ocupa los huecos de a (0,1,2) y sigue despues de b (5)
        fs.crearArchivo("/docs/c.txt", 4, "admin", Permiso.PUBLICO, "P3");
        verificar("cadena de c", "0 1 2 5", texto(disco.cadena(fs.buscar("/docs/c.txt").getPrimerBloque())));
        Bloque[] foto = disco.foto();
        verificar("bloque 2 apunta al 5", 5L, (long) foto[2].getSiguiente());
        verificar("bloque 5 es el ultimo", (long) Bloque.SIN_SIGUIENTE, (long) foto[5].getSiguiente());
        verificar("ocupante", "/docs/c.txt", foto[5].getOcupante());
        verificar("libres", 94L, (long) disco.bloquesLibres());
        rechazo("sin espacio", () -> fs.crearArchivo("/docs/grande", 95, "admin", Permiso.PUBLICO, "P4"));
        verificar("no quedo a medias", 94L, (long) disco.bloquesLibres());
        fs.renombrar("/docs", "papeles");
        verificar("renombrar actualiza bloques", "/papeles/c.txt", disco.foto()[0].getOcupante());
        verificar("eliminar directorio libera todo", 6L, (long) fs.eliminar("/papeles"));
        verificar("disco vacio", 100L, (long) disco.bloquesLibres());
    }

    private static void areaDeIntercambio() {
        System.out.println("== Area de intercambio ==");
        Disco disco = new Disco(100, 20, 0, 1);
        int primero = disco.asignarSwap("P7", 16);
        verificar("swap empieza despues de los archivos", 100L, (long) primero);
        verificar("uso", UsoBloque.INTERCAMBIO.name(), disco.foto()[primero].getUso().name());
        verificar("swap libre", 4L, (long) disco.swapLibre());
        verificar("no toca el area de archivos", 100L, (long) disco.bloquesLibres());
        boolean lleno = false;
        try {
            disco.asignarSwap("P8", 5);
        } catch (IllegalStateException e) {
            lleno = true;
        }
        verificar("swap lleno se rechaza", "true", String.valueOf(lleno));
        verificar("liberar swap", 16L, (long) disco.liberarCadena(primero));
    }

    // ---------- sistema de archivos ----------

    private static void permisos() {
        System.out.println("== Permisos ==");
        SistemaArchivos fs = new SistemaArchivos(new Disco(100, 0, 0, 1));
        fs.crearDirectorio("/sistema", "admin", Permiso.PUBLICO);
        fs.crearDirectorio("/home", "user1", Permiso.PUBLICO);
        fs.crearArchivo("/sistema/boot.bin", 1, "admin", Permiso.PUBLICO, "-");
        fs.crearArchivo("/home/notas.txt", 1, "user1", Permiso.PRIVADO, "-");
        ModoUsuario u = ModoUsuario.USUARIO;
        fs.validarPermiso(TipoOperacionES.LEER, "/sistema/boot.bin", "user2", u);
        fs.validarPermiso(TipoOperacionES.LEER, "/home/notas.txt", "user1", u);
        fs.validarPermiso(TipoOperacionES.CREAR, "/home/nuevo.txt", "user1", u);
        fs.validarPermiso(TipoOperacionES.ELIMINAR, "/home/notas.txt", "user2", ModoUsuario.ADMINISTRADOR);
        rechazo("leer privado ajeno", () -> fs.validarPermiso(TipoOperacionES.LEER, "/home/notas.txt", "user2", u));
        rechazo("crear en dir ajeno", () -> fs.validarPermiso(TipoOperacionES.CREAR, "/sistema/x", "user1", u));
        rechazo("borrar del sistema", () -> fs.validarPermiso(TipoOperacionES.ELIMINAR, "/sistema/boot.bin", "user1", u));
        rechazo("renombrar ajeno", () -> fs.validarPermiso(TipoOperacionES.ACTUALIZAR, "/home/notas.txt", "user2", u));
        rechazo("no existe", () -> fs.validarPermiso(TipoOperacionES.LEER, "/nada", "admin", ModoUsuario.ADMINISTRADOR));
        rechazo("duplicado", () -> fs.crearDirectorio("/home", "admin", Permiso.PUBLICO));
        rechazo("borrar raiz", () -> fs.eliminar("/"));
    }

    private static void operacionesPorSolicitud() {
        System.out.println("== CRUD a traves del disco ==");
        Disco disco = new Disco(100, 0, 0, 4);
        SistemaArchivos fs = new SistemaArchivos(disco);
        PlanificadorDisco plan = new PlanificadorDisco(TipoPoliticaDisco.SSTF, null);
        plan.setOyente((s, c) -> fs.aplicar(s));
        disco.setControlador(plan);
        ModoUsuario adm = ModoUsuario.ADMINISTRADOR;
        plan.encolar(fs.prepararSolicitud(TipoOperacionES.CREAR, "/datos", 0, null, "admin", adm, 1, "P1", null, 0));
        correr(disco, plan);
        plan.encolar(fs.prepararSolicitud(TipoOperacionES.CREAR, "/datos/a.bin", 3, null, "admin", adm, 2, "P2", null, 0));
        correr(disco, plan);
        verificar("creado", "3", String.valueOf(fs.buscar("/datos/a.bin").getTamanoBloques()));
        verificar("creador", "P2", fs.buscar("/datos/a.bin").getProcesoCreador());
        SolicitudES ren = fs.prepararSolicitud(TipoOperacionES.ACTUALIZAR, "/datos/a.bin", 0, "b.bin", "admin", adm, 3, "P3", null, 0);
        plan.encolar(ren);
        verificar("no se renombra hasta que el disco atiende", "true", String.valueOf(fs.buscar("/datos/b.bin") == null));
        correr(disco, plan);
        verificar("renombrado", "true", String.valueOf(fs.buscar("/datos/b.bin") != null));
        plan.encolar(fs.prepararSolicitud(TipoOperacionES.ELIMINAR, "/datos", 0, null, "admin", adm, 4, "P4", null, 0));
        correr(disco, plan);
        verificar("eliminado", "true", String.valueOf(fs.buscar("/datos") == null));
        verificar("bloques devueltos", 100L, (long) disco.bloquesLibres());
        verificar("cuatro atendidas", 4L, plan.getMetricas().getTotalAtendidas());
    }

    private static void idaYVueltaJson() {
        System.out.println("== JSON: guardar y volver a cargar ==");
        SistemaArchivos fs = new SistemaArchivos(new Disco(120, 10, 7, 1));
        fs.crearDirectorio("/a", "admin", Permiso.PUBLICO);
        fs.crearDirectorio("/a/b", "user1", Permiso.PRIVADO);
        fs.crearArchivo("/a/uno", 2, "admin", Permiso.PUBLICO, "P1");
        fs.crearArchivo("/a/b/dos \"x\"", 3, "user1", Permiso.PRIVADO, "P2");
        fs.eliminar("/a/uno");
        fs.crearArchivo("/a/tres", 4, "user2", Permiso.PUBLICO, "P3");
        String json = ArchivoSistemaArchivos.aTexto(fs);
        SistemaArchivos otro = ArchivoSistemaArchivos.desdeTexto(json, 10, 1);
        verificar("mismo JSON al regrabar", json, ArchivoSistemaArchivos.aTexto(otro));
        verificar("cadena partida se conserva", "0 1 5 6",
                texto(otro.getDisco().cadena(otro.buscar("/a/tres").getPrimerBloque())));
        verificar("cabezal", 7L, (long) otro.getDisco().getCabezal());
        verificar("dueno", "user1", otro.buscar("/a/b").getDueno());
    }

    private static void jsonInvalido() {
        System.out.println("== JSON invalido ==");
        errorJson("mal escrito", "{\"diskConfig\": {");
        errorJson("sin fileSystem", "{\"diskConfig\":{\"totalBlocks\":200,\"initialHead\":0}}");
        errorJson("disco muy chico", "{\"diskConfig\":{\"totalBlocks\":5},\"fileSystem\":[]}");
        errorJson("cabezal fuera", "{\"diskConfig\":{\"totalBlocks\":200,\"initialHead\":900},\"fileSystem\":[]}");
        errorJson("bloque repetido", "{\"diskConfig\":{\"totalBlocks\":200},\"fileSystem\":["
                + "{\"path\":\"/a\",\"type\":\"FILE\",\"startBlock\":3,\"blocks\":1},"
                + "{\"path\":\"/b\",\"type\":\"FILE\",\"startBlock\":3,\"blocks\":1}]}");
        errorJson("bloque fuera del disco", "{\"diskConfig\":{\"totalBlocks\":200},\"fileSystem\":["
                + "{\"path\":\"/a\",\"type\":\"FILE\",\"startBlock\":200,\"blocks\":1}]}");
        errorJson("tipo raro", "{\"diskConfig\":{\"totalBlocks\":200},\"fileSystem\":["
                + "{\"path\":\"/a\",\"type\":\"LINK\"}]}");
        errorJson("tamano no entero", "{\"diskConfig\":{\"totalBlocks\":200},\"fileSystem\":["
                + "{\"path\":\"/a\",\"type\":\"FILE\",\"startBlock\":1,\"blocks\":1.5}]}");
    }

    // ---------- utilidades ----------

    private static String texto(int[] v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            sb.append(i > 0 ? " " : "").append(v[i]);
        }
        return sb.toString();
    }

    private static void rechazo(String nombre, Runnable accion) {
        String motivo = null;
        try {
            accion.run();
        } catch (OperacionRechazada e) {
            motivo = e.getMessage();
        }
        anotar(nombre + " -> " + motivo, motivo != null);
    }

    private static void errorJson(String nombre, String json) {
        String motivo = null;
        try {
            ArchivoSistemaArchivos.desdeTexto(json, 0, 1);
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
