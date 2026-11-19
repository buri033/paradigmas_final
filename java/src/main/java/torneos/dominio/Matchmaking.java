package torneos.dominio;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import torneos.modelo.Emparejamiento;
import torneos.modelo.Emparejamiento.Bye;
import torneos.modelo.Emparejamiento.Pareja;
import torneos.modelo.Jugador;
import torneos.modelo.Resultados.ResultadoMatchmaking;

/** Matchmaking por cercanía de nivel: 1º vs 2º, 3º vs 4º...; si es impar, el último recibe BYE. */
public final class Matchmaking {
    private Matchmaking() { }

    /** Orden total y determinista: Elo descendente y, si empatan, id ascendente. */
    public static final Comparator<Jugador> POR_ELO =
            Comparator.comparingLong(Jugador::elo).reversed().thenComparingInt(Jugador::id);

    public static List<Jugador> ordenar(List<Jugador> jugadores) {
        return jugadores.stream().sorted(POR_ELO).toList();
    }

    public static List<Emparejamiento> emparejar(List<Jugador> ordenados) {
        return IntStream.range(0, (ordenados.size() + 1) / 2)
                .mapToObj(i -> 2 * i + 1 < ordenados.size()
                        ? (Emparejamiento) new Pareja(ordenados.get(2 * i), ordenados.get(2 * i + 1))
                        : new Bye(ordenados.get(2 * i)))
                .toList();
    }

    public static ResultadoMatchmaking ejecutar(List<Jugador> jugadores) {
        return new ResultadoMatchmaking(emparejar(ordenar(jugadores)));
    }
}
