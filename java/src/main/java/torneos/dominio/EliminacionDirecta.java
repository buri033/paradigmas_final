package torneos.dominio;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import torneos.modelo.Emparejamiento;
import torneos.modelo.Emparejamiento.Bye;
import torneos.modelo.Emparejamiento.Pareja;
import torneos.modelo.ErrorDatos;
import torneos.modelo.Jugador;
import torneos.modelo.Partida;
import torneos.modelo.Resultado;
import torneos.modelo.Resultados.ResultadoEliminacion;
import torneos.modelo.TipoError;

/**
 * Torneo por ELIMINACIÓN DIRECTA.
 * Cada ronda es una transformación pura del estado anterior (sin mutación compartida):
 *   vivos -> ordenar por Elo -> emparejar (1v2, 3v4, ..., BYE al último si es impar)
 *         -> resolver cada llave -> ganadores pasan a la siguiente ronda.
 */
public final class EliminacionDirecta {
    private EliminacionDirecta() { }

    /** Estado inmutable entre rondas. */
    record Estado(List<Jugador> vivos, List<Jugador> eliminados, List<List<Partida>> rondas) { }

    /** Resultado de resolver una llave. */
    record ResultadoLlave(Jugador ganador, Optional<Jugador> perdedor, Partida partida) { }

    public static ResultadoEliminacion jugar(List<Jugador> jugadores, long semilla) {
        if (jugadores.size() < 2) {
            throw new ErrorDatos(TipoError.TORNEO_INSUFICIENTE,
                    "Eliminacion directa: se requieren al menos 2 jugadores (hay " + jugadores.size() + ")");
        }
        Estado inicial = new Estado(jugadores, List.of(), List.of());
        // Stream.iterate produce la secuencia de estados ronda a ronda; se toma el primero con un solo vivo.
        Estado fin = Stream.iterate(inicial, e -> siguienteRonda(e, semilla))
                .dropWhile(e -> e.vivos().size() > 1)
                .findFirst()
                .orElseThrow();
        Jugador campeon = fin.vivos().get(0);
        return new ResultadoEliminacion(Optional.of(campeon), fin.rondas(),
                Stream.concat(fin.eliminados().stream(), Stream.of(campeon)).toList());
    }

    static Estado siguienteRonda(Estado e, long semilla) {
        int ronda = e.rondas().size() + 1;
        List<Emparejamiento> llaves = Matchmaking.emparejar(Matchmaking.ordenar(e.vivos()));
        List<ResultadoLlave> resultados = IntStream.range(0, llaves.size())
                .mapToObj(i -> resolver(llaves.get(i), ronda, i, semilla))
                .toList();
        List<Jugador> ganadores = resultados.stream().map(ResultadoLlave::ganador).toList();
        List<Jugador> perdedores = resultados.stream().map(ResultadoLlave::perdedor).flatMap(Optional::stream).toList();
        List<Partida> partidas = resultados.stream().map(ResultadoLlave::partida)
                .filter(p -> p.resultado() != Resultado.BYE).toList();
        return new Estado(ganadores,
                Stream.concat(e.eliminados().stream(), perdedores.stream()).toList(),
                Stream.concat(e.rondas().stream(), Stream.of(partidas)).toList());
    }

    /** Resuelve una llave. Función pura: el aleatorio depende solo de (semilla, ronda, índice). */
    static ResultadoLlave resolver(Emparejamiento llave, int ronda, int indice, long semilla) {
        if (llave instanceof Pareja p) {
            Jugador a = p.a();
            Jugador b = p.b();
            double u = GeneradorLCG.aleatorioPartida(semilla, ronda, indice);
            boolean ganaA = u < Elo.esperado(a.elo(), b.elo());
            long delta = Elo.delta(a.elo(), b.elo(), ganaA ? 1.0 : 0.0);
            Jugador a2 = ganaA ? a.conResultado(delta, 1, 0, 0, 3) : a.conResultado(delta, 0, 1, 0, 0);
            Jugador b2 = ganaA ? b.conResultado(-delta, 0, 1, 0, 0) : b.conResultado(-delta, 1, 0, 0, 3);
            Partida partida = new Partida(a.id(), b.id(), ganaA ? Resultado.GANA_A : Resultado.GANA_B, ronda, delta);
            return new ResultadoLlave(ganaA ? a2 : b2, Optional.of(ganaA ? b2 : a2), partida);
        }
        Jugador solo = ((Bye) llave).jugador();
        return new ResultadoLlave(solo, Optional.empty(), new Partida(solo.id(), 0, Resultado.BYE, ronda, 0));
    }
}
