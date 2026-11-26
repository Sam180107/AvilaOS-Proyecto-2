package unimet.avilaos.so;

import unimet.avilaos.estructuras.Cola;
import unimet.avilaos.estructuras.ListaEnlazada;
import unimet.avilaos.estructuras.ListaOrdenada;
import unimet.avilaos.estructuras.Recorrido;
import unimet.avilaos.hardware.CPU;
import unimet.avilaos.hardware.ComponenteDeReloj;
import unimet.avilaos.hardware.DispositivoES;
import unimet.avilaos.hardware.ManejadorInterrupciones;
import unimet.avilaos.hardware.MemoriaPrincipal;
import unimet.avilaos.hardware.ModoEjecucion;
import unimet.avilaos.hardware.disco.Disco;
import unimet.avilaos.hardware.disco.SolicitudES;
import unimet.avilaos.hardware.disco.TipoOperacionES;
import unimet.avilaos.metricas.Estadisticas;
import unimet.avilaos.sincronizacion.BufferAcotado;
import unimet.avilaos.sincronizacion.Mutex;
import unimet.avilaos.sincronizacion.SemaforoSO;
import unimet.avilaos.so.archivos.OperacionRechazada;
import unimet.avilaos.so.archivos.SistemaArchivos;
import unimet.avilaos.so.disco.OyenteDisco;
import unimet.avilaos.so.disco.PlanificadorDisco;
import unimet.avilaos.so.planificacion.FabricaPoliticas;
import unimet.avilaos.so.planificacion.PoliticaPlanificacion;
import unimet.avilaos.so.planificacion.PoliticaRoundRobin;
import unimet.avilaos.so.planificacion.TipoPolitica;
import unimet.avilaos.so.proceso.EstadoProceso;
import unimet.avilaos.so.proceso.FaseProductorConsumidor;
import unimet.avilaos.so.proceso.MotivoBloqueo;
import unimet.avilaos.so.proceso.PCB;
import unimet.avilaos.so.proceso.TipoProceso;
import unimet.avilaos.so.proceso.TransitoSwap;

/**
 * Sistema operativo de un computador: admision (largo plazo), suspension
 * (mediano plazo con 7 estados), planificacion (corto plazo) y E/S de disco.
 * Suspender y reactivar son solicitudes de E/S que pasan por la cola del disco.
 */
public class SistemaOperativo implements ComponenteDeReloj, ManejadorInterrupciones, OyenteDisco {

    private static final class Aviso {
        final PCB pcb;
        final String motivo;

        Aviso(PCB pcb, String motivo) {
            this.pcb = pcb;
            this.motivo = motivo;
        }
    }

    private Cola<PCB> nuevos = new Cola<>();
    private ListaOrdenada<PCB> listos;
    private final Cola<PCB> bloqueados = new Cola<>();
    private ListaOrdenada<PCB> listosSuspendidos;
    private final Cola<PCB> bloqueadosSuspendidos = new Cola<>();
    private final Cola<PCB> terminados = new Cola<>();
    private final MemoriaPrincipal ram;
    private final CPU cpu;
    private PoliticaPlanificacion politica;

    private final int computador;
    private final DispositivoES es;
    private final ParametrosSimulacion parametros;
    private final RegistroEventos log;
    private final Estadisticas estadisticas = new Estadisticas();
    private final int[] ventanaUso = new int[20];

    private final Mutex candado = new Mutex();
    private final Mutex candadoBuzon = new Mutex();
    private Cola<Aviso> buzon = new Cola<>();
    private Cola<SolicitudES> swapTerminados = new Cola<>();

    private PCB avisadoSinRam;
    private PCB beneficiario;
    private long suspensiones;
    private long reactivaciones;

    private MotivoBloqueo bloqueoPendiente = MotivoBloqueo.NINGUNO;
    private int ciclosBloqueoPendiente;
    private long ciclo;

    private volatile PlanificadorDisco planificadorDisco;
    private volatile SistemaArchivos archivos;
    private SolicitudES solicitudPendiente;

    public SistemaOperativo(int computador, MemoriaPrincipal ram, CPU cpu, PoliticaPlanificacion politica,
                            ParametrosSimulacion parametros, RegistroEventos log) {
        this.computador = computador;
        this.ram = ram;
        this.cpu = cpu;
        this.politica = politica;
        this.listos = new ListaOrdenada<>(politica.getComparador());
        this.listosSuspendidos = new ListaOrdenada<>(politica.getComparador());
        this.parametros = parametros;
        this.log = log;
        this.es = new DispositivoES(this);
    }

    // =================================================================
    // Llamadas desde la GUI o el cluster (otros hilos)
    // =================================================================

    public void cambiarPolitica(PoliticaPlanificacion nuevaPolitica) {
        candado.cerrar();
        try {
            this.politica = nuevaPolitica;
            this.listos.cambiarComparador(nuevaPolitica.getComparador());
            this.listosSuspendidos.cambiarComparador(nuevaPolitica.getComparador());
            log.registrar("Computador " + computador + " cambia su politica a " + nuevaPolitica.getNombre());
        } finally {
            candado.abrir();
        }
    }

