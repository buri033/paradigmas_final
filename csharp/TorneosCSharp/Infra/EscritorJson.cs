using System.Globalization;
using System.Text;
using Torneos.Modelo;

namespace Torneos.Infra;

/// <summary>Datos de la medición que acompañan al resultado en el JSON.</summary>
public sealed record Metricas(string Lenguaje, string Version, string Entrada, string Modo, long Semilla, int Warmup, int Runs,
    IReadOnlyDictionary<string, IReadOnlyList<double>> Tiempos, long PicoHeap, long PicoProceso,
    double PartidasPorSeg, double JugadoresPorSeg, bool InvariantesOk);

/// <summary>BORDE de salida: JSON de métricas con el mismo esquema en los 3 lenguajes.</summary>
public static class EscritorJson
{
    public static readonly string[] Fases = ["carga", "matchmaking", "eliminacion", "liga", "estadisticas", "total"];

    private static string Texto(string s) => "\"" + s.Replace("\\", "\\\\").Replace("\"", "\\\"").Replace("\n", "\\n")
        .Replace("\r", "\\r").Replace("\t", "\\t") + "\"";

    private static string Num(double d) => d.ToString("F4", CultureInfo.InvariantCulture);

    private static string Arreglo(IEnumerable<string> elementos) => "[" + string.Join(", ", elementos) + "]";

    /// <summary>Mediana, media, desviación estándar muestral, mínimo y máximo.</summary>
    public static (double Mediana, double Media, double Desviacion, double Min, double Max) Resumir(IReadOnlyList<double> v)
    {
        var o = v.Order().ToList();
        int n = o.Count;
        if (n == 0) return (0, 0, 0, 0, 0);
        double media = o.Average();
        double mediana = n % 2 == 1 ? o[n / 2] : (o[n / 2 - 1] + o[n / 2]) / 2.0;
        double desv = n < 2 ? 0 : Math.Sqrt(o.Sum(x => (x - media) * (x - media)) / (n - 1));
        return (mediana, media, desv, o[0], o[^1]);
    }

    private static string Resumen(IReadOnlyList<double> v)
    {
        var r = Resumir(v);
        return $"{{\"mediana\": {Num(r.Mediana)}, \"media\": {Num(r.Media)}, \"desviacion\": {Num(r.Desviacion)}, \"min\": {Num(r.Min)}, \"max\": {Num(r.Max)}}}";
    }

    private static string Jugador(Jugador j) =>
        $"{{\"id\": {j.Id}, \"nombre\": {Texto(j.Nombre)}, \"elo\": {Texto(SalidaNormalizada.Elo(j.Elo))}, \"puntos\": {j.Puntos}, " +
        $"\"victorias\": {j.Victorias}, \"empates\": {j.Empates}, \"derrotas\": {j.Derrotas}}}";

    private static string Partida(Partida p) =>
        $"{{\"a\": {p.JugadorA}, \"b\": {p.JugadorB}, \"resultado\": {Texto(p.Resultado.ToString())}, \"ganador\": {p.Ganador}, " +
        $"\"deltaEloA\": {Texto(SalidaNormalizada.Elo(p.DeltaEloA))}}}";

