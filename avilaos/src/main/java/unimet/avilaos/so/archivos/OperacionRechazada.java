package unimet.avilaos.so.archivos;

/**
 * El sistema de archivos no acepto la operacion. El mensaje explica por
 * que, para mostrarselo al usuario sin parar la simulacion.
 */
public class OperacionRechazada extends RuntimeException {

    public OperacionRechazada(String motivo) {
        super(motivo);
    }
}
