package torneos

import scala.math._

class EloTest extends munit.FunSuite {

  test("probabilidad esperada con Elo igual es 0.5") {
    assert(abs(Elo.esperado(150000.0, 150000.0) - 0.5) < 1e-12)
  }

  test("probabilidad esperada del favorito") {
    assert(abs(Elo.esperado(160000.0, 140000.0) - 0.7597469266479578) < 1e-12)
  }

  test("actualizacion con K = 32") {
    assertEquals(Elo.delta(150000.0, 150000.0, 1.0), 1600.0)
    assertEquals(Elo.delta(150000.0, 150000.0, 0.0), -1600.0)
    assertEquals(Elo.delta(150000.0, 150000.0, 0.5), 0.0)
    assertEquals(Elo.delta(160000.0, 140000.0, 1.0), 769.0)
  }

  test("generador LCG determinista e igual en los tres lenguajes") {
    val g = new GeneradorLCG(42L)
    assertEquals(g.siguienteLong(), -7964744663189004623L)
    assertEquals(g.siguienteDouble(), 0.2254634289477513)
    assertEquals(GeneradorLCG.aleatorioPartida(20261117L, 1, 0), 0.8718897256229194)
  }

  test("aleatorio en el rango [0, 1)") {
    val u = GeneradorLCG.aleatorioPartida(1L, 7, 99)
    assert(u >= 0.0 && u < 1.0)
  }
}