    public void cambiarPolitica(TipoPolitica tipo) {
        if (getTipoPolitica() != tipo) {
            cambiarPolitica(FabricaPoliticas.crear(tipo, parametros.getQuantum()));
        }
    }

    public TipoPolitica getTipoPolitica() {
        candado.cerrar();
        try {
            return politica.getTipo();
        } finally {
            candado.abrir();
        }
    }

    public void conectarDisco(PlanificadorDisco planificador, SistemaArchivos archivos) {
        this.planificadorDisco = planificador;
        this.archivos = archivos;
        planificador.setOyente(this);
    }

    @Override
    public void solicitudCompletada(SolicitudES solicitud, long ciclo) {
        if (!solicitud.getOperacion().esDeArchivo()) {
            // suspender o reactivar: lo termina el SO en su proximo ciclo
            candadoBuzon.cerrar();
            try {
                swapTerminados.encolar(solicitud);
            } finally {
                candadoBuzon.abrir();
            }
            return;
        }
        SistemaArchivos fs = archivos;
        if (fs != null && solicitud.getOperacion().esDeArchivo()) {
            String efecto = fs.aplicar(solicitud, ciclo);
            if (!efecto.isEmpty()) {
                log.registrar(efecto);
            }
        }
        if (solicitud.getPcb() != null) {
            pedirDespertar(solicitud.getPcb(), "fin de E/S de disco");
        }
    }

    public void admitirProceso(PCB pcb) {
        candado.cerrar();
        try {
            nuevos.encolar(pcb);
            log.registrar("Computador " + computador + ": llega " + pcb.getEtiqueta()
                    + " (" + pcb.getNombre() + ", " + pcb.getTipo().getEtiqueta() + ")");
            gestionarMemoria(ciclo);
        } finally {
            candado.abrir();
        }
    }

    public boolean reservarBuffer(BufferAcotado b) {
        candado.cerrar();
        try {
            if (!ram.hayEspacio(b.getMemoria())) {
                return false;
            }
            ram.asignar(b.getMemoria());
            return true;
        } finally {
            candado.abrir();
        }
    }

    public void pedirDespertar(PCB pcb, String motivo) {
        candadoBuzon.cerrar();
        try {
            buzon.encolar(new Aviso(pcb, motivo));
        } finally {
            candadoBuzon.abrir();
        }
    }

    @Override
    public void interrumpir(PCB pcb, String motivo) {
        pedirDespertar(pcb, motivo);
    }

    public int getCarga() {
        candado.cerrar();
        try {
            return nuevos.tamano() + listos.tamano() + bloqueados.tamano()
                    + listosSuspendidos.tamano() + bloqueadosSuspendidos.tamano()
                    + (cpu.getActual() != null ? 1 : 0);
        } finally {
            candado.abrir();
        }
    }

    public int getRamLibre() {
        candado.cerrar();
        try {
            return ram.getLibre();
        } finally {
            candado.abrir();
        }
    }

    // =================================================================
    // Ciclo de reloj
    // =================================================================

    @Override
    public void alTick(long ciclo) {
        candado.cerrar();
        try {
            this.ciclo = ciclo;
            bloqueoPendiente = MotivoBloqueo.NINGUNO;
            solicitudPendiente = null;
            actualizarQuantum();
            atenderBuzon();
            atenderSwapTerminados(ciclo);
            revisarDeadlines(ciclo);
            gestionarMemoria(ciclo);
            planificar(ciclo);
        } finally {
            candado.abrir();
        }
    }

    public void despuesDeEjecutar() {
        candado.cerrar();
        try {
            PCB activo = cpu.getActual();
            if (activo != null && bloqueoPendiente != MotivoBloqueo.NINGUNO) {
                bloquear(activo, bloqueoPendiente, ciclosBloqueoPendiente);
            } else if (activo != null && !cpu.esDespacho() && activo.puedeTerminar()) {
                terminar(activo, false);
            }
            actualizarCiclos();
            medirUso();
        } finally {
            candado.abrir();
        }
    }

    private void actualizarQuantum() {
        if (politica instanceof PoliticaRoundRobin rr && rr.getQuantum() != parametros.getQuantum()) {
            rr.setQuantum(parametros.getQuantum());
        }
    }