    public static string Construir(Metricas m, ResultadoCompleto r, string salida, string hash)
    {
        var porTipo = r.Errores.GroupBy(e => e.Tipo).ToDictionary(g => g.Key, g => g.Count());
        bool detalle = r.Carga.Jugadores.Count <= 128;
        Func<IEnumerable<Jugador>, string> lista = l => Arreglo(l.Select(Jugador));
        var tiempos = "{" + string.Join(", ", Fases.Select(f => $"{Texto(f)}: {Arreglo(m.Tiempos[f].Select(Num))}")) + "}";
        var resumen = "{" + string.Join(", ", Fases.Select(f => $"{Texto(f)}: {Resumen(m.Tiempos[f])}")) + "}";
        var tipos = "{" + string.Join(", ", Enum.GetValues<TipoError>().Select(t => $"{Texto(t.ToString())}: {porTipo.GetValueOrDefault(t, 0)}")) + "}";
        var detalleErrores = Arreglo(r.Errores.Take(100).Select(e => $"{{\"tipo\": {Texto(e.Tipo.ToString())}, \"mensaje\": {Texto(e.Mensaje)}}}"));
        var llaves = detalle ? Arreglo(r.Eliminacion.Rondas.Select(ronda => Arreglo(ronda.Select(Partida)))) : "[]";
        var participantes = detalle ? lista(r.Carga.Jugadores) : "[]";
        var tablaG1 = r.Liga.Tablas.Count == 0 ? "[]" : lista(r.Liga.Tablas[0]);
        var stats = Arreglo(r.Estadisticas.Grupos.Select(g =>
            $"{{\"rango\": {Texto(g.Rango)}, \"cantidad\": {g.Cantidad}, \"promedioElo\": {Texto(SalidaNormalizada.Elo(g.PromedioElo))}, \"victorias\": {g.Victorias}}}"));
        var campeon = r.Eliminacion.Campeon is { } c ? Jugador(c) : "null";
        return new StringBuilder()
            .Append("{\n")
            .Append($"  \"lenguaje\": {Texto(m.Lenguaje)},\n")
            .Append($"  \"version\": {Texto(m.Version)},\n")
            .Append($"  \"entrada\": {Texto(m.Entrada)},\n")
            .Append($"  \"modo\": {Texto(m.Modo)},\n")
            .Append($"  \"semilla\": {m.Semilla},\n")
            .Append($"  \"warmup\": {m.Warmup},\n")
            .Append($"  \"runs\": {m.Runs},\n")
            .Append($"  \"tiemposMs\": {tiempos},\n")
            .Append($"  \"resumenMs\": {resumen},\n")
            .Append($"  \"memoria\": {{\"picoHeapMB\": {Num(m.PicoHeap / 1048576.0)}, \"picoProcesoMB\": {Num(m.PicoProceso / 1048576.0)}}},\n")
            .Append($"  \"throughput\": {{\"partidasPorSeg\": {Num(m.PartidasPorSeg)}, \"jugadoresPorSeg\": {Num(m.JugadoresPorSeg)}}},\n")
            .Append($"  \"conteos\": {{\"jugadoresValidos\": {r.Carga.Jugadores.Count}, \"partidasEliminacion\": {r.Eliminacion.Partidas}, \"partidasLiga\": {r.Liga.Partidas}}},\n")
            .Append($"  \"errores\": {{\"total\": {r.Errores.Count}, \"porTipo\": {tipos}, \"detalle\": {detalleErrores}}},\n")
            .Append($"  \"resultado\": {{\"campeon\": {campeon}, \"rondas\": {r.Eliminacion.Rondas.Count}, \"gruposLiga\": {r.Liga.Tablas.Count}, ")
            .Append($"\"tablaG1\": {tablaG1}, \"top10\": {lista(r.Estadisticas.Top)}, \"estadisticas\": {stats}, ")
            .Append($"\"participantes\": {participantes}, \"llaves\": {llaves}}},\n")
            .Append($"  \"invariantesOk\": {(m.InvariantesOk ? "true" : "false")},\n")
            .Append($"  \"salidaNormalizada\": {Texto(salida.Length > 20000 ? salida[..20000] : salida)},\n")
            .Append($"  \"hashSalida\": {Texto(hash)}\n")
            .Append("}\n")
            .ToString();
    }

    public static void Escribir(string ruta, string contenido)
    {
        try
        {
            var carpeta = Path.GetDirectoryName(Path.GetFullPath(ruta));
            if (!string.IsNullOrEmpty(carpeta)) Directory.CreateDirectory(carpeta);
            File.WriteAllText(ruta, contenido, new UTF8Encoding(false));
        }
        catch (IOException e)
        {
            Console.Error.WriteLine($"No se pudo escribir {ruta}: {e.Message}");
        }
    }
}
