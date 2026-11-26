package unimet.avilaos.metricas;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import unimet.avilaos.cluster.Cluster;
import unimet.avilaos.hardware.Computador;
import unimet.avilaos.hardware.disco.Disco;
import unimet.avilaos.so.FotoSistema;
import unimet.avilaos.so.disco.CacheDisco;
import unimet.avilaos.so.disco.MetricasDisco;
import unimet.avilaos.so.disco.PlanificadorDisco;
import unimet.avilaos.so.disco.TipoPoliticaDisco;

/**
 * Exporta reportes completos de rendimiento de AvilaOS en formato CSV o TXT.
 */
public final class ExportadorReportes {

    private ExportadorReportes() {
    }

    public static void exportar(Cluster cluster, File destino) throws IOException {
        String nombre = destino.getName().toLowerCase();
        String contenido = nombre.endsWith(".csv") ? generarCsv(cluster) : generarTexto(cluster);
        Files.writeString(destino.toPath(), contenido, StandardCharsets.UTF_8);
    }

    public static String generarTexto(Cluster cluster) {
        StringBuilder sb = new StringBuilder();
        long ciclo = cluster.getReloj().getCiclo();
        Computador comp = cluster.getComputadores()[0];
        Estadisticas est = comp.getOs().getEstadisticas();
        Disco disco = cluster.getDisco();
        PlanificadorDisco plan = cluster.getPlanificadorDisco();
        CacheDisco cache = cluster.getSistemaArchivos() != null ? cluster.getSistemaArchivos().getCache() : null;

        sb.append("=================================================================\n");
        sb.append("              REPORTE DE SIMULACION - AVILAOS PROYECTO 2         \n");
        sb.append("=================================================================\n");
        sb.append("Ciclo actual: ").append(ciclo).append('\n');
        sb.append("Politica CPU: ").append(comp.getOs().getTipoPolitica().getEtiqueta()).append('\n');
        if (plan != null) {
            sb.append("Politica Disco activa: ").append(plan.getTipoPolitica().getEtiqueta()).append('\n');
        }
        sb.append('\n');

        sb.append("--- 1. METRICAS DE CPU Y PROCESOS ---\n");
        sb.append(String.format("Throughput global: %.4f proc/ciclo%n", cluster.throughputGlobal()));
        sb.append(String.format("Utilizacion de CPU: %.2f %%%n", cluster.utilizacionGlobal() * 100.0));
        sb.append(String.format("Tiempo de respuesta promedio: %.2f ciclos%n", est.getRespuestaPromedio()));
        sb.append(String.format("Cumplimiento de deadlines: %.2f %%%n", est.getCumplimientoDeadlines() * 100.0));
        sb.append(String.format("Indice de equidad de Jain: %.4f%n", est.getEquidad()));
        sb.append("Procesos completados a tiempo: ").append(est.getCompletados()).append('\n');
        sb.append("Procesos vencidos por deadline: ").append(est.getVencidos()).append('\n');
        sb.append("RAM usada / total: ").append(comp.getRam().getUsada())
          .append(" / ").append(comp.getRam().getCapacidad()).append('\n');
        sb.append('\n');

        if (disco != null && plan != null) {
            MetricasDisco md = plan.getMetricas();
            sb.append("--- 2. METRICAS DE DISCO Y PLANIFICACION DE E/S ---\n");
            sb.append("Bloques de archivos libres / total: ").append(disco.bloquesLibres())
              .append(" / ").append(disco.getTotalBloques()).append('\n');
            sb.append("Bloques de intercambio (Swap) libres / total: ").append(disco.swapLibre())
              .append(" / ").append(disco.getTamanoSwap()).append('\n');
            sb.append("Desplazamiento total del cabezal: ").append(disco.getDesplazamientoTotal()).append(" bloques\n");
            sb.append("Solicitudes atendidas totales: ").append(md.getTotalAtendidas()).append('\n');
            sb.append(String.format("Espera promedio en cola de disco: %.2f ciclos%n", md.esperaPromedio()));
            sb.append("Solicitudes atendidas por politica:\n");
            for (TipoPoliticaDisco t : TipoPoliticaDisco.values()) {
                sb.append("  - ").append(t.getEtiqueta()).append(": ").append(md.atendidasCon(t)).append('\n');
            }
            sb.append('\n');
        }

        if (cache != null) {
            sb.append("--- 3. CACHE LRU DE BLOQUES EN RAM ---\n");
            sb.append("Bloques en cache / capacidad: ").append(cache.getOcupados())
              .append(" / ").append(cache.getCapacidad()).append('\n');
            sb.append("Aciertos (Hits): ").append(cache.getHits()).append('\n');
            sb.append("Fallos (Misses): ").append(cache.getMisses()).append('\n');
            sb.append(String.format("Tasa de aciertos: %.2f %%%n", cache.getTasaAciertos() * 100.0));
            sb.append('\n');
        }

        FotoSistema foto = comp.getOs().foto();
        sb.append("--- 4. ESTADO DE COLAS (7 ESTADOS) ---\n");
        sb.append("Nuevos: ").append(foto.nuevos.length).append('\n');
        sb.append("Listos: ").append(foto.listos.length).append('\n');
        sb.append("Bloqueados: ").append(foto.bloqueados.length).append('\n');
        sb.append("Listos/Suspendidos (Swap): ").append(foto.listosSuspendidos.length).append('\n');
        sb.append("Bloqueados/Suspendidos (Swap): ").append(foto.bloqueadosSuspendidos.length).append('\n');
        sb.append("Terminados: ").append(foto.terminados.length).append('\n');
        return sb.toString();
    }

