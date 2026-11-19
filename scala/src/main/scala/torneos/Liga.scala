package torneos

// Torneo por LIGA (todos contra todos) en grupos de tamano fijo para evitar O(n^2).
// Elo "por lotes" (como FIDE): cada partida usa el Elo inicial; las variaciones se suman al final.
// Desempate de la tabla: puntos desc, Elo desc, id asc.
object Liga extends Torneo("LIGA") with Puntuable {

  override def descripcion: String = "Liga"

  val PROB_EMPATE: Double = 0.10

  case class Aporte(id: Int, puntos: Int, victorias: Int, empates: Int, derrotas: Int, deltaElo: Double) {
    def sumar(o: Aporte): Aporte =
      Aporte(id, puntos + o.puntos, victorias + o.victorias, empates + o.empates, derrotas + o.derrotas, deltaElo + o.deltaElo)
  }

  case class TablaGrupo(tabla: List[Jugador], partidas: Int)

  val antesEnTabla: (Jugador, Jugador) => Boolean = (a, b) =>
    a.puntos > b.puntos || (a.puntos == b.puntos && (a.elo > b.elo || (a.elo == b.elo && a.id < b.id)))

  def jugar(jugadores: List[Jugador], semilla: Long, tamGrupo: Int): ResultadoLiga = {
    validarParticipantes(Listas.contar(jugadores))
    val grupos = Listas.partir(Matchmaking.ordenar(jugadores), tamGrupo, Nil)
    val tablas = jugarGrupos(grupos, 0, semilla, Nil)
    ResultadoLiga(tablas.map(t => t.tabla), Listas.contar(tablas), tablas.map(t => t.partidas).fold(0)(_ + _))
  }

  def jugarGrupos(grupos: List[List[Jugador]], g: Int, semilla: Long, acumulado: List[TablaGrupo]): List[TablaGrupo] = grupos match {
    case grupo :: resto => jugarGrupos(resto, g + 1, semilla, jugarGrupo(grupo, g, semilla) :: acumulado)
    case Nil => Listas.invertir(acumulado)
  }

  // Todos contra todos: (0,1),(0,2)...(0,n-1),(1,2)... mismo orden en los tres lenguajes
  def generarEnfrentamientos(grupo: List[Jugador]): List[(Jugador, Jugador)] = grupo match {
    case x :: resto => resto.map(y => (x, y)) ::: generarEnfrentamientos(resto)
    case Nil => Nil
  }

  def jugarGrupo(grupo: List[Jugador], g: Int, semilla: Long): TablaGrupo = {
    val partidas = jugarPartidas(generarEnfrentamientos(grupo), g, 0, semilla, Nil)
    val aportes = Listas.aplanar(partidas.map(p => aportesDe(p)))
    val porJugador = acumular(aportes, Map[Int, Aporte]()) // [TABLA: Map]
    val filas = grupo.map(j => aplicar(j, porJugador.getOrElse(j.id, Aporte(j.id, 0, 0, 0, 0, 0.0))))
    TablaGrupo(Listas.ordenar(filas, antesEnTabla), Listas.contar(partidas))
  }

  def jugarPartidas(pares: List[(Jugador, Jugador)], g: Int, k: Int, semilla: Long, acumulado: List[Partida]): List[Partida] = pares match {
    case par :: resto => jugarPartidas(resto, g, k + 1, semilla, jugarPartida(par._1, par._2, g, k, semilla) :: acumulado)
    case Nil => Listas.invertir(acumulado)
  }

  def jugarPartida(a: Jugador, b: Jugador, g: Int, k: Int, semilla: Long): Partida = {
    val u = GeneradorLCG.aleatorioPartida(semilla, 100000 + g, k)
    val e = Elo.esperado(a.elo, b.elo)
    val resultado =
      if (u < PROB_EMPATE) Resultado.EMPATE
      else if (u < PROB_EMPATE + (1.0 - PROB_EMPATE) * e) Resultado.GANA_A // [TABLA: else if]
      else Resultado.GANA_B
    val s = resultado match {
      case "GANA_A" => 1.0
      case "EMPATE" => 0.5
      case _ => 0.0
    }
    Partida(a.id, b.id, resultado, g + 1, Elo.delta(a.elo, b.elo, s))
  }

  // Lo que una partida le aporta a cada uno de sus dos jugadores
  def aportesDe(p: Partida): List[Aporte] = p.resultado match {
    case "GANA_A" => List(aporte(p.jugadorA, "VICTORIA", p.deltaEloA), aporte(p.jugadorB, "DERROTA", -p.deltaEloA))
    case "GANA_B" => List(aporte(p.jugadorA, "DERROTA", p.deltaEloA), aporte(p.jugadorB, "VICTORIA", -p.deltaEloA))
    case _ => List(aporte(p.jugadorA, "EMPATE", p.deltaEloA), aporte(p.jugadorB, "EMPATE", -p.deltaEloA))
  }

  def aporte(id: Int, propio: String, delta: Double): Aporte =
    Aporte(id, puntos(propio), if (propio == "VICTORIA") 1 else 0, if (propio == "EMPATE") 1 else 0,
      if (propio == "DERROTA") 1 else 0, delta)

  // Agrupa los aportes por id en un Map (la tabla no tiene agrupacion; se usa recursion + Map)
  def acumular(pendientes: List[Aporte], acumulado: Map[Int, Aporte]): Map[Int, Aporte] = pendientes match {
    case a :: resto =>
      val previo = acumulado.getOrElse(a.id, Aporte(a.id, 0, 0, 0, 0, 0.0))
      acumular(resto, acumulado + (a.id -> previo.sumar(a)))
    case Nil => acumulado
  }

  def aplicar(j: Jugador, a: Aporte): Jugador = j.conResultado(a.deltaElo, a.victorias, a.derrotas, a.empates, a.puntos)
}
