using Torneos.Dominio;
using Torneos.Modelo;
using Xunit;

namespace Torneos.Tests;

public class EliminacionTest
{
    [Fact]
    public void OchoJugadoresTienenTresRondasYSietePartidas()
    {
        var r = EliminacionDirecta.Jugar(Apoyo.Jugadores(8), Apoyo.Semilla);
        Assert.Equal(3, r.Rondas.Count);
        Assert.Equal(7, r.Partidas);
        Assert.NotNull(r.Campeon);
    }

    [Fact]
    public void CantidadQueNoEsPotenciaDeDosUsaByes()
    {
        var r = EliminacionDirecta.Jugar(Apoyo.Jugadores(5), Apoyo.Semilla);
        Assert.Equal(3, r.Rondas.Count);
        Assert.Equal(4, r.Partidas);
        Assert.Equal(5, r.Finales.Count);
    }

    [Fact]
    public void EsDeterminista()
    {
        var c1 = EliminacionDirecta.Jugar(Apoyo.Jugadores(33), Apoyo.Semilla).Campeon!.Id;
        var c2 = EliminacionDirecta.Jugar(Apoyo.Jugadores(33), Apoyo.Semilla).Campeon!.Id;
        Assert.Equal(c1, c2);
    }

    [Fact]
    public void MenosDeDosJugadoresEsErrorControlado()
    {
        var e = Assert.Throws<ErrorDatos>(() => EliminacionDirecta.Jugar(Apoyo.Jugadores(1), Apoyo.Semilla));
        Assert.Equal(TipoError.TORNEO_INSUFICIENTE, e.Tipo);
        Assert.Throws<ErrorDatos>(() => EliminacionDirecta.Jugar([], Apoyo.Semilla));
    }
}
