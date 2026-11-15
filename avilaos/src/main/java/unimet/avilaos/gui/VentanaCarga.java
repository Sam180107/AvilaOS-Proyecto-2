package unimet.avilaos.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import unimet.avilaos.cluster.Cluster;
import unimet.avilaos.cluster.ConfiguracionProcesos;
import unimet.avilaos.cluster.EspecificacionES;
import unimet.avilaos.estructuras.Recorrido;
import unimet.avilaos.hardware.disco.TipoOperacionES;
import unimet.avilaos.json.ArchivoProcesos;
import unimet.avilaos.json.ArchivoSistemaArchivos;
import unimet.avilaos.json.ErrorJson;
import unimet.avilaos.so.archivos.SistemaArchivos;

/**
 * Primera ventana: obliga a cargar los dos JSON (sistema de archivos y
 * procesos) antes de poder arrancar. Cada archivo se valida al elegirlo y
 * el mensaje de error dice que esta mal. El reloj no corre hasta que se
 * cierra esta ventana y se arma el sistema.
 */
public class VentanaCarga extends JDialog {

    private static final Color VERDE = new Color(0x2E7D32);
    private static final Color ROJO = new Color(0xC62828);
    private static final Color NARANJA = new Color(0xE65100);

    private static File ultimaCarpeta = new File("datos").isDirectory() ? new File("datos") : null;

    private File archivoFs;
    private File archivoProcesos;
    private final JTextField rutaFs = new JTextField(32);
    private final JTextField rutaProcesos = new JTextField(32);
    private final JLabel estadoFs = new JLabel("Falta cargar el sistema de archivos");
    private final JLabel estadoProcesos = new JLabel("Falta cargar los procesos y la configuracion");
    private final JButton iniciar = new JButton("Iniciar AvilaOS");
    private Cluster resultado;

    public VentanaCarga() {
        super((java.awt.Frame) null, "AvilaOS - Carga de archivos", true);
        rutaFs.setEditable(false);
        rutaProcesos.setEditable(false);
        estadoFs.setForeground(NARANJA);
        estadoProcesos.setForeground(NARANJA);

        JPanel f = new JPanel(new GridBagLayout());
        f.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
        fila(f, 0, "1. Sistema de archivos (JSON):", rutaFs, "Examinar...", () -> elegir(false), estadoFs);
        fila(f, 3, "2. Procesos y configuracion (JSON):", rutaProcesos, "Examinar...", () -> elegir(true), estadoProcesos);

        JLabel nota = new JLabel("<html>Los dos archivos son obligatorios: el reloj no arranca sin ellos. "
                + "Cargue los procesos primero si el disco depende del swap.</html>");
        iniciar.setEnabled(false);
        iniciar.addActionListener(e -> iniciar());
        JButton salir = new JButton("Salir");
        salir.addActionListener(e -> dispose());
        JPanel botones = new JPanel();
        botones.add(iniciar);
        botones.add(salir);

        setLayout(new BorderLayout(6, 6));
        add(nota, BorderLayout.NORTH);
        add(f, BorderLayout.CENTER);
        add(botones, BorderLayout.SOUTH);
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    private static void fila(JPanel p, int y, String titulo, JTextField campo, String texto,
            Runnable accion, JLabel estado) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.gridy = y; c.gridwidth = 2;
        p.add(new JLabel(titulo), c);
        c.gridy = y + 1; c.gridwidth = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        p.add(campo, c);
        JButton b = new JButton(texto);
        b.addActionListener(e -> accion.run());
        c.gridx = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        p.add(b, c);
        c.gridx = 0; c.gridy = y + 2; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        p.add(estado, c);
    }

