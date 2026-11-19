package torneos

// Datos de apoyo para las pruebas (solo construcciones de la tabla)
object Apoyo {
  val SEMILLA: Long = 20261117L

  // n jugadores con Elo 1000, 1010, 1020... (en centesimas)
  def jugadores(n: Int): List[Jugador] = construir(n, Nil)

  def construir(i: Int, acumulado: List[Jugador]): List[Jugador] =
    if (i == 0) acumulado else construir(i - 1, Jugador.nuevo(i, "J" + i, 100000.0 + 1000.0 * i) :: acumulado)

  def csv(lineas: List[String]): List[(Int, String)] = numerar(lineas, 1, Nil)

  def numerar(lineas: List[String], n: Int, acumulado: List[(Int, String)]): List[(Int, String)] = lineas match {
    case l :: resto => numerar(resto, n + 1, (n, l) :: acumulado)
    case Nil => Listas.invertir(acumulado)
  }

  def conteo(r: ResultadoCarga, tipo: String): Int = Infraestructura.conteoPorTipo(r.errores, tipo)
}
