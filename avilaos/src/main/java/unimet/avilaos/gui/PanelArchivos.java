package unimet.avilaos.gui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.TreePath;
import unimet.avilaos.cluster.Cluster;
import unimet.avilaos.estructuras.ListaEnlazada;
import unimet.avilaos.estructuras.Recorrido;
import unimet.avilaos.gui.ArbolArchivosModelo.NodoVista;
import unimet.avilaos.gui.DialogoOperacionArchivo.Accion;
import unimet.avilaos.so.archivos.EntradaArchivo;
import unimet.avilaos.so.archivos.SistemaArchivos;

/**
 * JTree con la estructura de directorios y archivos, la informacion del
 * elemento seleccionado y los botones de CRUD y permisos.
 */
public class PanelArchivos extends JPanel {

    private final Cluster cluster;
    private final ContextoUsuario contexto;
    private final ArbolArchivosModelo modelo = new ArbolArchivosModelo();
    private final JTree arbol = new JTree(modelo);
    private final JLabel[] valores = new JLabel[8];
    private final ListaEnlazada<String> expandidos = new ListaEnlazada<>();
    private long versionVista = -1;
    private boolean actualizando = false;

    private static final String[] CAMPOS = {
        "Nombre", "Tipo", "Ruta", "Tamano (bloques)", "Dueno", "Permisos", "Primer bloque", "Creado por"
    };

