package unimet.avilaos.so.disco;

import unimet.avilaos.estructuras.ListaEnlazada;
import unimet.avilaos.hardware.disco.DecisionDisco;
import unimet.avilaos.hardware.disco.DireccionCabezal;
import unimet.avilaos.hardware.disco.SolicitudES;

/**
 * C-LOOK: barre en un solo sentido y, cuando no queda nada adelante,
 * vuelve a la solicitud mas lejana del otro lado para empezar de nuevo.
 */
public class PoliticaCLOOK implements PoliticaDisco {

    @Override
    public String nombre() {
        return "C-LOOK";
    }

    @Override
    public DecisionDisco decidir(ListaEnlazada<SolicitudES> cola, int cabezal,
            DireccionCabezal direccion, int ultimoBloque) {
        SolicitudES adelante = Barrido.masCercanaAdelante(cola, cabezal, direccion);
        if (adelante != null) {
            return DecisionDisco.atender(adelante);
        }
        // el viaje de regreso no cambia el sentido del barrido
        int inicio = Barrido.delExtremo(cola, direccion == DireccionCabezal.ASCENDENTE).getBloqueObjetivo();
        return DecisionDisco.irAExtremo(inicio, direccion);
    }
}
