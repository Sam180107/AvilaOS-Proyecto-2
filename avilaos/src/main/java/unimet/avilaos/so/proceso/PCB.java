package unimet.avilaos.so.proceso;

import unimet.avilaos.hardware.disco.TipoOperacionES;
import unimet.avilaos.sincronizacion.BufferAcotado;
import unimet.avilaos.so.archivos.ModoUsuario;
import unimet.avilaos.so.archivos.Permiso;

/**
 * Ficha de un proceso: id, estado, prioridad, deadline, memoria, PC, MAR y
 * los datos extra para E/S, productor-consumidor y area de intercambio (Swap).
 */
public class PCB {

    private final long id;
    private final String nombre;
    private final int idComputador;
    private final TipoProceso tipo;
    private final int prioridad;
    private final int memoria;
    private final int instruccionesTotales;
    private final int deadlineInicial;
    private final long cicloCreacion;

    private EstadoProceso estado = EstadoProceso.NUEVO;
    private MotivoBloqueo motivoBloqueo = MotivoBloqueo.NINGUNO;
    private int contadorPrograma = 0;
    private int tiempoRestante;
    private int deadlineRestante;
    private boolean terminadoPorDeadline = false;

    private long cicloPrimeraEjecucion = -1;
    private long cicloFinalizacion = -1;
    private long ciclosEnNuevo = 0;
    private long ciclosEnListo = 0;
    private long ciclosEnEjecucion = 0;
    private long ciclosBloqueado = 0;
    private long ciclosEnListoSuspendido = 0;
    private long ciclosEnBloqueadoSuspendido = 0;

    private int bloqueSwap = -1;
    private TransitoSwap transitoSwap = TransitoSwap.NINGUNO;
    private Permiso nuevoPermiso;
    private String duenoNuevo;

    private int mar = 0;
    private int ciclosEnCpu = 0;

    // I/O bound
    private int esCada = 0;
    private int duracionES = 0;
    private int desdeUltimaES = 0;

    // productor / consumidor
    private BufferAcotado buffer;
    private int cadaCuantosCiclos = 1;
    private int elementosRequeridos = 0;
    private int elementosHechos = 0;
    private FaseProductorConsumidor fase;
    private int contadorFase = 0;
    private boolean tieneMutex = false;
    private boolean reservoHueco = false;
    private long bloqueadoDesde = -1;
    private long ciclosEnSemaforos = 0;

    // E/S de archivo (Proyecto 2)
    private TipoOperacionES operacionES;
    private String rutaArchivo;
    private int tamanoArchivo = 0;
    private String nuevoNombre;
    private String usuario = "admin";
    private ModoUsuario modoUsuario = ModoUsuario.ADMINISTRADOR;
    private boolean solicitudHecha = false;

