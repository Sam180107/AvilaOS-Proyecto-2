package unimet.avilaos.so.proceso;

/** Los siete estados de un proceso y las transiciones validas entre ellos. */
public enum EstadoProceso {
    NUEVO("Nuevo"),
    LISTO("Listo"),
    EJECUCION("Ejecucion"),
    BLOQUEADO("Bloqueado"),
    LISTO_SUSPENDIDO("Listo/Suspendido"),
    BLOQUEADO_SUSPENDIDO("Bloqueado/Suspendido"),
    TERMINADO("Terminado");

    private final String etiqueta;

    EstadoProceso(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public boolean puedePasarA(EstadoProceso destino) {
        return switch (this) {
            case NUEVO -> destino == LISTO || destino == LISTO_SUSPENDIDO || destino == TERMINADO;
            case LISTO -> destino == EJECUCION || destino == LISTO_SUSPENDIDO || destino == TERMINADO;
            case EJECUCION -> destino == LISTO || destino == BLOQUEADO
                    || destino == LISTO_SUSPENDIDO || destino == TERMINADO;
            case BLOQUEADO -> destino == LISTO || destino == BLOQUEADO_SUSPENDIDO || destino == TERMINADO;
            case LISTO_SUSPENDIDO -> destino == LISTO || destino == TERMINADO;
            case BLOQUEADO_SUSPENDIDO -> destino == LISTO_SUSPENDIDO || destino == BLOQUEADO || destino == TERMINADO;
            case TERMINADO -> false;
        };
    }
}
