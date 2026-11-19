package torneos.dominio;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import torneos.modelo.ErrorDatos;
import torneos.modelo.Jugador;
import torneos.modelo.RegistroError;
import torneos.modelo.Resultados.LecturaCsv;
import torneos.modelo.Resultados.LineaCsv;
import torneos.modelo.Resultados.ResultadoCarga;
import torneos.modelo.TipoError;

/**
 * Reglas de validación (idénticas en los 3 lenguajes):
 *  - línea 1 que empieza por "id" = encabezado; líneas en blanco se ignoran.
 *  - exactamente 3 campos separados por coma; si no -> LINEA_MALFORMADA.
 *  - id: 1 a 9 dígitos y >= 1; nombre no vacío; si no -> LINEA_MALFORMADA.
 *  - elo: vacío -> 1000; "null" -> ELO_INVALIDO; formato -?d{1,6}(.d{1,2})? o LINEA_MALFORMADA; <= 0 -> ELO_INVALIDO.
 *  - id repetido (después de la primera aparición válida) -> ID_DUPLICADO.
 */
public final class Validacion {
    private Validacion() { }

    /** Análisis de una línea: o trae jugador o trae error (nunca ambos). */
    record Analisis(int linea, Jugador jugador, RegistroError error) {
        boolean esValida() { return error == null; }
    }

    static final Predicate<LineaCsv> IGNORABLE = l ->
            l.texto().trim().isEmpty() || (l.numero() == 1 && l.texto().trim().toLowerCase().startsWith("id"));

    public static ResultadoCarga validar(LecturaCsv lectura) {
        Map<Boolean, List<Analisis>> particion = lectura.lineas().stream()
                .filter(IGNORABLE.negate())
                .map(Validacion::analizar)
                .collect(Collectors.partitioningBy(Analisis::esValida));
        List<Analisis> candidatos = particion.get(true);
        // posición de la PRIMERA aparición de cada id (las demás son duplicadas)
        Map<Integer, Integer> primera = IntStream.range(0, candidatos.size()).boxed()
                .collect(Collectors.toMap(i -> candidatos.get(i).jugador().id(), i -> i, (x, y) -> x));
        Predicate<Integer> esPrimera = i -> primera.get(candidatos.get(i).jugador().id()).equals(i);
        List<Jugador> unicos = IntStream.range(0, candidatos.size()).boxed()
                .filter(esPrimera)
                .map(i -> candidatos.get(i).jugador())
                .toList();
        List<RegistroError> duplicados = IntStream.range(0, candidatos.size()).boxed()
                .filter(esPrimera.negate())
                .map(i -> new RegistroError(TipoError.ID_DUPLICADO, "Linea " + candidatos.get(i).linea()
                        + ": id duplicado " + candidatos.get(i).jugador().id()))
                .toList();
        List<RegistroError> errores = Stream.of(lectura.errores(),
                        particion.get(false).stream().map(Analisis::error).toList(), duplicados)
                .flatMap(List::stream)
                .toList();
        return new ResultadoCarga(unicos, errores);
    }

    static Analisis analizar(LineaCsv l) {
        try {
            return new Analisis(l.numero(), parsear(l), null);
        } catch (ErrorDatos e) {
            return new Analisis(l.numero(), null, e.comoRegistro());
        }
    }

    static Jugador parsear(LineaCsv l) {
        String[] campos = l.texto().split(",", -1);
        if (campos.length != 3) {
            throw malformada(l, "se esperaban 3 campos y hay " + campos.length);
        }
        String id = campos[0].trim();
        String nombre = campos[1].trim();
        String elo = campos[2].trim();
        if (!id.matches("[0-9]{1,9}") || Integer.parseInt(id) < 1) {
            throw malformada(l, "id invalido '" + id + "'");
        }
        if (nombre.isEmpty()) {
            throw malformada(l, "nombre vacio");
        }
        return Jugador.nuevo(Integer.parseInt(id), nombre, parsearElo(l, elo));
    }

    /** Devuelve el Elo en centésimas. */
    public static long parsearElo(LineaCsv l, String t) {
        if (t.isEmpty()) {
            return Jugador.ELO_POR_DEFECTO;
        }
        if (t.equalsIgnoreCase("null")) {
            throw new ErrorDatos(TipoError.ELO_INVALIDO, "Linea " + l.numero() + ": Elo nulo");
        }
        if (!t.matches("-?[0-9]{1,6}(\\.[0-9]{1,2})?")) {
            throw malformada(l, "Elo con formato invalido '" + t + "'");
        }
        boolean negativo = t.startsWith("-");
        String[] partes = (negativo ? t.substring(1) : t).split("\\.");
        long decimales = partes.length > 1 ? Long.parseLong(partes[1]) * (partes[1].length() == 1 ? 10 : 1) : 0;
        long centesimas = (Long.parseLong(partes[0]) * 100 + decimales) * (negativo ? -1 : 1);
        if (centesimas <= 0) {
            throw new ErrorDatos(TipoError.ELO_INVALIDO, "Linea " + l.numero() + ": Elo negativo o cero (" + t + ")");
        }
        return centesimas;
    }

    static ErrorDatos malformada(LineaCsv l, String detalle) {
        return new ErrorDatos(TipoError.LINEA_MALFORMADA, "Linea " + l.numero() + ": " + detalle);
    }
}
