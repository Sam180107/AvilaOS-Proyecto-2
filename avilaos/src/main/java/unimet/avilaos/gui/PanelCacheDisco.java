package unimet.avilaos.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import unimet.avilaos.cluster.Cluster;
import unimet.avilaos.so.disco.CacheDisco;

/**
 * Vista de la cache LRU de bloques de disco en RAM: estadisticas de hits/misses
 * y tabla ordenada desde el bloque mas reciente (MRU) hasta el mas antiguo (LRU).
 */
public class PanelCacheDisco extends JPanel {

    private static final String[] COLUMNAS = {
        "Prioridad LRU", "Bloque", "Archivo asociado", "Ultimo ciclo", "Accesos"
    };

    private final Cluster cluster;
    private final JLabel resumen = new JLabel(" ");
    private final ModeloTablaSoloLectura modelo = new ModeloTablaSoloLectura(COLUMNAS);

    public PanelCacheDisco(Cluster cluster) {
        super(new BorderLayout(4, 4));
        this.cluster = cluster;
        setBorder(BorderFactory.createTitledBorder("Cache LRU de bloques en RAM"));

        JPanel arriba = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        arriba.add(resumen);

        add(arriba, BorderLayout.NORTH);
        add(new JScrollPane(new JTable(modelo)), BorderLayout.CENTER);
    }

    public void refrescar() {
        if (cluster.getSistemaArchivos() == null) {
            return;
        }
        CacheDisco cache = cluster.getSistemaArchivos().getCache();
        if (cache == null) {
            return;
        }
        resumen.setText(String.format(
            "Ocupacion: %d / %d bloques | Hits: %d | Misses: %d | Tasa de aciertos: %.1f %%",
            cache.getOcupados(), cache.getCapacidad(),
            cache.getHits(), cache.getMisses(), cache.getTasaAciertos() * 100.0
        ));
        modelo.setFilas(cache.filas());
    }
}
