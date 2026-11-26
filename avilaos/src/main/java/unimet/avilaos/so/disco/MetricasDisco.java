package unimet.avilaos.so.disco;

import unimet.avilaos.sincronizacion.Mutex;

/** Metricas del disco: solicitudes atendidas por politica y espera en la cola de E/S. */
public class MetricasDisco {

    private final Mutex mutex = new Mutex();
    private final long[] atendidas = new long[TipoPoliticaDisco.values().length];
    private long totalAtendidas = 0;
    private long sumaEspera = 0;

    void registrar(TipoPoliticaDisco politica, long espera) {
        mutex.cerrar();
        try {
            atendidas[politica.ordinal()]++;
            totalAtendidas++;
            sumaEspera += Math.max(0, espera);
        } finally {
            mutex.abrir();
        }
    }

    public long atendidasCon(TipoPoliticaDisco politica) {
        mutex.cerrar();
        try {
            return atendidas[politica.ordinal()];
        } finally {
            mutex.abrir();
        }
    }

    public long getTotalAtendidas() {
        mutex.cerrar();
        try {
            return totalAtendidas;
        } finally {
            mutex.abrir();
        }
    }

    /** Ciclos promedio que una solicitud espera en la cola antes de ser atendida. */
    public double esperaPromedio() {
        mutex.cerrar();
        try {
            return totalAtendidas == 0 ? 0 : (double) sumaEspera / totalAtendidas;
        } finally {
            mutex.abrir();
        }
    }
}
