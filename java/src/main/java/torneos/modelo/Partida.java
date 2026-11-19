package torneos.modelo;

/** Partida inmutable: jugadores (por id), resultado, ronda y variación de Elo de A (centésimas). */
public record Partida(int jugadorA, int jugadorB, Resultado resultado, int ronda, long deltaEloA) {

    public int ganador() {
        return switch (resultado) {
            case GANA_A, BYE -> jugadorA;
            case GANA_B -> jugadorB;
            case EMPATE -> 0;
        };
    }
}
