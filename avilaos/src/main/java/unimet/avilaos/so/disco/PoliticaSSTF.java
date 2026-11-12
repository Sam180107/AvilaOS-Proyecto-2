package unimet.avilaos.so.disco;

import unimet.avilaos.estructuras.ListaEnlazada;
import unimet.avilaos.hardware.disco.DecisionDisco;
import unimet.avilaos.hardware.disco.DireccionCabezal;
import unimet.avilaos.hardware.disco.SolicitudES;

/** SSTF: primero la solicitud que queda mas cerca del cabezal. */
public class PoliticaSSTF implements PoliticaDisco {

    @Override
    public String nombre() {
        return "SSTF";
    }

    @Override
    public DecisionDisco decidir(ListaEnlazada<SolicitudES> cola, int cabezal,
            DireccionCabezal direccion, int ultimoBloque) {
        return DecisionDisco.atender(Barrido.masCercana(cola, cabezal));
    }
}
