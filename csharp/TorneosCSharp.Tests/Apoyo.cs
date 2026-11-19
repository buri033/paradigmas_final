using Torneos.Modelo;

namespace Torneos.Tests;

internal static class Apoyo
{
    public const long Semilla = 20261117L;

    /// <summary>n jugadores con Elo 1000, 1010, 1020... (centésimas).</summary>
    public static IReadOnlyList<Jugador> Jugadores(int n) =>
        Enumerable.Range(1, n).Select(i => new Jugador(i, $"J{i}", 100_000L + 1_000L * i)).ToList();

    public static LecturaCsv Csv(params string[] lineas) =>
        new(lineas.Select((t, i) => new LineaCsv(i + 1, t)).ToList(), []);

    public static int Conteo(ResultadoCarga r, TipoError tipo) => r.Errores.Count(e => e.Tipo == tipo);
}
