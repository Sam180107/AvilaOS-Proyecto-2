package unimet.avilaos.pruebas;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import unimet.avilaos.cluster.Cluster;
import unimet.avilaos.cluster.ConfiguracionProcesos;
import unimet.avilaos.cluster.EspecificacionES;
import unimet.avilaos.estructuras.Recorrido;
import unimet.avilaos.gui.ArbolArchivosModelo;
import unimet.avilaos.gui.ArbolArchivosModelo.NodoVista;
import unimet.avilaos.gui.ContextoUsuario;
import unimet.avilaos.gui.GraficaDesplazamiento;
import unimet.avilaos.gui.PaletaArchivos;
import unimet.avilaos.gui.PanelArchivos;
import unimet.avilaos.gui.PanelColaDisco;
import unimet.avilaos.gui.PanelDisco;
import unimet.avilaos.gui.PanelTablaAsignacion;
import unimet.avilaos.hardware.disco.TipoOperacionES;
import unimet.avilaos.json.ArchivoProcesos;
import unimet.avilaos.json.ArchivoSistemaArchivos;
import unimet.avilaos.so.archivos.ModoUsuario;
import unimet.avilaos.so.archivos.OperacionRechazada;
import unimet.avilaos.so.archivos.SistemaArchivos;
import unimet.avilaos.so.disco.TipoPoliticaDisco;

/**
 * Pruebas de la interfaz sin abrir ventanas (modo headless): el modelo del
 * JTree, que los paneles se construyan y se repinten, y que las operaciones
 * pedidas desde la interfaz sean procesos de E/S. Se corre como un main normal.
 */
public final class PruebasInterfaz {

    private static int fallas = 0;
    private static int total = 0;
    private static String carpeta = "datos";

