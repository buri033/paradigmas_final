using Torneos.Dominio;
using Xunit;

namespace Torneos.Tests;

public class LigaTest
{
    [Fact]
    public void TodosContraTodosGeneraNPorNMenosUnoMedios()
    {
        Assert.Equal(6, Liga.GenerarEnfrentamientos(Apoyo.Jugadores(4)).Count());
        Assert.Equal(2016, Liga.GenerarEnfrentamientos(Apoyo.Jugadores(64)).Count());
    }

    [Fact]
    public void PuntosVictoriasYDerrotasCuadran()
    {
        var r = Liga.Jugar(Apoyo.Jugadores(10), Apoyo.Semilla, 64);
        var t = r.Tablas[0];
        Assert.Equal(45, r.Partidas);
        Assert.Equal(t.Sum(j => j.Victorias), t.Sum(j => j.Derrotas));
        Assert.Equal(3 * t.Sum(j => j.Victorias) + t.Sum(j => j.Empates), t.Sum(j => j.Puntos));
        Assert.Equal(Apoyo.Jugadores(10).Sum(j => j.Elo), t.Sum(j => j.Elo));
    }

    [Fact]
    public void TablaOrdenadaPorPuntosEloEId()
    {
        var t = Liga.Jugar(Apoyo.Jugadores(20), Apoyo.Semilla, 64).Tablas[0];
        Assert.True(t.Zip(t.Skip(1)).All(p =>
            p.First.Puntos > p.Second.Puntos ||
            (p.First.Puntos == p.Second.Puntos && (p.First.Elo > p.Second.Elo ||
                                                   (p.First.Elo == p.Second.Elo && p.First.Id < p.Second.Id)))));
    }

    [Fact]
    public void GruposDeSesentaYCuatro()
    {
        var r = Liga.Jugar(Apoyo.Jugadores(130), Apoyo.Semilla, 64);
        Assert.Equal(3, r.Tablas.Count);
        Assert.Equal(2016 * 2 + 1, r.Partidas);
    }
}
