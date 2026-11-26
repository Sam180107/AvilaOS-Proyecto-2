package unimet.avilaos.hardware.disco;

import unimet.avilaos.so.proceso.PCB;

/**
 * Una solicitud en la cola de E/S del disco: que operacion es, quien la
 * pide y que bloques hay que recorrer para cumplirla.
 */
public class SolicitudES {

    private static long proximoId = 1;

    private final long id;
    private final TipoOperacionES operacion;
    private final String ruta;
    private final int[] bloques;
    private final long idProceso;
    private final String nombreProceso;
    private final PCB pcb;
    private final long cicloLlegada;

    private String dato;
    private String permisoNuevo;
    private long cicloInicio = -1;
    private long cicloFin = -1;
    private int hechos = 0;

    /**
     * @param bloques bloques a leer o escribir, en orden. Puede venir vacio
     *                (por ejemplo al crear un directorio).
     * @param pcb     proceso que queda bloqueado esperando; puede ser null.
     */
    public SolicitudES(TipoOperacionES operacion, String ruta, int[] bloques,
            long idProceso, String nombreProceso, PCB pcb, long cicloLlegada) {
        if (operacion == null) {
            throw new IllegalArgumentException("La operacion no puede ser null");
        }
        this.id = siguienteId();
        this.operacion = operacion;
        this.ruta = ruta;
        this.bloques = bloques == null ? new int[0] : bloques.clone();
        this.idProceso = idProceso;
        this.nombreProceso = nombreProceso;
        this.pcb = pcb;
        this.cicloLlegada = cicloLlegada;
    }

    private static synchronized long siguienteId() {
        return proximoId++;
    }

    public long getId() {
        return id;
    }

    public TipoOperacionES getOperacion() {
        return operacion;
    }

    public String getRuta() {
        return ruta;
    }

    public long getIdProceso() {
        return idProceso;
    }

    public String getNombreProceso() {
        return nombreProceso;
    }

    public PCB getPcb() {
        return pcb;
    }

    public long getCicloLlegada() {
        return cicloLlegada;
    }

    public int[] getBloques() {
        return bloques.clone();
    }

    public int cantidadBloques() {
        return bloques.length;
    }

    public boolean tieneBloques() {
        return bloques.length > 0;
    }

    /** Bloque que miran las politicas para ordenar la cola (el primero). */
    public int getBloqueObjetivo() {
        return bloques.length > 0 ? bloques[0] : -1;
    }

    /** Dato extra de la operacion; en ACTUALIZAR es el nombre nuevo. */
    public String getDato() {
        return dato;
    }

    public void setDato(String dato) {
        this.dato = dato;
    }

    /** En ACTUALIZAR: permiso nuevo del archivo (PUBLICO/PRIVADO), o null si no cambia. */
    public String getPermisoNuevo() {
        return permisoNuevo;
    }

    public void setPermisoNuevo(String permisoNuevo) {
        this.permisoNuevo = permisoNuevo;
    }

    public long getCicloInicio() {
        return cicloInicio;
    }

    public void setCicloInicio(long ciclo) {
        this.cicloInicio = ciclo;
    }

    public long getCicloFin() {
        return cicloFin;
    }

    public void setCicloFin(long ciclo) {
        this.cicloFin = ciclo;
    }

    /** Ciclos que paso en la cola antes de que el disco la tomara. */
    public long getEspera() {
        return cicloInicio < 0 ? -1 : cicloInicio - cicloLlegada;
    }

    public int getBloquesHechos() {
        return hechos;
    }

    /** Bloque que toca ahora, o -1 si no le queda ninguno. */
    int bloqueActual() {
        return hechos < bloques.length ? bloques[hechos] : -1;
    }

    void avanzar() {
        hechos++;
    }

    boolean terminada() {
        return hechos >= bloques.length;
    }

    @Override
    public String toString() {
        return operacion.getEtiqueta() + " " + ruta + " (" + nombreProceso + ")";
    }
}
