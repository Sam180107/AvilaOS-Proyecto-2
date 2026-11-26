package unimet.avilaos.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;
import unimet.avilaos.cluster.Cluster;
import unimet.avilaos.estructuras.ListaEnlazada;
import unimet.avilaos.estructuras.Recorrido;

/** Desplazamiento acumulado del cabezal contra el tiempo (ciclos), dibujado con Java2D. */
public class GraficaDesplazamiento extends JPanel {

    private final Cluster cluster;

    public GraficaDesplazamiento(Cluster cluster) {
        this.cluster = cluster;
        setBackground(Color.WHITE);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        if (cluster.getDisco() == null) {
            return;
        }
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int izq = 60, der = 20, arr = 28, aba = 30;
        int ancho = getWidth() - izq - der;
        int alto = getHeight() - arr - aba;
        if (ancho < 20 || alto < 20) {
            return;
        }

        ListaEnlazada<long[]> hist = cluster.getDisco().historialDesplazamiento();
        long cicloActual = Math.max(1, cluster.getReloj().getCiclo());
        long total = 0;
        Recorrido<long[]> r = hist.recorrido();
        while (r.haySiguiente()) {
            total = Math.max(total, r.siguiente()[1]);
        }
        long maxY = Math.max(10, total);
        long maxX = Math.max(1, cicloActual);

        g.setColor(Color.DARK_GRAY);
        g.drawString("Desplazamiento acumulado del cabezal (bloques) por ciclo   -   politica: "
                + cluster.getPlanificadorDisco().getTipoPolitica().getEtiqueta(), izq, 16);
        g.setColor(new Color(0xEEEEEE));
        for (int i = 0; i <= 4; i++) {
            int y = arr + alto - i * alto / 4;
            g.drawLine(izq, y, izq + ancho, y);
        }
        g.setColor(Color.DARK_GRAY);
        g.drawLine(izq, arr, izq, arr + alto);
        g.drawLine(izq, arr + alto, izq + ancho, arr + alto);
        for (int i = 0; i <= 4; i++) {
            int y = arr + alto - i * alto / 4;
            g.drawString(String.valueOf(maxY * i / 4), 8, y + 4);
        }
        g.drawString("ciclo 0", izq, arr + alto + 18);
        String fin = "ciclo " + maxX;
        g.drawString(fin, izq + ancho - g.getFontMetrics().stringWidth(fin), arr + alto + 18);

        g.setColor(new Color(0x1E88E5));
        g.setStroke(new BasicStroke(2f));
        int xAnt = izq, yAnt = arr + alto;
        long ultimo = 0;
        r = hist.recorrido();
        while (r.haySiguiente()) {
            long[] p = r.siguiente();
            int x = izq + (int) (p[0] * ancho / maxX);
            int y = arr + alto - (int) (p[1] * alto / maxY);
            g.drawLine(xAnt, yAnt, x, y);
            xAnt = x;
            yAnt = y;
            ultimo = p[1];
        }
        // el desplazamiento se queda quieto hasta el ciclo actual
        g.drawLine(xAnt, yAnt, izq + ancho, arr + alto - (int) (ultimo * alto / maxY));
    }
}
