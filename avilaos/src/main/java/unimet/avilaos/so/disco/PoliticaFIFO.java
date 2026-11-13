package unimet.avilaos.so.disco;

import unimet.avilaos.estructuras.ListaEnlazada;
import unimet.avilaos.hardware.disco.DecisionDisco;
import unimet.avilaos.hardware.disco.DireccionCabezal;
import unimet.avilaos.hardware.disco.SolicitudES;

/** FIFO: se atiende en orden de llegada. */
public class PoliticaFIFO implements PoliticaDisco {

    @Override
    public String nombre() {
        return "FIFO";
    }

    @Override
    public DecisionDisco decidir(ListaEnlazada<SolicitudES> cola, int cabezal,
            DireccionCabezal direccion, int ultimoBloque) {
        return DecisionDisco.atender(cola.obtenerPrimero());
    }
}
