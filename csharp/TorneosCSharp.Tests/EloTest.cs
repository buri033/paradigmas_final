using Torneos.Dominio;
using Xunit;

namespace Torneos.Tests;

public class EloTest
{
    [Fact]
    public void ProbabilidadEsperadaConEloIgualEsMedio() => Assert.Equal(0.5, Elo.Esperado(150_000, 150_000), 12);

    [Fact]
    public void ProbabilidadEsperadaDelFavorito() => Assert.Equal(0.7597469266479578, Elo.Esperado(160_000, 140_000), 12);

    [Fact]
    public void ActualizacionConKIgual32()
    {
        Assert.Equal(1600, Elo.Delta(150_000, 150_000, 1.0));
        Assert.Equal(-1600, Elo.Delta(150_000, 150_000, 0.0));
        Assert.Equal(0, Elo.Delta(150_000, 150_000, 0.5));
        Assert.Equal(769, Elo.Delta(160_000, 140_000, 1.0));
    }

    [Fact]
    public void GeneradorLcgDeterministaEIgualEnLosTresLenguajes()
    {
        var g = new GeneradorLCG(42);
        Assert.Equal(unchecked((ulong)-7964744663189004623L), g.SiguienteLong());
        Assert.Equal(0.2254634289477513, g.SiguienteDouble());
        Assert.Equal(0.8718897256229194, GeneradorLCG.AleatorioPartida(20261117L, 1, 0));
    }

    [Fact]
    public void AleatorioEnRangoCeroUno()
    {
        double u = GeneradorLCG.AleatorioPartida(1, 7, 99);
        Assert.True(u is >= 0.0 and < 1.0);
    }
}
