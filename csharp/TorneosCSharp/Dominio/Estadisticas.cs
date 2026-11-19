using Torneos.Modelo;

namespace Torneos.Dominio;

/// <summary>Estadísticas con LINQ: GroupBy por rango, promedios, Take para el top y Aggregate para el máximo.</summary>
public static class Estadisticas
{
    public static readonly string[] Rangos = ["BRONCE", "PLATA", "ORO", "PLATINO"];
    public const int TopN = 10;

    public static readonly Func<long, string> Rango = elo => elo switch
    {
        < 120_000 => "BRONCE",
        < 150_000 => "PLATA",
        < 180_000 => "ORO",
        _ => "PLATINO"
    };

    /// <summary>Más victorias; desempate por id menor (operador asociativo para Aggregate).</summary>
    private static readonly Func<Jugador, Jugador, Jugador> MasVictorias = (a, b) =>
        a.Victorias > b.Victorias || (a.Victorias == b.Victorias && a.Id < b.Id) ? a : b;

    public static ResultadoEstadisticas Calcular(IReadOnlyList<Jugador> iniciales, ResultadoLiga liga)
    {
        var filas = liga.Tablas.SelectMany(t => t).ToDictionary(j => j.Id);
        var porRango = iniciales
            .GroupBy(j => Rango(j.Elo))
            .ToDictionary(g => g.Key, g => (Cantidad: g.LongCount(), Suma: g.Sum(j => j.Elo),
                Victorias: g.Sum(j => (long)(filas.TryGetValue(j.Id, out var f) ? f.Victorias : 0))));
        var grupos = Rangos
            .Select(r => porRango.TryGetValue(r, out var t)
                ? new GrupoElo(r, t.Cantidad, (2 * t.Suma + t.Cantidad) / (2 * t.Cantidad), t.Victorias)
                : new GrupoElo(r, 0, 0, 0))
            .ToList();
        var top = iniciales.OrderByDescending(j => j.Elo).ThenBy(j => j.Id).Take(TopN).ToList();
        var mas = filas.Values.Any() ? filas.Values.Aggregate(MasVictorias) : null;
        return new ResultadoEstadisticas(grupos, top, mas);
    }
}
