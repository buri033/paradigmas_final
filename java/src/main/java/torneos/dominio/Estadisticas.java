package torneos.dominio;

import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

import torneos.modelo.Jugador;
import torneos.modelo.Resultados.GrupoElo;
import torneos.modelo.Resultados.ResultadoEstadisticas;
import torneos.modelo.Resultados.ResultadoLiga;

/** Estadísticas funcionales: agrupación por rango, promedios, top N y máximo de victorias. */
public final class Estadisticas {
    private Estadisticas() { }

    public static final List<String> RANGOS = List.of("BRONCE", "PLATA", "ORO", "PLATINO");
    public static final int TOP_N = 10;

    /** Rango por Elo (centésimas): <1200 bronce, <1500 plata, <1800 oro, resto platino. */
    public static final Function<Long, String> RANGO = elo ->
            elo < 120_000 ? "BRONCE" : elo < 150_000 ? "PLATA" : elo < 180_000 ? "ORO" : "PLATINO";

    /** Más victorias; desempate por id menor. Operador asociativo -> apto para reduce. */
    static final BinaryOperator<Jugador> MAS_VICTORIAS = (a, b) ->
            a.victorias() > b.victorias() || (a.victorias() == b.victorias() && a.id() < b.id()) ? a : b;

    public static ResultadoEstadisticas calcular(List<Jugador> iniciales, ResultadoLiga liga) {
        Map<Integer, Jugador> filas = liga.tablas().stream()
                .flatMap(List::stream)
                .collect(Collectors.toMap(Jugador::id, Function.identity()));
        Map<String, LongSummaryStatistics> resumen = iniciales.stream()
                .collect(Collectors.groupingBy(j -> RANGO.apply(j.elo()), Collectors.summarizingLong(Jugador::elo)));
        Map<String, Long> victorias = iniciales.stream()
                .collect(Collectors.groupingBy(j -> RANGO.apply(j.elo()),
                        Collectors.summingLong(j -> filas.containsKey(j.id()) ? filas.get(j.id()).victorias() : 0)));
        List<GrupoElo> grupos = RANGOS.stream()
                .map(r -> grupo(r, resumen.getOrDefault(r, new LongSummaryStatistics()), victorias.getOrDefault(r, 0L)))
                .toList();
        List<Jugador> top = iniciales.stream().sorted(Matchmaking.POR_ELO).limit(TOP_N).toList();
        Optional<Jugador> mas = filas.values().stream().reduce(MAS_VICTORIAS);
        return new ResultadoEstadisticas(grupos, top, mas);
    }

    /** Promedio redondeado a centésimas con aritmética entera: (2*suma + n) / (2*n). */
    static GrupoElo grupo(String rango, LongSummaryStatistics s, long victorias) {
        long n = s.getCount();
        long promedio = n == 0 ? 0 : (2 * s.getSum() + n) / (2 * n);
        return new GrupoElo(rango, n, promedio, victorias);
    }
}
