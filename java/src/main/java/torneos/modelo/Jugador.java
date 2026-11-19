package torneos.modelo;

/**
 * Jugador inmutable (record). El Elo se guarda en CENTÉSIMAS (long) para que las sumas
 * sean exactas e independientes del orden: 1534.50 se guarda como 153450.
 */
public record Jugador(int id, String nombre, long elo, int victorias, int derrotas, int empates, int puntos) {

    /** Elo inicial cuando el CSV no lo indica: 1000.00 */
    public static final long ELO_POR_DEFECTO = 100_000L;

    public static Jugador nuevo(int id, String nombre, long elo) {
        return new Jugador(id, nombre, elo, 0, 0, 0, 0);
    }

    /** Devuelve un NUEVO jugador con el resultado aplicado (no muta el original). */
    public Jugador conResultado(long deltaElo, int v, int d, int e, int pts) {
        return new Jugador(id, nombre, elo + deltaElo, victorias + v, derrotas + d, empates + e, puntos + pts);
    }
}
