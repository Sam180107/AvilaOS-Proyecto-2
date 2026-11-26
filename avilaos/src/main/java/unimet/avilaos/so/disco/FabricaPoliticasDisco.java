package unimet.avilaos.so.disco;

/** Crea la politica que corresponde a cada TipoPoliticaDisco. */
public final class FabricaPoliticasDisco {

    private FabricaPoliticasDisco() {
    }

    public static PoliticaDisco crear(TipoPoliticaDisco tipo) {
        return switch (tipo) {
            case FIFO -> new PoliticaFIFO();
            case SSTF -> new PoliticaSSTF();
            case SCAN -> new PoliticaSCAN();
            case C_SCAN -> new PoliticaCSCAN();
            case LOOK -> new PoliticaLOOK();
            case C_LOOK -> new PoliticaCLOOK();
        };
    }
}
