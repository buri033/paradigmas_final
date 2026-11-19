package torneos.modelo;

import java.util.List;
import java.util.Optional;

/** Records inmutables con los resultados de cada fase del pipeline. */
public final class Resultados {
    private Resultados() { }

    public record LineaCsv(int numero, String texto) { }

    public record LecturaCsv(List<LineaCsv> lineas, List<RegistroError> errores) { }

    public record ResultadoCarga(List<Jugador> jugadores, List<RegistroError> errores) { }

    public record ResultadoMatchmaking(List<Emparejamiento> emparejamientos) {
        public long parejas() { return emparejamientos.stream().filter(e -> e instanceof Emparejamiento.Pareja).count(); }
        public long byes() { return emparejamientos.stream().filter(e -> e instanceof Emparejamiento.Bye).count(); }
    }

    public record ResultadoEliminacion(Optional<Jugador> campeon, List<List<Partida>> rondas, List<Jugador> finales) {
        public static ResultadoEliminacion vacio() { return new ResultadoEliminacion(Optional.empty(), List.of(), List.of()); }
        public long partidas() { return rondas.stream().mapToLong(List::size).sum(); }
    }

    public record ResultadoLiga(List<List<Jugador>> tablas, long partidas) {
        public static ResultadoLiga vacia() { return new ResultadoLiga(List.of(), 0); }
    }

    public record GrupoElo(String rango, long cantidad, long promedioElo, long victorias) { }

    public record ResultadoEstadisticas(List<GrupoElo> grupos, List<Jugador> top, Optional<Jugador> masVictorias) { }

    /** Intento de una operación que puede fallar de forma controlada. */
    public record Intento<T>(T valor, List<RegistroError> errores) { }

    public record ResultadoCompleto(ResultadoCarga carga, ResultadoMatchmaking matchmaking,
                                    ResultadoEliminacion eliminacion, ResultadoLiga liga,
                                    ResultadoEstadisticas estadisticas, List<RegistroError> errores) { }
}
