namespace Torneos.Modelo;

/// <summary>Jugador inmutable. El Elo va en CENTÉSIMAS (long): 1534.50 se guarda como 153450.</summary>
public sealed record Jugador(int Id, string Nombre, long Elo, int Victorias = 0, int Derrotas = 0, int Empates = 0, int Puntos = 0)
{
    public const long EloPorDefecto = 100_000;

    /// <summary>Devuelve un NUEVO jugador (expresión with) sin modificar el original.</summary>
    public Jugador ConResultado(long delta, int v, int d, int e, int pts) => this with
    {
        Elo = Elo + delta, Victorias = Victorias + v, Derrotas = Derrotas + d, Empates = Empates + e, Puntos = Puntos + pts
    };
}

public enum Resultado { GANA_A, GANA_B, EMPATE, BYE }

public enum TipoError { LINEA_MALFORMADA, ELO_INVALIDO, ID_DUPLICADO, ARCHIVO_INEXISTENTE, TORNEO_INSUFICIENTE }

public enum TipoTorneo { ELIMINACION_DIRECTA, LIGA }

public sealed record Partida(int JugadorA, int JugadorB, Resultado Resultado, int Ronda, long DeltaEloA)
{
    public int Ganador => Resultado switch
    {
        Resultado.GANA_A or Resultado.BYE => JugadorA,
        Resultado.GANA_B => JugadorB,
        _ => 0
    };
}

/// <summary>Resultado del matchmaking: una pareja que juega o un BYE.</summary>
public abstract record Emparejamiento;
public sealed record Pareja(Jugador A, Jugador B) : Emparejamiento;
public sealed record Bye(Jugador Jugador) : Emparejamiento;

public sealed record RegistroError(TipoError Tipo, string Mensaje);

/// <summary>Excepción propia para errores de datos; se captura y se registra.</summary>
public sealed class ErrorDatos(TipoError tipo, string mensaje) : Exception(mensaje)
{
    public TipoError Tipo { get; } = tipo;
    public RegistroError ComoRegistro() => new(Tipo, Message);
}

public sealed record LineaCsv(int Numero, string Texto);
public sealed record LecturaCsv(IReadOnlyList<LineaCsv> Lineas, IReadOnlyList<RegistroError> Errores);
public sealed record ResultadoCarga(IReadOnlyList<Jugador> Jugadores, IReadOnlyList<RegistroError> Errores);

public sealed record ResultadoMatchmaking(IReadOnlyList<Emparejamiento> Emparejamientos)
{
    public int Parejas => Emparejamientos.Count(e => e is Pareja);
    public int Byes => Emparejamientos.Count(e => e is Bye);
}

public sealed record ResultadoEliminacion(Jugador? Campeon, IReadOnlyList<IReadOnlyList<Partida>> Rondas, IReadOnlyList<Jugador> Finales)
{
    public static ResultadoEliminacion Vacio { get; } = new(null, [], []);
    public long Partidas => Rondas.Sum(r => (long)r.Count);
}

public sealed record ResultadoLiga(IReadOnlyList<IReadOnlyList<Jugador>> Tablas, long Partidas)
{
    public static ResultadoLiga Vacia { get; } = new([], 0);
}

public sealed record GrupoElo(string Rango, long Cantidad, long PromedioElo, long Victorias);
public sealed record ResultadoEstadisticas(IReadOnlyList<GrupoElo> Grupos, IReadOnlyList<Jugador> Top, Jugador? MasVictorias);

/// <summary>Valor de una operación que pudo fallar de forma controlada.</summary>
public sealed record Intento<T>(T Valor, IReadOnlyList<RegistroError> Errores);

public sealed record ResultadoCompleto(ResultadoCarga Carga, ResultadoMatchmaking Matchmaking, ResultadoEliminacion Eliminacion,
    ResultadoLiga Liga, ResultadoEstadisticas Estadisticas, IReadOnlyList<RegistroError> Errores);
