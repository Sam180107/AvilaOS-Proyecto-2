package unimet.avilaos.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import unimet.avilaos.cluster.Cluster;
import unimet.avilaos.estructuras.ListaEnlazada;
import unimet.avilaos.estructuras.Recorrido;
import unimet.avilaos.hardware.disco.Disco;
import unimet.avilaos.hardware.disco.SolicitudES;
import unimet.avilaos.so.disco.MetricasDisco;
import unimet.avilaos.so.disco.PlanificadorDisco;
import unimet.avilaos.so.disco.TipoPoliticaDisco;

/**
 * Cola de E/S del disco con la operacion de cada solicitud, el selector de la
 * politica de planificacion y las metricas del disco.
 */
public class PanelColaDisco extends JPanel {

    private static final String[] COLUMNAS = {
        "Llegada", "Solicitud", "Proceso", "Operacion", "Ruta", "Bloque", "Llego en ciclo", "Espera (ciclos)"
    };

    private final Cluster cluster;
    private final JComboBox<TipoPoliticaDisco> politica = new JComboBox<>(TipoPoliticaDisco.values());
    private final JLabel atendiendo = new JLabel(" ");
    private final JLabel desplazamiento = new JLabel(" ");
    private final JLabel porPolitica = new JLabel(" ");
    private final ModeloTablaSoloLectura tabla = new ModeloTablaSoloLectura(COLUMNAS);
    private boolean refrescando = false;

    public PanelColaDisco(Cluster cluster) {
        super(new BorderLayout(4, 4));
        this.cluster = cluster;
        setBorder(BorderFactory.createTitledBorder("Cola de E/S del disco"));

        politica.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> l, Object v, int i,
                    boolean sel, boolean foco) {
                return super.getListCellRendererComponent(l, ((TipoPoliticaDisco) v).getEtiqueta(), i, sel, foco);
            }
        });
        politica.addActionListener(e -> {
            if (!refrescando) {
                cluster.cambiarPoliticaDisco((TipoPoliticaDisco) politica.getSelectedItem());
            }
        });

        JPanel arriba = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        arriba.add(new JLabel("Politica de disco:"));
        arriba.add(politica);
        arriba.add(atendiendo);

        JPanel metricas = new JPanel(new GridLayout(0, 1, 0, 1));
        metricas.add(desplazamiento);
        metricas.add(porPolitica);

        add(arriba, BorderLayout.NORTH);
        add(new JScrollPane(new JTable(tabla)), BorderLayout.CENTER);
        add(metricas, BorderLayout.SOUTH);
    }

    /** Solo el administrador puede cambiar la politica de planificacion. */
    public void setEditable(boolean puedeCambiar) {
        politica.setEnabled(puedeCambiar);
    }

    public void refrescar() {
        PlanificadorDisco plan = cluster.getPlanificadorDisco();
        Disco disco = cluster.getDisco();
        if (plan == null || disco == null) {
            return;
        }
        TipoPoliticaDisco tipo = plan.getTipoPolitica();
        if (politica.getSelectedItem() != tipo) {
            refrescando = true;
            politica.setSelectedItem(tipo);
            refrescando = false;
        }
        SolicitudES actual = disco.getSolicitudActual();
        atendiendo.setText(actual == null ? "   Disco ocioso" : "   Atendiendo: " + actual);

        long ciclo = cluster.getReloj().getCiclo();
        ListaEnlazada<SolicitudES> cola = plan.copiaCola();
        Object[][] filas = new Object[cola.tamano()][];
        Recorrido<SolicitudES> r = cola.recorrido();
        int i = 0;
        while (r.haySiguiente()) {
            SolicitudES s = r.siguiente();
            filas[i] = new Object[]{++i, s.getId(), s.getNombreProceso(), s.getOperacion().getEtiqueta(),
                s.getRuta(), s.tieneBloques() ? s.getBloqueObjetivo() : "-", s.getCicloLlegada(),
                Math.max(0, ciclo - s.getCicloLlegada())};
        }
        tabla.setFilas(filas);
        setBorder(BorderFactory.createTitledBorder("Cola de E/S del disco (" + filas.length + " pendientes)"));

        MetricasDisco m = plan.getMetricas();
        desplazamiento.setText(String.format("  Desplazamiento total del cabezal: %d bloques   |   "
                + "Espera promedio en la cola: %.2f ciclos   |   Atendidas: %d",
                disco.getDesplazamientoTotal(), m.esperaPromedio(), m.getTotalAtendidas()));
        StringBuilder sb = new StringBuilder("  Atendidas por politica:");
        for (TipoPoliticaDisco t : TipoPoliticaDisco.values()) {
            sb.append("  ").append(t.getEtiqueta()).append(" ").append(m.atendidasCon(t));
        }
        porPolitica.setText(sb.toString());
    }
}