    public PCB(long id, String nombre, int idComputador, TipoProceso tipo, int prioridad,
               int memoria, int instrucciones, int deadline, long cicloCreacion) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre no puede estar vacio");
        }
        if (tipo == null) {
            throw new IllegalArgumentException("El tipo no puede ser null");
        }
        if (idComputador < 0) {
            throw new IllegalArgumentException("Computador invalido: " + idComputador);
        }
        if (prioridad < 0) {
            throw new IllegalArgumentException("La prioridad no puede ser negativa");
        }
        if (memoria < 1) {
            throw new IllegalArgumentException("La memoria debe ser al menos 1");
        }
        if (instrucciones < 1) {
            throw new IllegalArgumentException("Las instrucciones deben ser al menos 1");
        }
        if (deadline < 1) {
            throw new IllegalArgumentException("El deadline debe ser al menos 1");
        }
        this.id = id;
        this.nombre = nombre.trim();
        this.idComputador = idComputador;
        this.tipo = tipo;
        this.prioridad = prioridad;
        this.memoria = memoria;
        this.instruccionesTotales = instrucciones;
        this.deadlineInicial = deadline;
        this.cicloCreacion = cicloCreacion;
        this.tiempoRestante = instrucciones;
        this.deadlineRestante = deadline;
    }

    /** Cambia de estado. Si el cambio no tiene sentido lanza una excepcion. */
    public void cambiarEstado(EstadoProceso destino, long ciclo) {
        if (destino == EstadoProceso.BLOQUEADO) {
            throw new IllegalArgumentException("Para bloquear use bloquear(motivo, ciclo)");
        }
        transicionar(destino, ciclo);
    }

    public void bloquear(MotivoBloqueo motivo, long ciclo) {
        if (motivo == null || motivo == MotivoBloqueo.NINGUNO) {
            throw new IllegalArgumentException("Un proceso bloqueado necesita un motivo");
        }
        transicionar(EstadoProceso.BLOQUEADO, ciclo);
        this.motivoBloqueo = motivo;
    }

    public void terminarPorDeadline(long ciclo) {
        transicionar(EstadoProceso.TERMINADO, ciclo);
        this.terminadoPorDeadline = true;
    }

    private void transicionar(EstadoProceso destino, long ciclo) {
        if (!estado.puedePasarA(destino)) {
            throw new IllegalStateException("Transicion invalida en " + this + ": "
                    + estado + " -> " + destino);
        }
        if (destino == EstadoProceso.EJECUCION && cicloPrimeraEjecucion < 0) {
            cicloPrimeraEjecucion = ciclo;
        }
        if (destino == EstadoProceso.TERMINADO) {
            cicloFinalizacion = ciclo;
        }
        if ((estado == EstadoProceso.BLOQUEADO || estado == EstadoProceso.BLOQUEADO_SUSPENDIDO)
                && destino != EstadoProceso.BLOQUEADO && destino != EstadoProceso.BLOQUEADO_SUSPENDIDO) {
            motivoBloqueo = MotivoBloqueo.NINGUNO;
        }
        estado = destino;
    }

    /** Una instruccion ejecutada: suben el PC y el MAR y baja el tiempo restante. */
    public void ejecutarInstruccion() {
        if (estado != EstadoProceso.EJECUCION) {
            throw new IllegalStateException(this + " no esta en ejecucion");
        }
        contadorPrograma++;
        mar++;
        ciclosEnCpu++;
        if (tiempoRestante > 0) {
            tiempoRestante--;
        }
    }

    /** Resta un ciclo al deadline. @return true si ya se vencio */
    public boolean descontarDeadline() {
        if (deadlineRestante > 0) {
            deadlineRestante--;
        }
        return deadlineRestante == 0;
    }

    public void registrarCicloTranscurrido() {
        switch (estado) {
            case NUEVO -> ciclosEnNuevo++;
            case LISTO -> ciclosEnListo++;
            case EJECUCION -> ciclosEnEjecucion++;
            case BLOQUEADO -> ciclosBloqueado++;
            case LISTO_SUSPENDIDO -> ciclosEnListoSuspendido++;
            case BLOQUEADO_SUSPENDIDO -> ciclosEnBloqueadoSuspendido++;
            case TERMINADO -> { }
        }
    }

    public boolean estaSuspendido() {
        return estado == EstadoProceso.LISTO_SUSPENDIDO || estado == EstadoProceso.BLOQUEADO_SUSPENDIDO;
    }

    public boolean haTerminadoTrabajo() {
        return tiempoRestante == 0;
    }

    public boolean cumplioDeadline() {
        return estado == EstadoProceso.TERMINADO && !terminadoPorDeadline;
    }

    public long tiempoRespuesta() {
        return cicloPrimeraEjecucion < 0 ? -1 : cicloPrimeraEjecucion - cicloCreacion;
    }

    public long tiempoRetorno() {
        return cicloFinalizacion < 0 ? -1 : cicloFinalizacion - cicloCreacion;
    }

    public void configurarES(int cada, int duracion) {
        if (tipo != TipoProceso.IO_BOUND) {
            throw new IllegalStateException("Solo los I/O bound hacen E/S");
        }
        if (cada < 1 || duracion < 1) {
            throw new IllegalArgumentException("Los ciclos de E/S deben ser al menos 1");
        }
        this.esCada = cada;
        this.duracionES = duracion;
    }

    public void configurarBuffer(BufferAcotado buffer, int cadaCuantos, int elementos) {
        if (!tipo.usaBuffer()) {
            throw new IllegalStateException("Solo productores y consumidores usan buffer");
        }
        if (buffer == null || cadaCuantos < 1 || elementos < 1) {
            throw new IllegalArgumentException("Datos del buffer invalidos");
        }
        this.buffer = buffer;
        this.cadaCuantosCiclos = cadaCuantos;
        this.elementosRequeridos = elementos;
        this.fase = tipo == TipoProceso.PRODUCTOR
                ? FaseProductorConsumidor.PRODUCIR : FaseProductorConsumidor.ACCESO_RED;
    }

    public void configurarArchivo(TipoOperacionES operacion, String ruta, int tamanoBloques,
                                  String nuevoNombre, String usuario, ModoUsuario modo) {
        if (tipo != TipoProceso.ES_ARCHIVO) {
            throw new IllegalStateException("Solo los procesos de E/S de archivo trabajan sobre el disco");
        }
        if (operacion == null || !operacion.esDeArchivo()) {
            throw new IllegalArgumentException("La operacion debe ser crear, leer, actualizar o eliminar");
        }
        if (ruta == null || !ruta.startsWith("/")) {
            throw new IllegalArgumentException("La ruta debe empezar con /");
        }
        if (tamanoBloques < 0) {
            throw new IllegalArgumentException("El tamano del archivo no puede ser negativo");
        }
        if (usuario == null || usuario.trim().isEmpty() || modo == null) {
            throw new IllegalArgumentException("Falta el usuario o el modo");
        }
        this.operacionES = operacion;
        this.rutaArchivo = ruta;
        this.tamanoArchivo = tamanoBloques;
        this.nuevoNombre = nuevoNombre;
        this.usuario = usuario.trim();
        this.modoUsuario = modo;
    }

    public boolean puedeTerminar() {
        if (!tipo.usaBuffer()) {
            return haTerminadoTrabajo();
        }
        boolean puntoSeguro = tipo == TipoProceso.PRODUCTOR
                ? fase == FaseProductorConsumidor.PRODUCIR && contadorFase == 0
                : fase == FaseProductorConsumidor.ACCESO_RED;
        return puntoSeguro && !tieneMutex && !reservoHueco
                && (elementosHechos >= elementosRequeridos || haTerminadoTrabajo());
    }

    public boolean esLocalA(int computador) {
        return buffer == null || buffer.getAnfitrion() == computador;
    }

    public void reiniciarCiclosEnCpu() { ciclosEnCpu = 0; }
    public void sumarElemento() { elementosHechos++; }
    public void sumarCiclosEnSemaforos(long c) { ciclosEnSemaforos += c; }

    public int getBloqueSwap() { return bloqueSwap; }
    public void setBloqueSwap(int bloqueSwap) { this.bloqueSwap = bloqueSwap; }
    public TransitoSwap getTransitoSwap() { return transitoSwap; }
    public void setTransitoSwap(TransitoSwap t) { transitoSwap = t == null ? TransitoSwap.NINGUNO : t; }

    /** Al crear: permiso inicial. Al actualizar: permiso nuevo. null si no aplica. */
    public Permiso getNuevoPermiso() { return nuevoPermiso; }
    public void setNuevoPermiso(Permiso p) { nuevoPermiso = p; }

    /** Dueno del archivo o directorio que se crea (solo lo elige el administrador). */
    public String getDuenoNuevo() { return duenoNuevo; }
    public void setDuenoNuevo(String d) { duenoNuevo = d == null || d.trim().isEmpty() ? null : d.trim(); }

    public int getMar() { return mar; }
    public int getCiclosEnCpu() { return ciclosEnCpu; }
    public int getEsCada() { return esCada; }
    public int getDuracionES() { return duracionES; }
    public int getDesdeUltimaES() { return desdeUltimaES; }
    public void setDesdeUltimaES(int v) { desdeUltimaES = v; }
    public BufferAcotado getBuffer() { return buffer; }
    public int getCadaCuantosCiclos() { return cadaCuantosCiclos; }
    public int getElementosRequeridos() { return elementosRequeridos; }
    public int getElementosHechos() { return elementosHechos; }
    public FaseProductorConsumidor getFase() { return fase; }
    public void setFase(FaseProductorConsumidor f) { fase = f; }
    public int getContadorFase() { return contadorFase; }
    public void setContadorFase(int c) { contadorFase = c; }
    public boolean tieneMutex() { return tieneMutex; }
    public void setTieneMutex(boolean b) { tieneMutex = b; }
    public boolean reservoHueco() { return reservoHueco; }
    public void setReservoHueco(boolean b) { reservoHueco = b; }
    public long getBloqueadoDesde() { return bloqueadoDesde; }
    public void setBloqueadoDesde(long c) { bloqueadoDesde = c; }
    public long getCiclosEnSemaforos() { return ciclosEnSemaforos; }

    public TipoOperacionES getOperacionES() { return operacionES; }
    public String getRutaArchivo() { return rutaArchivo; }
    public int getTamanoArchivo() { return tamanoArchivo; }
    public String getNuevoNombre() { return nuevoNombre; }
    public String getUsuario() { return usuario; }
    public ModoUsuario getModoUsuario() { return modoUsuario; }
    public boolean solicitudHecha() { return solicitudHecha; }
    public void marcarSolicitudHecha() { solicitudHecha = true; }

    public String getEtiqueta() { return "P" + id + "-C" + idComputador; }

    public long getId() { return id; }
    public String getNombre() { return nombre; }
    public int getIdComputador() { return idComputador; }
    public TipoProceso getTipo() { return tipo; }
    public int getPrioridad() { return prioridad; }
    public int getMemoria() { return memoria; }
    public int getInstruccionesTotales() { return instruccionesTotales; }
    public int getDeadlineInicial() { return deadlineInicial; }
    public long getCicloCreacion() { return cicloCreacion; }
    public EstadoProceso getEstado() { return estado; }
    public MotivoBloqueo getMotivoBloqueo() { return motivoBloqueo; }
    public int getContadorPrograma() { return contadorPrograma; }
    public int getTiempoRestante() { return tiempoRestante; }
    public int getDeadlineRestante() { return deadlineRestante; }
    public boolean isTerminadoPorDeadline() { return terminadoPorDeadline; }
    public long getCicloPrimeraEjecucion() { return cicloPrimeraEjecucion; }
    public long getCicloFinalizacion() { return cicloFinalizacion; }
    public long getCiclosEnNuevo() { return ciclosEnNuevo; }
    public long getCiclosEnListo() { return ciclosEnListo; }
    public long getCiclosEnEjecucion() { return ciclosEnEjecucion; }
    public long getCiclosBloqueado() { return ciclosBloqueado; }
    public long getCiclosEnListoSuspendido() { return ciclosEnListoSuspendido; }
    public long getCiclosEnBloqueadoSuspendido() { return ciclosEnBloqueadoSuspendido; }

    @Override
    public String toString() {
        return "P" + id + "[" + nombre + "]";
    }
}
