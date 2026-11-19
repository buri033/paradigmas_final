package torneos

// Torneo por ELIMINACION DIRECTA. Cada ronda transforma la lista de vivos de la anterior:
// ordenar por Elo -> emparejar -> resolver cada llave -> los ganadores pasan.
object EliminacionDirecta extends Torneo("ELIMINACION_DIRECTA") with Puntuable { // [TABLA: with]

  override def descripcion: String = "Eliminacion directa"

  case class ResultadoLlave(ganador: Jugador, perdedor: Jugador, partida: Partida, jugada: Boolean)

  def jugar(jugadores: List[Jugador], semilla: Long): ResultadoEliminacion = {
    validarParticipantes(Listas.contar(jugadores))
    jugarRondas(jugadores, 1, semilla, Nil, Nil)
  }

  // Recursion por rondas: termina cuando queda un solo jugador (el campeon)
  def jugarRondas(vivos: List[Jugador], ronda: Int, semilla: Long,
                  eliminados: List[Jugador], rondas: List[List[Partida]]): ResultadoEliminacion = vivos match {
    case campeon :: Nil =>
      val enOrden = Listas.invertir(rondas)
      val partidas = enOrden.map(r => Listas.contar(r)).fold(0)(_ + _)
      ResultadoEliminacion(true, campeon, enOrden, ronda - 1, partidas, campeon :: eliminados)
    case _ =>
      val llaves = Matchmaking.emparejar(Matchmaking.ordenar(vivos), Nil)
      val resultados = resolverLlaves(llaves, ronda, 0, semilla, Nil)
      val jugadas = resultados.filter(r => r.jugada) // [TABLA: filter]
      jugarRondas(resultados.map(r => r.ganador), ronda + 1, semilla,
        jugadas.map(r => r.perdedor) ::: eliminados, jugadas.map(r => r.partida) :: rondas)
  }

  def resolverLlaves(llaves: List[Emparejamiento], ronda: Int, indice: Int, semilla: Long,
                     acumulado: List[ResultadoLlave]): List[ResultadoLlave] = llaves match {
    case llave :: resto => resolverLlaves(resto, ronda, indice + 1, semilla, resolver(llave, ronda, indice, semilla) :: acumulado)
    case Nil => Listas.invertir(acumulado)
  }

  // Funcion pura: el aleatorio solo depende de (semilla, ronda, indice)
  def resolver(llave: Emparejamiento, ronda: Int, indice: Int, semilla: Long): ResultadoLlave = llave match {
    case Pareja(a, b) =>
      val u = GeneradorLCG.aleatorioPartida(semilla, ronda, indice)
      val ganaA = u < Elo.esperado(a.elo, b.elo)
      val s = if (ganaA) 1.0 else 0.0
      val delta = Elo.delta(a.elo, b.elo, s)
      val a2 = a.conResultado(delta, if (ganaA) 1 else 0, if (ganaA) 0 else 1, 0, puntos(if (ganaA) "VICTORIA" else "DERROTA"))
      val b2 = b.conResultado(-delta, if (ganaA) 0 else 1, if (ganaA) 1 else 0, 0, puntos(if (ganaA) "DERROTA" else "VICTORIA"))
      val partida = Partida(a.id, b.id, if (ganaA) Resultado.GANA_A else Resultado.GANA_B, ronda, delta)
      if (ganaA) ResultadoLlave(a2, b2, partida, true) else ResultadoLlave(b2, a2, partida, true)
    case Bye(j) => ResultadoLlave(j, j, Partida(j.id, 0, Resultado.BYE, ronda, 0.0), false)
  }
}