    public PanelArchivos(Cluster cluster, ContextoUsuario contexto) {
        super(new BorderLayout(4, 4));
        this.cluster = cluster;
        this.contexto = contexto;
        setBorder(BorderFactory.createTitledBorder("Sistema de archivos"));

        arbol.setRootVisible(true);
        arbol.addTreeSelectionListener(e -> mostrarInfo());
        arbol.addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent e) {
                String ruta = rutaDe(e.getPath());
                if (!actualizando && ruta != null && !expandidos.contiene(ruta)) {
                    expandidos.agregarAlFinal(ruta);
                }
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent e) {
                String ruta = rutaDe(e.getPath());
                if (!actualizando && ruta != null) {
                    expandidos.eliminar(ruta);
                }
            }
        });

        JPanel info = new JPanel(new GridLayout(0, 2, 4, 1));
        info.setBorder(BorderFactory.createTitledBorder("Elemento seleccionado"));
        for (int i = 0; i < CAMPOS.length; i++) {
            valores[i] = new JLabel("-");
            info.add(new JLabel(CAMPOS[i] + ":"));
            info.add(valores[i]);
        }

        JPanel botones = new JPanel(new GridLayout(0, 2, 4, 4));
        botones.add(boton("Crear archivo...", Accion.CREAR_ARCHIVO));
        botones.add(boton("Crear directorio...", Accion.CREAR_DIRECTORIO));
        botones.add(boton("Leer...", Accion.LEER));
        botones.add(boton("Renombrar...", Accion.RENOMBRAR));
        botones.add(boton("Eliminar...", Accion.ELIMINAR));
        JButton btnPermiso = new JButton("Cambiar permiso");
        btnPermiso.addActionListener(e -> alternarPermiso());
        botones.add(btnPermiso);

        JPanel sur = new JPanel(new BorderLayout());
        sur.add(info, BorderLayout.CENTER);
        sur.add(botones, BorderLayout.SOUTH);

        add(new JScrollPane(arbol), BorderLayout.CENTER);
        add(sur, BorderLayout.SOUTH);
        refrescar();
    }

    private JButton boton(String texto, Accion accion) {
        JButton b = new JButton(texto);
        b.addActionListener(e -> abrir(accion));
        return b;
    }

    /** El cambio de permiso tambien es un proceso de E/S (Actualizar); la interfaz no toca el arbol. */
    private void alternarPermiso() {
        EntradaArchivo e = seleccion();
        if (e == null || "/".equals(e.ruta())) {
            JOptionPane.showMessageDialog(this, "Seleccione un archivo o directorio valido.",
                    "Permisos", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        abrir(Accion.CAMBIAR_PERMISO);
    }

    public void refrescar() {
        SistemaArchivos fs = cluster.getSistemaArchivos();
        if (fs == null || fs.getVersion() == versionVista) {
            return;
        }
        boolean primeraVez = versionVista < 0;
        versionVista = fs.getVersion();
        String seleccionada = rutaSeleccionada();
        actualizando = true;
        try {
            modelo.actualizar(fs.instantanea());
            NodoVista raiz = (NodoVista) modelo.getRoot();
            if (primeraVez) {
                recordarDirectorios(raiz);
            }
            restaurar(new TreePath(raiz), raiz, seleccionada);
        } finally {
            actualizando = false;
        }
        mostrarInfo();
    }

    private void recordarDirectorios(NodoVista nodo) {
        EntradaArchivo e = nodo.getEntrada();
        if (e == null || !e.esDirectorio()) {
            return;
        }
        expandidos.agregarAlFinal(e.ruta());
        Recorrido<NodoVista> r = nodo.hijos();
        while (r.haySiguiente()) {
            recordarDirectorios(r.siguiente());
        }
    }

    private void restaurar(TreePath ruta, NodoVista nodo, String seleccionada) {
        EntradaArchivo e = nodo.getEntrada();
        if (e == null) {
            return;
        }
        if (e.ruta().equals(seleccionada)) {
            arbol.setSelectionPath(ruta);
        }
        if (e.esDirectorio()) {
            if (e.nivel() == 0 || expandidos.contiene(e.ruta())) {
                arbol.expandPath(ruta);
            }
            Recorrido<NodoVista> r = nodo.hijos();
            while (r.haySiguiente()) {
                NodoVista h = r.siguiente();
                restaurar(ruta.pathByAddingChild(h), h, seleccionada);
            }
        }
    }

    private static String rutaDe(TreePath p) {
        NodoVista n = (NodoVista) p.getLastPathComponent();
        return n.getEntrada() == null ? null : n.getEntrada().ruta();
    }

    private EntradaArchivo seleccion() {
        TreePath p = arbol.getSelectionPath();
        return p == null ? null : ((NodoVista) p.getLastPathComponent()).getEntrada();
    }

    private String rutaSeleccionada() {
        EntradaArchivo e = seleccion();
        return e == null ? null : e.ruta();
    }

    private void mostrarInfo() {
        EntradaArchivo e = seleccion();
        String[] v = e == null
                ? new String[]{"-", "-", "-", "-", "-", "-", "-", "-"}
                : new String[]{e.nombre(), e.tipo().getEtiqueta(), e.ruta(),
                    e.esDirectorio() ? "-" : String.valueOf(e.bloques()), e.dueno(), e.permiso().getEtiqueta(),
                    e.esDirectorio() ? "-" : String.valueOf(e.primerBloque()), e.esDirectorio() ? "-" : e.creador()};
        for (int i = 0; i < v.length; i++) {
            valores[i].setText(v[i]);
        }
    }

    private void abrir(Accion accion) {
        EntradaArchivo e = seleccion();
        String sugerida;
        if (accion == Accion.CREAR_ARCHIVO || accion == Accion.CREAR_DIRECTORIO) {
            String base = e == null ? "/" : e.esDirectorio() ? e.ruta() : padre(e.ruta());
            sugerida = (base.equals("/") ? "/" : base + "/") + (accion == Accion.CREAR_ARCHIVO ? "nuevo.txt" : "nuevo");
        } else {
            sugerida = e == null ? "/" : e.ruta();
        }
        new DialogoOperacionArchivo(SwingUtilities.getWindowAncestor(this), cluster, contexto, accion, sugerida)
                .setVisible(true);
    }

    private static String padre(String ruta) {
        int corte = ruta.lastIndexOf('/');
        return corte <= 0 ? "/" : ruta.substring(0, corte);
    }
}
