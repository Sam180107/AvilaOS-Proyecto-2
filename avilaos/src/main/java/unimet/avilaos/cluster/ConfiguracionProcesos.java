package unimet.avilaos.cluster;

import unimet.avilaos.estructuras.ListaEnlazada;
import unimet.avilaos.so.disco.TipoPoliticaDisco;
import unimet.avilaos.so.planificacion.TipoPolitica;

/** Lo que trae el JSON de procesos: parametros globales, el computador y los procesos de E/S. */
public class ConfiguracionProcesos {

    public int duracionCiclo = 300;
    public int tamanoSwap = 100;
    /** Posiciones que recorre el cabezal en un ciclo de reloj. */
    public int posicionesPorCiclo = 4;
    public TipoPoliticaDisco politicaDisco = TipoPoliticaDisco.FIFO;
    public int ram = 1024;
    public TipoPolitica politica = TipoPolitica.FCFS;
    public int quantum = 4;
    public final ListaEnlazada<EspecificacionES> procesos = new ListaEnlazada<>();
}
