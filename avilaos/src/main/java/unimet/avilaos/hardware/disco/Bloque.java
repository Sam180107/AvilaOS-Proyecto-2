package unimet.avilaos.hardware.disco;

/**
 * Un bloque del disco. Con asignacion encadenada cada bloque guarda
 * cual es el siguiente de su archivo (-1 si es el ultimo).
 */
public class Bloque {

    public static final int SIN_SIGUIENTE = -1;

    private final int numero;
    private final boolean deIntercambio;
    private UsoBloque uso = UsoBloque.LIBRE;
    private String ocupante = null;
    private int siguiente = SIN_SIGUIENTE;

    Bloque(int numero, boolean deIntercambio) {
        this.numero = numero;
        this.deIntercambio = deIntercambio;
    }

    public int getNumero() {
        return numero;
    }

    /** true si el bloque pertenece al area de intercambio. */
    public boolean esDeIntercambio() {
        return deIntercambio;
    }

    public UsoBloque getUso() {
        return uso;
    }

    public boolean estaLibre() {
        return uso == UsoBloque.LIBRE;
    }

    /** Ruta del archivo o nombre del proceso suspendido que lo ocupa. */
    public String getOcupante() {
        return ocupante;
    }

    public int getSiguiente() {
        return siguiente;
    }

    void ocupar(UsoBloque uso, String ocupante) {
        this.uso = uso;
        this.ocupante = ocupante;
        this.siguiente = SIN_SIGUIENTE;
    }

    void setSiguiente(int siguiente) {
        this.siguiente = siguiente;
    }

    void setOcupante(String ocupante) {
        this.ocupante = ocupante;
    }

    void liberar() {
        this.uso = UsoBloque.LIBRE;
        this.ocupante = null;
        this.siguiente = SIN_SIGUIENTE;
    }

    Bloque copia() {
        Bloque b = new Bloque(numero, deIntercambio);
        b.uso = uso;
        b.ocupante = ocupante;
        b.siguiente = siguiente;
        return b;
    }
}
