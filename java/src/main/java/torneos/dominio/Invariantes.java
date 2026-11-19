package torneos.dominio;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import torneos.modelo.Jugador;
import torneos.modelo.Resultados.ResultadoCompleto;

/** Autoverificación del resultado (funciones puras): si algo no cuadra, se reporta en el JSON. */
public final class Invariantes {
    private Invariantes() { }

    public static boolean cumple(ResultadoCompleto r) {
        int n = r.carga().jugadores().size();
        return Stream.of(
                n < 2 || r.eliminacion().partidas() == n - 1,                                  // eliminación: n - 1 partidas
                r.liga().tablas().stream().allMatch(Invariantes::ordenada),                    // tablas bien ordenadas
                r.carga().jugadores().stream().noneMatch(j -> j.elo() <= 0),                   // inválidos filtrados
                r.liga().tablas().stream().flatMap(List::stream).anyMatch(j -> j.puntos() > 0) || n < 2)
                .allMatch(Boolean::booleanValue);
    }

    static boolean ordenada(List<Jugador> t) {
        return IntStream.range(1, t.size()).allMatch(i -> Liga.POR_TABLA.compare(t.get(i - 1), t.get(i)) < 0);
    }

    /** Elo promedio (en puntos) con Collectors.averagingLong; solo informativo (no entra en el hash). */
    public static double eloPromedio(List<Jugador> jugadores) {
        return jugadores.stream().collect(Collectors.averagingLong(Jugador::elo)) / 100.0;
    }
}
