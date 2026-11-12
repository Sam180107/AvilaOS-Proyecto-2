package unimet.avilaos.hardware.disco;

/**
 * Lo que el planificador le ordena al disco: atender una solicitud o
 * llevar el cabezal hasta un extremo (SCAN y C-SCAN).
 */
public final class DecisionDisco {

    public static final int SIN_SALTO = -1;

    private final SolicitudES solicitud;
    private final int extremo;
    private final DireccionCabezal direccionAlLlegar;
    private final int saltarA;

    private DecisionDisco(SolicitudES solicitud, int extremo,
            DireccionCabezal direccionAlLlegar, int saltarA) {
        this.solicitud = solicitud;
        this.extremo = extremo;
        this.direccionAlLlegar = direccionAlLlegar;
        this.saltarA = saltarA;
    }

    public static DecisionDisco atender(SolicitudES solicitud) {
        return new DecisionDisco(solicitud, -1, null, SIN_SALTO);
    }

    /** Ir hasta el extremo y devolverse (SCAN). */
    public static DecisionDisco irAExtremo(int extremo, DireccionCabezal direccionAlLlegar) {
        return new DecisionDisco(null, extremo, direccionAlLlegar, SIN_SALTO);
    }

    /** Ir hasta el extremo y saltar al otro lado sin atender nada (C-SCAN). */
    public static DecisionDisco irAExtremoYSaltar(int extremo, int saltarA,
            DireccionCabezal direccionAlLlegar) {
        return new DecisionDisco(null, extremo, direccionAlLlegar, saltarA);
    }

    public boolean esAtender() {
        return solicitud != null;
    }

    public SolicitudES getSolicitud() {
        return solicitud;
    }

    public int getExtremo() {
        return extremo;
    }

    public DireccionCabezal getDireccionAlLlegar() {
        return direccionAlLlegar;
    }

    public boolean haySalto() {
        return saltarA != SIN_SALTO;
    }

    public int getSaltarA() {
        return saltarA;
    }
}
