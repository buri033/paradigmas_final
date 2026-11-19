package torneos.dominio;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import torneos.infra.CargadorCsv;
import torneos.infra.Cronometro;
import torneos.modelo.ErrorDatos;
import torneos.modelo.Jugador;
import torneos.modelo.RegistroError;
import torneos.modelo.Resultados.Intento;
import torneos.modelo.Resultados.ResultadoCarga;
import torneos.modelo.Resultados.ResultadoCompleto;
import torneos.modelo.Resultados.ResultadoEliminacion;
import torneos.modelo.Resultados.ResultadoEstadisticas;
import torneos.modelo.Resultados.ResultadoLiga;
import torneos.modelo.Resultados.ResultadoMatchmaking;

/** Composición de las fases: carga -> matchmaking -> eliminación -> liga -> estadísticas. */
public final class Pipeline {
    private Pipeline() { }

    public static ResultadoCompleto ejecutar(String ruta, long semilla, int tamGrupo, Cronometro c) {
        ResultadoCarga carga = c.medir("carga", () -> Validacion.validar(CargadorCsv.leer(ruta)));
        List<Jugador> jugadores = carga.jugadores();
        ResultadoMatchmaking mm = c.medir("matchmaking", () -> Matchmaking.ejecutar(jugadores));
        Intento<ResultadoEliminacion> elim = c.medir("eliminacion",
                () -> intentar(() -> EliminacionDirecta.jugar(jugadores, semilla), ResultadoEliminacion.vacio()));
        Intento<ResultadoLiga> liga = c.medir("liga",
                () -> intentar(() -> Liga.jugar(jugadores, semilla, tamGrupo), ResultadoLiga.vacia()));
        ResultadoEstadisticas stats = c.medir("estadisticas", () -> Estadisticas.calcular(jugadores, liga.valor()));
        List<RegistroError> errores = Stream.of(carga.errores(), elim.errores(), liga.errores())
                .flatMap(List::stream).toList();
        return new ResultadoCompleto(carga, mm, elim.valor(), liga.valor(), stats, errores);
    }

    /** Ejecuta una operación; si lanza ErrorDatos devuelve el valor por defecto y registra el error. */
    static <T> Intento<T> intentar(Supplier<T> operacion, T porDefecto) {
        try {
            return new Intento<>(operacion.get(), List.of());
        } catch (ErrorDatos e) {
            return new Intento<>(porDefecto, List.of(e.comoRegistro()));
        }
    }
}
