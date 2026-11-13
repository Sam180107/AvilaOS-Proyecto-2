package unimet.avilaos.so.disco;

import unimet.avilaos.estructuras.ListaEnlazada;
import unimet.avilaos.hardware.disco.DecisionDisco;
import unimet.avilaos.hardware.disco.DireccionCabezal;
import unimet.avilaos.hardware.disco.SolicitudES;

/**
 * Politica de planificacion de disco. Para agregar una nueva basta con
 * implementar esta interfaz; el planificador no cambia.
 */
public interface PoliticaDisco {

    String nombre();

    /**
     * Decide que hace el disco. La cola viene en orden de llegada y nunca
     * esta vacia. La politica no debe modificarla.
     */
    DecisionDisco decidir(ListaEnlazada<SolicitudES> cola, int cabezal,
            DireccionCabezal direccion, int ultimoBloque);
}
