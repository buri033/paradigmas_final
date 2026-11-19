using Torneos.Modelo;

namespace Torneos.Dominio;

/// <summary>
/// Torneo por ELIMINACIÓN DIRECTA: cada ronda es una transformación pura de la anterior
/// (ordenar -> emparejar -> resolver -> ganadores), sin estado compartido.
/// </summary>
public static class EliminacionDirecta
{
    private sealed record Estado(IReadOnlyList<Jugador> Vivos, IReadOnlyList<Jugador> Eliminados, IReadOnlyList<IReadOnlyList<Partida>> Rondas);

    private sealed record ResultadoLlave(Jugador Ganador, Jugador? Perdedor, Partida Partida);

    public static ResultadoEliminacion Jugar(IReadOnlyList<Jugador> jugadores, long semilla)
    {
        if (jugadores.Count < 2)
            throw new ErrorDatos(TipoError.TORNEO_INSUFICIENTE,
                $"Eliminacion directa: se requieren al menos 2 jugadores (hay {jugadores.Count})");
        var fin = JugarRondas(new Estado(jugadores, [], []), semilla);
        var campeon = fin.Vivos[0];
        return new ResultadoEliminacion(campeon, fin.Rondas, fin.Eliminados.Append(campeon).ToList());
    }

    // Recursión por rondas (profundidad = log2 n): termina cuando queda un solo vivo.
    private static Estado JugarRondas(Estado e, long semilla) =>
        e.Vivos.Count <= 1 ? e : JugarRondas(SiguienteRonda(e, semilla), semilla);

    private static Estado SiguienteRonda(Estado e, long semilla)
    {
        int ronda = e.Rondas.Count + 1;
        var resultados = Matchmaking.Emparejar(Matchmaking.Ordenar(e.Vivos))
            .Select((llave, i) => Resolver(llave, ronda, i, semilla))
            .ToList();
        return new Estado(
            resultados.Select(r => r.Ganador).ToList(),
            e.Eliminados.Concat(resultados.Where(r => r.Perdedor is not null).Select(r => r.Perdedor!)).ToList(),
            e.Rondas.Append(resultados.Where(r => r.Partida.Resultado != Resultado.BYE).Select(r => r.Partida).ToList()).ToList());
    }

    /// <summary>Pattern matching (switch expression) sobre el tipo de llave.</summary>
    private static ResultadoLlave Resolver(Emparejamiento llave, int ronda, int indice, long semilla) => llave switch
    {
        Pareja(var a, var b) => ResolverPareja(a, b, ronda, indice, semilla),
        Bye(var solo) => new ResultadoLlave(solo, null, new Partida(solo.Id, 0, Resultado.BYE, ronda, 0)),
        _ => throw new InvalidOperationException("Llave desconocida")
    };

    private static ResultadoLlave ResolverPareja(Jugador a, Jugador b, int ronda, int indice, long semilla)
    {
        double u = GeneradorLCG.AleatorioPartida(semilla, ronda, indice);
        bool ganaA = u < Elo.Esperado(a.Elo, b.Elo);
        long delta = Elo.Delta(a.Elo, b.Elo, ganaA ? 1.0 : 0.0);
        var a2 = ganaA ? a.ConResultado(delta, 1, 0, 0, 3) : a.ConResultado(delta, 0, 1, 0, 0);
        var b2 = ganaA ? b.ConResultado(-delta, 0, 1, 0, 0) : b.ConResultado(-delta, 1, 0, 0, 3);
        var partida = new Partida(a.Id, b.Id, ganaA ? Resultado.GANA_A : Resultado.GANA_B, ronda, delta);
        return ganaA ? new ResultadoLlave(a2, b2, partida) : new ResultadoLlave(b2, a2, partida);
    }
}
