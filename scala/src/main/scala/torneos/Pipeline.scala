package torneos

// Composicion de las fases: carga -> matchmaking -> eliminacion -> liga -> estadisticas.
// Los efectos (leer archivo, medir) se delegan a Infraestructura; el resto son funciones puras.
object Pipeline {

  case class Intento[T](valor: T, errores: List[RegistroError])

  def ejecutar(ruta: String, semilla: Long, tamGrupo: Int, c: Infraestructura.Cronometro): ResultadoCompleto = {
    val carga = c.medir("carga", () => cargar(ruta))
    val jugadores = carga.jugadores
    val mm = c.medir("matchmaking", () => Matchmaking.ejecutar(jugadores))
    val elim = c.medir("eliminacion", () =>
      intentar(() => EliminacionDirecta.jugar(jugadores, semilla), ResultadosVacios.eliminacion))
    val liga = c.medir("liga", () => intentar(() => Liga.jugar(jugadores, semilla, tamGrupo), ResultadosVacios.liga))
    val stats = c.medir("estadisticas", () => Estadisticas.calcular(jugadores, liga.valor))
    ResultadoCompleto(carga, mm, elim.valor, liga.valor, stats, carga.errores ::: elim.errores ::: liga.errores)
  }

  def cargar(ruta: String): ResultadoCarga =
    try {
      Validacion.validar(Infraestructura.leerLineas(ruta), Nil)
    } catch {
      case e: ErrorDatos => ResultadoCarga(Nil, List(e.comoRegistro), 0)
    }

  // Ejecuta una operacion que puede fallar de forma controlada (funcion de orden superior)
  def intentar[T](operacion: () => T, porDefecto: T): Intento[T] =
    try {
      Intento(operacion(), Nil)
    } catch {
      case e: ErrorDatos => Intento(porDefecto, List(e.comoRegistro))
    }
}
