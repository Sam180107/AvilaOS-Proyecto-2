package unimet.avilaos.so.archivos;

/** Modo en que se esta usando AvilaOS. */
public enum ModoUsuario {
    ADMINISTRADOR("Administrador"),
    USUARIO("Usuario");

    private final String etiqueta;

    ModoUsuario(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
