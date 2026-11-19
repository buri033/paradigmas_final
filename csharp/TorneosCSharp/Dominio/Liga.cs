using Torneos.Modelo;

namespace Torneos.Dominio;

/// <summary>
/// Torneo por LIGA (todos contra todos) en grupos de tamaño fijo. Victoria 3, empate 1, derrota 0.
/// Elo "por lotes": cada partida usa el Elo inicial y las variaciones (enteras) se suman al final.
/// Desempate: puntos desc, Elo desc, id asc.
/// </summary>
public static class Liga
{
    public const double ProbEmpate = 0.10;

    private sealed record Aporte(int Id, int Puntos, int Victorias, int Empates, int Derrotas, long DeltaElo)
    {
        public Aporte Sumar(Aporte o) => new(Id, Puntos + o.Puntos, Victorias + o.Victorias, Empates + o.Empates,
            Derrotas + o.Derrotas, DeltaElo + o.DeltaElo);
    }

    private sealed record TablaGrupo(IReadOnlyList<Jugador> Tabla, long Partidas);

    public static ResultadoLiga Jugar(IReadOnlyList<Jugador> jugadores, long semilla, int tamGrupo)
    {
        if (jugadores.Count < 2)
            throw new ErrorDatos(TipoError.TORNEO_INSUFICIENTE, $"Liga: se requieren al menos 2 jugadores (hay {jugadores.Count})");
        var tablas = Matchmaking.Ordenar(jugadores)
            .Chunk(tamGrupo)
            .Select((grupo, g) => JugarGrupo(grupo, g, semilla))
            .ToList();
        return new ResultadoLiga(tablas.Select(t => t.Tabla).ToList(), tablas.Sum(t => t.Partidas));
    }

    /// <summary>Todos contra todos: (0,1),(0,2)...(1,2)... mismo orden en los tres lenguajes.</summary>
    public static IEnumerable<Pareja> GenerarEnfrentamientos(IReadOnlyList<Jugador> grupo) =>
        Enumerable.Range(0, grupo.Count)
            .SelectMany(i => Enumerable.Range(i + 1, grupo.Count - i - 1).Select(j => new Pareja(grupo[i], grupo[j])));

    private static TablaGrupo JugarGrupo(IReadOnlyList<Jugador> grupo, int g, long semilla)
    {
        var partidas = GenerarEnfrentamientos(grupo).Select((par, k) => JugarPartida(par, g, k, semilla)).ToList();
        var aportes = partidas.SelectMany(Aportes)
            .GroupBy(a => a.Id)
            .ToDictionary(gr => gr.Key, gr => gr.Aggregate((x, y) => x.Sumar(y)));
        var tabla = grupo
            .Select(j => Aplicar(j, aportes.GetValueOrDefault(j.Id, new Aporte(j.Id, 0, 0, 0, 0, 0))))
            .OrderByDescending(j => j.Puntos).ThenByDescending(j => j.Elo).ThenBy(j => j.Id)
            .ToList();
        return new TablaGrupo(tabla, partidas.Count);
    }

    public static Partida JugarPartida(Pareja par, int grupo, int indice, long semilla)
    {
        double u = GeneradorLCG.AleatorioPartida(semilla, 100_000 + grupo, indice);
        double e = Elo.Esperado(par.A.Elo, par.B.Elo);
        var r = u < ProbEmpate ? Resultado.EMPATE
            : u < ProbEmpate + (1.0 - ProbEmpate) * e ? Resultado.GANA_A : Resultado.GANA_B;
        double s = r switch { Resultado.GANA_A => 1.0, Resultado.EMPATE => 0.5, _ => 0.0 };
        return new Partida(par.A.Id, par.B.Id, r, grupo + 1, Elo.Delta(par.A.Elo, par.B.Elo, s));
    }

    public static int Puntos(Resultado propio) => propio switch { Resultado.GANA_A => 3, Resultado.EMPATE => 1, _ => 0 };

    private static IEnumerable<Aporte> Aportes(Partida p)
    {
        var paraB = p.Resultado switch { Resultado.GANA_A => Resultado.GANA_B, Resultado.GANA_B => Resultado.GANA_A, var x => x };
        return [Crear(p.JugadorA, p.Resultado, p.DeltaEloA), Crear(p.JugadorB, paraB, -p.DeltaEloA)];
    }

    // "GANA_A" se interpreta como "gana el jugador del que hablamos"
    private static Aporte Crear(int id, Resultado propio, long delta) => new(id, Puntos(propio),
        propio == Resultado.GANA_A ? 1 : 0, propio == Resultado.EMPATE ? 1 : 0, propio == Resultado.GANA_B ? 1 : 0, delta);

    private static Jugador Aplicar(Jugador j, Aporte a) => j.ConResultado(a.DeltaElo, a.Victorias, a.Derrotas, a.Empates, a.Puntos);
}
