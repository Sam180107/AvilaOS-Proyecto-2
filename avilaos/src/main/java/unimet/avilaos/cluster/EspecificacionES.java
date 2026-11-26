package unimet.avilaos.cluster;

import unimet.avilaos.hardware.disco.TipoOperacionES;
import unimet.avilaos.so.archivos.ModoUsuario;
import unimet.avilaos.so.archivos.Permiso;

/**
 * Lo que hace falta para crear un proceso de E/S: la operacion, la ruta y
 * a nombre de quien se hace. Viene de la interfaz o del JSON de procesos.
 *
 * @param permiso al crear, el permiso inicial; al actualizar, el permiso
 *                nuevo. null deja el de siempre.
 * @param dueno   dueno del archivo o directorio que se crea. Solo lo puede
 *                elegir el administrador; null usa el usuario que lo pide.
 */
public record EspecificacionES(
        String nombre, int memoria, int prioridad, int deadline, int instrucciones,
        TipoOperacionES operacion, String ruta, int tamanoBloques, String nuevoNombre,
        String usuario, ModoUsuario modo, Permiso permiso, String dueno) {

    public EspecificacionES(String nombre, int memoria, int prioridad, int deadline, int instrucciones,
            TipoOperacionES operacion, String ruta, int tamanoBloques, String nuevoNombre,
            String usuario, ModoUsuario modo) {
        this(nombre, memoria, prioridad, deadline, instrucciones, operacion, ruta, tamanoBloques,
                nuevoNombre, usuario, modo, null, null);
    }
}
