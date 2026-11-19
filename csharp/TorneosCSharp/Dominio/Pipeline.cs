using Torneos.Infra;
using Torneos.Modelo;

namespace Torneos.Dominio;

/// <summary>Composición de fases: carga -> matchmaking -> eliminación -> liga -> estadísticas.</summary>
public static class Pipeline
{
    public static ResultadoCompleto Ejecutar(string ruta, long semilla, int tamGrupo, Cronometro c)
    {
        var carga = c.Medir("carga", () => Validacion.Validar(CargadorCsv.Leer(ruta)));
        var jugadores = carga.Jugadores;
        var mm = c.Medir("matchmaking", () => Matchmaking.Ejecutar(jugadores));
        var elim = c.Medir("eliminacion", () => Intentar(() => EliminacionDirecta.Jugar(jugadores, semilla), ResultadoEliminacion.Vacio));
        var liga = c.Medir("liga", () => Intentar(() => Liga.Jugar(jugadores, semilla, tamGrupo), ResultadoLiga.Vacia));
        var stats = c.Medir("estadisticas", () => Estadisticas.Calcular(jugadores, liga.Valor));
        var errores = carga.Errores.Concat(elim.Errores).Concat(liga.Errores).ToList();
        return new ResultadoCompleto(carga, mm, elim.Valor, liga.Valor, stats, errores);
    }

    /// <summary>Función de orden superior: ejecuta y, si hay ErrorDatos, devuelve el valor por defecto + el error.</summary>
    public static Intento<T> Intentar<T>(Func<T> operacion, T porDefecto)
    {
        try
        {
            return new Intento<T>(operacion(), []);
        }
        catch (ErrorDatos e)
        {
            return new Intento<T>(porDefecto, [e.ComoRegistro()]);
        }
    }
}
