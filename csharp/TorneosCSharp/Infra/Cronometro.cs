using System.Diagnostics;

namespace Torneos.Infra;

/// <summary>BORDE de medición: Stopwatch por fase y muestreo de memoria administrada al terminar cada fase.</summary>
public sealed class Cronometro
{
    private readonly Dictionary<string, double> _tiemposMs = new();

    public IReadOnlyDictionary<string, double> TiemposMs => _tiemposMs;

    public long PicoMuestreadoBytes { get; private set; }

    public T Medir<T>(string fase, Func<T> bloque)
    {
        var sw = Stopwatch.StartNew();
        try
        {
            return bloque();
        }
        finally
        {
            sw.Stop();
            _tiemposMs[fase] = sw.Elapsed.TotalMilliseconds;
            PicoMuestreadoBytes = Math.Max(PicoMuestreadoBytes, GC.GetTotalMemory(false));
        }
    }
}
