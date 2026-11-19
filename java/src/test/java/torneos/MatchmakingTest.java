package torneos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import torneos.dominio.Matchmaking;
import torneos.modelo.Emparejamiento;
import torneos.modelo.Emparejamiento.Bye;
import torneos.modelo.Emparejamiento.Pareja;
import torneos.modelo.Jugador;

class MatchmakingTest {

    @Test
    void ordenaPorEloDescendenteYDesempataPorId() {
        List<Jugador> js = List.of(Jugador.nuevo(3, "C", 150_000), Jugador.nuevo(1, "A", 150_000), Jugador.nuevo(2, "B", 170_000));
        List<Integer> ids = Matchmaking.ordenar(js).stream().map(Jugador::id).toList();
        assertEquals(List.of(2, 1, 3), ids);
    }

    @Test
    void emparejaPrimeroConSegundoYTerceroConCuarto() {
        List<Emparejamiento> e = Matchmaking.ejecutar(Apoyo.jugadores(4)).emparejamientos();
        assertEquals(2, e.size());
        Pareja p = (Pareja) e.get(0);
        assertEquals(4, p.a().id());
        assertEquals(3, p.b().id());
    }

    @Test
    void numeroImparDaByeAlUltimo() {
        List<Emparejamiento> e = Matchmaking.ejecutar(Apoyo.jugadores(5)).emparejamientos();
        assertEquals(3, e.size());
        assertInstanceOf(Bye.class, e.get(2));
        assertEquals(1, ((Bye) e.get(2)).jugador().id()); // el de menor Elo
    }

    @Test
    void listaVaciaNoGeneraParejas() {
        assertTrue(Matchmaking.ejecutar(List.of()).emparejamientos().isEmpty());
    }
}
