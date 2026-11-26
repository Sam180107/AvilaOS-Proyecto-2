package unimet.avilaos.gui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Window;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import unimet.avilaos.cluster.Cluster;
import unimet.avilaos.cluster.EspecificacionES;
import unimet.avilaos.hardware.disco.Disco;
import unimet.avilaos.hardware.disco.TipoOperacionES;
import unimet.avilaos.so.archivos.OperacionRechazada;
import unimet.avilaos.so.archivos.SistemaArchivos;

/**
 * Crea un proceso de E/S para una operacion sobre un archivo o directorio.
 * La interfaz nunca toca el sistema de archivos: solo crea el proceso y este
 * hace la operacion cuando le toque la CPU y el disco lo atienda.
 */
public class DialogoOperacionArchivo extends JDialog {

    /** Lo que el usuario puede pedir; cada accion se traduce a una operacion de E/S. */
    public enum Accion {
        CREAR_ARCHIVO("Crear archivo", TipoOperacionES.CREAR),
        CREAR_DIRECTORIO("Crear directorio", TipoOperacionES.CREAR),
        LEER("Leer", TipoOperacionES.LEER),
        RENOMBRAR("Actualizar (renombrar)", TipoOperacionES.ACTUALIZAR),
        ELIMINAR("Eliminar", TipoOperacionES.ELIMINAR);

        private final String etiqueta;
        private final TipoOperacionES operacion;

        Accion(String etiqueta, TipoOperacionES operacion) {
            this.etiqueta = etiqueta;
            this.operacion = operacion;
        }

        public TipoOperacionES getOperacion() {
            return operacion;
        }

        @Override
        public String toString() {
            return etiqueta;
        }
    }

    private static int contadorProcesos = 1;

    private final Cluster cluster;
    private final ContextoUsuario contexto;
    private final int ramTotal;

    private final JComboBox<Accion> accion = new JComboBox<>(Accion.values());
    private final JTextField ruta = new JTextField();
    private final JSpinner tamano;
    private final JTextField nuevoNombre = new JTextField();
    private final JTextField nombre = new JTextField();
    private final JSpinner memoria;
    private final JSpinner instrucciones = DialogoProceso.numero(2, 1, 1_000_000);
    private final JSpinner deadline = DialogoProceso.numero(500, 1, 10_000_000);
    private final JSpinner prioridad = DialogoProceso.numero(5, 0, 99);

    public DialogoOperacionArchivo(Window duena, Cluster cluster, ContextoUsuario contexto,
            Accion inicial, String rutaSugerida) {
        super(duena, "Nueva operacion de archivo", ModalityType.APPLICATION_MODAL);
        this.cluster = cluster;
        this.contexto = contexto;
        this.ramTotal = cluster.getComputadores()[0].getRam().getCapacidad();
        Disco disco = cluster.getDisco();
        this.tamano = DialogoProceso.numero(1, 1, Math.max(1, disco.getTotalBloques()));
        this.memoria = DialogoProceso.numero(Math.min(16, ramTotal), 1, ramTotal);

        nombre.setText("ES_" + contadorProcesos);
        ruta.setText(rutaSugerida == null ? "/" : rutaSugerida);
        accion.setSelectedItem(inicial);
        accion.addActionListener(e -> habilitar());

        JPanel f = new JPanel(new GridLayout(0, 2, 6, 4));
        f.add(new JLabel("Operacion:")); f.add(accion);
        f.add(new JLabel("Ruta (ej. /usuarios/notas.txt):")); f.add(ruta);
        f.add(new JLabel("Tamano en bloques (crear archivo):")); f.add(tamano);
        f.add(new JLabel("Nuevo nombre (renombrar):")); f.add(nuevoNombre);
        f.add(new JLabel("A nombre de:")); f.add(new JLabel(contexto.usuario() + " (" + contexto.modo().getEtiqueta() + ")"));
        f.add(new JLabel("Nombre del proceso de E/S:")); f.add(nombre);
        f.add(new JLabel("Memoria del proceso (RAM " + ramTotal + "):")); f.add(memoria);
        f.add(new JLabel("Instrucciones:")); f.add(instrucciones);
        f.add(new JLabel("Deadline (ciclos):")); f.add(deadline);
        f.add(new JLabel("Prioridad (0 = la mas alta):")); f.add(prioridad);

        JButton crear = new JButton("Crear proceso");
        crear.addActionListener(e -> crear());
        JButton cancelar = new JButton("Cancelar");
        cancelar.addActionListener(e -> dispose());
        JPanel botones = new JPanel();
        botones.add(crear);
        botones.add(cancelar);

        setLayout(new BorderLayout(6, 6));
        add(f, BorderLayout.CENTER);
        add(botones, BorderLayout.SOUTH);
        habilitar();
        pack();
        setLocationRelativeTo(duena);
    }

    private void habilitar() {
        Accion a = (Accion) accion.getSelectedItem();
        tamano.setEnabled(a == Accion.CREAR_ARCHIVO);
        nuevoNombre.setEnabled(a == Accion.RENOMBRAR);
    }

    private void crear() {
        Accion a = (Accion) accion.getSelectedItem();
        String r = ruta.getText().trim();
        String n = nombre.getText().trim();
        if (n.isEmpty() || n.length() > 30 || n.contains(",")) {
            error("El nombre del proceso es obligatorio, de maximo 30 caracteres y sin comas.");
            return;
        }
        if (!r.startsWith("/") || (r.length() > 1 && r.endsWith("/")) || r.contains("//")) {
            error("La ruta debe empezar con / y no terminar en /. Ejemplo: /usuarios/notas.txt");
            return;
        }
        SistemaArchivos fs = cluster.getSistemaArchivos();
        boolean crea = a == Accion.CREAR_ARCHIVO || a == Accion.CREAR_DIRECTORIO;
        if (crea && r.length() == 1) {
            error("Falta el nombre del archivo o directorio nuevo.");
            return;
        }
        if (!crea && fs.buscar(r) == null) {
            error("No existe " + r + " en el sistema de archivos.");
            return;
        }
        String nuevo = null;
        if (a == Accion.RENOMBRAR) {
            nuevo = nuevoNombre.getText().trim();
            if (nuevo.isEmpty() || nuevo.contains("/") || nuevo.length() > 60) {
                error("El nuevo nombre es obligatorio, sin / y de maximo 60 caracteres.");
                return;
            }
        }
        int bloques = a == Accion.CREAR_ARCHIVO ? DialogoProceso.valor(tamano) : 0;
        Disco disco = cluster.getDisco();
        if (bloques > disco.bloquesLibres()) {
            error("No hay bloques libres suficientes: se piden " + bloques + " y quedan "
                    + disco.bloquesLibres() + ".");
            return;
        }
        try {
            cluster.crearProcesoES(new EspecificacionES(n, DialogoProceso.valor(memoria),
                    DialogoProceso.valor(prioridad), DialogoProceso.valor(deadline),
                    DialogoProceso.valor(instrucciones), a.getOperacion(), r, bloques, nuevo,
                    contexto.usuario(), contexto.modo()));
            contadorProcesos++;
            dispose();
        } catch (OperacionRechazada ex) {
            // el modo usuario no permite la operacion: se explica por que y la simulacion sigue
            cluster.getLog().registrar("Operacion rechazada (" + contexto.usuario() + "): " + ex.getMessage());
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Operacion no permitida",
                    JOptionPane.WARNING_MESSAGE);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            error(ex.getMessage());
        }
    }

    private void error(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Dato invalido", JOptionPane.WARNING_MESSAGE);
    }
}
