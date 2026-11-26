package unimet.avilaos.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import unimet.avilaos.cluster.Cluster;
import unimet.avilaos.estructuras.ListaEnlazada;
import unimet.avilaos.estructuras.Recorrido;
import unimet.avilaos.hardware.Computador;
import unimet.avilaos.json.ArchivoSistemaArchivos;
import unimet.avilaos.json.ErrorJson;
import unimet.avilaos.metricas.Estadisticas;
import unimet.avilaos.metricas.ExportadorReportes;
import unimet.avilaos.so.archivos.EntradaArchivo;
import unimet.avilaos.so.archivos.ModoUsuario;
import unimet.avilaos.so.archivos.SistemaArchivos;

/**
 * Ventana principal del Proyecto 2. Cada 100 ms mira al sistema y se repinta:
 * no contiene logica de AvilaOS, solo crea procesos de E/S y cambia opciones.
 */
public class VentanaPrincipal extends JFrame implements ContextoUsuario {

    private final Cluster cluster;
    private final PanelComputador panelComputador;
    private final PanelArchivos panelArchivos;
    private final PanelTablaAsignacion tablaAsignacion;
    private final PanelDisco panelDisco;
    private final PanelColaDisco panelCola;
    private final PanelCacheDisco panelCache;
    private final GraficaDesplazamiento graficaDesplazamiento;
    private final GraficaUtilizacion graficaUso;

    private final JLabel ciclo = new JLabel("Ciclo: 0");
    private final JButton iniciarPausar = new JButton("Iniciar");
    private final JComboBox<ModoUsuario> modo = new JComboBox<>(ModoUsuario.values());
    private final JComboBox<String> usuario = new JComboBox<>();
    private final ModeloTablaSoloLectura tablaMetricas = new ModeloTablaSoloLectura(new String[] {
        "Computador", "Throughput (proc/ciclo)", "Uso de CPU", "T. respuesta prom.",
        "Cumplen deadline", "Equidad (Jain)", "Completados", "Vencidos", "Suspensiones", "Reactivaciones"
    });
    private final JTextArea log = new JTextArea();
    private final Timer refresco = new Timer(100, e -> refrescar());
    private long versionUsuarios = -1;

    public VentanaPrincipal(Cluster cluster) {
        super("AvilaOS - Proyecto 2: disco, sistema de archivos, planificacion de E/S y suspension");
        this.cluster = cluster;

        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        iniciarPausar.addActionListener(e -> iniciarOPausar());
        JButton paso = new JButton("Paso");
        paso.addActionListener(e -> new Thread(() -> cluster.getReloj().paso()).start());
        JSpinner ms = DialogoProceso.numero(cluster.getReloj().getDuracionCiclo(), 1, 10_000);
        ms.addChangeListener(e -> cluster.getReloj().setDuracionCiclo((Integer) ms.getValue()));
        modo.addActionListener(e -> aplicarModo());
        JButton nueva = new JButton("Nueva operacion...");
        nueva.addActionListener(e -> new DialogoOperacionArchivo(this, cluster, this,
                DialogoOperacionArchivo.Accion.LEER, "/").setVisible(true));
        JButton guardar = new JButton("Guardar FS...");
        guardar.addActionListener(e -> guardar());
        JButton reporte = new JButton("Exportar reporte...");
        reporte.addActionListener(e -> exportarReporte());
        JButton reiniciar = new JButton("Reiniciar");
        reiniciar.addActionListener(e -> reiniciar());

        ciclo.setFont(ciclo.getFont().deriveFont(15f));
        barra.add(iniciarPausar);
        barra.add(paso);
        barra.add(ciclo);
        barra.add(new JLabel(" Ciclo(ms):"));
        barra.add(ms);
        barra.add(new JLabel(" Modo:"));
        barra.add(modo);
        barra.add(new JLabel("Usuario:"));
        barra.add(usuario);
        barra.add(nueva);
        barra.add(guardar);
        barra.add(reporte);
        barra.add(reiniciar);
        barra.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        Computador comp = cluster.getComputadores()[0];
        panelComputador = new PanelComputador(comp);
        panelArchivos = new PanelArchivos(cluster, this);
        tablaAsignacion = new PanelTablaAsignacion(cluster);
        panelDisco = new PanelDisco(cluster);
        panelCola = new PanelColaDisco(cluster);
        panelCache = new PanelCacheDisco(cluster);
        graficaDesplazamiento = new GraficaDesplazamiento(cluster);
        graficaUso = new GraficaUtilizacion(cluster.getComputadores());
        log.setEditable(false);

        JTabbedPane abajo = new JTabbedPane();
        abajo.addTab("Cola de E/S del disco", panelCola);
        abajo.addTab("Tabla de asignacion", tablaAsignacion);
        if (cluster.getSistemaArchivos().getCache().getCapacidad() > 0) {
            abajo.addTab("Cache LRU (RAM)", panelCache);
        }
        abajo.addTab("Computador y colas", panelComputador);
        abajo.addTab("Metricas", new JScrollPane(new JTable(tablaMetricas)));
        abajo.addTab("Desplazamiento del cabezal", graficaDesplazamiento);
        abajo.addTab("Uso de CPU", graficaUso);
        abajo.addTab("Log de eventos", new JScrollPane(log));
        abajo.setPreferredSize(new Dimension(100, 330));

        JSplitPane derecha = new JSplitPane(JSplitPane.VERTICAL_SPLIT, panelDisco, abajo);
        derecha.setResizeWeight(0.55);
        panelArchivos.setPreferredSize(new Dimension(340, 100));
        JSplitPane division = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, panelArchivos, derecha);
        division.setResizeWeight(0.0);

