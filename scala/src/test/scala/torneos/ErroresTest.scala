package torneos

class ErroresTest extends munit.FunSuite {

  test("linea malformada se registra sin detener el programa") {
    val r = Validacion.validar(Apoyo.csv(List("id,nombre,elo", "1,Ana", "2,Beto,1500", "x,Caro,1400", "3,,1300")), Nil)
    assertEquals(r.validos, 1)
    assertEquals(Apoyo.conteo(r, TipoError.LINEA_MALFORMADA), 3)
  }

  test("Elo negativo, cero o nulo es invalido") {
    val r = Validacion.validar(Apoyo.csv(List("1,A,-1200", "2,B,0", "3,C,null", "4,D,1500.5")), Nil)
    assertEquals(Apoyo.conteo(r, TipoError.ELO_INVALIDO), 3)
    assertEquals(r.jugadores.map(j => j.elo), List(150050.0))
  }

  test("id duplicado conserva la primera aparicion") {
    val r = Validacion.validar(Apoyo.csv(List("7,Primero,1500", "7,Segundo,1600")), Nil)
    assertEquals(r.jugadores.map(j => j.nombre), List("Primero"))
    assertEquals(Apoyo.conteo(r, TipoError.ID_DUPLICADO), 1)
  }

  test("Elo vacio vale 1000") {
    val r = Validacion.validar(Apoyo.csv(List("5,SinElo,")), Nil)
    assertEquals(r.jugadores.map(j => j.elo), List(100000.0))
  }

  test("archivo inexistente no lanza excepcion") {
    val r = Pipeline.cargar("no/existe/archivo.csv")
    assertEquals(r.validos, 0)
    assertEquals(Apoyo.conteo(r, TipoError.ARCHIVO_INEXISTENTE), 1)
  }

  test("escenario sucio detecta todos los errores") {
    val r = Pipeline.cargar("../datos/escenarios/sucio.csv")
    assertEquals(r.validos, 999)
    assertEquals(Apoyo.conteo(r, TipoError.LINEA_MALFORMADA), 12)
    assertEquals(Apoyo.conteo(r, TipoError.ELO_INVALIDO), 10)
    assertEquals(Apoyo.conteo(r, TipoError.ID_DUPLICADO), 8)
  }
}
