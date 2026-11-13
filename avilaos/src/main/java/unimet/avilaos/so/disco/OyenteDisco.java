package unimet.avilaos.so.disco;

import unimet.avilaos.hardware.disco.SolicitudES;

/** Quien quiera enterarse de que el disco termino una solicitud (el SO, para despertar al proceso). */
public interface OyenteDisco {

    void solicitudCompletada(SolicitudES solicitud, long ciclo);
}
