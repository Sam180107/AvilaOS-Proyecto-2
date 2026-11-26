package unimet.avilaos.gui;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import unimet.avilaos.estructuras.ListaEnlazada;
import unimet.avilaos.estructuras.Pila;
import unimet.avilaos.estructuras.Recorrido;
import unimet.avilaos.so.archivos.EntradaArchivo;

/**
 * Modelo de JTree para el sistema de archivos de AvilaOS, construido a partir
 * de una instantanea en preorden usando estructuras propias.
 */
public class ArbolArchivosModelo implements TreeModel {

    /** Nodo visual del arbol que envuelve una EntradaArchivo. */
    public static class NodoVista {

        private final EntradaArchivo entrada;
        private final ListaEnlazada<NodoVista> hijos = new ListaEnlazada<>();

        public NodoVista(EntradaArchivo entrada) {
            this.entrada = entrada;
        }

        public EntradaArchivo getEntrada() {
            return entrada;
        }

        public Recorrido<NodoVista> hijos() {
            return hijos.recorrido();
        }

        public int cantidadHijos() {
            return hijos.tamano();
        }

        public NodoVista hijoEn(int indice) {
            return hijos.obtener(indice);
        }

        public int indiceDeHijo(NodoVista hijo) {
            return hijos.indiceDe(hijo);
        }

        void agregarHijo(NodoVista hijo) {
            hijos.agregarAlFinal(hijo);
        }

        @Override
        public String toString() {
            return entrada == null ? "/" : entrada.nombre();
        }
    }

    private NodoVista raiz = new NodoVista(null);
    private final ListaEnlazada<TreeModelListener> oyentes = new ListaEnlazada<>();

    /** Reconstruye el arbol a partir de una lista en preorden de entradas. */
    public void actualizar(ListaEnlazada<EntradaArchivo> entradas) {
        if (entradas == null || entradas.estaVacia()) {
            raiz = new NodoVista(null);
            notificarCambio();
            return;
        }
        Recorrido<EntradaArchivo> r = entradas.recorrido();
        NodoVista nuevaRaiz = new NodoVista(r.siguiente());
        Pila<NodoVista> pila = new Pila<>();
        pila.apilar(nuevaRaiz);

        while (r.haySiguiente()) {
            EntradaArchivo e = r.siguiente();
            while (!pila.estaVacia() && pila.verTope().getEntrada().nivel() >= e.nivel()) {
                pila.desapilar();
            }
            NodoVista hijo = new NodoVista(e);
            if (!pila.estaVacia()) {
                pila.verTope().agregarHijo(hijo);
            }
            pila.apilar(hijo);
        }

        raiz = nuevaRaiz;
        notificarCambio();
    }

    @Override
    public Object getRoot() {
        return raiz;
    }

    @Override
    public Object getChild(Object parent, int index) {
        return ((NodoVista) parent).hijoEn(index);
    }

    @Override
    public int getChildCount(Object parent) {
        return ((NodoVista) parent).cantidadHijos();
    }

    @Override
    public boolean isLeaf(Object node) {
        EntradaArchivo e = ((NodoVista) node).getEntrada();
        return e == null || !e.esDirectorio();
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
        // El arbol es de solo lectura; las operaciones pasan por procesos de E/S.
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        if (parent == null || child == null) {
            return -1;
        }
        return ((NodoVista) parent).indiceDeHijo((NodoVista) child);
    }

    @Override
    public void addTreeModelListener(TreeModelListener l) {
        if (l != null && !oyentes.contiene(l)) {
            oyentes.agregarAlFinal(l);
        }
    }

    @Override
    public void removeTreeModelListener(TreeModelListener l) {
        if (l != null) {
            oyentes.eliminar(l);
        }
    }

    private void notificarCambio() {
        TreeModelEvent evento = new TreeModelEvent(this, new Object[]{raiz});
        Recorrido<TreeModelListener> r = oyentes.recorrido();
        while (r.haySiguiente()) {
            r.siguiente().treeStructureChanged(evento);
        }
    }
}