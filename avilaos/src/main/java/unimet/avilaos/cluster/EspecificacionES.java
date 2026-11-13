package unimet.avilaos.cluster;

import unimet.avilaos.hardware.disco.TipoOperacionES;
import unimet.avilaos.so.archivos.ModoUsuario;

/**
 * Lo que hace falta para crear un proceso de E/S: la operacion, la ruta y
 * a nombre de quien se hace. Viene de la interfaz o del JSON de procesos.
 * tamanoBloques solo importa al crear y nuevoNombre solo al actualizar.
 */
public record EspecificacionES(
        String nombre, int memoria, int prioridad, int deadline, int instrucciones,
        TipoOperacionES operacion, String ruta, int tamanoBloques, String nuevoNombre,
        String usuario, ModoUsuario modo) {
}