    private void atenderBuzon() {
        Cola<Aviso> avisos;
        candadoBuzon.cerrar();
        try {
            avisos = buzon;
            buzon = new Cola<>();
        } finally {
            candadoBuzon.abrir();
        }
        boolean huboListos = false;
        boolean huboSuspendidos = false;
        while (!avisos.estaVacia()) {
            Aviso a = avisos.desencolar();
            PCB p = a.pcb;
            if (p.getEstado() == EstadoProceso.BLOQUEADO) {
                MotivoBloqueo m = p.getMotivoBloqueo();
                if (m == MotivoBloqueo.SEM_VACIOS || m == MotivoBloqueo.SEM_LLENOS || m == MotivoBloqueo.SEM_MUTEX) {
                    p.sumarCiclosEnSemaforos(ciclo - p.getBloqueadoDesde());
                }
                bloqueados.eliminar(p);
                p.cambiarEstado(EstadoProceso.LISTO, ciclo);
                listos.insertar(p);
                huboListos = true;
                log.registrar("Computador " + computador + ": " + p.getEtiqueta() + " se desbloquea (" + a.motivo + ")");
            } else if (p.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO) {
                MotivoBloqueo m = p.getMotivoBloqueo();
                if (m == MotivoBloqueo.SEM_VACIOS || m == MotivoBloqueo.SEM_LLENOS || m == MotivoBloqueo.SEM_MUTEX) {
                    p.sumarCiclosEnSemaforos(ciclo - p.getBloqueadoDesde());
                }
                bloqueadosSuspendidos.eliminar(p);
                p.cambiarEstado(EstadoProceso.LISTO_SUSPENDIDO, ciclo);
                listosSuspendidos.insertar(p);
                huboSuspendidos = true;
                log.registrar("Computador " + computador + ": " + p.getEtiqueta()
                        + " pasa a Listo/Suspendido (" + a.motivo + ")");
            }
        }
        if (huboListos) {
            listos.reordenar();
        }
        if (huboSuspendidos) {
            listosSuspendidos.reordenar();
        }
    }

    private void revisarDeadlines(long ciclo) {
        ListaEnlazada<PCB> vencidos = new ListaEnlazada<>();
        revisarDeadlineEn(nuevos.recorrido(), vencidos);
        revisarDeadlineEn(listos.recorrido(), vencidos);
        revisarDeadlineEn(bloqueados.recorrido(), vencidos);
        revisarDeadlineEn(listosSuspendidos.recorrido(), vencidos);
        revisarDeadlineEn(bloqueadosSuspendidos.recorrido(), vencidos);
        PCB activo = cpu.getActual();
        if (activo != null && activo.descontarDeadline()) {
            vencidos.agregarAlFinal(activo);
        }
        Recorrido<PCB> r = vencidos.recorrido();
        while (r.haySiguiente()) {
            terminar(r.siguiente(), true);
        }
        listos.reordenar();
        listosSuspendidos.reordenar();
    }

    private static void revisarDeadlineEn(Recorrido<PCB> r, ListaEnlazada<PCB> vencidos) {
        while (r.haySiguiente()) {
            PCB p = r.siguiente();
            if (p.descontarDeadline()) {
                vencidos.agregarAlFinal(p);
            }
        }
    }

    /**
     * Termina lo que el disco ya hizo con el area de intercambio. Una
     * suspension libera la RAM cuando el proceso quedo escrito en el swap;
     * una reactivacion devuelve el proceso a Listo cuando ya se leyo.
     */
    private void atenderSwapTerminados(long ciclo) {
        Cola<SolicitudES> hechas;
        candadoBuzon.cerrar();
        try {
            hechas = swapTerminados;
            swapTerminados = new Cola<>();
        } finally {
            candadoBuzon.abrir();
        }
        while (!hechas.estaVacia()) {
            SolicitudES s = hechas.desencolar();
            PCB p = s.getPcb();
            if (p == null || p.getEstado() == EstadoProceso.TERMINADO) {
                continue; // murio mientras el disco lo atendia
            }
            if (s.getOperacion() == TipoOperacionES.SUSPENDER && p.getTransitoSwap() == TransitoSwap.SALIENDO) {
                p.setTransitoSwap(TransitoSwap.NINGUNO);
                ram.liberar(p.getMemoria());
                suspensiones++;
                log.registrar("Computador " + computador + ": " + p.getEtiqueta()
                        + " quedo escrito en el area de intercambio (" + s.cantidadBloques()
                        + " bloques) y libera " + p.getMemoria() + " de RAM (libre: " + ram.getLibre() + ")");
            } else if (s.getOperacion() == TipoOperacionES.REACTIVAR
                    && p.getTransitoSwap() == TransitoSwap.ENTRANDO) {
                p.setTransitoSwap(TransitoSwap.NINGUNO);
                if (p.getBloqueSwap() >= 0) {
                    archivos.getDisco().liberarCadena(p.getBloqueSwap());
                    p.setBloqueSwap(-1);
                }
                reactivaciones++;
                if (p.getEstado() == EstadoProceso.LISTO_SUSPENDIDO) {
                    listosSuspendidos.eliminar(p);
                    p.cambiarEstado(EstadoProceso.LISTO, ciclo);
                    listos.insertar(p);
                    listos.reordenar();
                    log.registrar("Computador " + computador + ": " + p.getEtiqueta()
                            + " vuelve a la RAM desde el area de intercambio -> Listo");
                } else if (p.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO) {
                    bloqueadosSuspendidos.eliminar(p);
                    p.cambiarEstado(EstadoProceso.BLOQUEADO, ciclo);
                    bloqueados.encolar(p);
                    log.registrar("Computador " + computador + ": " + p.getEtiqueta()
                            + " vuelve a la RAM desde el area de intercambio -> Bloqueado");
                }
            }
        }
    }

