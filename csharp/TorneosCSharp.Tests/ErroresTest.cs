using Torneos.Dominio;
using Torneos.Infra;
using Torneos.Modelo;
using Xunit;

namespace Torneos.Tests;

public class ErroresTest
{
    [Fact]
    public void LineaMalformadaSeRegistraSinDetenerElPrograma()
    {
        var r = Validacion.Validar(Apoyo.Csv("id,nombre,elo", "1,Ana", "2,Beto,1500", "x,Caro,1400", "3,,1300"));
        Assert.Single(r.Jugadores);
        Assert.Equal(3, Apoyo.Conteo(r, TipoError.LINEA_MALFORMADA));
    }

    [Fact]
    public void EloNegativoCeroONuloEsInvalido()
    {
        var r = Validacion.Validar(Apoyo.Csv("1,A,-1200", "2,B,0", "3,C,null", "4,D,1500.5"));
        Assert.Equal(3, Apoyo.Conteo(r, TipoError.ELO_INVALIDO));
        Assert.Equal(150_050, r.Jugadores[0].Elo);
    }

    [Fact]
    public void IdDuplicadoConservaLaPrimeraAparicion()
    {
        var r = Validacion.Validar(Apoyo.Csv("7,Primero,1500", "7,Segundo,1600"));
        Assert.Equal("Primero", Assert.Single(r.Jugadores).Nombre);
        Assert.Equal(1, Apoyo.Conteo(r, TipoError.ID_DUPLICADO));
    }

    [Fact]
    public void EloVacioValeMil() => Assert.Equal(100_000, Validacion.Validar(Apoyo.Csv("5,SinElo,")).Jugadores[0].Elo);

    [Fact]
    public void ArchivoInexistenteNoLanzaExcepcion()
    {
        var r = Validacion.Validar(CargadorCsv.Leer("no/existe/archivo.csv"));
        Assert.Empty(r.Jugadores);
        Assert.Equal(1, Apoyo.Conteo(r, TipoError.ARCHIVO_INEXISTENTE));
    }

    [Fact]
    public void EscenarioSucioDetectaTodosLosErrores()
    {
        // dotnet test ejecuta desde bin/...; se busca la carpeta datos subiendo directorios
        var ruta = BuscarArriba(Path.Combine("datos", "escenarios", "sucio.csv"));
        var r = Validacion.Validar(CargadorCsv.Leer(ruta));
        Assert.Equal(999, r.Jugadores.Count);
        Assert.Equal(12, Apoyo.Conteo(r, TipoError.LINEA_MALFORMADA));
        Assert.Equal(10, Apoyo.Conteo(r, TipoError.ELO_INVALIDO));
        Assert.Equal(8, Apoyo.Conteo(r, TipoError.ID_DUPLICADO));
    }

    private static string BuscarArriba(string relativa) =>
        new[] { AppContext.BaseDirectory, Directory.GetCurrentDirectory() }
            .SelectMany(baseDir => Enumerable.Range(0, 8)
                .Select(n => Path.GetFullPath(Path.Combine(Enumerable.Repeat("..", n).Prepend(baseDir).Append(relativa).ToArray()))))
            .FirstOrDefault(File.Exists) ?? relativa;
}
