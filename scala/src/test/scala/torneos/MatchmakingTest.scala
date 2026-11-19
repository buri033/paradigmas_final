package torneos

class MatchmakingTest extends munit.FunSuite {

  test("ordena por Elo descendente y desempata por id") {
    val js = List(Jugador.nuevo(3, "C", 150000.0), Jugador.nuevo(1, "A", 150000.0), Jugador.nuevo(2, "B", 170000.0))
    assertEquals(Matchmaking.ordenar(js).map(j => j.id), List(2, 1, 3))
  }

  test("empareja 1o con 2o y 3o con 4o") {
    val e = Matchmaking.ejecutar(Apoyo.jugadores(4))
    assertEquals(e.parejas, 2)
    e.emparejamientos match {
      case Pareja(a, b) :: _ =>
        assertEquals(a.id, 4)
        assertEquals(b.id, 3)
      case _ => fail("se esperaba una pareja")
    }
  }

  test("numero impar da BYE al ultimo") {
    val e = Matchmaking.ejecutar(Apoyo.jugadores(5))
    assertEquals(e.byes, 1)
    assertEquals(Listas.enPosicion(e.emparejamientos, 2, Bye(Jugador.vacio)), Bye(Apoyo.jugadores(1) match {
      case j :: _ => j
      case Nil => Jugador.vacio
    }))
  }

  test("lista vacia no genera parejas") {
    assertEquals(Matchmaking.ejecutar(Nil).emparejamientos, Nil)
  }
}
