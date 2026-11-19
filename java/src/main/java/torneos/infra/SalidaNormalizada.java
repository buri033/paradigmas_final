package torneos.infra;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import torneos.modelo.Jugador;
import torneos.modelo.RegistroError;
import torneos.modelo.Resultados.ResultadoCompleto;
import torneos.modelo.Resultados.ResultadoEliminacion;
import torneos.modelo.Resultados.ResultadoLiga;
import torneos.modelo.TipoError;

/**
 * Texto canónico del resultado (mismo formato byte a byte en los 3 lenguajes) y su SHA-256.
 * Si el hash coincide entre Java, Scala y C#, la salida funcional es idéntica.
 */
public final class SalidaNormalizada {
    private SalidaNormalizada() { }

    /** Formatea centésimas como "1234.56" sin depender de la configuración regional. */
    public static String elo(long c) {
        long abs = Math.abs(c);
        return (c < 0 ? "-" : "") + (abs / 100) + "." + (abs % 100 < 10 ? "0" : "") + (abs % 100);
    }

    public static long checksumEliminacion(ResultadoEliminacion e) {
        return e.finales().stream().mapToLong(j -> j.elo() * (j.id() % 1000 + 1)).sum();
    }

    public static long checksumLiga(ResultadoLiga l) {
        return l.tablas().stream().flatMap(List::stream)
                .mapToLong(j -> j.elo() * (j.id() % 1000 + 1) + (long) j.puntos() * (j.id() % 97 + 1))
                .sum();
    }

    public static String generar(ResultadoCompleto r) {
        Map<TipoError, Long> porTipo = r.errores().stream()
                .collect(Collectors.groupingBy(RegistroError::tipo, Collectors.counting()));
        ResultadoEliminacion e = r.eliminacion();
        ResultadoLiga l = r.liga();
        String tiposTexto = Stream.of(TipoError.values())
                .map(t -> t.name() + ":" + porTipo.getOrDefault(t, 0L))
                .collect(Collectors.joining(";"));
        String tablaG1 = l.tablas().isEmpty() ? "" : l.tablas().get(0).stream()
                .map(j -> j.id() + ":" + j.puntos() + ":" + j.victorias() + ":" + j.empates() + ":" + j.derrotas() + ":" + elo(j.elo()))
                .collect(Collectors.joining(","));
        String lideres = l.tablas().stream().map(t -> String.valueOf(t.get(0).id())).collect(Collectors.joining(","));
        String stats = r.estadisticas().grupos().stream()
                .map(g -> g.rango() + ":" + g.cantidad() + ":" + elo(g.promedioElo()) + ":" + g.victorias())
                .collect(Collectors.joining(";"));
        String top = r.estadisticas().top().stream().map(j -> j.id() + ":" + elo(j.elo())).collect(Collectors.joining(","));
        String mas = r.estadisticas().masVictorias().map(j -> j.id() + ":" + j.victorias()).orElse("0:0");
        Function<Jugador, String> idCampeon = j -> String.valueOf(j.id());
        return "VALIDOS=" + r.carga().jugadores().size() + "\n"
                + "ERRORES=" + r.errores().size() + "\n"
                + "ERRORES_TIPO=" + tiposTexto + "\n"
                + "PAREJAS=" + r.matchmaking().parejas() + ";BYES=" + r.matchmaking().byes() + "\n"
                + "ELIM=RONDAS:" + e.rondas().size() + ";PARTIDAS:" + e.partidas()
                + ";CAMPEON:" + e.campeon().map(idCampeon).orElse("0")
                + ";ELO:" + elo(e.campeon().map(Jugador::elo).orElse(0L)) + "\n"
                + "LIGA=GRUPOS:" + l.tablas().size() + ";PARTIDAS:" + l.partidas() + "\n"
                + "TABLA_G1=" + tablaG1 + "\n"
                + "LIDERES=" + lideres + "\n"
                + "CHECK_ELIM=" + checksumEliminacion(e) + "\n"
                + "CHECK_LIGA=" + checksumLiga(l) + "\n"
                + "STATS=" + stats + "\n"
                + "TOP10=" + top + "\n"
                + "MAS_VICTORIAS=" + mas + "\n";
    }

    public static String sha256(String texto) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
