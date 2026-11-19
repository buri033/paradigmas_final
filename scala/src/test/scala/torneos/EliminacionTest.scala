package torneos

class EliminacionTest extends munit.FunSuite {

  test("ocho jugadores: tres rondas y siete partidas") {
    val r = EliminacionDirecta.jugar(Apoyo.jugadores(8), Apoyo.SEMILLA)
    assertEquals(r.numRondas, 3)
    assertEquals(r.partidas, 7)
    assert(r.hayCampeon)
  }

  test("cantidad que no es potencia de 2 usa BYEs") {
    val r = EliminacionDirecta.jugar(Apoyo.jugadores(5), Apoyo.SEMILLA)
    assertEquals(r.numRondas, 3)
    assertEquals(r.partidas, 4)
    assertEquals(Listas.contar(r.finales), 5)
  }

  test("es determinista") {
    val c1 = EliminacionDirecta.jugar(Apoyo.jugadores(33), Apoyo.SEMILLA).campeon.id
    val c2 = EliminacionDirecta.jugar(Apoyo.jugadores(33), Apoyo.SEMILLA).campeon.id
    assertEquals(c1, c2)
  }

  test("menos de dos jugadores es un error controlado") {
    val e = intercept[ErrorDatos](EliminacionDirecta.jugar(Apoyo.jugadores(1), Apoyo.SEMILLA))
    assertEquals(e.tipo, TipoError.TORNEO_INSUFICIENTE)
    intercept[ErrorDatos](EliminacionDirecta.jugar(Nil, Apoyo.SEMILLA))
  }
}