    private void elegir(boolean procesos) {
        JFileChooser fc = new JFileChooser(ultimaCarpeta);
        fc.setDialogTitle(procesos ? "JSON de procesos y configuracion" : "JSON del sistema de archivos");
        fc.setFileFilter(new FileNameExtensionFilter("Archivos JSON", "json"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File elegido = fc.getSelectedFile();
        ultimaCarpeta = elegido.getParentFile();
        if (procesos) {
            archivoProcesos = elegido;
            rutaProcesos.setText(elegido.getAbsolutePath());
        } else {
            archivoFs = elegido;
            rutaFs.setText(elegido.getAbsolutePath());
        }
        validar();
    }

    /** Lee los dos archivos y muestra el resultado de cada uno. Habilita Iniciar solo si ambos sirven. */
    private void validar() {
        ConfiguracionProcesos cfg = null;
        boolean procesosOk = false;
        boolean fsOk = false;
        if (archivoProcesos != null) {
            try {
                cfg = ArchivoProcesos.cargar(archivoProcesos);
                procesosOk = true;
                estado(estadoProcesos, VERDE, cfg.procesos.tamano() + " procesos de E/S, RAM " + cfg.ram
                        + ", CPU " + cfg.politica + ", disco " + cfg.politicaDisco.getEtiqueta()
                        + ", swap " + cfg.tamanoSwap + " bloques");
            } catch (ErrorJson | IllegalArgumentException e) {
                estado(estadoProcesos, ROJO, e.getMessage());
            }
        }
        if (archivoFs != null) {
            if (cfg == null) {
                estado(estadoFs, NARANJA, "Se valida cuando los procesos esten cargados (hace falta el tamano del swap)");
            } else {
                try {
                    SistemaArchivos fs = ArchivoSistemaArchivos.cargar(archivoFs, cfg.tamanoSwap, cfg.posicionesPorCiclo);
                    fsOk = true;
                    estado(estadoFs, VERDE, fs.getDisco().getTotalBloques() + " bloques, " + fs.listarArchivos().tamano()
                            + " archivos, cabezal en " + fs.getDisco().getCabezal());
                    avisarRutas(cfg, fs);
                } catch (ErrorJson | IllegalArgumentException e) {
                    estado(estadoFs, ROJO, e.getMessage());
                }
            }
        }
        iniciar.setEnabled(procesosOk && fsOk);
        pack();
    }

    /** Un proceso que apunta a una ruta que no existe no falla al cargar, pero conviene avisarlo. */
    private void avisarRutas(ConfiguracionProcesos cfg, SistemaArchivos fs) {
        int faltan = 0;
        String primera = null;
        Recorrido<EspecificacionES> r = cfg.procesos.recorrido();
        while (r.haySiguiente()) {
            EspecificacionES e = r.siguiente();
            if (e.operacion() != TipoOperacionES.CREAR && fs.buscar(e.ruta()) == null) {
                faltan++;
                if (primera == null) {
                    primera = e.ruta();
                }
            }
        }
        if (faltan > 0) {
            estado(estadoProcesos, NARANJA, "Aviso: " + faltan + " proceso(s) apuntan a rutas que no estan en el sistema"
                    + " de archivos (por ejemplo " + primera + "); su operacion sera rechazada al correr");
        }
    }

    private static void estado(JLabel l, Color color, String texto) {
        l.setForeground(color);
        l.setText("<html><body style='width:420px'>" + texto.replace("&", "&amp;").replace("<", "&lt;") + "</body></html>");
    }

    private void iniciar() {
        try {
            // se vuelve a leer todo para que el disco del sistema sea una copia limpia
            ConfiguracionProcesos cfg = ArchivoProcesos.cargar(archivoProcesos);
            SistemaArchivos fs = ArchivoSistemaArchivos.cargar(archivoFs, cfg.tamanoSwap, cfg.posicionesPorCiclo);
            resultado = Cluster.crear(cfg, fs);
            dispose();
        } catch (ErrorJson | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "No se pudo iniciar", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Muestra la ventana y devuelve el sistema ya armado, o null si la cerraron sin iniciar. */
    public Cluster mostrar() {
        setVisible(true);
        return resultado;
    }
}
