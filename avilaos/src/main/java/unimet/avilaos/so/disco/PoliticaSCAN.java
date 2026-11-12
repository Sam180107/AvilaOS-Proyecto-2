package unimet.avilaos.so.disco;

import unimet.avilaos.estructuras.ListaEnlazada;
import unimet.avilaos.hardware.disco.DecisionDisco;
import unimet.avilaos.hardware.disco.DireccionCabezal;
import unimet.avilaos.hardware.disco.SolicitudES;

/**
 * SCAN (ascensor): atiende lo que encuentra en su sentido, llega hasta
 * el extremo del disco y se devuelve.
 */
public class PoliticaSCAN implements PoliticaDisco {

    @Override
    public String nombre() {
        return "SCAN";
    }

    @Override
    public DecisionDisco decidir(ListaEnlazada<SolicitudES> cola, int cabezal,
            DireccionCabezal direccion, int ultimoBloque) {
        SolicitudES adelante = Barrido.masCercanaAdelante(cola, cabezal, direccion);
        if (adelante != null) {
            return DecisionDisco.atender(adelante);
        }
        int extremo = direccion == DireccionCabezal.ASCENDENTE ? ultimoBloque : 0;
        if (cabezal == extremo) {
            // ya estamos en el borde: solo queda devolverse
            return DecisionDisco.atender(Barrido.masCercana(cola, cabezal));
        }
        return DecisionDisco.irAExtremo(extremo, direccion.opuesta());
    }
}
