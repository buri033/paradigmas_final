package torneos

class LigaTest extends munit.FunSuite {

  def ordenada(t: List[Jugador]): Boolean = t match {
    case a :: b :: resto => Liga.antesEnTabla(a, b) && ordenada(b :: resto)
    case _ => true
  }

  test("todos contra todos genera n(n-1)/2 enfrentamientos") {
    assertEquals(Listas.contar(Liga.generarEnfrentamientos(Apoyo.jugadores(4))), 6)
    assertEquals(Listas.contar(Liga.generarEnfrentamientos(Apoyo.jugadores(64))), 2016)
  }

  test("puntos, victorias y derrotas cuadran") {
    val r = Liga.jugar(Apoyo.jugadores(10), Apoyo.SEMILLA, 64)
    val t = Listas.aplanar(r.tablas)
    val v = t.map(j => j.victorias).fold(0)(_ + _)
    val d = t.map(j => j.derrotas).fold(0)(_ + _)
    val e = t.map(j => j.empates).fold(0)(_ + _)
    val pts = t.map(j => j.puntos).fold(0)(_ + _)
    assertEquals(r.partidas, 45)
    assertEquals(v, d)
    assertEquals(pts, 3 * v + e)
    assertEquals(t.map(j => j.elo).fold(0.0)(_ + _), Apoyo.jugadores(10).map(j => j.elo).fold(0.0)(_ + _))
  }

  test("tabla ordenada por puntos, Elo e id") {
    val r = Liga.jugar(Apoyo.jugadores(20), Apoyo.SEMILLA, 64)
    assert(ordenada(Listas.aplanar(r.tablas)))
  }

  test("grupos de 64") {
    val r = Liga.jugar(Apoyo.jugadores(130), Apoyo.SEMILLA, 64)
    assertEquals(r.grupos, 3)
    assertEquals(r.partidas, 2016 * 2 + 1)
  }
}
