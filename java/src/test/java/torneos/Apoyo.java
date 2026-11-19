package torneos;

import java.util.List;
import java.util.stream.IntStream;

import torneos.modelo.Jugador;
import torneos.modelo.Resultados.LecturaCsv;
import torneos.modelo.Resultados.LineaCsv;

/** Datos de apoyo para las pruebas. */
final class Apoyo {
    private Apoyo() { }

    static final long SEMILLA = 20261117L;

    /** n jugadores con Elo 1000, 1010, 1020... (en centésimas). */
    static List<Jugador> jugadores(int n) {
        return IntStream.rangeClosed(1, n).mapToObj(i -> Jugador.nuevo(i, "J" + i, 100_000L + 1_000L * i)).toList();
    }

    static LecturaCsv csv(String... lineas) {
        return new LecturaCsv(IntStream.range(0, lineas.length)
                .mapToObj(i -> new LineaCsv(i + 1, lineas[i])).toList(), List.of());
    }
}
