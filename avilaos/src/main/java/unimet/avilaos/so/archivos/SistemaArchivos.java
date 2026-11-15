package unimet.avilaos.so.archivos;

import unimet.avilaos.estructuras.ListaEnlazada;
import unimet.avilaos.estructuras.Recorrido;
import unimet.avilaos.hardware.disco.Disco;
import unimet.avilaos.hardware.disco.SolicitudES;
import unimet.avilaos.hardware.disco.TipoOperacionES;
import unimet.avilaos.sincronizacion.Mutex;
import unimet.avilaos.so.proceso.PCB;

/**
 * Sistema de archivos jerarquico de AvilaOS, montado sobre el disco.
 * Guarda el arbol de directorios y pide o libera bloques al disco.
 * Tambien decide que puede hacer cada usuario segun el modo.
 */
public class SistemaArchivos {

    public static final String ADMIN = "admin";

    private final Mutex mutex = new Mutex();
    private volatile long version = 0;
    private final Disco disco;
    private final NodoArchivo raiz = new NodoArchivo("", TipoNodo.DIRECTORIO, ADMIN, Permiso.PUBLICO);

    public SistemaArchivos(Disco disco) {
        this.disco = disco;
    }

    public Disco getDisco() {
        return disco;
    }

    /** Raiz del arbol. */
    public NodoArchivo getRaiz() {
        return raiz;
    }

    /** Sube cada vez que cambia la estructura del arbol; la interfaz lo usa para saber cuando repintar. */
    public long getVersion() {
        return version;
    }

    /**
     * Copia del arbol en preorden (la raiz primero, cada directorio antes de
     * sus hijos), tomada con el mutex. Es lo que la interfaz usa para el JTree
     * y la tabla de asignacion.
     */
    public ListaEnlazada<EntradaArchivo> instantanea() {
        mutex.cerrar();
        try {
            ListaEnlazada<EntradaArchivo> out = new ListaEnlazada<>();
            copiarEntrada(raiz, 0, out);
            return out;
        } finally {
            mutex.abrir();
        }
    }

    private void copiarEntrada(NodoArchivo n, int nivel, ListaEnlazada<EntradaArchivo> out) {
        out.agregarAlFinal(new EntradaArchivo(n.getRuta(), n.getPadre() == null ? "/" : n.getNombre(),
                n.getTipo(), n.getDueno(), n.getPermiso(), n.getTamanoBloques(), n.getPrimerBloque(),
                n.getProcesoCreador(), nivel));
        Recorrido<NodoArchivo> r = n.hijos();
        while (r.haySiguiente()) {
            copiarEntrada(r.siguiente(), nivel + 1, out);
        }
    }

    // ---------- operaciones basicas ----------

    public NodoArchivo buscar(String ruta) {
        mutex.cerrar();
        try {
            return buscarNodo(ruta);
        } finally {
            mutex.abrir();
        }
    }

    public NodoArchivo crearDirectorio(String ruta, String dueno, Permiso permiso) {
        mutex.cerrar();
        try {
            NodoArchivo padre = padreParaCrear(ruta);
            NodoArchivo dir = new NodoArchivo(nombreDe(ruta), TipoNodo.DIRECTORIO, dueno, permiso);
            padre.agregarHijo(dir);
            version++;
            return dir;
        } finally {
            mutex.abrir();
        }
    }

    /** Crea un archivo y le asigna sus bloques encadenados en el disco. */
    public NodoArchivo crearArchivo(String ruta, int bloques, String dueno, Permiso permiso,
            String procesoCreador) {
        mutex.cerrar();
        try {
            NodoArchivo padre = padreParaCrear(ruta);
            validarTamano(bloques);
            int primero;
            try {
                primero = disco.asignarCadena(ruta, bloques);
            } catch (IllegalStateException e) {
                throw new OperacionRechazada("No se puede crear " + ruta + ": " + e.getMessage());
            }
            return colgarArchivo(padre, ruta, primero, bloques, dueno, permiso, procesoCreador);
        } finally {
            mutex.abrir();
        }
    }

