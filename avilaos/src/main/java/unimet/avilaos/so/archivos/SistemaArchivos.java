package unimet.avilaos.so.archivos;

import unimet.avilaos.estructuras.ListaEnlazada;
import unimet.avilaos.estructuras.Recorrido;
import unimet.avilaos.hardware.disco.Disco;
import unimet.avilaos.hardware.disco.SolicitudES;
import unimet.avilaos.hardware.disco.TipoOperacionES;
import unimet.avilaos.sincronizacion.Mutex;
import unimet.avilaos.so.disco.CacheDisco;
import unimet.avilaos.so.proceso.PCB;

/**
 * Sistema de archivos jerarquico de AvilaOS, montado sobre el disco.
 * Guarda el arbol de directorios, gestiona permisos y coordina la cache LRU.
 */
public class SistemaArchivos {

    public static final String ADMIN = "admin";
    public static final int CACHE_POR_DEFECTO = 0;

    private final Mutex mutex = new Mutex();
    private volatile long version = 0;
    private final Disco disco;
    private volatile CacheDisco cache;
    private final NodoArchivo raiz = new NodoArchivo("", TipoNodo.DIRECTORIO, ADMIN, Permiso.PUBLICO);

    public SistemaArchivos(Disco disco) {
        this(disco, CACHE_POR_DEFECTO);
    }

    public SistemaArchivos(Disco disco, int capacidadCache) {
        this.disco = disco;
        this.cache = new CacheDisco(capacidadCache);
    }

    public Disco getDisco() {
        return disco;
    }

    public CacheDisco getCache() {
        return cache;
    }

    public void configurarCache(int capacidadCache) {
        this.cache = new CacheDisco(capacidadCache);
    }

    public NodoArchivo getRaiz() {
        return raiz;
    }

