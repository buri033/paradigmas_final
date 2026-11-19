using System.Globalization;
using System.Text.RegularExpressions;
using Torneos.Modelo;

namespace Torneos.Dominio;

/// <summary>
/// Reglas de validación idénticas en los 3 lenguajes (ver README): encabezado y líneas en blanco se ignoran;
/// 3 campos; id 1-9 dígitos >= 1; nombre no vacío; Elo vacío = 1000, "null" o <= 0 = ELO_INVALIDO;
/// formato -?d{1,6}(.d{1,2})? o LINEA_MALFORMADA; ids repetidos = ID_DUPLICADO.
/// </summary>
public static partial class Validacion
{
    private sealed record Analisis(int Linea, Jugador? Jugador, RegistroError? Error);

    [GeneratedRegex("^[0-9]{1,9}$")]
    private static partial Regex IdValido();

    [GeneratedRegex(@"^-?[0-9]{1,6}(\.[0-9]{1,2})?$")]
    private static partial Regex EloValido();

    private static readonly Predicate<LineaCsv> Ignorable = l =>
        l.Texto.Trim().Length == 0 || (l.Numero == 1 && l.Texto.Trim().ToLowerInvariant().StartsWith("id", StringComparison.Ordinal));

    public static ResultadoCarga Validar(LecturaCsv lectura)
    {
        var analizadas = lectura.Lineas.Where(l => !Ignorable(l)).Select(Analizar).ToList();
        var validas = analizadas.Where(a => a.Error is null).ToList();
        // índice de la PRIMERA aparición de cada id
        var primera = validas.Select((a, i) => (a.Jugador!.Id, i))
            .GroupBy(t => t.Id)
            .ToDictionary(g => g.Key, g => g.Min(t => t.i));
        var unicos = validas.Where((a, i) => primera[a.Jugador!.Id] == i).Select(a => a.Jugador!).ToList();
        var duplicados = validas.Select((a, i) => (a, i))
            .Where(t => primera[t.a.Jugador!.Id] != t.i)
            .Select(t => new RegistroError(TipoError.ID_DUPLICADO, $"Linea {t.a.Linea}: id duplicado {t.a.Jugador!.Id}"));
        var errores = lectura.Errores
            .Concat(analizadas.Where(a => a.Error is not null).Select(a => a.Error!))
            .Concat(duplicados)
            .ToList();
        return new ResultadoCarga(unicos, errores);
    }

    private static Analisis Analizar(LineaCsv l)
    {
        try
        {
            return new Analisis(l.Numero, Parsear(l), null);
        }
        catch (ErrorDatos e)
        {
            return new Analisis(l.Numero, null, e.ComoRegistro());
        }
    }

    private static Jugador Parsear(LineaCsv l)
    {
        var campos = l.Texto.Split(',');
        if (campos.Length != 3) throw Malformada(l, $"se esperaban 3 campos y hay {campos.Length}");
        var (id, nombre, elo) = (campos[0].Trim(), campos[1].Trim(), campos[2].Trim());
        if (!IdValido().IsMatch(id) || int.Parse(id, CultureInfo.InvariantCulture) < 1) throw Malformada(l, $"id invalido '{id}'");
        if (nombre.Length == 0) throw Malformada(l, "nombre vacio");
        return new Jugador(int.Parse(id, CultureInfo.InvariantCulture), nombre, ParsearElo(l, elo));
    }

    /// <summary>Elo en centésimas.</summary>
    public static long ParsearElo(LineaCsv l, string t)
    {
        if (t.Length == 0) return Jugador.EloPorDefecto;
        if (t.Equals("null", StringComparison.OrdinalIgnoreCase))
            throw new ErrorDatos(TipoError.ELO_INVALIDO, $"Linea {l.Numero}: Elo nulo");
        if (!EloValido().IsMatch(t)) throw Malformada(l, $"Elo con formato invalido '{t}'");
        bool negativo = t.StartsWith('-');
        var partes = (negativo ? t[1..] : t).Split('.');
        long decimales = partes.Length > 1 ? long.Parse(partes[1], CultureInfo.InvariantCulture) * (partes[1].Length == 1 ? 10 : 1) : 0;
        long centesimas = (long.Parse(partes[0], CultureInfo.InvariantCulture) * 100 + decimales) * (negativo ? -1 : 1);
        if (centesimas <= 0)
            throw new ErrorDatos(TipoError.ELO_INVALIDO, $"Linea {l.Numero}: Elo negativo o cero ({t})");
        return centesimas;
    }

    private static ErrorDatos Malformada(LineaCsv l, string detalle) => new(TipoError.LINEA_MALFORMADA, $"Linea {l.Numero}: {detalle}");
}
