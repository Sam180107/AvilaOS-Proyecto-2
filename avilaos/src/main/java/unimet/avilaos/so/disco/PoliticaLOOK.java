package unimet.avilaos.so.disco;

import unimet.avilaos.estructuras.ListaEnlazada;
import unimet.avilaos.hardware.disco.DecisionDisco;
import unimet.avilaos.hardware.disco.DireccionCabezal;
import unimet.avilaos.hardware.disco.SolicitudES;

/** LOOK: como SCAN pero se devuelve en la ultima solicitud, sin ir hasta el borde. */
public class PoliticaLOOK implements PoliticaDisco {

    @Override
    public String nombre() {
        return "LOOK";
    }

    @Override
    public DecisionDisco decidir(ListaEnlazada<SolicitudES> cola, int cabezal,
            DireccionCabezal direccion, int ultimoBloque) {
        SolicitudES adelante = Barrido.masCercanaAdelante(cola, cabezal, direccion);
        if (adelante != null) {
            return DecisionDisco.atender(adelante);
        }
        return DecisionDisco.atender(Barrido.masCercanaAdelante(cola, cabezal, direccion.opuesta()));
    }
}