    public static String generarCsv(Cluster cluster) {
        StringBuilder sb = new StringBuilder();
        Computador comp = cluster.getComputadores()[0];
        Estadisticas est = comp.getOs().getEstadisticas();
        Disco disco = cluster.getDisco();
        PlanificadorDisco plan = cluster.getPlanificadorDisco();
        CacheDisco cache = cluster.getSistemaArchivos() != null ? cluster.getSistemaArchivos().getCache() : null;

        sb.append("Seccion,Metrica,Valor\n");
        sb.append("General,Ciclo,").append(cluster.getReloj().getCiclo()).append('\n');
        sb.append("CPU,Politica,").append(comp.getOs().getTipoPolitica().getEtiqueta()).append('\n');
        sb.append("CPU,Throughput_Proc_Por_Ciclo,").append(String.format("%.4f", cluster.throughputGlobal())).append('\n');
        sb.append("CPU,Utilizacion_Porcentaje,").append(String.format("%.2f", cluster.utilizacionGlobal() * 100.0)).append('\n');
        sb.append("CPU,Respuesta_Promedio_Ciclos,").append(String.format("%.2f", est.getRespuestaPromedio())).append('\n');
        sb.append("CPU,Cumplimiento_Deadlines_Porcentaje,").append(String.format("%.2f", est.getCumplimientoDeadlines() * 100.0)).append('\n');
        sb.append("CPU,Indice_Equidad_Jain,").append(String.format("%.4f", est.getEquidad())).append('\n');
        sb.append("CPU,Completados,").append(est.getCompletados()).append('\n');
        sb.append("CPU,Vencidos,").append(est.getVencidos()).append('\n');
        sb.append("Memoria,RAM_Usada,").append(comp.getRam().getUsada()).append('\n');
        sb.append("Memoria,RAM_Capacidad,").append(comp.getRam().getCapacidad()).append('\n');

        if (disco != null && plan != null) {
            MetricasDisco md = plan.getMetricas();
            sb.append("Disco,Politica_Activa,").append(plan.getTipoPolitica().getEtiqueta()).append('\n');
            sb.append("Disco,Bloques_Libres,").append(disco.bloquesLibres()).append('\n');
            sb.append("Disco,Bloques_Totales,").append(disco.getTotalBloques()).append('\n');
            sb.append("Swap,Bloques_Libres,").append(disco.swapLibre()).append('\n');
            sb.append("Swap,Bloques_Totales,").append(disco.getTamanoSwap()).append('\n');
            sb.append("Disco,Desplazamiento_Cabezal_Bloques,").append(disco.getDesplazamientoTotal()).append('\n');
            sb.append("Disco,Solicitudes_Atendidas,").append(md.getTotalAtendidas()).append('\n');
            sb.append("Disco,Espera_Promedio_Ciclos,").append(String.format("%.2f", md.esperaPromedio())).append('\n');
            for (TipoPoliticaDisco t : TipoPoliticaDisco.values()) {
                sb.append("Disco_Politica,").append(t.getEtiqueta()).append(',').append(md.atendidasCon(t)).append('\n');
            }
        }
        if (cache != null) {
            sb.append("Cache_LRU,Bloques_Ocupados,").append(cache.getOcupados()).append('\n');
            sb.append("Cache_LRU,Capacidad,").append(cache.getCapacidad()).append('\n');
            sb.append("Cache_LRU,Hits,").append(cache.getHits()).append('\n');
            sb.append("Cache_LRU,Misses,").append(cache.getMisses()).append('\n');
            sb.append("Cache_LRU,Tasa_Aciertos_Porcentaje,").append(String.format("%.2f", cache.getTasaAciertos() * 100.0)).append('\n');
        }
        return sb.toString();
    }
}
