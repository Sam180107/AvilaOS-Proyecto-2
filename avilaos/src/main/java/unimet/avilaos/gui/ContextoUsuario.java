package unimet.avilaos.gui;

import unimet.avilaos.so.archivos.ModoUsuario;

/** Quien esta usando AvilaOS ahora: la ventana principal lo implementa y los paneles lo consultan. */
public interface ContextoUsuario {

    ModoUsuario modo();

    /** Usuario activo; en modo administrador es siempre "admin". */
    String usuario();
}