    /**
     * Planificadores de largo y mediano plazo.
     *
     * Quien espera RAM, en orden: los Listo/Suspendido (ya avanzaron y solo
     * les falta volver) y despues los Nuevos de la cola de largo plazo, en
     * orden de llegada. Si el primero de esa fila no cabe, el mediano plazo
     * suspende procesos Bloqueados para hacerle espacio, y la RAM que se
     * libera queda apartada para el (el "beneficiario") hasta que entre.
     */
    private void gestionarMemoria(long ciclo) {
        SistemaArchivos fs = archivos;
        Disco disco = fs != null ? fs.getDisco() : null;
        boolean hayMedianoPlazo = disco != null && planificadorDisco != null && disco.getTamanoSwap() > 0;
        if (beneficiario != null && !esperaRam(beneficiario)) {
            beneficiario = null;
        }
        // 1) el beneficiario entra en cuanto quepa
        int presupuesto = ram.getLibre();
        if (beneficiario != null) {
            if (ram.hayEspacio(beneficiario.getMemoria())) {
                darRam(beneficiario, disco, ciclo);
                beneficiario = null;
                presupuesto = ram.getLibre();
            } else {
                // lo que esta saliendo al swap mas lo libre tiene que alcanzarle
                int sobra = ram.getLibre() + ramSaliendo() - beneficiario.getMemoria();
                presupuesto = Math.max(0, Math.min(ram.getLibre(), sobra));
            }
        }
        // 2) los demas, con la RAM que no esta apartada
        if (hayMedianoPlazo) {
            ListaEnlazada<PCB> entran = new ListaEnlazada<>();
            Recorrido<PCB> r = listosSuspendidos.recorrido();
            while (r.haySiguiente()) {
                PCB p = r.siguiente();
                if (p != beneficiario && p.getTransitoSwap() == TransitoSwap.NINGUNO
                        && p.getMemoria() <= presupuesto) {
                    entran.agregarAlFinal(p);
                    presupuesto -= p.getMemoria();
                }
            }
            Recorrido<PCB> e = entran.recorrido();
            while (e.haySiguiente()) {
                darRam(e.siguiente(), disco, ciclo);
            }
        }
        while (!nuevos.estaVacia() && nuevos.verFrente() != beneficiario
                && nuevos.verFrente().getMemoria() <= presupuesto) {
            PCB p = nuevos.verFrente();
            presupuesto -= p.getMemoria();
            darRam(p, disco, ciclo);
        }
        avisarSinRam();
        // 3) si nadie tiene espacio apartado, se busca a quien hacerle espacio
        if (hayMedianoPlazo && beneficiario == null) {
            PCB primero = primeroEnEsperarRam();
            if (primero != null && !ram.hayEspacio(primero.getMemoria()) && suspenderPara(primero, disco, ciclo)) {
                beneficiario = primero;
            }
        }
    }

    /** Sigue esperando RAM: Nuevo sin admitir, o Listo/Suspendido sin reactivacion en curso. */
    private static boolean esperaRam(PCB p) {
        return p.getEstado() == EstadoProceso.NUEVO
                || (p.getEstado() == EstadoProceso.LISTO_SUSPENDIDO && p.getTransitoSwap() == TransitoSwap.NINGUNO);
    }

    private PCB primeroEnEsperarRam() {
        Recorrido<PCB> r = listosSuspendidos.recorrido();
        while (r.haySiguiente()) {
            PCB p = r.siguiente();
            if (p.getTransitoSwap() == TransitoSwap.NINGUNO) {
                return p;
            }
        }
        return nuevos.estaVacia() ? null : nuevos.verFrente();
    }

    /** Admite a un Nuevo (largo plazo) o empieza a reactivar a un Listo/Suspendido (mediano plazo). */
    private void darRam(PCB p, Disco disco, long ciclo) {
        if (p.getEstado() == EstadoProceso.NUEVO) {
            nuevos.eliminar(p);
            ram.asignar(p.getMemoria());
            p.cambiarEstado(EstadoProceso.LISTO, ciclo);
            listos.insertar(p);
            log.registrar("Computador " + computador + " admite " + p.getEtiqueta()
                    + " (RAM libre: " + ram.getLibre() + ")");
            return;
        }
        ram.asignar(p.getMemoria()); // se aparta ya para que nadie mas la tome
        p.setTransitoSwap(TransitoSwap.ENTRANDO);
        log.registrar("Se reactiva al proceso " + p.getEtiqueta() + ": hay RAM para el y se pide al disco"
                + " leerlo del area de intercambio (bloque " + p.getBloqueSwap() + ")");
        planificadorDisco.encolar(new SolicitudES(TipoOperacionES.REACTIVAR, "swap/" + p.getEtiqueta(),
                disco.cadena(p.getBloqueSwap()), p.getId(), p.getEtiqueta(), p, ciclo));
    }

