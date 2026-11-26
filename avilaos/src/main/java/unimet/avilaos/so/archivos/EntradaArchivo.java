package unimet.avilaos.so.archivos;

/**
 * Copia inmutable de un nodo del arbol de archivos. La interfaz trabaja con
 * estas copias para no tocar el arbol real mientras la simulacion lo modifica.
 *
 * @param nivel profundidad en el arbol (la raiz es 0).
 */
public record EntradaArchivo(String ruta, String nombre, TipoNodo tipo, String dueno,
        Permiso permiso, int bloques, int primerBloque, String creador, int nivel) {

    public boolean esDirectorio() {
        return tipo == TipoNodo.DIRECTORIO;
    }
}
