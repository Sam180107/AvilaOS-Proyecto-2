package unimet.avilaos.hardware.disco;

import unimet.avilaos.estructuras.ListaEnlazada;
import unimet.avilaos.estructuras.Recorrido;
import unimet.avilaos.hardware.ComponenteDeReloj;
import unimet.avilaos.sincronizacion.Mutex;

/**
 * Disco simulado (SD). Lleva el mapa de bloques con asignacion encadenada,
 * el area de intercambio y el cabezal. Es un componente mas del hardware:
 * en cada ciclo de reloj avanza exactamente un ciclo de trabajo.
 *
 * Los bloques 0..totalBloques-1 son de archivos. El area de intercambio va
 * despues, en totalBloques..totalBloques+swap-1.
 */
public class Disco implements ComponenteDeReloj {

    public static final int MIN_BLOQUES = 100;
    public static final int MAX_BLOQUES = 500;

    private final Mutex mutex = new Mutex();
    private final int totalBloques;
    private int tamanoSwap;
    private Bloque[] bloques;

    private int cabezal;
    private DireccionCabezal direccion = DireccionCabezal.ASCENDENTE;
    private int posicionesPorCiclo;

    private ControladorDisco controlador;
    private SolicitudES actual;
    private DecisionDisco viaje;

    private long desplazamientoTotal = 0;
    private final ListaEnlazada<long[]> historial = new ListaEnlazada<>();

    public Disco(int totalBloques, int tamanoSwap, int cabezalInicial, int posicionesPorCiclo) {
        if (totalBloques < MIN_BLOQUES || totalBloques > MAX_BLOQUES) {
            throw new IllegalArgumentException("El disco debe tener entre " + MIN_BLOQUES
                    + " y " + MAX_BLOQUES + " bloques (vino " + totalBloques + ")");
        }
        if (tamanoSwap < 0) {
            throw new IllegalArgumentException("El area de intercambio no puede ser negativa");
        }
        if (cabezalInicial < 0 || cabezalInicial >= totalBloques + tamanoSwap) {
            throw new IllegalArgumentException("Cabezal fuera del disco: " + cabezalInicial);
        }
        if (posicionesPorCiclo < 1) {
            throw new IllegalArgumentException("El cabezal debe moverse al menos 1 posicion por ciclo");
        }
        this.totalBloques = totalBloques;
        this.tamanoSwap = tamanoSwap;
        this.cabezal = cabezalInicial;
        this.posicionesPorCiclo = posicionesPorCiclo;
        this.bloques = new Bloque[totalBloques + tamanoSwap];
        for (int i = 0; i < bloques.length; i++) {
            bloques[i] = new Bloque(i, i >= totalBloques);
        }
    }

    public void setControlador(ControladorDisco controlador) {
        this.controlador = controlador;
    }

    // ---------- reloj ----------

    /** Un ciclo de trabajo: o mueve el cabezal o lee/escribe un bloque. */
    @Override
    public void alTick(long ciclo) {
        SolicitudES lista = null;
        mutex.cerrar();
        try {
            if (actual == null && viaje == null && controlador != null) {
                DecisionDisco d = controlador.siguiente(cabezal, direccion, bloques.length - 1, ciclo);
                if (d != null) {
                    if (d.esAtender()) {
                        actual = d.getSolicitud();
                    } else {
                        viaje = d;
                    }
                }
            }
            if (actual != null) {
                int objetivo = actual.bloqueActual();
                if (objetivo >= 0 && objetivo != cabezal) {
                    mover(objetivo);
                } else {
                    // el cabezal ya esta encima: leer o escribir cuesta un ciclo
                    actual.avanzar();
                    if (actual.terminada()) {
                        lista = actual;
                        actual = null;
                    }
                }
            } else if (viaje != null) {
                mover(viaje.getExtremo());
                if (cabezal == viaje.getExtremo()) {
                    if (viaje.haySalto()) {
                        cabezal = viaje.getSaltarA(); // el salto no cuenta como desplazamiento
                    }
                    direccion = viaje.getDireccionAlLlegar();
                    viaje = null;
                }
            }
            anotarHistorial(ciclo);
        } finally {
            mutex.abrir();
        }
        // fuera del mutex: el SO puede querer tocar el disco al despertar al proceso
        if (lista != null && controlador != null) {
            controlador.completada(lista, ciclo);
        }
    }

    private void mover(int objetivo) {
        int distancia = Math.abs(objetivo - cabezal);
        if (distancia == 0) {
            return;
        }
        int paso = Math.min(posicionesPorCiclo, distancia);
        if (objetivo > cabezal) {
            cabezal += paso;
            direccion = DireccionCabezal.ASCENDENTE;
        } else {
            cabezal -= paso;
            direccion = DireccionCabezal.DESCENDENTE;
        }
        desplazamientoTotal += paso;
    }

