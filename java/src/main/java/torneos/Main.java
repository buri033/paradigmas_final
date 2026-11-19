package torneos;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import torneos.dominio.Invariantes;
import torneos.dominio.Pipeline;
import torneos.infra.Cronometro;
import torneos.infra.EscritorJson;
import torneos.infra.Memoria;
import torneos.infra.SalidaNormalizada;
import torneos.modelo.Resultados.ResultadoCompleto;

/**
 * Punto de entrada (CLI común a los 3 lenguajes):
 *   java -jar torneos-java.jar --input datos.csv --mode all --runs 10 --warmup 3 --out resultado.json
 */
public final class Main {
    private Main() { }

    public static final String LENGUAJE = "Java";

    static Map<String, String> argumentos(String[] args) {
        return IntStream.range(0, args.length / 2).boxed()
                .filter(i -> args[2 * i].startsWith("--"))
                .collect(Collectors.toMap(i -> args[2 * i].substring(2), i -> args[2 * i + 1], (a, b) -> b, HashMap::new));
    }

    public static void main(String[] args) {
        Map<String, String> a = argumentos(args);
        String entrada = a.getOrDefault("input", "../datos/escenarios/pequeno.csv");
        String salida = a.getOrDefault("out", "resultado_java.json");
        String modo = a.getOrDefault("mode", "all");
        int runs = Integer.parseInt(a.getOrDefault("runs", "10"));
        int warmup = Integer.parseInt(a.getOrDefault("warmup", "3"));
        long semilla = Long.parseLong(a.getOrDefault("seed", "20261117"));
        int grupo = Integer.parseInt(a.getOrDefault("grupo", "64"));

        // Calentamiento (JIT): ejecuciones descartadas. El bucle de efectos se expresa con forEach.
        IntStream.range(0, warmup).forEach(i -> Pipeline.ejecutar(entrada, semilla, grupo, new Cronometro()));
        System.gc();
        Memoria.reiniciarPicos();

        // Ejecuciones medidas: cada una devuelve su cronómetro (tiempos por fase + total).
        List<Cronometro> mediciones = IntStream.range(0, runs).mapToObj(i -> {
            Cronometro c = new Cronometro();
            c.medir("total", () -> Pipeline.ejecutar(entrada, semilla, grupo, c));
            return c;
        }).toList();
        ResultadoCompleto r = Pipeline.ejecutar(entrada, semilla, grupo, new Cronometro());

        Map<String, List<Double>> tiempos = EscritorJson.FASES.stream().collect(Collectors.toMap(
                f -> f, f -> mediciones.stream().map(c -> c.tiemposMs().getOrDefault(f, 0.0)).toList(),
                (x, y) -> x, LinkedHashMap::new));
        double medianaTotalSeg = mediana(tiempos.get("total")) / 1000.0;
        long partidas = r.eliminacion().partidas() + r.liga().partidas();
        long picoHeap = Math.max(Memoria.picoHeapPoolsBytes(),
                mediciones.stream().mapToLong(Cronometro::picoMuestreadoBytes).max().orElse(0));
        String texto = SalidaNormalizada.generar(r);
        String hash = SalidaNormalizada.sha256(texto);

        Map<String, Object> m = new HashMap<>();
        m.put("lenguaje", LENGUAJE);
        m.put("version", "Java " + System.getProperty("java.version") + " (" + System.getProperty("java.vm.name") + ")");
        m.put("entrada", entrada);
        m.put("modo", modo);
        m.put("semilla", semilla);
        m.put("warmup", warmup);
        m.put("runs", runs);
        m.put("tiempos", tiempos);
        m.put("picoHeap", picoHeap);
        m.put("picoProceso", Memoria.picoProcesoBytes());
        m.put("partidasPorSeg", medianaTotalSeg > 0 ? partidas / medianaTotalSeg : 0.0);
        m.put("invariantes", Invariantes.cumple(r));
        m.put("jugadoresPorSeg", medianaTotalSeg > 0 ? r.carga().jugadores().size() / medianaTotalSeg : 0.0);
        EscritorJson.escribir(salida, EscritorJson.construir(m, r, texto, hash));

        System.out.println("[" + LENGUAJE + "] " + entrada + " | validos=" + r.carga().jugadores().size()
                + " errores=" + r.errores().size()
                + " | elo promedio=" + String.format(java.util.Locale.ROOT, "%.2f", Invariantes.eloPromedio(r.carga().jugadores()))
                + " | invariantes=" + (Invariantes.cumple(r) ? "OK" : "FALLAN") + " | campeon=" + r.eliminacion().campeon().map(j -> j.id()).orElse(0)
                + " | mediana total=" + String.format(java.util.Locale.ROOT, "%.2f", medianaTotalSeg * 1000) + " ms"
                + " | hash=" + hash.substring(0, 16) + "...");
    }

    static double mediana(List<Double> v) {
        double[] o = v.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return o.length == 0 ? 0 : o.length % 2 == 1 ? o[o.length / 2] : (o[o.length / 2 - 1] + o[o.length / 2]) / 2.0;
    }
}
