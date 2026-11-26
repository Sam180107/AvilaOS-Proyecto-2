package unimet.avilaos.gui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import unimet.avilaos.cluster.Cluster;
import unimet.avilaos.hardware.disco.Bloque;
import unimet.avilaos.hardware.disco.Disco;
import unimet.avilaos.hardware.disco.DireccionCabezal;
import unimet.avilaos.hardware.disco.SolicitudES;
import unimet.avilaos.hardware.disco.UsoBloque;

/**
 * Mapa del disco: un cuadro por bloque, con el color del archivo (o proceso
 * suspendido) que lo ocupa, el area de intercambio aparte y el cabezal marcado
 * en rojo. Solo observa: trabaja con una copia del mapa de bloques.
 */
public class PanelDisco extends JPanel {

    private static final Color COLOR_CABEZAL = new Color(0xD50000);

    private final Cluster cluster;
    private final JLabel estado = new JLabel(" ");
    private final Lienzo lienzo = new Lienzo();

    public PanelDisco(Cluster cluster) {
        super(new BorderLayout(4, 4));
        this.cluster = cluster;
        setBorder(BorderFactory.createTitledBorder("Disco (SD)"));

        JPanel leyenda = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        leyenda.add(muestra("Libre", PaletaArchivos.LIBRE, null));
        leyenda.add(muestra("Archivo (un color por archivo)", PaletaArchivos.MUESTRA_ARCHIVO, null));
        leyenda.add(muestra("Proceso suspendido (swap)", PaletaArchivos.MUESTRA_INTERCAMBIO, null));
        leyenda.add(muestra("Cabezal", Color.WHITE, COLOR_CABEZAL));

        add(estado, BorderLayout.NORTH);
        add(lienzo, BorderLayout.CENTER);
        add(leyenda, BorderLayout.SOUTH);
    }

    private static JLabel muestra(String texto, Color fondo, Color borde) {
        JLabel l = new JLabel(" " + texto + " ");
        l.setOpaque(true);
        l.setBackground(fondo);
        l.setBorder(BorderFactory.createLineBorder(borde == null ? Color.GRAY : borde, borde == null ? 1 : 2));
        return l;
    }

    public void refrescar() {
        Disco d = cluster.getDisco();
        if (d == null) {
            return;
        }
        Bloque[] foto = d.foto();
        int cabezal = d.getCabezal();
        DireccionCabezal dir = d.getDireccion();
        SolicitudES actual = d.getSolicitudActual();
        estado.setText("  Cabezal: bloque " + cabezal + "   Direccion: " + dir.getFlecha() + " " + dir.getEtiqueta()
                + "   |   " + (actual == null ? "Disco ocioso" : "Atendiendo: " + actual)
                + "   |   Libres: " + d.bloquesLibres() + " de " + d.getTotalBloques()
                + "   Swap libre: " + d.swapLibre() + " de " + d.getTamanoSwap());
        lienzo.actualizar(foto, d.getTotalBloques(), cabezal);
    }

    /** Dibuja la rejilla de bloques. El tamano del cuadro se ajusta para que todo quepa sin scroll. */
    private static final class Lienzo extends JPanel {

        private static final int MARGEN = 6;
        private static final int CABECERA = 16;

        private Bloque[] bloques = new Bloque[0];
        private int totalArchivos;
        private int cabezal = -1;

        Lienzo() {
            setBackground(Color.WHITE);
            setToolTipText("");
        }

        void actualizar(Bloque[] bloques, int totalArchivos, int cabezal) {
            this.bloques = bloques;
            this.totalArchivos = totalArchivos;
            this.cabezal = cabezal;
            repaint();
        }

        /** {tamano del cuadro, columnas}. */
        private int[] distribuir() {
            int swap = bloques.length - totalArchivos;
            int cabeceras = swap > 0 ? 2 : 1;
            for (int c = 30; c > 6; c--) {
                int cols = Math.max(1, (getWidth() - 2 * MARGEN) / c);
                int filas = techo(totalArchivos, cols) + techo(swap, cols);
                if (filas * c + cabeceras * CABECERA + 2 * MARGEN <= getHeight()) {
                    return new int[]{c, cols};
                }
            }
            return new int[]{6, Math.max(1, (getWidth() - 2 * MARGEN) / 6)};
        }

