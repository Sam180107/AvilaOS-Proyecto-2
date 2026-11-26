package unimet.avilaos.so.disco;

/** Politicas de planificacion de disco disponibles. */
public enum TipoPoliticaDisco {
    FIFO("FIFO"),
    SSTF("SSTF"),
    SCAN("SCAN"),
    C_SCAN("C-SCAN"),
    LOOK("LOOK"),
    C_LOOK("C-LOOK");

    private final String etiqueta;

    TipoPoliticaDisco(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** Acepta "C-SCAN", "CSCAN", "c_scan"... como viene en el JSON. */
    public static TipoPoliticaDisco desdeTexto(String texto) {
        if (texto != null) {
            String limpio = texto.trim().replace("-", "").replace("_", "");
            for (TipoPoliticaDisco t : values()) {
                if (t.name().replace("_", "").equalsIgnoreCase(limpio)) {
                    return t;
                }
            }
        }
        throw new IllegalArgumentException("Politica de disco desconocida: " + texto);
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
