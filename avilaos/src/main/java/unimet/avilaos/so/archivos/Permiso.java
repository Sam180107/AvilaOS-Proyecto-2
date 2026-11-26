package unimet.avilaos.so.archivos;

/** Quien puede leer un archivo ademas de su dueno. */
public enum Permiso {
    PUBLICO("public", "Publico"),
    PRIVADO("private", "Privado");

    private final String clave;
    private final String etiqueta;

    Permiso(String clave, String etiqueta) {
        this.clave = clave;
        this.etiqueta = etiqueta;
    }

    public String getClave() {
        return clave;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public static Permiso desdeClave(String texto) {
        for (Permiso p : values()) {
            if (texto != null && p.clave.equalsIgnoreCase(texto.trim())) {
                return p;
            }
        }
        throw new IllegalArgumentException("Permiso desconocido: " + texto);
    }
}
