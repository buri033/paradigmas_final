package torneos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import torneos.dominio.Liga;
import torneos.modelo.Jugador;
import torneos.modelo.Resultados.ResultadoLiga;

class LigaTest {

    @Test
    void todosContraTodosGeneraNPorNMenosUnoMedios() {
        assertEquals(6, Liga.generarEnfrentamientos(Apoyo.jugadores(4)).size());
        assertEquals(2016, Liga.generarEnfrentamientos(Apoyo.jugadores(64)).size());
    }

    @Test
    void puntosVictoriasYDerrotasCuadran() {
        ResultadoLiga r = Liga.jugar(Apoyo.jugadores(10), Apoyo.SEMILLA, 64);
        List<Jugador> t = r.tablas().get(0);
        int v = t.stream().mapToInt(Jugador::victorias).sum();
        int d = t.stream().mapToInt(Jugador::derrotas).sum();
        int e = t.stream().mapToInt(Jugador::empates).sum();
        int pts = t.stream().mapToInt(Jugador::puntos).sum();
        assertEquals(45, r.partidas());
        assertEquals(v, d);
        assertEquals(3 * v + e, pts); // victoria = 3, empate = 1 para cada uno
        assertEquals(0, t.stream().mapToLong(j -> j.elo()).sum() - Apoyo.jugadores(10).stream().mapToLong(Jugador::elo).sum());
    }

    @Test
    void tablaOrdenadaPorPuntosEloEId() {
        List<Jugador> t = Liga.jugar(Apoyo.jugadores(20), Apoyo.SEMILLA, 64).tablas().get(0);
        assertTrue(IntStream.range(1, t.size()).allMatch(i -> Liga.POR_TABLA.compare(t.get(i - 1), t.get(i)) < 0));
    }

    @Test
    void gruposDeSesentaYCuatro() {
        ResultadoLiga r = Liga.jugar(Apoyo.jugadores(130), Apoyo.SEMILLA, 64);
        assertEquals(3, r.tablas().size());            // 64 + 64 + 2
        assertEquals(2016 * 2 + 1, r.partidas());
    }
}
