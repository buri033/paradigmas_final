package torneos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import torneos.dominio.Validacion;
import torneos.infra.CargadorCsv;
import torneos.modelo.RegistroError;
import torneos.modelo.Resultados.ResultadoCarga;
import torneos.modelo.TipoError;

class ErroresTest {

    static Map<TipoError, Long> porTipo(ResultadoCarga r) {
        return r.errores().stream().collect(Collectors.groupingBy(RegistroError::tipo, Collectors.counting()));
    }

    @Test
    void lineaMalformadaSeRegistraSinDetenerElPrograma() {
        ResultadoCarga r = Validacion.validar(Apoyo.csv("id,nombre,elo", "1,Ana", "2,Beto,1500", "x,Caro,1400", "3,,1300"));
        assertEquals(1, r.jugadores().size());
        assertEquals(3L, porTipo(r).get(TipoError.LINEA_MALFORMADA));
    }

    @Test
    void eloNegativoCeroONuloEsInvalido() {
        ResultadoCarga r = Validacion.validar(Apoyo.csv("1,A,-1200", "2,B,0", "3,C,null", "4,D,1500.5"));
        assertEquals(3L, porTipo(r).get(TipoError.ELO_INVALIDO));
        assertEquals(150_050L, r.jugadores().get(0).elo());
    }

    @Test
    void idDuplicadoConservaLaPrimeraAparicion() {
        ResultadoCarga r = Validacion.validar(Apoyo.csv("7,Primero,1500", "7,Segundo,1600"));
        assertEquals(1, r.jugadores().size());
        assertEquals("Primero", r.jugadores().get(0).nombre());
        assertEquals(1L, porTipo(r).get(TipoError.ID_DUPLICADO));
    }

    @Test
    void eloVacioValeMil() {
        ResultadoCarga r = Validacion.validar(Apoyo.csv("5,SinElo,"));
        assertEquals(100_000L, r.jugadores().get(0).elo());
    }

    @Test
    void archivoInexistenteNoLanzaExcepcion() {
        ResultadoCarga r = Validacion.validar(CargadorCsv.leer("no/existe/archivo.csv"));
        assertEquals(0, r.jugadores().size());
        assertEquals(1L, porTipo(r).get(TipoError.ARCHIVO_INEXISTENTE));
    }

    @Test
    void escenarioSucioDetectaTodosLosErrores() {
        ResultadoCarga r = Validacion.validar(CargadorCsv.leer("../datos/escenarios/sucio.csv"));
        assertEquals(999, r.jugadores().size());
        assertEquals(12L, porTipo(r).get(TipoError.LINEA_MALFORMADA));
        assertEquals(10L, porTipo(r).get(TipoError.ELO_INVALIDO));
        assertEquals(8L, porTipo(r).get(TipoError.ID_DUPLICADO));
    }
}
