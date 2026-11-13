package unimet.avilaos.so.archivos;

/** Un nodo del arbol es un directorio o un archivo. */
public enum TipoNodo {
    DIRECTORIO("DIRECTORY", "Directorio"),
    ARCHIVO("FILE", "Archivo");

    private final String clave;
    private final String etiqueta;

    TipoNodo(String clave, String etiqueta) {
        this.clave = clave;
        this.etiqueta = etiqueta;
    }

    /** Nombre que usa el JSON del sistema de archivos. */
    public String getClave() {
        return clave;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public static TipoNodo desdeClave(String texto) {
        for (TipoNodo t : values()) {
            if (texto != null && t.clave.equalsIgnoreCase(texto.trim())) {
                return t;
            }
        }
        throw new IllegalArgumentException("Tipo de nodo desconocido: " + texto);
    }
}