    private void anotarHistorial(long ciclo) {
        if (historial.estaVacia() || historial.obtenerUltimo()[1] != desplazamientoTotal) {
            historial.agregarAlFinal(new long[]{ciclo, desplazamientoTotal});
        }
    }

    // ---------- asignacion encadenada ----------

    /** Aparta bloques libres para un archivo y los encadena. Devuelve el primero. */
    public int asignarCadena(String ocupante, int cantidad) {
        mutex.cerrar();
        try {
            return asignar(ocupante, UsoBloque.ARCHIVO, 0, totalBloques, -1, cantidad);
        } finally {
            mutex.abrir();
        }
    }

    /** Igual que asignarCadena pero el primer bloque es fijo (lo usa la carga del JSON). */
    public int asignarCadenaDesde(String ocupante, int primerBloque, int cantidad) {
        mutex.cerrar();
        try {
            if (primerBloque < 0 || primerBloque >= totalBloques) {
                throw new IllegalArgumentException("Bloque fuera del area de archivos: " + primerBloque);
            }
            if (!bloques[primerBloque].estaLibre()) {
                throw new IllegalStateException("El bloque " + primerBloque + " ya esta ocupado por "
                        + bloques[primerBloque].getOcupante());
            }
            return asignar(ocupante, UsoBloque.ARCHIVO, 0, totalBloques, primerBloque, cantidad);
        } finally {
            mutex.abrir();
        }
    }

    /** Ocupa exactamente esos bloques, en ese orden (para restaurar un disco guardado). */
    public void asignarCadenaExacta(String ocupante, int[] cadena) {
        mutex.cerrar();
        try {
            if (cadena == null || cadena.length == 0) {
                throw new IllegalArgumentException("La cadena esta vacia");
            }
            for (int i = 0; i < cadena.length; i++) {
                int n = cadena[i];
                if (n < 0 || n >= totalBloques) {
                    throw new IllegalArgumentException("Bloque fuera del area de archivos: " + n);
                }
                if (!bloques[n].estaLibre()) {
                    throw new IllegalStateException("El bloque " + n + " ya esta ocupado");
                }
                for (int j = 0; j < i; j++) {
                    if (cadena[j] == n) {
                        throw new IllegalArgumentException("Bloque repetido en la cadena: " + n);
                    }
                }
            }
            enlazar(ocupante, UsoBloque.ARCHIVO, cadena);
        } finally {
            mutex.abrir();
        }
    }

    /** Aparta bloques del area de intercambio para un proceso suspendido. */
    public int asignarSwap(String ocupante, int cantidad) {
        mutex.cerrar();
        try {
            return asignar(ocupante, UsoBloque.INTERCAMBIO, totalBloques, bloques.length, -1, cantidad);
        } finally {
            mutex.abrir();
        }
    }

    private int asignar(String ocupante, UsoBloque uso, int desde, int hasta, int fijo, int cantidad) {
        if (cantidad < 1) {
            throw new IllegalArgumentException("Hay que pedir al menos 1 bloque");
        }
        if (contarLibres(desde, hasta) < cantidad) {
            throw new IllegalStateException("No hay bloques libres suficientes: se piden "
                    + cantidad + " y quedan " + contarLibres(desde, hasta));
        }
        int[] cadena = new int[cantidad];
        int n = 0;
        if (fijo >= 0) {
            cadena[n++] = fijo;
        }
        // el resto: los primeros libres despues del fijo, dando la vuelta
        int inicio = fijo >= 0 ? fijo + 1 : desde;
        int rango = hasta - desde;
        for (int k = 0; k < rango && n < cantidad; k++) {
            int i = desde + ((inicio - desde + k) % rango);
            if (i != fijo && bloques[i].estaLibre()) {
                cadena[n++] = i;
            }
        }
        enlazar(ocupante, uso, cadena);
        return cadena[0];
    }

    private void enlazar(String ocupante, UsoBloque uso, int[] cadena) {
        for (int i = 0; i < cadena.length; i++) {
            bloques[cadena[i]].ocupar(uso, ocupante);
            if (i > 0) {
                bloques[cadena[i - 1]].setSiguiente(cadena[i]);
            }
        }
    }

    /** Recorre la lista enlazada a partir del primer bloque. */
    public int[] cadena(int primerBloque) {
        mutex.cerrar();
        try {
            return cadenaSinMutex(primerBloque);
        } finally {
            mutex.abrir();
        }
    }

    private int[] cadenaSinMutex(int primerBloque) {
        if (primerBloque < 0 || primerBloque >= bloques.length || bloques[primerBloque].estaLibre()) {
            return new int[0];
        }
        int largo = 0;
        for (int i = primerBloque; i != Bloque.SIN_SIGUIENTE && largo <= bloques.length;
                i = bloques[i].getSiguiente()) {
            largo++;
        }
        int[] out = new int[largo];
        int i = primerBloque;
        for (int k = 0; k < largo; k++) {
            out[k] = i;
            i = bloques[i].getSiguiente();
        }
        return out;
    }

