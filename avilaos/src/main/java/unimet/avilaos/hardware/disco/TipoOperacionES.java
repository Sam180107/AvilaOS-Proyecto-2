package unimet.avilaos.hardware.disco;

/** Operaciones que un proceso le puede pedir al disco. */
public enum TipoOperacionES {
    CREAR("CREATE", "Crear"),
    LEER("READ", "Leer"),
    ACTUALIZAR("UPDATE", "Actualizar"),
    ELIMINAR("DELETE", "Eliminar"),
    SUSPENDER("SUSPEND", "Suspender"),
    REACTIVAR("RESUME", "Reactivar");

    private final String clave;
    private final String etiqueta;

    TipoOperacionES(String clave, String etiqueta) {
        this.clave = clave;
        this.etiqueta = etiqueta;
    }

    /** Nombre que usa el JSON de procesos (READ, UPDATE...). */
    public String getClave() {
        return clave;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** Las CRUD trabajan sobre el sistema de archivos; las otras dos sobre el swap. */
    public boolean esDeArchivo() {
        return this != SUSPENDER && this != REACTIVAR;
    }

    public static TipoOperacionES desdeClave(String texto) {
        if (texto != null) {
            for (TipoOperacionES t : values()) {
                if (t.clave.equalsIgnoreCase(texto.trim()) || t.name().equalsIgnoreCase(texto.trim())) {
                    return t;
                }
            }
        }
        throw new IllegalArgumentException("Operacion de E/S desconocida: " + texto);
    }
}
