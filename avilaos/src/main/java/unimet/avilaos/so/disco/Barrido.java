package unimet.avilaos.so.disco;

import unimet.avilaos.estructuras.ListaEnlazada;
import unimet.avilaos.estructuras.Recorrido;
import unimet.avilaos.hardware.disco.DireccionCabezal;
import unimet.avilaos.hardware.disco.SolicitudES;

/** Busquedas sobre la cola que comparten varias politicas. */
final class Barrido {

    private Barrido() {
    }

    /** La solicitud mas cercana al cabezal sin importar el lado. */
    static SolicitudES masCercana(ListaEnlazada<SolicitudES> cola, int cabezal) {
        SolicitudES mejor = null;
        Recorrido<SolicitudES> r = cola.recorrido();
        while (r.haySiguiente()) {
            SolicitudES s = r.siguiente();
            // con < los empates los gana la que llego primero
            if (mejor == null || distancia(s, cabezal) < distancia(mejor, cabezal)) {
                mejor = s;
            }
        }
        return mejor;
    }

    /** La mas cercana en el sentido en que va el cabezal, o null si no hay ninguna. */
    static SolicitudES masCercanaAdelante(ListaEnlazada<SolicitudES> cola, int cabezal,
            DireccionCabezal direccion) {
        SolicitudES mejor = null;
        Recorrido<SolicitudES> r = cola.recorrido();
        while (r.haySiguiente()) {
            SolicitudES s = r.siguiente();
            int b = s.getBloqueObjetivo();
            boolean adelante = direccion == DireccionCabezal.ASCENDENTE ? b >= cabezal : b <= cabezal;
            if (adelante && (mejor == null || distancia(s, cabezal) < distancia(mejor, cabezal))) {
                mejor = s;
            }
        }
        return mejor;
    }

    /** La del bloque mas bajo (o mas alto) de toda la cola. */
    static SolicitudES delExtremo(ListaEnlazada<SolicitudES> cola, boolean masBajo) {
        SolicitudES mejor = null;
        Recorrido<SolicitudES> r = cola.recorrido();
        while (r.haySiguiente()) {
            SolicitudES s = r.siguiente();
            if (mejor == null
                    || (masBajo && s.getBloqueObjetivo() < mejor.getBloqueObjetivo())
                    || (!masBajo && s.getBloqueObjetivo() > mejor.getBloqueObjetivo())) {
                mejor = s;
            }
        }
        return mejor;
    }

    static int distancia(SolicitudES s, int cabezal) {
        return Math.abs(s.getBloqueObjetivo() - cabezal);
    }
}