    private void avisarSinRam() {
        if (nuevos.estaVacia()) {
            return;
        }
        PCB p = nuevos.verFrente();
        if (p != avisadoSinRam) {
            avisadoSinRam = p;
            log.registrar("Computador " + computador + ": " + p.getEtiqueta() + " no cabe en la RAM (pide "
                    + p.getMemoria() + ", libre " + ram.getLibre() + ") y espera en la cola de largo plazo");
        }
    }

    /**
     * Suspende procesos Bloqueados para hacerle espacio a quien espera,
     * primero el que lleva mas tiempo bloqueado: es el que menos
     * probabilidad tiene de necesitar la CPU pronto. La suspension es una
     * escritura en el swap (solicitud SUSPENDER) que pasa por la cola del
     * disco; la RAM se libera cuando el disco termina.
     *
     * @return true si con lo que ya va saliendo, o con lo que se suspende ahora, va a caber.
     */
    private boolean suspenderPara(PCB esperando, Disco disco, long ciclo) {
        int necesita = esperando.getMemoria();
        int vaASobrar = ram.getLibre() + ramSaliendo();
        if (vaASobrar >= necesita) {
            return true;
        }
        // solo se suspende si con los bloqueados de ahora alcanza; si no, no vale la pena
        ListaEnlazada<PCB> victimas = new ListaEnlazada<>();
        int swapLibre = disco.swapLibre();
        Recorrido<PCB> r = bloqueados.recorrido();
        while (r.haySiguiente() && vaASobrar < necesita) {
            PCB b = r.siguiente();
            if (b.getMemoria() <= swapLibre) {
                victimas.agregarAlFinal(b);
                vaASobrar += b.getMemoria();
                swapLibre -= b.getMemoria();
            }
        }
        if (vaASobrar < necesita) {
            return false;
        }
        String para = esperando.getEstado() == EstadoProceso.NUEVO ? "admitir" : "reactivar";
        Recorrido<PCB> v = victimas.recorrido();
        while (v.haySiguiente()) {
            PCB b = v.siguiente();
            bloqueados.eliminar(b);
            int primero = disco.asignarSwap(b.getEtiqueta(), b.getMemoria());
            b.setBloqueSwap(primero);
            b.cambiarEstado(EstadoProceso.BLOQUEADO_SUSPENDIDO, ciclo);
            b.setTransitoSwap(TransitoSwap.SALIENDO);
            bloqueadosSuspendidos.encolar(b);
            log.registrar("Se suspende al proceso " + b.getEtiqueta() + " para " + para + " a "
                    + esperando.getEtiqueta() + " (lleva " + (ciclo - b.getBloqueadoDesde())
                    + " ciclos bloqueado; va al area de intercambio desde el bloque " + primero + ")");
            planificadorDisco.encolar(new SolicitudES(TipoOperacionES.SUSPENDER, "swap/" + b.getEtiqueta(),
                    disco.cadena(primero), b.getId(), b.getEtiqueta(), b, ciclo));
        }
        return true;
    }

    /** RAM que van a soltar los procesos que el disco esta escribiendo en el swap. */
    private int ramSaliendo() {
        int total = 0;
        Recorrido<PCB> r = bloqueadosSuspendidos.recorrido();
        while (r.haySiguiente()) {
            PCB p = r.siguiente();
            if (p.getTransitoSwap() == TransitoSwap.SALIENDO) {
                total += p.getMemoria();
            }
        }
        Recorrido<PCB> l = listosSuspendidos.recorrido();
        while (l.haySiguiente()) {
            PCB p = l.siguiente();
            if (p.getTransitoSwap() == TransitoSwap.SALIENDO) {
                total += p.getMemoria();
            }
        }
        return total;
    }

    private void planificar(long ciclo) {
        PCB activo = cpu.getActual();
        if (activo != null && !listos.estaVacia()) {
            PCB candidato = listos.verPrimero();
            if (politica.debeExpropiar(activo, candidato, activo.getCiclosEnCpu())) {
                cpu.liberar();
                activo.cambiarEstado(EstadoProceso.LISTO, ciclo);
                listos.insertar(activo);
                log.registrar("Computador " + computador + " expulsa a " + activo.getEtiqueta()
                        + " (" + politica.getNombre() + ")");
                activo = null;
            }
        }
        if (activo == null) {
            if (listos.estaVacia()) {
                cpu.ociosa();
                return;
            }
            PCB nuevoActivo = listos.extraerPrimero();
            nuevoActivo.cambiarEstado(EstadoProceso.EJECUCION, ciclo);
            nuevoActivo.reiniciarCiclosEnCpu();
            cpu.cargar(nuevoActivo, ModoEjecucion.SISTEMA_OPERATIVO, "despacho", false);
            log.registrar("Computador " + computador + " selecciona el proceso " + nuevoActivo.getEtiqueta());
            return;
        }
        ejecutar(activo);
    }

