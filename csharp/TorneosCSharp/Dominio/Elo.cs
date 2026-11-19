namespace Torneos.Dominio;

/// <summary>
/// Sistema Elo (funciones puras). E = 1 / (1 + 10^((Rb - Ra)/400)); R' = R + K (S - E), K = 32.
/// Elo en centésimas: (Rb - Ra)/400 = (rb - ra)/40000. Redondeo floor(x + 0.5) igual que Java y Scala.
/// </summary>
public static class Elo
{
    public const double K = 32.0;

    public static readonly Func<long, long, double> Esperado =
        (ra, rb) => 1.0 / (1.0 + Math.Pow(10.0, (rb - ra) / 40000.0));

    public static long Delta(long ra, long rb, double s) => (long)Math.Floor(K * (s - Esperado(ra, rb)) * 100.0 + 0.5);
}
