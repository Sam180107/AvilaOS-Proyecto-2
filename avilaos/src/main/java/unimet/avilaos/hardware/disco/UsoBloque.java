package unimet.avilaos.hardware.disco;

/** Que hay guardado en un bloque del disco. */
public enum UsoBloque {
    LIBRE("Libre"),
    ARCHIVO("Archivo"),
    INTERCAMBIO("Proceso suspendido");

    private final String etiqueta;

    UsoBloque(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
