package unimet.avilaos.so.archivos;

import unimet.avilaos.estructuras.ListaEnlazada;
import unimet.avilaos.estructuras.Recorrido;

/**
 * Nodo del arbol de directorios. Un directorio tiene hijos; un archivo
 * tiene tamano en bloques y la direccion de su primer bloque en el disco.
 */
public class NodoArchivo {

    private String nombre;
    private final TipoNodo tipo;
    private final String dueno;
    private final Permiso permiso;
    private NodoArchivo padre;
    private final ListaEnlazada<NodoArchivo> hijos = new ListaEnlazada<>();

    private int tamanoBloques = 0;
    private int primerBloque = -1;
    private String procesoCreador = "-";

    NodoArchivo(String nombre, TipoNodo tipo, String dueno, Permiso permiso) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.dueno = dueno;
        this.permiso = permiso;
    }

    public String getNombre() {
        return nombre;
    }

    public TipoNodo getTipo() {
        return tipo;
    }

    public boolean esDirectorio() {
        return tipo == TipoNodo.DIRECTORIO;
    }

    public String getDueno() {
        return dueno;
    }

    public Permiso getPermiso() {
        return permiso;
    }

    public NodoArchivo getPadre() {
        return padre;
    }

    public int getTamanoBloques() {
        return tamanoBloques;
    }

    /** Direccion del primer bloque, o -1 si es un directorio. */
    public int getPrimerBloque() {
        return primerBloque;
    }

    /** Proceso de E/S que lo creo ("-" si vino del JSON). */
    public String getProcesoCreador() {
        return procesoCreador;
    }

    /** Ruta completa desde la raiz, por ejemplo /usuarios/notas.txt. */
    public String getRuta() {
        if (padre == null) {
            return "/";
        }
        String base = padre.getRuta();
        return base.equals("/") ? "/" + nombre : base + "/" + nombre;
    }

    public int cantidadHijos() {
        return hijos.tamano();
    }

    public Recorrido<NodoArchivo> hijos() {
        return hijos.recorrido();
    }

    public NodoArchivo hijo(String nombreHijo) {
        Recorrido<NodoArchivo> r = hijos.recorrido();
        while (r.haySiguiente()) {
            NodoArchivo n = r.siguiente();
            if (n.nombre.equals(nombreHijo)) {
                return n;
            }
        }
        return null;
    }

    void agregarHijo(NodoArchivo n) {
        n.padre = this;
        hijos.agregarAlFinal(n);
    }

    void quitarHijo(NodoArchivo n) {
        hijos.eliminar(n);
        n.padre = null;
    }

    void setNombre(String nombre) {
        this.nombre = nombre;
    }

    void setBloques(int primerBloque, int tamanoBloques) {
        this.primerBloque = primerBloque;
        this.tamanoBloques = tamanoBloques;
    }

    void setProcesoCreador(String procesoCreador) {
        this.procesoCreador = procesoCreador;
    }

    /** Asi lo muestra el JTree. */
    @Override
    public String toString() {
        return padre == null ? "/" : nombre;
    }
}