    private void ejecutar(PCB p) {
        switch (p.getTipo()) {
            case CPU_BOUND -> usuario(p);
            case IO_BOUND -> {
                if (p.getEsCada() > 0 && p.getDesdeUltimaES() >= p.getEsCada()) {
                    p.setDesdeUltimaES(0);
                    sistema(p, "llamada de E/S");
                    bloqueoPendiente = MotivoBloqueo.ENTRADA_SALIDA;
                    ciclosBloqueoPendiente = p.getDuracionES();
                } else {
                    p.setDesdeUltimaES(p.getDesdeUltimaES() + 1);
                    usuario(p);
                }
            }
            case PRODUCTOR, CONSUMIDOR -> productorConsumidor(p);
            case ES_ARCHIVO -> entradaSalidaArchivo(p);
        }
    }

    private void entradaSalidaArchivo(PCB p) {
        if (p.solicitudHecha()) {
            usuario(p);
            return;
        }
        p.marcarSolicitudHecha();
        String que = p.getOperacionES().getClave() + " " + p.getRutaArchivo();
        sistema(p, que);
        SistemaArchivos fs = archivos;
        if (fs == null || planificadorDisco == null) {
            log.registrar("Computador " + computador + ": " + p.getEtiqueta() + " pide " + que
                    + " pero no hay disco conectado");
            return;
        }
        try {
            if (p.getOperacionES() == TipoOperacionES.LEER
                    && fs.intentarLeerDesdeCache(p.getRutaArchivo(), p.getUsuario(), p.getModoUsuario(), ciclo)) {
                log.registrar("Computador " + computador + ": Cache LRU HIT para " + p.getEtiqueta()
                        + " en " + p.getRutaArchivo() + " (servido desde RAM)");
                return;
            }
            solicitudPendiente = fs.prepararSolicitud(p.getOperacionES(), p.getRutaArchivo(),
                    p.getTamanoArchivo(), p.getNuevoNombre(), p.getUsuario(), p.getModoUsuario(),
                    p.getId(), p.getEtiqueta(), p, ciclo);
            bloqueoPendiente = MotivoBloqueo.ENTRADA_SALIDA;
        } catch (OperacionRechazada e) {
            log.registrar("Computador " + computador + ": se rechaza " + que + " de " + p.getEtiqueta()
                    + ". " + e.getMessage());
        }
    }

    private void productorConsumidor(PCB p) {
        BufferAcotado b = p.getBuffer();
        boolean productor = p.getTipo() == TipoProceso.PRODUCTOR;
        boolean remoto = !p.esLocalA(computador) && parametros.getLatenciaRed() > 0;

        if (p.getFase() == FaseProductorConsumidor.ACCESO_RED && !remoto) {
            p.setFase(productor ? FaseProductorConsumidor.ESPERAR_VACIOS : FaseProductorConsumidor.ESPERAR_LLENOS);
        }

        switch (p.getFase()) {
            case PRODUCIR, CONSUMIR -> {
                usuario(p);
                p.setContadorFase(p.getContadorFase() + 1);
                if (p.getContadorFase() >= p.getCadaCuantosCiclos()) {
                    p.setContadorFase(0);
                    p.setFase(FaseProductorConsumidor.ACCESO_RED);
                }
            }
            case ACCESO_RED -> {
                sistema(p, "acceso remoto a " + b.getNombre());
                bloqueoPendiente = MotivoBloqueo.ACCESO_REMOTO;
                ciclosBloqueoPendiente = parametros.getLatenciaRed();
                p.setFase(productor ? FaseProductorConsumidor.ESPERAR_VACIOS : FaseProductorConsumidor.ESPERAR_LLENOS);
                log.registrar("Proceso " + p.getEtiqueta() + " accede al buffer " + b.getNombre() + " de forma remota");
            }
            case ESPERAR_VACIOS -> semWait(p, b, b.getVacios(), MotivoBloqueo.SEM_VACIOS);
            case ESPERAR_LLENOS -> semWait(p, b, b.getLlenos(), MotivoBloqueo.SEM_LLENOS);
            case ESPERAR_MUTEX -> {
                semWait(p, b, b.getMutex(), MotivoBloqueo.SEM_MUTEX);
                p.setTieneMutex(true);
            }
            case INSERTAR -> {
                sistema(p, "insertar en " + b.getNombre());
                b.cerrar();
                try {
                    b.insertar(p);
                } finally {
                    b.abrir();
                }
                p.setReservoHueco(false);
                p.sumarElemento();
                p.setFase(FaseProductorConsumidor.SENALAR_MUTEX);
                log.registrar("Proceso " + p.getEtiqueta() + " produce un elemento en " + b.getNombre());
            }
            case EXTRAER -> {
                sistema(p, "extraer de " + b.getNombre());
                b.cerrar();
                try {
                    b.extraer();
                } finally {
                    b.abrir();
                }
                p.setReservoHueco(false);
                p.sumarElemento();
                p.setFase(FaseProductorConsumidor.SENALAR_MUTEX);
                log.registrar("Proceso " + p.getEtiqueta() + " consume un elemento de " + b.getNombre());
            }
            case SENALAR_MUTEX -> {
                semSignal(p, b, b.getMutex());
                p.setTieneMutex(false);
                p.setFase(productor ? FaseProductorConsumidor.SENALAR_LLENOS : FaseProductorConsumidor.SENALAR_VACIOS);
            }
            case SENALAR_LLENOS -> {
                semSignal(p, b, b.getLlenos());
                p.setFase(FaseProductorConsumidor.PRODUCIR);
            }
            case SENALAR_VACIOS -> {
                semSignal(p, b, b.getVacios());
                p.setFase(FaseProductorConsumidor.CONSUMIR);
            }
        }
    }

