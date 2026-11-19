package torneos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import torneos.dominio.Elo;
import torneos.dominio.GeneradorLCG;

class EloTest {

    @Test
    void probabilidadEsperadaConEloIgualEsMedio() {
        assertEquals(0.5, Elo.esperado(150_000, 150_000), 1e-12);
    }

    @Test
    void probabilidadEsperadaDelFavorito() {
        assertEquals(0.7597469266479578, Elo.esperado(160_000, 140_000), 1e-12);
    }

    @Test
    void actualizacionConKIgual32() {
        assertEquals(1600, Elo.delta(150_000, 150_000, 1.0));   // +16.00
        assertEquals(-1600, Elo.delta(150_000, 150_000, 0.0));  // -16.00
        assertEquals(0, Elo.delta(150_000, 150_000, 0.5));      // empate entre iguales
        assertEquals(769, Elo.delta(160_000, 140_000, 1.0));    // el favorito gana poco: +7.69
    }

    @Test
    void generadorLcgEsDeterministaEIgualEnLosTresLenguajes() {
        GeneradorLCG g = new GeneradorLCG(42);
        assertEquals(-7964744663189004623L, g.siguienteLong());
        assertEquals(0.2254634289477513, g.siguienteDouble(), 0.0);
        assertEquals(0.8718897256229194, GeneradorLCG.aleatorioPartida(20261117L, 1, 0), 0.0);
    }

    @Test
    void aleatorioEnRangoCeroUno() {
        double u = GeneradorLCG.aleatorioPartida(1, 7, 99);
        assertTrue(u >= 0.0 && u < 1.0);
    }
}