    /**
     * Crea un archivo que ya tenia sitio en el disco (carga del JSON).
     * Si viene la cadena completa se respeta; si no, el primer bloque es
     * el indicado y el resto se toma de los libres.
     */
    public NodoArchivo restaurarArchivo(String ruta, int primerBloque, int bloques, int[] cadena,
            String dueno, Permiso permiso, String procesoCreador) {
        mutex.cerrar();
        try {
            NodoArchivo padre = padreParaCrear(ruta);
            validarTamano(bloques);
            try {
                if (cadena != null && cadena.length == bloques && cadena[0] == primerBloque) {
                    disco.asignarCadenaExacta(ruta, cadena);
                } else {
                    disco.asignarCadenaDesde(ruta, primerBloque, bloques);
                }
            } catch (IllegalStateException | IllegalArgumentException e) {
                throw new OperacionRechazada("No se puede ubicar " + ruta + ": " + e.getMessage());
            }
            return colgarArchivo(padre, ruta, primerBloque, bloques, dueno, permiso, procesoCreador);
        } finally {
            mutex.abrir();
        }
    }

    /** Cambia el nombre de un archivo o directorio. */
    public void renombrar(String ruta, String nuevoNombre) {
        mutex.cerrar();
        try {
            NodoArchivo n = existente(ruta);
            if (n == raiz) {
                throw new OperacionRechazada("La raiz no se puede renombrar");
            }
            validarNombre(nuevoNombre);
            if (n.getPadre().hijo(nuevoNombre) != null) {
                throw new OperacionRechazada("Ya existe " + nuevoNombre + " en " + n.getPadre().getRuta());
            }
            n.setNombre(nuevoNombre);
            actualizarOcupantes(n);
            version++;
        } finally {
            mutex.abrir();
        }
    }

    /**
     * Elimina un archivo y libera sus bloques. Si es un directorio se van
     * tambien todos sus archivos y subdirectorios. Devuelve los bloques liberados.
     */
    public int eliminar(String ruta) {
        mutex.cerrar();
        try {
            NodoArchivo n = existente(ruta);
            if (n == raiz) {
                throw new OperacionRechazada("La raiz no se puede eliminar");
            }
            int liberados = liberarBloques(n);
            n.getPadre().quitarHijo(n);
            version++;
            return liberados;
        } finally {
            mutex.abrir();
        }
    }

    /** Todos los archivos del arbol (sin directorios), para la tabla de asignacion. */
    public ListaEnlazada<NodoArchivo> listarArchivos() {
        mutex.cerrar();
        try {
            ListaEnlazada<NodoArchivo> out = new ListaEnlazada<>();
            recolectar(raiz, out, true, false);
            return out;
        } finally {
            mutex.abrir();
        }
    }

    /** Todos los nodos menos la raiz, los padres siempre antes que sus hijos. */
    public ListaEnlazada<NodoArchivo> listarTodo() {
        mutex.cerrar();
        try {
            ListaEnlazada<NodoArchivo> out = new ListaEnlazada<>();
            recolectar(raiz, out, true, true);
            return out;
        } finally {
            mutex.abrir();
        }
    }

    // ---------- permisos ----------

    /**
     * Revisa si el usuario puede hacer la operacion. Si no puede lanza
     * OperacionRechazada con el motivo.
     */
    public void validarPermiso(TipoOperacionES operacion, String ruta, String usuario, ModoUsuario modo) {
        mutex.cerrar();
        try {
            validarPermisoSinMutex(operacion, ruta, usuario, modo);
        } finally {
            mutex.abrir();
        }
    }

    private void validarPermisoSinMutex(TipoOperacionES operacion, String ruta, String usuario,
            ModoUsuario modo) {
        if (!operacion.esDeArchivo()) {
            throw new OperacionRechazada(operacion.getEtiqueta() + " no es una operacion de archivos");
        }
        if (operacion == TipoOperacionES.CREAR) {
            NodoArchivo padre = padreParaCrear(ruta);
            if (modo == ModoUsuario.USUARIO && !padre.getDueno().equals(usuario)) {
                throw new OperacionRechazada("Modo usuario: " + usuario + " solo puede crear dentro de"
                        + " sus propios directorios y " + padre.getRuta() + " es de " + padre.getDueno());
            }
            return;
        }
        NodoArchivo n = existente(ruta);
        if (modo == ModoUsuario.ADMINISTRADOR) {
            return;
        }
        boolean propio = n.getDueno().equals(usuario);
        if (operacion == TipoOperacionES.LEER) {
            if (!propio && n.getPermiso() == Permiso.PRIVADO) {
                throw new OperacionRechazada("Modo usuario: " + ruta + " es privado y pertenece a "
                        + n.getDueno());
            }
        } else if (!propio) {
            throw new OperacionRechazada("Modo usuario: no se puede " + operacion.getEtiqueta().toLowerCase()
                    + " " + ruta + " porque pertenece a " + n.getDueno());
        }
    }