    private void semWait(PCB p, BufferAcotado b, SemaforoSO s, MotivoBloqueo motivo) {
        sistema(p, "semWait(" + s.getNombre() + ") " + b.getNombre());
        boolean seBloquea;
        b.cerrar();
        try {
            seBloquea = s.esperar(p);
        } finally {
            b.abrir();
        }
        boolean productor = p.getTipo() == TipoProceso.PRODUCTOR;
        if (motivo == MotivoBloqueo.SEM_MUTEX) {
            p.setFase(productor ? FaseProductorConsumidor.INSERTAR : FaseProductorConsumidor.EXTRAER);
        } else {
            p.setReservoHueco(true);
            p.setFase(FaseProductorConsumidor.ESPERAR_MUTEX);
        }
        if (seBloquea) {
            bloqueoPendiente = motivo;
            log.registrar("Proceso " + p.getEtiqueta() + " se bloquea en el semaforo '" + s.getNombre()
                    + "' del buffer " + b.getNombre());
        }
    }

    private void semSignal(PCB p, BufferAcotado b, SemaforoSO s) {
        sistema(p, "semSignal(" + s.getNombre() + ") " + b.getNombre());
        b.cerrar();
        try {
            b.senalar(s);
        } finally {
            b.abrir();
        }
    }

    private void usuario(PCB p) {
        cpu.cargar(p, ModoEjecucion.USUARIO, "instruccion @" + p.getContadorPrograma(), true);
    }

    private void sistema(PCB p, String que) {
        cpu.cargar(p, ModoEjecucion.SISTEMA_OPERATIVO, que, true);
    }

    private void bloquear(PCB p, MotivoBloqueo motivo, int ciclos) {
        cpu.liberar();
        p.bloquear(motivo, ciclo);
        p.setBloqueadoDesde(ciclo);
        bloqueados.encolar(p);
        if (motivo == MotivoBloqueo.ENTRADA_SALIDA && solicitudPendiente != null) {
            SolicitudES s = solicitudPendiente;
            solicitudPendiente = null;
            log.registrar("Computador " + computador + ": " + p.getEtiqueta() + " se bloquea por E/S de disco ("
                    + s.getOperacion().getEtiqueta() + " " + s.getRuta() + ")");
            planificadorDisco.encolar(s);
            return;
        }
        if (motivo == MotivoBloqueo.ENTRADA_SALIDA || motivo == MotivoBloqueo.ACCESO_REMOTO) {
            es.solicitar(p, ciclos, motivo == MotivoBloqueo.ENTRADA_SALIDA ? "fin de E/S" : "fin de latencia de red");
        }
        if (motivo == MotivoBloqueo.ENTRADA_SALIDA) {
            log.registrar("Computador " + computador + ": " + p.getEtiqueta() + " se bloquea por E/S (" + ciclos + " ciclos)");
        }
    }

    private void terminar(PCB p, boolean porDeadline) {
        boolean estabaSuspendido = p.estaSuspendido();
        // si el disco lo esta escribiendo o leyendo del swap, todavia ocupa (o tiene apartada) su RAM
        boolean teniaRam = (p.getEstado() != EstadoProceso.NUEVO && !estabaSuspendido)
                || p.getTransitoSwap() != TransitoSwap.NINGUNO;
        p.setTransitoSwap(TransitoSwap.NINGUNO);
        if (cpu.getActual() == p) {
            cpu.liberar();
        }
        nuevos.eliminar(p);
        listos.eliminar(p);
        bloqueados.eliminar(p);
        listosSuspendidos.eliminar(p);
        bloqueadosSuspendidos.eliminar(p);
        es.cancelar(p);
        if (planificadorDisco != null) {
            planificadorDisco.cancelarDe(p.getId());
        }
        if (p.getBuffer() != null) {
            liberarBuffer(p);
        }
        if (teniaRam) {
            ram.liberar(p.getMemoria());
        }
        if (p.getBloqueSwap() >= 0 && archivos != null) {
            archivos.getDisco().liberarCadena(p.getBloqueSwap());
            p.setBloqueSwap(-1);
        }
        if (porDeadline) {
            p.terminarPorDeadline(ciclo);
        } else {
            p.cambiarEstado(EstadoProceso.TERMINADO, ciclo);
        }
        terminados.encolar(p);
        estadisticas.registrarFin(p, ciclo);
        log.registrar("Computador " + computador + ": " + p.getEtiqueta()
                + (porDeadline ? " TERMINADO por deadline vencido" : " termina"));
        gestionarMemoria(ciclo);
    }

