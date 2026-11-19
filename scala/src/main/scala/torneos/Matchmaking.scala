package torneos

// Matchmaking por cercania de nivel: 1o vs 2o, 3o vs 4o, ...; si es impar, el ultimo recibe BYE
object Matchmaking {

  // Orden total y determinista: Elo descendente y, si empatan, id ascendente
  val antesPorElo: (Jugador, Jugador) => Boolean =
    (a, b) => a.elo > b.elo || (a.elo == b.elo && a.id < b.id)

  def ordenar(jugadores: List[Jugador]): List[Jugador] = Listas.ordenar(jugadores, antesPorElo)

  def emparejar(ordenados: List[Jugador], acumulado: List[Emparejamiento]): List[Emparejamiento] = ordenados match {
    case a :: b :: resto => emparejar(resto, Pareja(a, b) :: acumulado)
    case ultimo :: Nil => Listas.volcar(acumulado, List(Bye(ultimo)))
    case Nil => Listas.volcar(acumulado, Nil)
  }

  def ejecutar(jugadores: List[Jugador]): ResultadoMatchmaking = {
    val emparejamientos = emparejar(ordenar(jugadores), Nil)
    val esBye = emparejamientos.map(e => e match {
      case Bye(_) => 1
      case _ => 0
    })
    val byes = esBye.fold(0)(_ + _)
    ResultadoMatchmaking(emparejamientos, Listas.contar(emparejamientos) - byes, byes)
  }
}
