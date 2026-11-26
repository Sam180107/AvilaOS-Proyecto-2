package unimet.avilaos.so.proceso;

/**
 * Si un proceso suspendido tiene una solicitud de intercambio en la cola
 * del disco. Mientras el disco no la termine, el proceso no puede volver
 * a suspenderse ni reactivarse.
 */
public enum TransitoSwap {
    NINGUNO("-"),
    SALIENDO("Escribiendose en swap"),
    ENTRANDO("Leyendose de swap");

    private final String etiqueta;

    TransitoSwap(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
