using Torneos.Modelo;

namespace Torneos.Dominio;

/// <summary>Matchmaking por cercanía de nivel: 1º vs 2º, 3º vs 4º...; impar => el último recibe BYE.</summary>
public static class Matchmaking
{
    /// <summary>Orden total determinista: Elo descendente, desempate por id ascendente (OrderBy es estable).</summary>
    public static IReadOnlyList<Jugador> Ordenar(IEnumerable<Jugador> jugadores) =>
        jugadores.OrderByDescending(j => j.Elo).ThenBy(j => j.Id).ToList();

    public static IReadOnlyList<Emparejamiento> Emparejar(IReadOnlyList<Jugador> ordenados) =>
        ordenados.Chunk(2)
            .Select(par => par.Length == 2 ? (Emparejamiento)new Pareja(par[0], par[1]) : new Bye(par[0]))
            .ToList();

    public static ResultadoMatchmaking Ejecutar(IReadOnlyList<Jugador> jugadores) => new(Emparejar(Ordenar(jugadores)));
}
