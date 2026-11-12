package unimet.avilaos.so.disco;

import unimet.avilaos.estructuras.ListaEnlazada;
import unimet.avilaos.estructuras.Recorrido;
import unimet.avilaos.hardware.disco.ControladorDisco;
import unimet.avilaos.hardware.disco.DecisionDisco;
import unimet.avilaos.hardware.disco.DireccionCabezal;
import unimet.avilaos.hardware.disco.SolicitudES;
import unimet.avilaos.sincronizacion.Mutex;
import unimet.avilaos.so.RegistroEventos;

/**
 * Planificador de disco. Tiene la unica cola de E/S y le dice al disco
 * que solicitud atender segun la politica activa. La politica se puede
 * cambiar con el sistema corriendo.
 */
public class PlanificadorDisco implements ControladorDisco {

    private final Mutex mutex = new Mutex();
    private final ListaEnlazada<SolicitudES> cola = new ListaEnlazada<>();
    private final MetricasDisco metricas = new MetricasDisco();
    private final RegistroEventos log;

    private TipoPoliticaDisco tipo;
    private PoliticaDisco politica;
    private OyenteDisco oyente;

    /** @param log puede ser null (en las pruebas). */
    public PlanificadorDisco(TipoPoliticaDisco tipo, RegistroEventos log) {
        this.log = log;
        this.tipo = tipo;
        this.politica = FabricaPoliticasDisco.crear(tipo);
    }

    public void setOyente(OyenteDisco oyente) {
        this.oyente = oyente;
    }

    /** Una solicitud nueva entra a la cola de E/S. */
    public void encolar(SolicitudES solicitud) {
        mutex.cerrar();
        try {
            cola.agregarAlFinal(solicitud);
        } finally {
            mutex.abrir();
        }
        registrar(solicitud.getNombreProceso() + " encola " + solicitud.getOperacion().getEtiqueta()
                + " sobre " + solicitud.getRuta());
    }

    public void cambiarPolitica(TipoPoliticaDisco nueva) {
        mutex.cerrar();
        try {
            tipo = nueva;
            politica = FabricaPoliticasDisco.crear(nueva);
        } finally {
            mutex.abrir();
        }
        registrar("Politica de disco cambiada a " + nueva.getEtiqueta());
    }

    /** Saca de la cola las solicitudes de un proceso (si lo matan antes de ser atendido). */
    public int cancelarDe(long idProceso) {
        mutex.cerrar();
        try {
            ListaEnlazada<SolicitudES> quitar = new ListaEnlazada<>();
            Recorrido<SolicitudES> r = cola.recorrido();
            while (r.haySiguiente()) {
                SolicitudES s = r.siguiente();
                if (s.getIdProceso() == idProceso) {
                    quitar.agregarAlFinal(s);
                }
            }
            Recorrido<SolicitudES> q = quitar.recorrido();
            while (q.haySiguiente()) {
                cola.eliminar(q.siguiente());
            }
            return quitar.tamano();
        } finally {
            mutex.abrir();
        }
    }

    @Override
    public DecisionDisco siguiente(int cabezal, DireccionCabezal direccion, int ultimoBloque, long ciclo) {
        DecisionDisco decision;
        String mensaje = null;
        mutex.cerrar();
        try {
            if (cola.estaVacia()) {
                return null;
            }
            SolicitudES sinBloques = primeraSinBloques();
            if (sinBloques != null) {
                // no hay que mover el cabezal (p. ej. crear un directorio): sale de una vez
                decision = DecisionDisco.atender(sinBloques);
            } else {
                decision = politica.decidir(cola, cabezal, direccion, ultimoBloque);
            }
            if (decision.esAtender()) {
                SolicitudES s = decision.getSolicitud();
                cola.eliminar(s);
                s.setCicloInicio(ciclo);
                metricas.registrar(tipo, s.getEspera());
                mensaje = "El disco atiende la solicitud de " + s.getNombreProceso()
                        + (s.tieneBloques() ? " en el bloque " + s.getBloqueObjetivo() : "")
                        + " (" + tipo.getEtiqueta() + ")";
            } else if (decision.haySalto()) {
                mensaje = "El cabezal va al bloque " + decision.getExtremo() + " y salta al "
                        + decision.getSaltarA() + " (" + tipo.getEtiqueta() + ")";
            } else {
                mensaje = "El cabezal va al bloque " + decision.getExtremo()
                        + " (" + tipo.getEtiqueta() + ")";
            }
        } finally {
            mutex.abrir();
        }
        registrar(mensaje);
        return decision;
    }

    private SolicitudES primeraSinBloques() {
        Recorrido<SolicitudES> r = cola.recorrido();
        while (r.haySiguiente()) {
            SolicitudES s = r.siguiente();
            if (!s.tieneBloques()) {
                return s;
            }
        }
        return null;
    }

    @Override
    public void completada(SolicitudES solicitud, long ciclo) {
        solicitud.setCicloFin(ciclo);
        registrar("El disco termino " + solicitud.getOperacion().getEtiqueta() + " de "
                + solicitud.getNombreProceso());
        if (oyente != null) {
            oyente.solicitudCompletada(solicitud, ciclo);
        }
    }

    private void registrar(String mensaje) {
        if (log != null && mensaje != null) {
            log.registrar(mensaje);
        }
    }

    // ---------- consultas ----------

    public TipoPoliticaDisco getTipoPolitica() {
        mutex.cerrar();
        try {
            return tipo;
        } finally {
            mutex.abrir();
        }
    }

    public MetricasDisco getMetricas() {
        return metricas;
    }

    public int pendientes() {
        mutex.cerrar();
        try {
            return cola.tamano();
        } finally {
            mutex.abrir();
        }
    }

    /** Copia de la cola de E/S, en orden de llegada, para mostrarla. */
    public ListaEnlazada<SolicitudES> copiaCola() {
        mutex.cerrar();
        try {
            ListaEnlazada<SolicitudES> copia = new ListaEnlazada<>();
            Recorrido<SolicitudES> r = cola.recorrido();
            while (r.haySiguiente()) {
                copia.agregarAlFinal(r.siguiente());
            }
            return copia;
        } finally {
            mutex.abrir();
        }
    }
}
