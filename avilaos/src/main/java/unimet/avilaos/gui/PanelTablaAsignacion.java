package unimet.avilaos.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import unimet.avilaos.cluster.Cluster;
import unimet.avilaos.estructuras.ListaEnlazada;
import unimet.avilaos.estructuras.Recorrido;
import unimet.avilaos.hardware.disco.Disco;
import unimet.avilaos.so.archivos.EntradaArchivo;
import unimet.avilaos.so.archivos.SistemaArchivos;

/** Tabla de asignacion de archivos: se actualiza sola con cada operacion de E/S. */
public class PanelTablaAsignacion extends JPanel {

    private static final String[] COLUMNAS = {
        "Color", "Archivo", "Bloques", "Primer bloque", "Dueno", "Permisos", "Proceso que lo creo"
    };

    private final Cluster cluster;
    private final ModeloTablaSoloLectura modelo = new ModeloTablaSoloLectura(COLUMNAS);
    private final JTable tabla = new JTable(modelo);
    private final JLabel resumen = new JLabel(" ");
    private long versionVista = -1;

    public PanelTablaAsignacion(Cluster cluster) {
        super(new BorderLayout(2, 2));
        this.cluster = cluster;
        tabla.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object valor, boolean sel,
                    boolean foco, int fila, int col) {
                super.getTableCellRendererComponent(t, "", sel, foco, fila, col);
                setBackground((Color) valor);
                return this;
            }
        });
        tabla.getColumnModel().getColumn(0).setMaxWidth(50);
        add(new JScrollPane(tabla), BorderLayout.CENTER);
        add(resumen, BorderLayout.SOUTH);
    }

    public void refrescar() {
        SistemaArchivos fs = cluster.getSistemaArchivos();
        if (fs == null) {
            return;
        }
        Disco disco = fs.getDisco();
        int libres = disco.bloquesLibres();
        resumen.setText("  Bloques de archivos: " + (disco.getTotalBloques() - libres) + " ocupados, "
                + libres + " libres de " + disco.getTotalBloques());
        if (fs.getVersion() == versionVista) {
            return;
        }
        versionVista = fs.getVersion();
        ListaEnlazada<EntradaArchivo> entradas = fs.instantanea();
        ListaEnlazada<Object[]> filas = new ListaEnlazada<>();
        Recorrido<EntradaArchivo> r = entradas.recorrido();
        while (r.haySiguiente()) {
            EntradaArchivo e = r.siguiente();
            if (!e.esDirectorio()) {
                filas.agregarAlFinal(new Object[]{PaletaArchivos.deArchivo(e.ruta()), e.ruta(), e.bloques(),
                    e.primerBloque(), e.dueno(), e.permiso().getEtiqueta(), e.creador()});
            }
        }
        Object[][] datos = new Object[filas.tamano()][];
        int i = 0;
        Recorrido<Object[]> rf = filas.recorrido();
        while (rf.haySiguiente()) {
            datos[i++] = rf.siguiente();
        }
        modelo.setFilas(datos);
    }
}
