package unimet.avilaos.gui;

import java.awt.Color;
import unimet.avilaos.estructuras.ListaEnlazada;

/**
 * Colores del disco: cada archivo (o proceso suspendido) tiene el suyo, igual
 * en el mapa y en la tabla. Los tonos se reparten con la razon aurea segun el
 * orden en que aparece cada nombre, asi los archivos vecinos nunca se parecen.
 */
public final class PaletaArchivos {

    public static final Color LIBRE = new Color(0xF2F2F2);
    /** Colores de ejemplo para la leyenda. */
    public static final Color MUESTRA_ARCHIVO = Color.getHSBColor(0.58f, 0.5f, 0.93f);
    public static final Color MUESTRA_INTERCAMBIO = Color.getHSBColor(0.33f, 0.45f, 0.72f);

    private static final float RAZON_AUREA = 0.61803398875f;
    private static final ListaEnlazada<String> VISTOS = new ListaEnlazada<>();

    private PaletaArchivos() {
    }

    /** Color de un archivo, segun su ruta. */
    public static Color deArchivo(String ocupante) {
        return color(ocupante, 0.50f, 0.93f);
    }

    /** Color de un proceso suspendido en el area de intercambio (mas oscuro, se dibuja rayado). */
    public static Color deIntercambio(String ocupante) {
        return color(ocupante, 0.45f, 0.72f);
    }

    private static synchronized Color color(String ocupante, float saturacion, float brillo) {
        if (ocupante == null) {
            return LIBRE;
        }
        int i = VISTOS.indiceDe(ocupante);
        if (i < 0) {
            VISTOS.agregarAlFinal(ocupante);
            i = VISTOS.tamano() - 1;
        }
        float tono = (i * RAZON_AUREA) % 1f;
        return Color.getHSBColor(tono, saturacion, brillo);
    }
}
