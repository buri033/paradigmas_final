package torneos

// Autoverificacion del resultado (funciones puras): recursion sobre listas y fold de booleanos
object Invariantes {

  def cumple(r: ResultadoCompleto): Boolean = {
    val n = r.carga.validos
    val filas = Listas.aplanar(r.liga.tablas)
    val comprobaciones = List(
      n < 2 || r.eliminacion.partidas == n - 1,
      r.liga.tablas.map(t => ordenada(t)).fold(true)(_ && _),
      r.carga.jugadores.map(j => j.elo > 0.0).fold(true)(_ && _),
      n < 2 || filas.map(j => j.puntos > 0).fold(false)(_ || _))
    comprobaciones.fold(true)(_ && _)
  }

  def ordenada(t: List[Jugador]): Boolean = t match {
    case a :: b :: resto => Liga.antesEnTabla(a, b) && ordenada(b :: resto)
    case _ => true
  }
}
