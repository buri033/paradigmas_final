using System.Text;
using Torneos.Modelo;

namespace Torneos.Infra;

/// <summary>BORDE de entrada: único lugar que lee del sistema de archivos.</summary>
public static class CargadorCsv
{
    public static LecturaCsv Leer(string ruta)
    {
        try
        {
            var lineas = File.ReadAllLines(ruta, Encoding.UTF8);
            return new LecturaCsv(lineas.Select((texto, i) => new LineaCsv(i + 1, texto)).ToList(), []);
        }
        catch (Exception e) when (e is FileNotFoundException or DirectoryNotFoundException)
        {
            return new LecturaCsv([], [new RegistroError(TipoError.ARCHIVO_INEXISTENTE, $"No existe el archivo: {ruta}")]);
        }
        catch (IOException e)
        {
            return new LecturaCsv([], [new RegistroError(TipoError.ARCHIVO_INEXISTENTE, $"No se pudo leer el archivo: {ruta} ({e.Message})")]);
        }
    }
}
