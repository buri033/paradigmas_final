package torneos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import torneos.dominio.EliminacionDirecta;
import torneos.modelo.ErrorDatos;
import torneos.modelo.Resultados.ResultadoEliminacion;
import torneos.modelo.TipoError;

class EliminacionTest {

    @Test
    void ochoJugadoresTienenTresRondasYSietePartidas() {
        ResultadoEliminacion r = EliminacionDirecta.jugar(Apoyo.jugadores(8), Apoyo.SEMILLA);
        assertEquals(3, r.rondas().size());
        assertEquals(7, r.partidas());
        assertTrue(r.campeon().isPresent());
    }

    @Test
    void cantidadQueNoEsPotenciaDeDosUsaByes() {
        ResultadoEliminacion r = EliminacionDirecta.jugar(Apoyo.jugadores(5), Apoyo.SEMILLA);
        assertEquals(3, r.rondas().size()); // 5 -> 3 -> 2 -> 1
        assertEquals(4, r.partidas());      // siempre n - 1 partidas
        assertEquals(5, r.finales().size());
    }

    @Test
    void esDeterminista() {
        int c1 = EliminacionDirecta.jugar(Apoyo.jugadores(33), Apoyo.SEMILLA).campeon().orElseThrow().id();
        int c2 = EliminacionDirecta.jugar(Apoyo.jugadores(33), Apoyo.SEMILLA).campeon().orElseThrow().id();
        assertEquals(c1, c2);
    }

    @Test
    void menosDeDosJugadoresEsErrorControlado() {
        ErrorDatos e = assertThrows(ErrorDatos.class, () -> EliminacionDirecta.jugar(Apoyo.jugadores(1), Apoyo.SEMILLA));
        assertEquals(TipoError.TORNEO_INSUFICIENTE, e.tipo());
        assertThrows(ErrorDatos.class, () -> EliminacionDirecta.jugar(List.of(), Apoyo.SEMILLA));
    }
}
