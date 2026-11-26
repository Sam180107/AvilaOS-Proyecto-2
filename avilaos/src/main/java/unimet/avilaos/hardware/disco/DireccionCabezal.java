package unimet.avilaos.hardware.disco;

/** Hacia donde se esta moviendo el cabezal del disco. */
public enum DireccionCabezal {
    ASCENDENTE("Hacia bloques mayores", "↑"),
    DESCENDENTE("Hacia bloques menores", "↓");

    private final String etiqueta;
    private final String flecha;

    DireccionCabezal(String etiqueta, String flecha) {
        this.etiqueta = etiqueta;
        this.flecha = flecha;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public String getFlecha() {
        return flecha;
    }

    public DireccionCabezal opuesta() {
        return this == ASCENDENTE ? DESCENDENTE : ASCENDENTE;
    }
}
