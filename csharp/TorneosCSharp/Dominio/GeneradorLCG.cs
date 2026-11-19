namespace Torneos.Dominio;

/// <summary>
/// LCG de 64 bits PROPIO (mismo algoritmo y constantes que Java y Scala). No se usa System.Random
/// porque su secuencia es distinta a la de java.util.Random y scala.util.Random.
/// estado' = estado * 6364136223846793005 + 1442695040888963407 (mod 2^64); double = (estado >> 11) * 2^-53
/// </summary>
public sealed class GeneradorLCG(ulong semilla)
{
    private const ulong A = 6364136223846793005UL;
    private const ulong C = 1442695040888963407UL;
    private const ulong Dorado = 0x9E3779B97F4A7C15UL;
    private const double Escala = 1.0 / 9007199254740992.0; // 2^-53

    // Única mutación del dominio: el estado interno encapsulado del generador.
    private ulong _estado = semilla;

    public ulong SiguienteLong()
    {
        unchecked { _estado = _estado * A + C; } // ulong desborda módulo 2^64
        return _estado;
    }

    public double SiguienteDouble() => (SiguienteLong() >> 11) * Escala;

    /// <summary>Aleatorio de una partida: función pura de (semilla, ronda, índice).</summary>
    public static double AleatorioPartida(long semilla, int ronda, int indice)
    {
        ulong clave = unchecked((ulong)((long)ronda * 1_000_003L + indice));
        var g = new GeneradorLCG(unchecked((ulong)semilla ^ (clave * Dorado)));
        g.SiguienteLong();
        return g.SiguienteDouble();
    }
}
