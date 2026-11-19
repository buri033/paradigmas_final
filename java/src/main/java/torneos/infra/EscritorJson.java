package torneos.infra;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import torneos.modelo.Jugador;
import torneos.modelo.Partida;
import torneos.modelo.RegistroError;
import torneos.modelo.Resultados.ResultadoCompleto;
import torneos.modelo.TipoError;

/** BORDE de salida: construye el JSON de métricas (mismo esquema en los 3 lenguajes) y lo escribe. */
public final class EscritorJson {
    private EscritorJson() { }

    public static final List<String> FASES = List.of("carga", "matchmaking", "eliminacion", "liga", "estadisticas", "total");

    static String texto(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    static String num(double d) {
        return String.format(Locale.ROOT, "%.4f", d);
    }

    static String arreglo(List<String> elementos) {
        return "[" + String.join(", ", elementos) + "]";
    }

    static String objeto(Map<String, String> campos) {
        return "{" + campos.entrySet().stream().map(e -> texto(e.getKey()) + ": " + e.getValue())
                .collect(Collectors.joining(", ")) + "}";
    }

    /** mediana, media, desviación estándar muestral, mínimo y máximo. */
    public static String resumen(List<Double> v) {
        List<Double> o = v.stream().sorted().toList();
        int n = o.size();
        double media = o.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double mediana = n == 0 ? 0 : n % 2 == 1 ? o.get(n / 2) : (o.get(n / 2 - 1) + o.get(n / 2)) / 2.0;
        double desv = n < 2 ? 0 : Math.sqrt(o.stream().mapToDouble(x -> (x - media) * (x - media)).sum() / (n - 1));
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("mediana", num(mediana));
        campos.put("media", num(media));
        campos.put("desviacion", num(desv));
        campos.put("min", num(n == 0 ? 0 : o.get(0)));
        campos.put("max", num(n == 0 ? 0 : o.get(n - 1)));
        return objeto(campos);
    }

    static String jugador(Jugador j) {
        return "{\"id\": " + j.id() + ", \"nombre\": " + texto(j.nombre()) + ", \"elo\": " + texto(SalidaNormalizada.elo(j.elo()))
                + ", \"puntos\": " + j.puntos() + ", \"victorias\": " + j.victorias() + ", \"empates\": " + j.empates()
                + ", \"derrotas\": " + j.derrotas() + "}";
    }

    static String partida(Partida p) {
        return "{\"a\": " + p.jugadorA() + ", \"b\": " + p.jugadorB() + ", \"resultado\": " + texto(p.resultado().name())
                + ", \"ganador\": " + p.ganador() + ", \"deltaEloA\": " + texto(SalidaNormalizada.elo(p.deltaEloA())) + "}";
    }

    public static String construir(Map<String, Object> m, ResultadoCompleto r, String salida, String hash) {
        @SuppressWarnings("unchecked")
        Map<String, List<Double>> tiempos = (Map<String, List<Double>>) m.get("tiempos");
        Map<TipoError, Long> porTipo = r.errores().stream()
                .collect(Collectors.groupingBy(RegistroError::tipo, Collectors.counting()));
        int validos = r.carga().jugadores().size();
        boolean detalle = validos <= 128;
        String tiemposJson = "{" + FASES.stream().map(f -> texto(f) + ": " + arreglo(tiempos.get(f).stream().map(EscritorJson::num).toList()))
                .collect(Collectors.joining(", ")) + "}";
        String resumenJson = "{" + FASES.stream().map(f -> texto(f) + ": " + resumen(tiempos.get(f)))
                .collect(Collectors.joining(", ")) + "}";
        String tiposJson = "{" + IntStream.range(0, TipoError.values().length).mapToObj(i -> TipoError.values()[i])
                .map(t -> texto(t.name()) + ": " + porTipo.getOrDefault(t, 0L)).collect(Collectors.joining(", ")) + "}";
        String detalleErrores = arreglo(r.errores().stream().limit(100)
                .map(e -> "{\"tipo\": " + texto(e.tipo().name()) + ", \"mensaje\": " + texto(e.mensaje()) + "}").toList());
        Function<List<Jugador>, String> lista = l -> arreglo(l.stream().map(EscritorJson::jugador).toList());
        String llaves = detalle ? arreglo(r.eliminacion().rondas().stream()
                .map(ronda -> arreglo(ronda.stream().map(EscritorJson::partida).toList())).toList()) : "[]";
        String participantes = detalle ? lista.apply(r.carga().jugadores()) : "[]";
        String tablaG1 = r.liga().tablas().isEmpty() ? "[]" : lista.apply(r.liga().tablas().get(0));
        String stats = arreglo(r.estadisticas().grupos().stream()
                .map(g -> "{\"rango\": " + texto(g.rango()) + ", \"cantidad\": " + g.cantidad() + ", \"promedioElo\": "
                        + texto(SalidaNormalizada.elo(g.promedioElo())) + ", \"victorias\": " + g.victorias() + "}").toList());
        String campeon = r.eliminacion().campeon().map(EscritorJson::jugador).orElse("null");
        return "{\n"
                + "  \"lenguaje\": " + texto((String) m.get("lenguaje")) + ",\n"
                + "  \"version\": " + texto((String) m.get("version")) + ",\n"
                + "  \"entrada\": " + texto((String) m.get("entrada")) + ",\n"
                + "  \"modo\": " + texto((String) m.get("modo")) + ",\n"
                + "  \"semilla\": " + m.get("semilla") + ",\n"
                + "  \"warmup\": " + m.get("warmup") + ",\n"
                + "  \"runs\": " + m.get("runs") + ",\n"
                + "  \"tiemposMs\": " + tiemposJson + ",\n"
                + "  \"resumenMs\": " + resumenJson + ",\n"
                + "  \"memoria\": {\"picoHeapMB\": " + num((Long) m.get("picoHeap") / 1048576.0)
                + ", \"picoProcesoMB\": " + num((Long) m.get("picoProceso") / 1048576.0) + "},\n"
                + "  \"throughput\": {\"partidasPorSeg\": " + num((Double) m.get("partidasPorSeg"))
                + ", \"jugadoresPorSeg\": " + num((Double) m.get("jugadoresPorSeg")) + "},\n"
                + "  \"conteos\": {\"jugadoresValidos\": " + validos
                + ", \"partidasEliminacion\": " + r.eliminacion().partidas()
                + ", \"partidasLiga\": " + r.liga().partidas() + "},\n"
                + "  \"errores\": {\"total\": " + r.errores().size() + ", \"porTipo\": " + tiposJson
                + ", \"detalle\": " + detalleErrores + "},\n"
                + "  \"resultado\": {\"campeon\": " + campeon + ", \"rondas\": " + r.eliminacion().rondas().size()
                + ", \"gruposLiga\": " + r.liga().tablas().size() + ", \"tablaG1\": " + tablaG1
                + ", \"top10\": " + lista.apply(r.estadisticas().top()) + ", \"estadisticas\": " + stats
                + ", \"participantes\": " + participantes + ", \"llaves\": " + llaves + "},\n"
                + "  \"invariantesOk\": " + m.get("invariantes") + ",\n"
                + "  \"salidaNormalizada\": " + texto(salida.length() > 20000 ? salida.substring(0, 20000) : salida) + ",\n"
                + "  \"hashSalida\": " + texto(hash) + "\n"
                + "}\n";
    }

    public static void escribir(String ruta, String contenido) {
        try {
            Path p = Path.of(ruta);
            if (p.getParent() != null) {
                Files.createDirectories(p.getParent());
            }
            Files.writeString(p, contenido, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("No se pudo escribir " + ruta + ": " + e.getMessage());
        }
    }
}