    public long getVersion() {
        return version;
    }

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
            String rutaAnterior = n.getRuta();
            n.setNombre(nuevoNombre);
            actualizarOcupantes(n);
            cache.actualizarRuta(rutaAnterior, n.getRuta());
            version++;
        } finally {
            mutex.abrir();
        }
    }

    public int eliminar(String ruta) {
        mutex.cerrar();
        try {
            NodoArchivo n = existente(ruta);
            if (n == raiz) {
                throw new OperacionRechazada("La raiz no se puede eliminar");
            }
            cache.invalidarPrefijo(n.getRuta());
            int liberados = liberarBloques(n);
            n.getPadre().quitarHijo(n);
            version++;
            return liberados;
        } finally {
            mutex.abrir();
        }
    }

    private void ponerPermiso(String ruta, Permiso nuevo) {
        mutex.cerrar();
        try {
            NodoArchivo n = existente(ruta);
            n.setPermiso(nuevo);
            version++;
        } finally {
            mutex.abrir();
        }
    }

    /** Cambia el permiso (Publico/Privado) si el usuario es administrador o dueno. */
    public void cambiarPermiso(String ruta, Permiso nuevoPermiso, String usuario, ModoUsuario modo) {
        if (nuevoPermiso == null) {
            throw new IllegalArgumentException("El permiso no puede ser null");
        }
        mutex.cerrar();
        try {
            NodoArchivo n = existente(ruta);
            if (n == raiz) {
                throw new OperacionRechazada("No se puede cambiar el permiso de la raiz");
            }
            if (modo != ModoUsuario.ADMINISTRADOR && !n.getDueno().equals(usuario)) {
                throw new OperacionRechazada("Modo usuario: solo " + n.getDueno()
                        + " o un administrador puede cambiar los permisos de " + ruta);
            }
            n.setPermiso(nuevoPermiso);
            version++;
        } finally {
            mutex.abrir();
        }
    }

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

    // ---------- cache LRU ----------

    /**
     * Consulta la cache LRU para una lectura. Devuelve true si todos los bloques
     * estaban en RAM (Cache HIT) tras validar permisos.
     */
    public boolean intentarLeerDesdeCache(String ruta, String usuario, ModoUsuario modo, long ciclo) {
        int[] bloques;
        mutex.cerrar();
        try {
            validarPermisoSinMutex(TipoOperacionES.LEER, ruta, usuario, modo, false);
            NodoArchivo n = existente(ruta);
            bloques = bloquesDe(n);
        } finally {
            mutex.abrir();
        }
        if (bloques.length == 0) {
            return false;
        }
        return cache.consultarTodos(bloques, ruta, ciclo);
    }

    // ---------- permisos ----------

    public void validarPermiso(TipoOperacionES operacion, String ruta, String usuario, ModoUsuario modo) {
        validarPermiso(operacion, ruta, usuario, modo, 1);
    }

    /** @param tamanoBloques al crear: 0 es un directorio, 1 o mas un archivo. */
    public void validarPermiso(TipoOperacionES operacion, String ruta, String usuario, ModoUsuario modo,
                               int tamanoBloques) {
        mutex.cerrar();
        try {
            validarPermisoSinMutex(operacion, ruta, usuario, modo, tamanoBloques <= 0);
        } finally {
            mutex.abrir();
        }
    }

    private void validarPermisoSinMutex(TipoOperacionES operacion, String ruta, String usuario,
                                        ModoUsuario modo, boolean esDirectorio) {
        if (!operacion.esDeArchivo()) {
            throw new OperacionRechazada(operacion.getEtiqueta() + " no es una operacion de archivos");
        }
        if (operacion == TipoOperacionES.CREAR) {
            if (modo == ModoUsuario.USUARIO && esDirectorio) {
                throw new OperacionRechazada("Modo usuario: solo el administrador puede crear directorios");
            }
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

    public SolicitudES prepararSolicitud(TipoOperacionES operacion, String ruta, int tamanoBloques,
                                         String nuevoNombre, String usuario, ModoUsuario modo, long idProceso,
                                         String nombreProceso, PCB pcb, long ciclo) {
        int[] bloques;
        mutex.cerrar();
        try {
            validarPermisoSinMutex(operacion, ruta, usuario, modo, tamanoBloques <= 0);
            Permiso pedido = pcb == null ? null : pcb.getNuevoPermiso();
            String dueno = usuario;
            if (modo == ModoUsuario.ADMINISTRADOR && pcb != null && pcb.getDuenoNuevo() != null) {
                dueno = pcb.getDuenoNuevo();
            }
            switch (operacion) {
                case CREAR -> {
                    NodoArchivo padre = padreParaCrear(ruta);
                    if (tamanoBloques <= 0) {
                        padre.agregarHijo(new NodoArchivo(nombreDe(ruta), TipoNodo.DIRECTORIO,
                                dueno, pedido == null ? Permiso.PUBLICO : pedido));
                        version++;
                        bloques = new int[0];
                    } else {
                        int primero;
                        try {
                            primero = disco.asignarCadena(ruta, tamanoBloques);
                        } catch (IllegalStateException e) {
                            throw new OperacionRechazada("No se puede crear " + ruta + ": " + e.getMessage());
                        }
                        colgarArchivo(padre, ruta, primero, tamanoBloques, dueno,
                                pedido == null ? Permiso.PRIVADO : pedido, nombreProceso);
                        bloques = disco.cadena(primero);
                    }
                }
                case ACTUALIZAR -> {
                    NodoArchivo n = existente(ruta);
                    if (n == raiz) {
                        throw new OperacionRechazada("La raiz no se puede actualizar");
                    }
                    if (nuevoNombre != null && !nuevoNombre.trim().isEmpty()) {
                        validarNombre(nuevoNombre);
                    }
                    bloques = n.esDirectorio() ? new int[0] : new int[]{n.getPrimerBloque()};
                }
                default -> bloques = bloquesDe(existente(ruta));
            }
        } finally {
            mutex.abrir();
        }
        SolicitudES s = new SolicitudES(operacion, ruta, bloques, idProceso, nombreProceso, pcb, ciclo);
        s.setDato(nuevoNombre);
        if (operacion == TipoOperacionES.ACTUALIZAR && pcb != null && pcb.getNuevoPermiso() != null) {
            s.setPermisoNuevo(pcb.getNuevoPermiso().name());
        }
        return s;
    }

    public String aplicar(SolicitudES s) {
        return aplicar(s, s.getCicloFin() > 0 ? s.getCicloFin() : s.getCicloLlegada());
    }

    public String aplicar(SolicitudES s, long ciclo) {
        try {
            switch (s.getOperacion()) {
                case ELIMINAR -> {
                    int n = eliminar(s.getRuta());
                    return "Se elimino " + s.getRuta() + " y se liberaron " + n + " bloques";
                }
                case ACTUALIZAR -> {
                    String hecho = "";
                    if (s.getPermisoNuevo() != null) {
                        Permiso nuevo = Permiso.valueOf(s.getPermisoNuevo());
                        ponerPermiso(s.getRuta(), nuevo);
                        hecho = "Permiso de " + s.getRuta() + " cambiado a " + nuevo.getEtiqueta();
                    }
                    if (s.getDato() != null && !s.getDato().trim().isEmpty()) {
                        renombrar(s.getRuta(), s.getDato());
                        return (hecho.isEmpty() ? "" : hecho + ". ") + s.getRuta() + " ahora se llama " + s.getDato();
                    }
                    if (!hecho.isEmpty()) {
                        return hecho;
                    }
                    cache.cargarBloques(s.getBloques(), s.getRuta(), ciclo);
                    return "Se actualizo " + s.getRuta();
                }
                case CREAR -> {
                    cache.cargarBloques(s.getBloques(), s.getRuta(), ciclo);
                    return "Se creo " + s.getRuta();
                }
                case LEER -> {
                    cache.cargarBloques(s.getBloques(), s.getRuta(), ciclo);
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

    // ---------- internos ----------

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