    private void liberarBuffer(PCB p) {
        BufferAcotado b = p.getBuffer();
        boolean productor = p.getTipo() == TipoProceso.PRODUCTOR;
        b.cerrar();
        try {
            boolean esperabaHueco = productor ? b.getVacios().quitar(p) : b.getLlenos().quitar(p);
            boolean esperabaMutex = b.getMutex().quitar(p);
            if (p.tieneMutex() && !esperabaMutex) {
                b.senalar(b.getMutex());
            }
            if (p.reservoHueco() && !esperabaHueco) {
                b.senalar(productor ? b.getVacios() : b.getLlenos());
            }
            FaseProductorConsumidor f = p.getFase();
            if (f == FaseProductorConsumidor.SENALAR_MUTEX || f == FaseProductorConsumidor.SENALAR_LLENOS
                    || f == FaseProductorConsumidor.SENALAR_VACIOS) {
                b.senalar(productor ? b.getLlenos() : b.getVacios());
            }
        } finally {
            b.abrir();
        }
        p.setTieneMutex(false);
        p.setReservoHueco(false);
    }

    private void actualizarCiclos() {
        actualizarCiclosEn(nuevos.recorrido());
        actualizarCiclosEn(listos.recorrido());
        actualizarCiclosEn(bloqueados.recorrido());
        actualizarCiclosEn(listosSuspendidos.recorrido());
        actualizarCiclosEn(bloqueadosSuspendidos.recorrido());
        PCB activo = cpu.getActual();
        if (activo != null) {
            activo.registrarCicloTranscurrido();
        }
    }

    private static void actualizarCiclosEn(Recorrido<PCB> r) {
        while (r.haySiguiente()) {
            r.siguiente().registrarCicloTranscurrido();
        }
    }

    private void medirUso() {
        ventanaUso[(int) (ciclo % ventanaUso.length)] = cpu.getModo() == ModoEjecucion.OCIOSO ? 0 : 1;
        int n = (int) Math.min(ciclo, ventanaUso.length);
        int suma = 0;
        for (int i = 0; i < n; i++) {
            suma += ventanaUso[i];
        }
        estadisticas.getUtilizacion().agregar(n == 0 ? 0 : (double) suma / n);
    }

    public FotoSistema foto() {
        candado.cerrar();
        try {
            FotoSistema f = new FotoSistema();
            f.computador = computador;
            f.politica = politica.getTipo();
            f.modo = cpu.getModo();
            f.instruccion = cpu.getInstruccion();
            PCB p = cpu.getActual();
            f.enCpu = p == null ? null : FotoSistema.fila(p);
            f.pcEnCpu = p == null ? -1 : p.getContadorPrograma();
            f.memUsada = ram.getUsada();
            f.memTotal = ram.getCapacidad();
            f.nuevos = filas(nuevos.recorrido(), nuevos.tamano());
            f.listos = filas(listos.recorrido(), listos.tamano());
            f.bloqueados = filas(bloqueados.recorrido(), bloqueados.tamano());
            f.listosSuspendidos = filas(listosSuspendidos.recorrido(), listosSuspendidos.tamano());
            f.bloqueadosSuspendidos = filas(bloqueadosSuspendidos.recorrido(), bloqueadosSuspendidos.tamano());
            f.terminados = filas(terminados.recorrido(), terminados.tamano());
            f.suspensiones = suspensiones;
            f.reactivaciones = reactivaciones;
            return f;
        } finally {
            candado.abrir();
        }
    }

    private static Object[][] filas(Recorrido<PCB> r, int n) {
        Object[][] out = new Object[n][];
        int i = 0;
        while (r.haySiguiente()) {
            out[i++] = FotoSistema.fila(r.siguiente());
        }
        return out;
    }

    /** Procesos que el disco termino de escribir en el area de intercambio. */
    public long getSuspensiones() {
        candado.cerrar();
        try {
            return suspensiones;
        } finally {
            candado.abrir();
        }
    }

    /** Procesos que el disco termino de leer del area de intercambio y volvieron a la RAM. */
    public long getReactivaciones() {
        candado.cerrar();
        try {
            return reactivaciones;
        } finally {
            candado.abrir();
        }
    }

    public DispositivoES getES() { return es; }
    public Estadisticas getEstadisticas() { return estadisticas; }
    public Cola<PCB> getNuevos() { return nuevos; }
    public ListaOrdenada<PCB> getListos() { return listos; }
    public Cola<PCB> getBloqueados() { return bloqueados; }
    public ListaOrdenada<PCB> getListosSuspendidos() { return listosSuspendidos; }
    public Cola<PCB> getBloqueadosSuspendidos() { return bloqueadosSuspendidos; }
    public Cola<PCB> getTerminados() { return terminados; }
}
