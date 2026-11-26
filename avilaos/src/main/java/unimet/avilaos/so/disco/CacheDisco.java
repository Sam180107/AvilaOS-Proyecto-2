package unimet.avilaos.so.disco;

import unimet.avilaos.estructuras.ListaEnlazada;
import unimet.avilaos.estructuras.Recorrido;
import unimet.avilaos.sincronizacion.Mutex;

/**
 * Cache LRU (Least Recently Used) de bloques de disco en memoria principal.
 * El inicio de la lista representa el bloque usado mas recientemente (MRU)
 * y el final el menos usado recientemente (LRU).
 */
public class CacheDisco {

    public static final class EntradaCache {
        private final int bloque;
        private String ruta;
        private long ultimoCiclo;
        private int accesos;

        EntradaCache(int bloque, String ruta, long ciclo) {
            this.bloque = bloque;
            this.ruta = ruta;
            this.ultimoCiclo = ciclo;
            this.accesos = 1;
        }

        public int getBloque() { return bloque; }
        public String getRuta() { return ruta; }
        public long getUltimoCiclo() { return ultimoCiclo; }
        public int getAccesos() { return accesos; }
    }

    private final Mutex mutex = new Mutex();
    private final int capacidad;
    private final ListaEnlazada<EntradaCache> entradas = new ListaEnlazada<>();
    private long hits = 0;
    private long misses = 0;

    public CacheDisco(int capacidad) {
        if (capacidad < 0) {
            throw new IllegalArgumentException("La capacidad de la cache no puede ser negativa");
        }
        this.capacidad = capacidad;
    }

    /**
     * Consulta si todos los bloques indicados estan en cache.
     * Si todos estan presentes registra un HIT (actualizando su orden LRU);
     * si falta al menos uno registra un MISS.
     */
    public boolean consultarTodos(int[] bloques, String ruta, long ciclo) {
        if (capacidad == 0 || bloques == null || bloques.length == 0) {
            return false;
        }
        mutex.cerrar();
        try {
            for (int b : bloques) {
                if (buscarEntrada(b) == null) {
                    misses++;
                    return false;
                }
            }
            for (int b : bloques) {
                EntradaCache e = buscarEntrada(b);
                entradas.eliminar(e);
                e.ruta = ruta;
                e.ultimoCiclo = ciclo;
                e.accesos++;
                entradas.agregarAlInicio(e);
            }
            hits++;
            return true;
        } finally {
            mutex.abrir();
        }
    }

    /** Carga o actualiza en cache los bloques leidos o escritos en disco. */
    public void cargarBloques(int[] bloques, String ruta, long ciclo) {
        if (capacidad == 0 || bloques == null) {
            return;
        }
        mutex.cerrar();
        try {
            for (int b : bloques) {
                if (b < 0) {
                    continue;
                }
                EntradaCache existente = buscarEntrada(b);
                if (existente != null) {
                    entradas.eliminar(existente);
                    existente.ruta = ruta;
                    existente.ultimoCiclo = ciclo;
                    existente.accesos++;
                    entradas.agregarAlInicio(existente);
                } else {
                    if (entradas.tamano() >= capacidad) {
                        entradas.eliminarUltimo();
                    }
                    entradas.agregarAlInicio(new EntradaCache(b, ruta, ciclo));
                }
            }
        } finally {
            mutex.abrir();
        }
    }

    /** Invalida los bloques asociados a una ruta o subarbol eliminado. */
    public void invalidarPrefijo(String ruta) {
        if (ruta == null) {
            return;
        }
        String prefijo = ruta.equals("/") ? "/" : ruta + "/";
        mutex.cerrar();
        try {
            ListaEnlazada<EntradaCache> quitar = new ListaEnlazada<>();
            Recorrido<EntradaCache> r = entradas.recorrido();
            while (r.haySiguiente()) {
                EntradaCache e = r.siguiente();
                if (e.ruta != null && (e.ruta.equals(ruta) || e.ruta.startsWith(prefijo))) {
                    quitar.agregarAlFinal(e);
                }
            }
            Recorrido<EntradaCache> q = quitar.recorrido();
            while (q.haySiguiente()) {
                entradas.eliminar(q.siguiente());
            }
        } finally {
            mutex.abrir();
        }
    }

    /** Actualiza la ruta registrada en cache tras un renombrado. */
    public void actualizarRuta(String rutaVieja, String rutaNueva) {
        if (rutaVieja == null || rutaNueva == null) {
            return;
        }
        String prefijoViejo = rutaVieja + "/";
        mutex.cerrar();
        try {
            Recorrido<EntradaCache> r = entradas.recorrido();
            while (r.haySiguiente()) {
                EntradaCache e = r.siguiente();
                if (e.ruta != null) {
                    if (e.ruta.equals(rutaVieja)) {
                        e.ruta = rutaNueva;
                    } else if (e.ruta.startsWith(prefijoViejo)) {
                        e.ruta = rutaNueva + e.ruta.substring(rutaVieja.length());
                    }
                }
            }
        } finally {
            mutex.abrir();
        }
    }

    private EntradaCache buscarEntrada(int bloque) {
        Recorrido<EntradaCache> r = entradas.recorrido();
        while (r.haySiguiente()) {
            EntradaCache e = r.siguiente();
            if (e.bloque == bloque) {
                return e;
            }
        }
        return null;
    }

    public int getCapacidad() { return capacidad; }

    public int getOcupados() {
        mutex.cerrar();
        try {
            return entradas.tamano();
        } finally {
            mutex.abrir();
        }
    }

    public long getHits() {
        mutex.cerrar();
        try {
            return hits;
        } finally {
            mutex.abrir();
        }
    }

    public long getMisses() {
        mutex.cerrar();
        try {
            return misses;
        } finally {
            mutex.abrir();
        }
    }

    public long getTotalConsultas() {
        mutex.cerrar();
        try {
            return hits + misses;
        } finally {
            mutex.abrir();
        }
    }

    public double getTasaAciertos() {
        mutex.cerrar();
        try {
            long total = hits + misses;
            return total == 0 ? 0.0 : (double) hits / total;
        } finally {
            mutex.abrir();
        }
    }

    /** Copia ordenada de MRU a LRU para mostrar en la GUI. */
    public Object[][] filas() {
        mutex.cerrar();
        try {
            Object[][] out = new Object[entradas.tamano()][];
            Recorrido<EntradaCache> r = entradas.recorrido();
            int i = 0;
            while (r.haySiguiente()) {
                EntradaCache e = r.siguiente();
                out[i++] = new Object[]{
                    i == 1 ? "1 (MRU)" : (i == entradas.tamano() ? i + " (LRU)" : String.valueOf(i)),
                    e.bloque,
                    e.ruta,
                    e.ultimoCiclo,
                    e.accesos
                };
            }
            return out;
        } finally {
            mutex.abrir();
        }
    }
}
