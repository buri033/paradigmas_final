package torneos.dominio;

/**
 * Generador pseudoaleatorio PROPIO (LCG de 64 bits), idéntico en Java, Scala y C#.
 *   estado' = estado * 6364136223846793005 + 1442695040888963407  (mod 2^64)
 *   double  = (estado >>> 11) * 2^-53   (53 bits altos -> [0, 1))
 * No se usa java.util.Random porque cada lenguaje genera secuencias distintas.
 */
public final class GeneradorLCG {
    static final long A = 6364136223846793005L;
    static final long C = 1442695040888963407L;
    /** 0x9E3779B97F4A7C15 (razón áurea) escrito como long con signo. */
    static final long DORADO = -7046029254386353131L;
    static final double ESCALA = 1.0 / 9007199254740992.0; // 2^-53

    // Única mutación del dominio: estado interno encapsulado del generador.
    private long estado;

    public GeneradorLCG(long semilla) { this.estado = semilla; }

    public long siguienteLong() {
        estado = estado * A + C; // el desbordamiento de long equivale a "mod 2^64"
        return estado;
    }

    public double siguienteDouble() {
        return (siguienteLong() >>> 11) * ESCALA;
    }

    /**
     * Número aleatorio de una partida, función PURA de (semilla, ronda, índice):
     * no depende del orden en que se evalúen las partidas (sirve igual con streams paralelos).
     */
    public static double aleatorioPartida(long semilla, int ronda, int indice) {
        long clave = (long) ronda * 1_000_003L + indice;
        GeneradorLCG g = new GeneradorLCG(semilla ^ (clave * DORADO));
        g.siguienteLong(); // se descarta la primera salida
        return g.siguienteDouble();
    }
}