        setLayout(new BorderLayout());
        add(barra, BorderLayout.NORTH);
        add(division, BorderLayout.CENTER);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1440, 900);
        setLocationRelativeTo(null);

        refrescarUsuarios();
        aplicarModo();
        refresco.start();
    }

    public static void abrir() {
        Cluster cluster = new VentanaCarga().mostrar();
        if (cluster == null) {
            System.exit(0);
        }
        new VentanaPrincipal(cluster).setVisible(true);
    }

    @Override
    public ModoUsuario modo() {
        return (ModoUsuario) modo.getSelectedItem();
    }

    @Override
    public String usuario() {
        if (modo() == ModoUsuario.ADMINISTRADOR) {
            return SistemaArchivos.ADMIN;
        }
        Object u = usuario.getSelectedItem();
        return u == null ? "user1" : u.toString();
    }

    private void aplicarModo() {
        boolean admin = modo() == ModoUsuario.ADMINISTRADOR;
        usuario.setEnabled(!admin);
        panelCola.setEditable(admin);
        panelComputador.setPoliticaEditable(admin);
        cluster.getLog().registrar("Modo de uso: " + modo().getEtiqueta()
                + (admin ? "" : " (usuario " + usuario() + ")"));
    }

    private void refrescarUsuarios() {
        SistemaArchivos fs = cluster.getSistemaArchivos();
        if (fs == null || fs.getVersion() == versionUsuarios) {
            return;
        }
        versionUsuarios = fs.getVersion();
        Object elegido = usuario.getSelectedItem();
        ListaEnlazada<String> nombres = new ListaEnlazada<>();
        Recorrido<EntradaArchivo> r = fs.instantanea().recorrido();
        while (r.haySiguiente()) {
            String d = r.siguiente().dueno();
            if (!d.equals(SistemaArchivos.ADMIN) && !nombres.contiene(d)) {
                nombres.agregarAlFinal(d);
            }
        }
        usuario.removeAllItems();
        Recorrido<String> rn = nombres.recorrido();
        while (rn.haySiguiente()) {
            usuario.addItem(rn.siguiente());
        }
        if (elegido != null && nombres.contiene(elegido.toString())) {
            usuario.setSelectedItem(elegido);
        }
    }

    private void reiniciar() {
        int r = JOptionPane.showConfirmDialog(this,
                "Se pierde la simulacion actual (guarde antes el sistema de archivos si quiere conservarlo).\nReiniciar?",
                "Reiniciar", JOptionPane.YES_NO_OPTION);
        if (r != JOptionPane.YES_OPTION) {
            return;
        }
        cluster.getReloj().detener();
        refresco.stop();
        SwingUtilities.invokeLater(VentanaPrincipal::abrir);
        dispose();
    }

    private void iniciarOPausar() {
        if (cluster.getReloj().estaCorriendo()) {
            cluster.getReloj().pausar();
        } else {
            cluster.getReloj().iniciar();
        }
    }

    private void guardar() {
        JFileChooser fc = new JFileChooser(new File("datos").isDirectory() ? new File("datos") : null);
        fc.setFileFilter(new FileNameExtensionFilter("Archivos JSON", "json"));
        fc.setSelectedFile(new File("sistema_archivos_guardado.json"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File destino = fc.getSelectedFile();
        if (!destino.getName().toLowerCase().endsWith(".json")) {
            destino = new File(destino.getPath() + ".json");
        }
        try {
            ArchivoSistemaArchivos.guardar(cluster.getSistemaArchivos(), destino);
            cluster.getLog().registrar("Sistema de archivos guardado en " + destino.getName());
            JOptionPane.showMessageDialog(this, "Sistema de archivos guardado.");
        } catch (ErrorJson ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportarReporte() {
        JFileChooser fc = new JFileChooser(new File("datos").isDirectory() ? new File("datos") : null);
        fc.addChoosableFileFilter(new FileNameExtensionFilter("Reporte CSV (*.csv)", "csv"));
        fc.addChoosableFileFilter(new FileNameExtensionFilter("Reporte de Texto (*.txt)", "txt"));
        fc.setSelectedFile(new File("reporte_avilaos.csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File destino = fc.getSelectedFile();
        String nombre = destino.getName().toLowerCase();
        if (!nombre.endsWith(".csv") && !nombre.endsWith(".txt")) {
            destino = new File(destino.getPath() + ".csv");
        }
        try {
            ExportadorReportes.exportar(cluster, destino);
            cluster.getLog().registrar("Reporte de metricas exportado en " + destino.getName());
            JOptionPane.showMessageDialog(this, "Reporte exportado correctamente en:\n" + destino.getAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "No se pudo exportar: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refrescar() {
        ciclo.setText("Ciclo: " + cluster.getReloj().getCiclo());
        iniciarPausar.setText(cluster.getReloj().estaCorriendo() ? "Pausar" : "Iniciar");
        refrescarUsuarios();
        panelComputador.refrescar();
        panelArchivos.refrescar();
        tablaAsignacion.refrescar();
        panelDisco.refrescar();
        panelCola.refrescar();
        panelCache.refrescar();
        refrescarMetricas();
        graficaDesplazamiento.repaint();
        graficaUso.repaint();

        ListaEnlazada<String> lineas = cluster.getLog().vaciar();
        if (!lineas.estaVacia()) {
            StringBuilder sb = new StringBuilder();
            Recorrido<String> rl = lineas.recorrido();
            while (rl.haySiguiente()) {
                sb.append(rl.siguiente()).append('\n');
            }
            log.append(sb.toString());
            log.setCaretPosition(log.getDocument().getLength());
        }
    }

    private void refrescarMetricas() {
        Computador c = cluster.getComputadores()[0];
        Estadisticas e = c.getOs().getEstadisticas();
        long ciclos = Math.max(1, cluster.getReloj().getCiclo());
        long total = Math.max(1, c.getCpu().getCiclosTotales());
        tablaMetricas.setFilas(new Object[][] {{
            "Computador " + c.getId(),
            num((double) e.getCompletados() / ciclos),
            pct((double) c.getCpu().getCiclosOcupada() / total),
            num(e.getRespuestaPromedio()),
            pct(e.getCumplimientoDeadlines()),
            num(e.getEquidad()),
            e.getCompletados(),
            e.getVencidos(),
            c.getOs().getSuspensiones(),
            c.getOs().getReactivaciones()
        }});
    }

    private static String num(double d) {
        return String.format("%.3f", d);
    }

    private static String pct(double d) {
        return String.format("%.1f %%", d * 100);
    }
}
