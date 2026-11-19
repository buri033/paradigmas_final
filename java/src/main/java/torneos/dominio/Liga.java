package torneos.dominio;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import torneos.modelo.Emparejamiento.Pareja;
import torneos.modelo.ErrorDatos;
import torneos.modelo.Jugador;
import torneos.modelo.Partida;
import torneos.modelo.Resultado;
import torneos.modelo.Resultados.ResultadoLiga;
import torneos.modelo.TipoError;

/**
 * Torneo por LIGA (todos contra todos) en grupos de tamaño fijo (64 por defecto) para evitar O(n²).
 * Puntos: victoria = 3, empate = 1, derrota = 0.
 * Elo "por lotes" (como FIDE): cada partida usa el Elo con el que el jugador empezó la liga y
 * las variaciones se suman al final. Al ser enteros (centésimas), la suma es exacta en cualquier orden.
 * Desempate de la tabla: puntos desc, Elo desc, id asc.
 */
public final class Liga {
    private Liga() { }

    public static final double PROB_EMPATE = 0.10;

    public static final Comparator<Jugador> POR_TABLA = Comparator.comparingInt(Jugador::puntos).reversed()
            .thenComparing(Comparator.comparingLong(Jugador::elo).reversed())
            .thenComparingInt(Jugador::id);

    /** Lo que una partida le aporta a un jugador; se combina con una reducción asociativa. */
    record Aporte(int id, int puntos, int victorias, int empates, int derrotas, long deltaElo) {
        Aporte sumar(Aporte o) {
            return new Aporte(id, puntos + o.puntos, victorias + o.victorias, empates + o.empates,
                    derrotas + o.derrotas, deltaElo + o.deltaElo);
        }
    }

    record TablaGrupo(List<Jugador> tabla, long partidas) { }

    public static ResultadoLiga jugar(List<Jugador> jugadores, long semilla, int tamGrupo) {
        if (jugadores.size() < 2) {
            throw new ErrorDatos(TipoError.TORNEO_INSUFICIENTE,
                    "Liga: se requieren al menos 2 jugadores (hay " + jugadores.size() + ")");
        }
        List<Jugador> orden = Matchmaking.ordenar(jugadores);
        int grupos = (orden.size() + tamGrupo - 1) / tamGrupo;
        List<TablaGrupo> tablas = IntStream.range(0, grupos)
                .mapToObj(g -> jugarGrupo(orden.subList(g * tamGrupo, Math.min(orden.size(), (g + 1) * tamGrupo)), g, semilla))
                .toList();
        return new ResultadoLiga(tablas.stream().map(TablaGrupo::tabla).toList(),
                tablas.stream().mapToLong(TablaGrupo::partidas).sum());
    }

    /** Todos contra todos: (0,1),(0,2)...(0,n-1),(1,2)... mismo orden en los tres lenguajes. */
    public static List<Pareja> generarEnfrentamientos(List<Jugador> grupo) {
        return IntStream.range(0, grupo.size()).boxed()
                .flatMap(i -> IntStream.range(i + 1, grupo.size()).mapToObj(j -> new Pareja(grupo.get(i), grupo.get(j))))
                .toList();
    }

    static TablaGrupo jugarGrupo(List<Jugador> grupo, int g, long semilla) {
        List<Pareja> pares = generarEnfrentamientos(grupo);
        List<Partida> partidas = IntStream.range(0, pares.size())
                .mapToObj(k -> jugarPartida(pares.get(k), g, k, semilla))
                .toList();
        Map<Integer, Aporte> aportes = partidas.stream()
                .flatMap(Liga::aportes)
                .collect(Collectors.toMap(Aporte::id, Function.identity(), Aporte::sumar));
        List<Jugador> tabla = grupo.stream()
                .map(j -> aplicar(j, aportes.getOrDefault(j.id(), new Aporte(j.id(), 0, 0, 0, 0, 0))))
                .sorted(POR_TABLA)
                .toList();
        return new TablaGrupo(tabla, partidas.size());
    }

    public static Partida jugarPartida(Pareja par, int grupo, int indice, long semilla) {
        Jugador a = par.a();
        Jugador b = par.b();
        double u = GeneradorLCG.aleatorioPartida(semilla, 100_000 + grupo, indice);
        double e = Elo.esperado(a.elo(), b.elo());
        Resultado r = u < PROB_EMPATE ? Resultado.EMPATE
                : u < PROB_EMPATE + (1.0 - PROB_EMPATE) * e ? Resultado.GANA_A : Resultado.GANA_B;
        double s = switch (r) {
            case GANA_A -> 1.0;
            case EMPATE -> 0.5;
            default -> 0.0;
        };
        return new Partida(a.id(), b.id(), r, grupo + 1, Elo.delta(a.elo(), b.elo(), s));
    }

    public static int puntos(Resultado propio) {
        return switch (propio) {
            case GANA_A -> 3;
            case EMPATE -> 1;
            default -> 0;
        };
    }

    static Stream<Aporte> aportes(Partida p) {
        Resultado paraB = switch (p.resultado()) {
            case GANA_A -> Resultado.GANA_B;
            case GANA_B -> Resultado.GANA_A;
            default -> p.resultado();
        };
        return Stream.of(aporte(p.jugadorA(), p.resultado(), p.deltaEloA()),
                aporte(p.jugadorB(), paraB, -p.deltaEloA()));
    }

    /** "GANA_A" se interpreta como "gana el jugador del que hablamos". */
    static Aporte aporte(int id, Resultado propio, long delta) {
        return new Aporte(id, puntos(propio),
                propio == Resultado.GANA_A ? 1 : 0,
                propio == Resultado.EMPATE ? 1 : 0,
                propio == Resultado.GANA_B ? 1 : 0,
                delta);
    }

    static Jugador aplicar(Jugador j, Aporte a) {
        return j.conResultado(a.deltaElo(), a.victorias(), a.derrotas(), a.empates(), a.puntos());
    }
}
