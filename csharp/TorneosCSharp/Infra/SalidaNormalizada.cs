using System.Security.Cryptography;
using System.Text;
using Torneos.Modelo;

namespace Torneos.Infra;

/// <summary>Texto canónico (idéntico byte a byte en Java, Scala y C#) y su SHA-256.</summary>
public static class SalidaNormalizada
{
    public static string Elo(long c)
    {
        long abs = Math.Abs(c);
        return (c < 0 ? "-" : "") + (abs / 100) + "." + (abs % 100 < 10 ? "0" : "") + (abs % 100);
    }

    public static long ChecksumEliminacion(ResultadoEliminacion e) => e.Finales.Sum(j => j.Elo * (j.Id % 1000 + 1));

    public static long ChecksumLiga(ResultadoLiga l) =>
        l.Tablas.SelectMany(t => t).Sum(j => j.Elo * (j.Id % 1000 + 1) + (long)j.Puntos * (j.Id % 97 + 1));

    public static string Generar(ResultadoCompleto r)
    {
        var porTipo = r.Errores.GroupBy(e => e.Tipo).ToDictionary(g => g.Key, g => g.LongCount());
        var e = r.Eliminacion;
        var l = r.Liga;
        var tipos = string.Join(";", Enum.GetValues<TipoError>().Select(t => $"{t}:{porTipo.GetValueOrDefault(t, 0)}"));
        var tablaG1 = l.Tablas.Count == 0 ? "" : string.Join(",", l.Tablas[0]
            .Select(j => $"{j.Id}:{j.Puntos}:{j.Victorias}:{j.Empates}:{j.Derrotas}:{Elo(j.Elo)}"));
        var lideres = string.Join(",", l.Tablas.Select(t => t[0].Id));
        var stats = string.Join(";", r.Estadisticas.Grupos.Select(g => $"{g.Rango}:{g.Cantidad}:{Elo(g.PromedioElo)}:{g.Victorias}"));
        var top = string.Join(",", r.Estadisticas.Top.Select(j => $"{j.Id}:{Elo(j.Elo)}"));
        var mas = r.Estadisticas.MasVictorias is { } m ? $"{m.Id}:{m.Victorias}" : "0:0";
        return new StringBuilder()
            .Append($"VALIDOS={r.Carga.Jugadores.Count}\n")
            .Append($"ERRORES={r.Errores.Count}\n")
            .Append($"ERRORES_TIPO={tipos}\n")
            .Append($"PAREJAS={r.Matchmaking.Parejas};BYES={r.Matchmaking.Byes}\n")
            .Append($"ELIM=RONDAS:{e.Rondas.Count};PARTIDAS:{e.Partidas};CAMPEON:{e.Campeon?.Id ?? 0};ELO:{Elo(e.Campeon?.Elo ?? 0)}\n")
            .Append($"LIGA=GRUPOS:{l.Tablas.Count};PARTIDAS:{l.Partidas}\n")
            .Append($"TABLA_G1={tablaG1}\n")
            .Append($"LIDERES={lideres}\n")
            .Append($"CHECK_ELIM={ChecksumEliminacion(e)}\n")
            .Append($"CHECK_LIGA={ChecksumLiga(l)}\n")
            .Append($"STATS={stats}\n")
            .Append($"TOP10={top}\n")
            .Append($"MAS_VICTORIAS={mas}\n")
            .ToString();
    }

    public static string Sha256(string texto) =>
        Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(texto))).ToLowerInvariant();
}