    private static final ContextoUsuario ADMIN = new ContextoUsuario() {
        @Override
        public ModoUsuario modo() {
            return ModoUsuario.ADMINISTRADOR;
        }

        @Override
        public String usuario() {
            return SistemaArchivos.ADMIN;
        }
    };

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        if (args.length > 0) {
            carpeta = args[0];
        }
        modeloDelArbol();
        paletaDistinta();
        panelesSeRepintan();
        crudDesdeLaInterfaz();
        modoUsuario();
        System.out.println();
        System.out.println(fallas == 0 ? "TODO BIEN: " + total + " verificaciones"
                : fallas + " de " + total + " verificaciones fallaron");
        System.exit(fallas > 0 ? 1 : 0);
    }

    private static Cluster armar() {
        ConfiguracionProcesos cfg = ArchivoProcesos.cargar(new File(carpeta, "procesos.json"));
        SistemaArchivos fs = ArchivoSistemaArchivos.cargar(new File(carpeta, "sistema_archivos.json"),
                cfg.tamanoSwap, cfg.posicionesPorCiclo);
        return Cluster.crear(cfg, fs);
    }

    private static void correr(Cluster c, int ciclos) {
        for (int i = 0; i < ciclos; i++) {
            c.getReloj().paso();
        }
    }

    private static int contar(NodoVista n) {
        int cuenta = 1;
        Recorrido<NodoVista> r = n.hijos();
        while (r.haySiguiente()) {
            cuenta += contar(r.siguiente());
        }
        return cuenta;
    }

    private static void modeloDelArbol() {
        System.out.println("== Modelo del JTree ==");
        Cluster c = armar();
        SistemaArchivos fs = c.getSistemaArchivos();
        ArbolArchivosModelo m = new ArbolArchivosModelo();
        verificar("antes de cargar solo hay un nodo vacio", 1L, contar((NodoVista) m.getRoot()));
        m.actualizar(fs.instantanea());
        NodoVista raiz = (NodoVista) m.getRoot();
        verificar("la raiz es /", "/", raiz.getEntrada().ruta());
        verificar("un nodo por entrada del sistema de archivos", fs.instantanea().tamano(), contar(raiz));
        verificar("la raiz no es hoja", "false", String.valueOf(m.isLeaf(raiz)));
        verificar("la raiz tiene hijos", "true", String.valueOf(m.getChildCount(raiz) > 0));
        Object primero = m.getChild(raiz, 0);
        verificar("indice del hijo", 0L, m.getIndexOfChild(raiz, primero));
    }

    private static void paletaDistinta() {
        System.out.println("== Paleta del disco ==");
        boolean todasDistintas = true;
        String[] nombres = new String[12];
        Color[] colores = new Color[12];
        for (int i = 0; i < nombres.length; i++) {
            nombres[i] = "/paleta/archivo" + i;
            colores[i] = PaletaArchivos.deArchivo(nombres[i]);
        }
        for (int i = 0; i < colores.length; i++) {
            for (int j = i + 1; j < colores.length; j++) {
                if (colores[i].equals(colores[j])) {
                    todasDistintas = false;
                }
            }
        }
        verificar("12 archivos, 12 colores distintos", "true", String.valueOf(todasDistintas));
        verificar("el mismo archivo mantiene su color", "true",
                String.valueOf(PaletaArchivos.deArchivo(nombres[3]).equals(colores[3])));
        verificar("un bloque libre es gris", "true", String.valueOf(PaletaArchivos.deArchivo(null) == PaletaArchivos.LIBRE));
    }

    private static void panelesSeRepintan() {
        System.out.println("== Los paneles se arman y se repintan sin errores ==");
        Cluster c = armar();
        correr(c, 30);
        String error = null;
        try {
            PanelArchivos pa = new PanelArchivos(c, ADMIN);
            PanelDisco pd = new PanelDisco(c);
            PanelColaDisco pc = new PanelColaDisco(c);
            PanelTablaAsignacion pt = new PanelTablaAsignacion(c);
            GraficaDesplazamiento gd = new GraficaDesplazamiento(c);
            for (int i = 0; i < 3; i++) {
                correr(c, 5);
                pa.refrescar();
                pd.refrescar();
                pc.refrescar();
                pt.refrescar();
            }
            pd.setSize(900, 420);
            gd.setSize(900, 300);
            BufferedImage img = new BufferedImage(900, 420, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            pd.printAll(g);
            gd.printAll(g);
            g.dispose();
        } catch (RuntimeException e) {
            error = e.toString();
        }
        verificar("sin excepciones" + (error == null ? "" : " (" + error + ")"), "true", String.valueOf(error == null));
    }

    private static void crudDesdeLaInterfaz() {
        System.out.println("== La interfaz solo crea procesos de E/S ==");
        Cluster c = armar();
        SistemaArchivos fs = c.getSistemaArchivos();
        long antes = fs.getVersion();
        unimet.avilaos.so.proceso.PCB creador = c.crearProcesoES(new EspecificacionES("ES_nuevo", 16, 5, 5000, 2, TipoOperacionES.CREAR,
                "/usuarios/nuevo.txt", 3, null, "admin", ModoUsuario.ADMINISTRADOR));
        verificar("el archivo no existe hasta que corre el proceso", "true",
                String.valueOf(fs.buscar("/usuarios/nuevo.txt") == null));
        correr(c, 400);
        verificar("el proceso lo creo", "true", String.valueOf(fs.buscar("/usuarios/nuevo.txt") != null));
        verificar("la version del arbol subio", "true", String.valueOf(fs.getVersion() > antes));
        verificar("ocupa 3 bloques", 3L, fs.buscar("/usuarios/nuevo.txt").getTamanoBloques());
        verificar("con su cadena de 3 bloques en el disco", 3L,
                c.getDisco().cadena(fs.buscar("/usuarios/nuevo.txt").getPrimerBloque()).length);
        verificar("figura el proceso que lo creo", creador.getEtiqueta(), fs.buscar("/usuarios/nuevo.txt").getProcesoCreador());
        boolean enSnapshot = false;
        Recorrido<unimet.avilaos.so.archivos.EntradaArchivo> r = fs.instantanea().recorrido();
        while (r.haySiguiente()) {
            enSnapshot |= r.siguiente().ruta().equals("/usuarios/nuevo.txt");
        }
        verificar("aparece en la copia que usa la interfaz", "true", String.valueOf(enSnapshot));
        c.cambiarPoliticaDisco(TipoPoliticaDisco.SCAN);
        verificar("cambio de politica desde el selector", "SCAN", c.getPlanificadorDisco().getTipoPolitica().toString());
    }

    private static void modoUsuario() {
        System.out.println("== Modo usuario: se rechaza y se explica ==");
        Cluster c = armar();
        String motivo = null;
        try {
            c.crearProcesoES(new EspecificacionES("Intruso", 16, 5, 500, 2, TipoOperacionES.ELIMINAR,
                    "/sistema/boot.bin", 0, null, "user1", ModoUsuario.USUARIO));
        } catch (OperacionRechazada e) {
            motivo = e.getMessage();
        }
        anotar("user1 no puede eliminar archivos del sistema -> " + motivo, motivo != null);
        String motivo2 = null;
        try {
            c.crearProcesoES(new EspecificacionES("Curioso", 16, 5, 500, 2, TipoOperacionES.LEER,
                    "/usuarios/notas.txt", 0, null, "user2", ModoUsuario.USUARIO));
        } catch (OperacionRechazada e) {
            motivo2 = e.getMessage();
        }
        anotar("user2 no puede leer lo privado de user1 -> " + motivo2, motivo2 != null);
        String motivo3 = null;
        try {
            c.crearProcesoES(new EspecificacionES("Dueno", 16, 5, 500, 2, TipoOperacionES.LEER,
                    "/usuarios/notas.txt", 0, null, "user1", ModoUsuario.USUARIO));
        } catch (OperacionRechazada e) {
            motivo3 = e.getMessage();
        }
        verificar("user1 si puede leer lo suyo", "true", String.valueOf(motivo3 == null));
        correr(c, 5);
        verificar("la simulacion sigue corriendo tras un rechazo", "true", String.valueOf(c.getReloj().getCiclo() >= 5));
    }

    // ---------- utilidades ----------

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
