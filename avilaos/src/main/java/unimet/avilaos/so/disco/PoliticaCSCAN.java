package unimet.avilaos.so.disco;

import unimet.avilaos.estructuras.ListaEnlazada;
import unimet.avilaos.hardware.disco.DecisionDisco;
import unimet.avilaos.hardware.disco.DireccionCabezal;
import unimet.avilaos.hardware.disco.SolicitudES;

/**
 * C-SCAN: barre en un solo sentido. Al llegar al extremo salta al otro
 * lado sin atender nada y sigue en el mismo sentido.
 */
public class PoliticaCSCAN implements PoliticaDisco {

    @Override
    public String nombre() {
        return "C-SCAN";
    }

    @Override
    public DecisionDisco decidir(ListaEnlazada<SolicitudES> cola, int cabezal,
            DireccionCabezal direccion, int ultimoBloque) {
        SolicitudES adelante = Barrido.masCercanaAdelante(cola, cabezal, direccion);
        if (adelante != null) {
            return DecisionDisco.atender(adelante);
        }
        boolean sube = direccion == DireccionCabezal.ASCENDENTE;
        int extremo = sube ? ultimoBloque : 0;
        int otroLado = sube ? 0 : ultimoBloque;
        return DecisionDisco.irAExtremoYSaltar(extremo, otroLado, direccion);
    }
}
