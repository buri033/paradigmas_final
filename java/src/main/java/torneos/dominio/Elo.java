package torneos.dominio;

import java.util.function.BiFunction;

/**
 * Sistema Elo (funciones puras).
 *   E  = 1 / (1 + 10^((Rb - Ra) / 400))
 *   R' = R + K * (S - E),  K = 32,  S en {1, 0.5, 0}
 * Los Elo llegan en centésimas, por eso (Rb - Ra) / 400 se escribe (rb - ra) / 40000.0.
 * La variación se redondea a centésimas con floor(x + 0.5): misma regla en Java, Scala y C#.
 */
public final class Elo {
    private Elo() { }

    public static final double K = 32.0;

    /** Probabilidad esperada de que A le gane a B. */
    public static final BiFunction<Long, Long, Double> ESPERADO =
            (ra, rb) -> 1.0 / (1.0 + Math.pow(10.0, (rb - ra) / 40000.0));

    public static double esperado(long ra, long rb) {
        return ESPERADO.apply(ra, rb);
    }

    /** Variación del Elo de A (en centésimas) dado el puntaje real S de A. */
    public static long delta(long ra, long rb, double s) {
        return (long) Math.floor(K * (s - esperado(ra, rb)) * 100.0 + 0.5);
    }
}