        private static int techo(int a, int b) {
            return (a + b - 1) / b;
        }

        private int origenX(int indice, int c, int cols) {
            int dentro = indice < totalArchivos ? indice : indice - totalArchivos;
            return MARGEN + (dentro % cols) * c;
        }

        private int origenY(int indice, int c, int cols) {
            int yArchivos = MARGEN + CABECERA;
            if (indice < totalArchivos) {
                return yArchivos + (indice / cols) * c;
            }
            int ySwap = yArchivos + techo(totalArchivos, cols) * c + CABECERA;
            return ySwap + ((indice - totalArchivos) / cols) * c;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            if (bloques.length == 0) {
                return;
            }
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int[] d = distribuir();
            int c = d[0];
            int cols = d[1];
            g.setFont(getFont().deriveFont(Font.BOLD, 11f));
            g.setColor(Color.DARK_GRAY);
            g.drawString("Area de archivos: bloques 0 a " + (totalArchivos - 1), MARGEN, MARGEN + 11);
            if (bloques.length > totalArchivos) {
                int ySwap = MARGEN + CABECERA + techo(totalArchivos, cols) * c;
                g.drawString("Area de intercambio (swap): bloques " + totalArchivos + " a "
                        + (bloques.length - 1), MARGEN, ySwap + 11);
            }
            g.setFont(getFont().deriveFont(Math.max(7f, c * 0.38f)));
            for (int i = 0; i < bloques.length; i++) {
                int x = origenX(i, c, cols);
                int y = origenY(i, c, cols);
                Bloque b = bloques[i];
                Color fondo = b.getUso() == UsoBloque.LIBRE ? PaletaArchivos.LIBRE
                        : b.getUso() == UsoBloque.ARCHIVO ? PaletaArchivos.deArchivo(b.getOcupante())
                        : PaletaArchivos.deIntercambio(b.getOcupante());
                g.setColor(fondo);
                g.fillRect(x, y, c - 1, c - 1);
                if (b.getUso() == UsoBloque.INTERCAMBIO) {
                    g.setColor(new Color(0, 0, 0, 70));
                    g.drawLine(x, y + c - 2, x + c - 2, y);
                }
                g.setColor(new Color(0xBDBDBD));
                g.drawRect(x, y, c - 1, c - 1);
                if (c >= 20) {
                    g.setColor(b.estaLibre() ? new Color(0x9E9E9E) : Color.BLACK);
                    String n = String.valueOf(i);
                    g.drawString(n, x + (c - 1 - g.getFontMetrics().stringWidth(n)) / 2,
                            y + (c + g.getFontMetrics().getAscent()) / 2 - 2);
                }
            }
            if (cabezal >= 0 && cabezal < bloques.length) {
                g.setColor(COLOR_CABEZAL);
                g.setStroke(new BasicStroke(3f));
                g.drawRect(origenX(cabezal, c, cols) - 1, origenY(cabezal, c, cols) - 1, c, c);
            }
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            if (bloques.length == 0) {
                return null;
            }
            int[] d = distribuir();
            for (int i = 0; i < bloques.length; i++) {
                int x = origenX(i, d[0], d[1]);
                int y = origenY(i, d[0], d[1]);
                if (e.getX() >= x && e.getX() < x + d[0] && e.getY() >= y && e.getY() < y + d[0]) {
                    Bloque b = bloques[i];
                    if (b.estaLibre()) {
                        return "Bloque " + i + ": libre";
                    }
                    return "Bloque " + i + ": " + b.getOcupante() + " (" + b.getUso().getEtiqueta() + ")"
                            + (b.getSiguiente() == Bloque.SIN_SIGUIENTE ? ", ultimo de la cadena"
                            : ", siguiente: " + b.getSiguiente());
                }
            }
            return null;
        }
    }
}