    // ---------- procesos de E/S ----------

    /**
     * Arma la solicitud al disco para un proceso de E/S. Valida permisos y
     * calcula los bloques que el cabezal tiene que recorrer.
     *
     * CREAR reserva los bloques de una vez (tamano 0 crea un directorio);
     * ELIMINAR y ACTUALIZAR se aplican cuando el disco termina, con aplicar().
     *
     * @param nuevoNombre solo para ACTUALIZAR; null si no se renombra.
     */
    public SolicitudES prepararSolicitud(TipoOperacionES operacion, String ruta, int tamanoBloques,
            String nuevoNombre, String usuario, ModoUsuario modo, long idProceso,
            String nombreProceso, PCB pcb, long ciclo) {
        int[] bloques;
        mutex.cerrar();
        try {
            validarPermisoSinMutex(operacion, ruta, usuario, modo);
            switch (operacion) {
                case CREAR -> {
                    NodoArchivo padre = padreParaCrear(ruta);
                    if (tamanoBloques <= 0) {
                        padre.agregarHijo(new NodoArchivo(nombreDe(ruta), TipoNodo.DIRECTORIO,
                                usuario, Permiso.PUBLICO));
                        version++;
                        bloques = new int[0];
                    } else {
                        int primero;
                        try {
                            primero = disco.asignarCadena(ruta, tamanoBloques);
                        } catch (IllegalStateException e) {
                            throw new OperacionRechazada("No se puede crear " + ruta + ": " + e.getMessage());
                        }
                        colgarArchivo(padre, ruta, primero, tamanoBloques, usuario, Permiso.PRIVADO,
                                nombreProceso);
                        bloques = disco.cadena(primero);
                    }
                }
                case ACTUALIZAR -> {
                    NodoArchivo n = existente(ruta);
                    if (nuevoNombre != null) {
                        validarNombre(nuevoNombre);
                    }
                    // renombrar solo toca la entrada del archivo: su primer bloque
                    bloques = n.esDirectorio() ? new int[0] : new int[]{n.getPrimerBloque()};
                }
                default -> bloques = bloquesDe(existente(ruta));
            }
        } finally {
            mutex.abrir();
        }
        SolicitudES s = new SolicitudES(operacion, ruta, bloques, idProceso, nombreProceso, pcb, ciclo);
        s.setDato(nuevoNombre);
        return s;
    }

    /**
     * Aplica el efecto de una solicitud que el disco ya atendio. Devuelve
     * un texto para el log. Si el archivo ya no existe no hace nada.
     */
    public String aplicar(SolicitudES s) {
        try {
            switch (s.getOperacion()) {
                case ELIMINAR -> {
                    int n = eliminar(s.getRuta());
                    return "Se elimino " + s.getRuta() + " y se liberaron " + n + " bloques";
                }
                case ACTUALIZAR -> {
                    if (s.getDato() != null) {
                        renombrar(s.getRuta(), s.getDato());
                        return s.getRuta() + " ahora se llama " + s.getDato();
                    }
                    return "Se actualizo " + s.getRuta();
                }
                case CREAR -> {
                    return "Se creo " + s.getRuta();
                }
                case LEER -> {
                    return "Se leyo " + s.getRuta();
                }
                default -> {
                    return "";
                }
            }
        } catch (OperacionRechazada e) {
            return "No se pudo " + s.getOperacion().getEtiqueta().toLowerCase() + " " + s.getRuta()
                    + ": " + e.getMessage();
        }
    }

    // ---------- internos (se llaman con el mutex tomado) ----------

    private NodoArchivo buscarNodo(String ruta) {
        if (ruta == null || !ruta.startsWith("/")) {
            return null;
        }
        NodoArchivo actual = raiz;
        for (String parte : ruta.split("/")) {
            if (parte.isEmpty()) {
                continue;
            }
            actual = actual.hijo(parte);
            if (actual == null) {
                return null;
            }
        }
        return actual;
    }

