package unimet.avilaos.json;

/** El JSON esta mal escrito o le falta algo. El mensaje dice que. */
public class ErrorJson extends RuntimeException {

    public ErrorJson(String mensaje) {
        super(mensaje);
    }
}
