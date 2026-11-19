using System.Diagnostics;
using System.Globalization;
using System.Runtime.InteropServices;
using Torneos.Dominio;
using Torneos.Infra;

// Punto de entrada (CLI común a los 3 lenguajes):
//   dotnet TorneosCSharp.dll --input datos.csv --mode all --runs 10 --warmup 3 --out resultado.json

const string Lenguaje = "C#";

// --clave valor --clave valor ...  ->  diccionario
var a = Enumerable.Range(0, args.Length / 2)
    .Where(i => args[2 * i].StartsWith("--", StringComparison.Ordinal))
    .ToDictionary(i => args[2 * i][2..], i => args[2 * i + 1]);
string Arg(string clave, string porDefecto) => a.GetValueOrDefault(clave, porDefecto);

var entrada = Arg("input", "../datos/escenarios/pequeno.csv");
var salida = Arg("out", "resultado_csharp.json");
var modo = Arg("mode", "all");
int runs = int.Parse(Arg("runs", "10"), CultureInfo.InvariantCulture);
int warmup = int.Parse(Arg("warmup", "3"), CultureInfo.InvariantCulture);
long semilla = long.Parse(Arg("seed", "20261117"), CultureInfo.InvariantCulture);
int grupo = int.Parse(Arg("grupo", "64"), CultureInfo.InvariantCulture);

// Calentamiento (JIT por niveles): ejecuciones descartadas
Enumerable.Range(0, warmup).ToList().ForEach(_ => Pipeline.Ejecutar(entrada, semilla, grupo, new Cronometro()));
GC.Collect();
GC.WaitForPendingFinalizers();

// Ejecuciones medidas
var mediciones = Enumerable.Range(0, runs).Select(_ =>
{
    var c = new Cronometro();
    c.Medir("total", () => Pipeline.Ejecutar(entrada, semilla, grupo, c));
    return c;
}).ToList();
var r = Pipeline.Ejecutar(entrada, semilla, grupo, new Cronometro());

var tiempos = EscritorJson.Fases.ToDictionary(f => f,
    f => (IReadOnlyList<double>)mediciones.Select(c => c.TiemposMs.GetValueOrDefault(f, 0.0)).ToList());
double medianaTotalSeg = EscritorJson.Resumir(tiempos["total"]).Mediana / 1000.0;
long partidas = r.Eliminacion.Partidas + r.Liga.Partidas;
var texto = SalidaNormalizada.Generar(r);
var hash = SalidaNormalizada.Sha256(texto);
using var proceso = Process.GetCurrentProcess();

var metricas = new Metricas(Lenguaje, $"C# / {RuntimeInformation.FrameworkDescription}", entrada, modo, semilla, warmup, runs, tiempos,
    mediciones.Max(c => c.PicoMuestreadoBytes), proceso.PeakWorkingSet64,
    medianaTotalSeg > 0 ? partidas / medianaTotalSeg : 0, medianaTotalSeg > 0 ? r.Carga.Jugadores.Count / medianaTotalSeg : 0, Invariantes.Cumple(r));
EscritorJson.Escribir(salida, EscritorJson.Construir(metricas, r, texto, hash));

var eloPromedio = r.Carga.Jugadores.Count > 0 ? r.Carga.Jugadores.Average(j => j.Elo) / 100.0 : 0;
Console.WriteLine($"[{Lenguaje}] {entrada} | validos={r.Carga.Jugadores.Count} errores={r.Errores.Count} | " +
                  $"elo promedio={eloPromedio.ToString("F2", CultureInfo.InvariantCulture)} | invariantes={(metricas.InvariantesOk ? "OK" : "FALLAN")} | " +
                  $"campeon={r.Eliminacion.Campeon?.Id ?? 0} | " +
                  $"mediana total={(medianaTotalSeg * 1000).ToString("F2", CultureInfo.InvariantCulture)} ms | hash={hash[..16]}...");