    private NodoArchivo existente(String ruta) {
        NodoArchivo n = buscarNodo(ruta);
        if (n == null) {
            throw new OperacionRechazada("No existe " + ruta);
        }
        return n;
    }

    /** Valida la ruta de algo nuevo y devuelve el directorio donde va. */
    private NodoArchivo padreParaCrear(String ruta) {
        if (ruta == null || !ruta.startsWith("/") || ruta.endsWith("/")) {
            throw new OperacionRechazada("Ruta invalida: " + ruta);
        }
        String nombre = nombreDe(ruta);
        validarNombre(nombre);
        int corte = ruta.lastIndexOf('/');
        String rutaPadre = corte == 0 ? "/" : ruta.substring(0, corte);
        NodoArchivo padre = buscarNodo(rutaPadre);
        if (padre == null) {
            throw new OperacionRechazada("No existe el directorio " + rutaPadre);
        }
        if (!padre.esDirectorio()) {
            throw new OperacionRechazada(rutaPadre + " no es un directorio");
        }
        if (padre.hijo(nombre) != null) {
            throw new OperacionRechazada("Ya existe " + ruta);
        }
        return padre;
    }

    private static String nombreDe(String ruta) {
        return ruta.substring(ruta.lastIndexOf('/') + 1);
    }

    private static void validarNombre(String nombre) {
        if (nombre == null || nombre.trim().isEmpty() || nombre.contains("/")) {
            throw new OperacionRechazada("Nombre invalido: " + nombre);
        }
    }

    private void validarTamano(int bloques) {
        if (bloques < 1) {
            throw new OperacionRechazada("Un archivo debe ocupar al menos 1 bloque");
        }
    }

    private NodoArchivo colgarArchivo(NodoArchivo padre, String ruta, int primero, int bloques,
            String dueno, Permiso permiso, String procesoCreador) {
        NodoArchivo archivo = new NodoArchivo(nombreDe(ruta), TipoNodo.ARCHIVO, dueno, permiso);
        archivo.setBloques(primero, bloques);
        archivo.setProcesoCreador(procesoCreador == null ? "-" : procesoCreador);
        padre.agregarHijo(archivo);
        version++;
        return archivo;
    }

    private int liberarBloques(NodoArchivo n) {
        if (!n.esDirectorio()) {
            return disco.liberarCadena(n.getPrimerBloque());
        }
        int total = 0;
        Recorrido<NodoArchivo> r = n.hijos();
        while (r.haySiguiente()) {
            total += liberarBloques(r.siguiente());
        }
        return total;
    }

    /** Al renombrar, los bloques deben mostrar la ruta nueva. */
    private void actualizarOcupantes(NodoArchivo n) {
        if (!n.esDirectorio()) {
            disco.renombrarOcupante(n.getPrimerBloque(), n.getRuta());
            return;
        }
        Recorrido<NodoArchivo> r = n.hijos();
        while (r.haySiguiente()) {
            actualizarOcupantes(r.siguiente());
        }
    }

    /** Bloques de un archivo, o de todo lo que cuelga de un directorio. */
    private int[] bloquesDe(NodoArchivo n) {
        if (!n.esDirectorio()) {
            return disco.cadena(n.getPrimerBloque());
        }
        ListaEnlazada<NodoArchivo> archivos = new ListaEnlazada<>();
        recolectar(n, archivos, true, false);
        int total = 0;
        Recorrido<NodoArchivo> r = archivos.recorrido();
        while (r.haySiguiente()) {
            total += r.siguiente().getTamanoBloques();
        }
        int[] out = new int[total];
        int k = 0;
        r = archivos.recorrido();
        while (r.haySiguiente()) {
            for (int b : disco.cadena(r.siguiente().getPrimerBloque())) {
                out[k++] = b;
            }
        }
        return out;
    }

    private void recolectar(NodoArchivo n, ListaEnlazada<NodoArchivo> out, boolean archivos,
            boolean directorios) {
        Recorrido<NodoArchivo> r = n.hijos();
        while (r.haySiguiente()) {
            NodoArchivo h = r.siguiente();
            if (h.esDirectorio()) {
                if (directorios) {
                    out.agregarAlFinal(h);
                }
                recolectar(h, out, archivos, directorios);
            } else if (archivos) {
                out.agregarAlFinal(h);
            }
        }
    }
}
