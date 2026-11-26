package unimet.avilaos.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JSpinner;
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
import unimet.avilaos.so.disco.TipoPoliticaDisco;
import unimet.avilaos.so.planificacion.TipoPolitica;

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

    // configuracion: sale de los JSON y se puede ajustar antes de iniciar
    private final JSpinner duracion = DialogoProceso.numero(300, 1, 10_000);
    private final JSpinner ram = DialogoProceso.numero(1024, 1, 1_000_000);
    private final JComboBox<TipoPolitica> politicaCpu = new JComboBox<>(TipoPolitica.values());
    private final JSpinner quantum = DialogoProceso.numero(4, 1, 1_000);
    private final JSpinner bloques = DialogoProceso.numero(200, 100, 500);
    private final JSpinner swap = DialogoProceso.numero(100, 0, 1_000);
    private final JComboBox<TipoPoliticaDisco> politicaDisco = new JComboBox<>(TipoPoliticaDisco.values());
    private final JSpinner cabezal = DialogoProceso.numero(50, 0, 1_499);
    private final JSpinner velocidad = DialogoProceso.numero(4, 1, 500);
    private final JCheckBox discoDetenido = new JCheckBox("Arrancar con el disco detenido (para ver los casos P2 a P4 con la cola llena)");
    private final JPanel configuracion = new JPanel(new GridLayout(0, 2, 6, 3));
    private File cargadoFs;
    private File cargadoProcesos;
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

        politicaCpu.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> l, Object v, int i,
                    boolean sel, boolean foco) {
                return super.getListCellRendererComponent(l, ((TipoPolitica) v).getEtiqueta(), i, sel, foco);
            }
        });
        configuracion.setBorder(BorderFactory.createTitledBorder("3. Configuracion (sale de los JSON; se puede ajustar)"));
        campo("Duracion del ciclo (ms):", duracion);
        campo("RAM del computador:", ram);
        campo("Politica de CPU:", politicaCpu);
        campo("Quantum (Round Robin):", quantum);
        campo("Bloques del disco (100 a 500):", bloques);
        campo("Tamano del area de intercambio (bloques):", swap);
        campo("Politica de disco inicial:", politicaDisco);
        campo("Posicion inicial del cabezal:", cabezal);
        campo("Posiciones del cabezal por ciclo:", velocidad);
        configuracion.add(discoDetenido);
        habilitarConfiguracion(false);
        JPanel centro = new JPanel(new BorderLayout(4, 4));
        centro.add(f, BorderLayout.NORTH);
        centro.add(configuracion, BorderLayout.CENTER);

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
        add(centro, BorderLayout.CENTER);
        add(botones, BorderLayout.SOUTH);
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    private void campo(String titulo, java.awt.Component c) {
        configuracion.add(new JLabel(titulo));
        configuracion.add(c);
    }

    private void habilitarConfiguracion(boolean si) {
        for (java.awt.Component c : configuracion.getComponents()) {
            c.setEnabled(si);
        }
    }

    /** Pone en los campos lo que dicen los JSON, solo cuando cambia alguno de los archivos. */
    private void llenarConfiguracion(ConfiguracionProcesos cfg, SistemaArchivos fs) {
        if (archivoFs == cargadoFs && archivoProcesos == cargadoProcesos) {
            return;
        }
        cargadoFs = archivoFs;
        cargadoProcesos = archivoProcesos;
        duracion.setValue(Math.min(10_000, cfg.duracionCiclo));
        ram.setValue(Math.min(1_000_000, cfg.ram));
        politicaCpu.setSelectedItem(cfg.politica);
        quantum.setValue(Math.min(1_000, cfg.quantum));
        bloques.setValue(fs.getDisco().getTotalBloques());
        swap.setValue(Math.min(1_000, cfg.tamanoSwap));
        politicaDisco.setSelectedItem(cfg.politicaDisco);
        cabezal.setValue(Math.min(1_499, fs.getDisco().getCabezal()));
        velocidad.setValue(Math.min(500, cfg.posicionesPorCiclo));
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
                    llenarConfiguracion(cfg, fs);
                } catch (ErrorJson | IllegalArgumentException e) {
                    estado(estadoFs, ROJO, e.getMessage());
                }
            }
        }
        iniciar.setEnabled(procesosOk && fsOk);
        habilitarConfiguracion(procesosOk && fsOk);
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
            cfg.duracionCiclo = DialogoProceso.valor(duracion);
            cfg.ram = DialogoProceso.valor(ram);
            cfg.politica = (TipoPolitica) politicaCpu.getSelectedItem();
            cfg.quantum = DialogoProceso.valor(quantum);
            cfg.tamanoSwap = DialogoProceso.valor(swap);
            cfg.politicaDisco = (TipoPoliticaDisco) politicaDisco.getSelectedItem();
            cfg.posicionesPorCiclo = DialogoProceso.valor(velocidad);
            int total = DialogoProceso.valor(bloques);
            int inicio = DialogoProceso.valor(cabezal);
            if (inicio >= total) {
                JOptionPane.showMessageDialog(this, "El cabezal debe empezar en un bloque de archivos: entre 0 y "
                        + (total - 1) + ".", "Dato invalido", JOptionPane.WARNING_MESSAGE);
                return;
            }
            SistemaArchivos fs = ArchivoSistemaArchivos.cargar(archivoFs, cfg.tamanoSwap, cfg.posicionesPorCiclo,
                    total, inicio);
            resultado = Cluster.crear(cfg, fs);
            if (discoDetenido.isSelected()) {
                resultado.detenerDisco(true);
            }
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
