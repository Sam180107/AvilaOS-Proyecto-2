package unimet.avilaos.hardware.disco;

/**
 * Lo que el disco necesita del sistema operativo. El disco no sabe de
 * politicas: solo pregunta que hacer y avisa cuando termina.
 */
public interface ControladorDisco {

    /** Que hacer ahora. Devuelve null si la cola de E/S esta vacia. */
    DecisionDisco siguiente(int cabezal, DireccionCabezal direccion, int ultimoBloque, long ciclo);

    /** El disco termino de atender la solicitud. */
    void completada(SolicitudES solicitud, long ciclo);
}
