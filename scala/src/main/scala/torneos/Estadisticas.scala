package torneos

import scala.math._

// Estadisticas funcionales: agrupacion por rango (map + fold + combinar), promedio, top N, maximo (reduce)
object Estadisticas {

  val RANGOS: List[String] = List("BRONCE", "PLATA", "ORO", "PLATINO")
  val TOP_N: Int = 10

  case class Acumulado(cantidad: Int, sumaElo: Double, victorias: Int)

  val cero: Acumulado = Acumulado(0, 0.0, 0)

  // Rango por Elo (centesimas): <1200 bronce, <1500 plata, <1800 oro, resto platino
  def rango(elo: Double): String =
    if (elo < 120000.0) "BRONCE"
    else if (elo < 150000.0) "PLATA"
    else if (elo < 180000.0) "ORO"
    else "PLATINO"

  // Fusiona dos Map de rangos sumando sus acumulados (operacion asociativa para fold)
  def combinar(a: Map[String, Acumulado], b: Map[String, Acumulado]): Map[String, Acumulado] =
    construirMapa(RANGOS.map(r => (r, sumar(a.getOrElse(r, cero), b.getOrElse(r, cero)))), Map[String, Acumulado]())

  def sumar(x: Acumulado, y: Acumulado): Acumulado =
    Acumulado(x.cantidad + y.cantidad, x.sumaElo + y.sumaElo, x.victorias + y.victorias)

  def construirMapa(pares: List[(String, Acumulado)], acumulado: Map[String, Acumulado]): Map[String, Acumulado] = pares match {
    case par :: resto => construirMapa(resto, acumulado + par)
    case Nil => acumulado
  }

  def indexarVictorias(filas: List[Jugador], acumulado: Map[Int, Int]): Map[Int, Int] = filas match {
    case j :: resto => indexarVictorias(resto, acumulado + (j.id -> j.victorias))
    case Nil => acumulado
  }

  // Promedio redondeado a centesimas: floor((2*suma + n) / (2*n)), igual que la division entera de Java/C#
  def promedio(a: Acumulado): Double =
    if (a.cantidad == 0) 0.0 else floor((2.0 * a.sumaElo + a.cantidad) / (2.0 * a.cantidad))

  // Mas victorias; si empatan, el de id menor (operador asociativo para reduce)
  def masVictorias(a: Jugador, b: Jugador): Jugador =
    if (a.victorias > b.victorias || (a.victorias == b.victorias && a.id < b.id)) a else b

  def calcular(iniciales: List[Jugador], liga: ResultadoLiga): ResultadoEstadisticas = {
    val filas = Listas.aplanar(liga.tablas)
    val victoriasPorId = indexarVictorias(filas, Map[Int, Int]())
    val porRango = iniciales
      .map(j => Map(rango(j.elo) -> Acumulado(1, j.elo, victoriasPorId.getOrElse(j.id, 0))))
      .fold(Map[String, Acumulado]())(combinar)
    val grupos = RANGOS.map(r => {
      val a = porRango.getOrElse(r, cero)
      GrupoElo(r, a.cantidad, promedio(a), a.victorias)
    })
    val top = Listas.primeros(Matchmaking.ordenar(iniciales), TOP_N)
    filas match {
      case Nil => ResultadoEstadisticas(grupos, top, false, Jugador.vacio)
      case _ => ResultadoEstadisticas(grupos, top, true, filas.reduce(masVictorias)) // [TABLA: reduce]
    }
  }
}
