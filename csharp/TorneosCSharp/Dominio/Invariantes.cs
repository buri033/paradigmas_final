using Torneos.Modelo;

namespace Torneos.Dominio;

/// <summary>Autoverificación del resultado (funciones puras con Zip, Skip, All y Any).</summary>
public static class Invariantes
{
    public static bool Cumple(ResultadoCompleto r)
    {
        int n = r.Carga.Jugadores.Count;
        bool[] comprobaciones =
        [
            n < 2 || r.Eliminacion.Partidas == n - 1,                                  // eliminación: n - 1 partidas
            r.Liga.Tablas.All(Ordenada),                                               // tablas bien ordenadas
            !r.Carga.Jugadores.Any(j => j.Elo <= 0),                                   // inválidos filtrados
            n < 2 || r.Liga.Tablas.SelectMany(t => t).Any(j => j.Puntos > 0)
        ];
        return comprobaciones.All(c => c);
    }

    private static bool Ordenada(IReadOnlyList<Jugador> t) =>
        t.Zip(t.Skip(1)).All(p => p.First.Puntos > p.Second.Puntos ||
                                  (p.First.Puntos == p.Second.Puntos && (p.First.Elo > p.Second.Elo ||
                                                                         (p.First.Elo == p.Second.Elo && p.First.Id < p.Second.Id))));
}
