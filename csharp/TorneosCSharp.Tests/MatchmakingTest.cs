using Torneos.Dominio;
using Torneos.Modelo;
using Xunit;

namespace Torneos.Tests;

public class MatchmakingTest
{
    [Fact]
    public void OrdenaPorEloDescendenteYDesempataPorId()
    {
        var js = new[] { new Jugador(3, "C", 150_000), new Jugador(1, "A", 150_000), new Jugador(2, "B", 170_000) };
        Assert.Equal(new[] { 2, 1, 3 }, Matchmaking.Ordenar(js).Select(j => j.Id).ToArray());
    }

    [Fact]
    public void EmparejaPrimeroConSegundoYTerceroConCuarto()
    {
        var e = Matchmaking.Ejecutar(Apoyo.Jugadores(4)).Emparejamientos;
        Assert.Equal(2, e.Count);
        var p = Assert.IsType<Pareja>(e[0]);
        Assert.Equal(4, p.A.Id);
        Assert.Equal(3, p.B.Id);
    }

    [Fact]
    public void NumeroImparDaByeAlUltimo()
    {
        var e = Matchmaking.Ejecutar(Apoyo.Jugadores(5)).Emparejamientos;
        Assert.Equal(3, e.Count);
        Assert.Equal(1, Assert.IsType<Bye>(e[2]).Jugador.Id);
    }

    [Fact]
    public void ListaVaciaNoGeneraParejas() => Assert.Empty(Matchmaking.Ejecutar([]).Emparejamientos);
}
