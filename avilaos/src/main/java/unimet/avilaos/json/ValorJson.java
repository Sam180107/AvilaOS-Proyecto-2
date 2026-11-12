package unimet.avilaos.json;

import unimet.avilaos.estructuras.ListaEnlazada;
import unimet.avilaos.estructuras.Recorrido;

/** Un valor de JSON ya leido: objeto, arreglo, texto, numero, booleano o null. */
public class ValorJson {

    private static final class Par {
        final String clave;
        final ValorJson valor;

        Par(String clave, ValorJson valor) {
            this.clave = clave;
            this.valor = valor;
        }
    }

    private final TipoJson tipo;
    private String texto;
    private double numero;
    private boolean booleano;
    private ListaEnlazada<Par> campos;
    private ListaEnlazada<ValorJson> elementos;

    private ValorJson(TipoJson tipo) {
        this.tipo = tipo;
    }

    static ValorJson objeto() {
        ValorJson v = new ValorJson(TipoJson.OBJETO);
        v.campos = new ListaEnlazada<>();
        return v;
    }

    static ValorJson arreglo() {
        ValorJson v = new ValorJson(TipoJson.ARREGLO);
        v.elementos = new ListaEnlazada<>();
        return v;
    }

    static ValorJson texto(String s) {
        ValorJson v = new ValorJson(TipoJson.TEXTO);
        v.texto = s;
        return v;
    }

    static ValorJson numero(double d) {
        ValorJson v = new ValorJson(TipoJson.NUMERO);
        v.numero = d;
        return v;
    }

    static ValorJson booleano(boolean b) {
        ValorJson v = new ValorJson(TipoJson.BOOLEANO);
        v.booleano = b;
        return v;
    }

    static ValorJson nulo() {
        return new ValorJson(TipoJson.NULO);
    }

    void poner(String clave, ValorJson valor) {
        campos.agregarAlFinal(new Par(clave, valor));
    }

    void agregar(ValorJson valor) {
        elementos.agregarAlFinal(valor);
    }

    public TipoJson getTipo() {
        return tipo;
    }

    /** Campo de un objeto, o null si no esta. */
    public ValorJson obtener(String clave) {
        if (tipo != TipoJson.OBJETO) {
            return null;
        }
        Recorrido<Par> r = campos.recorrido();
        while (r.haySiguiente()) {
            Par p = r.siguiente();
            if (p.clave.equals(clave)) {
                return p.valor;
            }
        }
        return null;
    }

    public boolean tiene(String clave) {
        ValorJson v = obtener(clave);
        return v != null && v.tipo != TipoJson.NULO;
    }

    /** Campo obligatorio de tipo objeto. */
    public ValorJson objetoEn(String clave) {
        return campo(clave, TipoJson.OBJETO);
    }

    /** Campo obligatorio de tipo arreglo. */
    public ValorJson arregloEn(String clave) {
        return campo(clave, TipoJson.ARREGLO);
    }

    /** Campo obligatorio de tipo texto. */
    public String textoEn(String clave) {
        return campo(clave, TipoJson.TEXTO).texto;
    }

    public String textoEn(String clave, String porDefecto) {
        return tiene(clave) ? textoEn(clave) : porDefecto;
    }

    /** Campo obligatorio numerico y entero. */
    public int enteroEn(String clave) {
        return campo(clave, TipoJson.NUMERO).comoEntero(clave);
    }

    public int enteroEn(String clave, int porDefecto) {
        return tiene(clave) ? enteroEn(clave) : porDefecto;
    }

    public int comoEntero(String nombre) {
        if (tipo != TipoJson.NUMERO || numero != Math.rint(numero)
                || numero > Integer.MAX_VALUE || numero < Integer.MIN_VALUE) {
            throw new ErrorJson("\"" + nombre + "\" debe ser un numero entero");
        }
        return (int) numero;
    }

    public String comoTexto() {
        return texto;
    }

    public double comoNumero() {
        return numero;
    }

    public boolean comoBooleano() {
        return booleano;
    }

    /** Elementos de un arreglo. */
    public Recorrido<ValorJson> elementos() {
        if (tipo != TipoJson.ARREGLO) {
            throw new ErrorJson("Se esperaba un arreglo");
        }
        return elementos.recorrido();
    }

    public int cantidad() {
        return tipo == TipoJson.ARREGLO ? elementos.tamano() : 0;
    }

    private ValorJson campo(String clave, TipoJson esperado) {
        ValorJson v = obtener(clave);
        if (v == null) {
            throw new ErrorJson("Falta el campo \"" + clave + "\"");
        }
        if (v.tipo != esperado) {
            throw new ErrorJson("El campo \"" + clave + "\" debe ser " + esperado.name().toLowerCase());
        }
        return v;
    }
}
