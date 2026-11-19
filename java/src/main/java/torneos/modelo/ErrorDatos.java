package torneos.modelo;

/** Excepción propia para errores de datos; se captura y se convierte en RegistroError. */
public class ErrorDatos extends RuntimeException {
    private final TipoError tipo;

    public ErrorDatos(TipoError tipo, String mensaje) {
        super(mensaje);
        this.tipo = tipo;
    }

    public TipoError tipo() { return tipo; }

    public RegistroError comoRegistro() { return new RegistroError(tipo, getMessage()); }
}