    /** Libera todos los bloques de la cadena. Devuelve cuantos libero. */
    public int liberarCadena(int primerBloque) {
        mutex.cerrar();
        try {
            int[] cadena = cadenaSinMutex(primerBloque);
            for (int n : cadena) {
                bloques[n].liberar();
            }
            return cadena.length;
        } finally {
            mutex.abrir();
        }
    }

    /** Cambia el nombre del ocupante en toda la cadena (al renombrar un archivo). */
    public void renombrarOcupante(int primerBloque, String nuevo) {
        mutex.cerrar();
        try {
            for (int n : cadenaSinMutex(primerBloque)) {
                bloques[n].setOcupante(nuevo);
            }
        } finally {
            mutex.abrir();
        }
    }

    private int contarLibres(int desde, int hasta) {
        int libres = 0;
        for (int i = desde; i < hasta; i++) {
            if (bloques[i].estaLibre()) {
                libres++;
            }
        }
        return libres;
    }

    // ---------- configuracion ----------

    /**
     * Cambia el tamano del area de intercambio. Solo se puede mientras
     * no haya procesos suspendidos guardados en ella.
     */
    public void redimensionarSwap(int nuevoTamano) {
        mutex.cerrar();
        try {
            if (nuevoTamano < 0) {
                throw new IllegalArgumentException("El area de intercambio no puede ser negativa");
            }
            if (contarLibres(totalBloques, bloques.length) != tamanoSwap) {
                throw new IllegalStateException("Hay procesos suspendidos en el area de intercambio");
            }
            Bloque[] nuevos = new Bloque[totalBloques + nuevoTamano];
            for (int i = 0; i < nuevos.length; i++) {
                nuevos[i] = i < totalBloques ? bloques[i] : new Bloque(i, true);
            }
            bloques = nuevos;
            tamanoSwap = nuevoTamano;
            if (cabezal >= bloques.length) {
                cabezal = bloques.length - 1;
            }
        } finally {
            mutex.abrir();
        }
    }

    public void setPosicionesPorCiclo(int posiciones) {
        if (posiciones < 1) {
            throw new IllegalArgumentException("El cabezal debe moverse al menos 1 posicion por ciclo");
        }
        mutex.cerrar();
        posicionesPorCiclo = posiciones;
        mutex.abrir();
    }

    // ---------- consultas (la GUI solo observa) ----------

    public int getTotalBloques() {
        return totalBloques;
    }

    public int getTamanoSwap() {
        mutex.cerrar();
        try {
            return tamanoSwap;
        } finally {
            mutex.abrir();
        }
    }

    public int getCabezal() {
        mutex.cerrar();
        try {
            return cabezal;
        } finally {
            mutex.abrir();
        }
    }

    public DireccionCabezal getDireccion() {
        mutex.cerrar();
        try {
            return direccion;
        } finally {
            mutex.abrir();
        }
    }

    public int getPosicionesPorCiclo() {
        return posicionesPorCiclo;
    }

    public int bloquesLibres() {
        mutex.cerrar();
        try {
            return contarLibres(0, totalBloques);
        } finally {
            mutex.abrir();
        }
    }

    public int swapLibre() {
        mutex.cerrar();
        try {
            return contarLibres(totalBloques, bloques.length);
        } finally {
            mutex.abrir();
        }
    }

    /** Solicitud que se esta atendiendo ahora, o null si el disco esta ocioso. */
    public SolicitudES getSolicitudActual() {
        mutex.cerrar();
        try {
            return actual;
        } finally {
            mutex.abrir();
        }
    }

    public long getDesplazamientoTotal() {
        mutex.cerrar();
        try {
            return desplazamientoTotal;
        } finally {
            mutex.abrir();
        }
    }

    /** Copia del mapa de bloques para pintarlo sin tocar el original. */
    public Bloque[] foto() {
        mutex.cerrar();
        try {
            Bloque[] copia = new Bloque[bloques.length];
            for (int i = 0; i < bloques.length; i++) {
                copia[i] = bloques[i].copia();
            }
            return copia;
        } finally {
            mutex.abrir();
        }
    }

    /** Puntos {ciclo, desplazamiento acumulado} para la grafica. */
    public ListaEnlazada<long[]> historialDesplazamiento() {
        mutex.cerrar();
        try {
            ListaEnlazada<long[]> copia = new ListaEnlazada<>();
            Recorrido<long[]> r = historial.recorrido();
            while (r.haySiguiente()) {
                copia.agregarAlFinal(r.siguiente().clone());
            }
            return copia;
        } finally {
            mutex.abrir();
        }
    }
}
