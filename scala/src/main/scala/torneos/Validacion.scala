package torneos

// Reglas de validacion (identicas en los 3 lenguajes). El texto se trocea en Infraestructura
// (split/trim son API de String); aqui se aplican las reglas de negocio.
object Validacion {

  case class Analisis(linea: Int, valida: Boolean, jugador: Jugador, error: RegistroError)

  def validar(lineas: List[(Int, String)], erroresLectura: List[RegistroError]): ResultadoCarga = {
    val analizadas = lineas
      .filter(l => !Infraestructura.esIgnorable(l._1, l._2))
      .map(l => analizar(l._1, l._2))
    val invalidas = analizadas.filter(a => !a.valida).map(a => a.error)
    val sinDuplicados = deduplicar(analizadas.filter(a => a.valida), Set[Int](), Nil, Nil) // [TABLA: Set]
    ResultadoCarga(sinDuplicados._1, erroresLectura ::: invalidas ::: sinDuplicados._2, Listas.contar(sinDuplicados._1))
  }

  // Cada linea se analiza con try/catch: un error de datos NO detiene el programa, se registra
  def analizar(numero: Int, texto: String): Analisis =
    try { // [TABLA: try]
      val campos = Infraestructura.trocearLinea(numero, texto)
      Analisis(numero, true, Jugador.nuevo(campos.id, campos.nombre, validarElo(numero, campos)), RegistroError("", ""))
    } catch { // [TABLA: catch]
      case e: ErrorDatos => Analisis(numero, false, Jugador.vacio, e.comoRegistro)
    }

  // Reglas del Elo: vacio -> 1000; "null" o <= 0 -> ELO_INVALIDO; formato raro -> LINEA_MALFORMADA
  def validarElo(numero: Int, c: CamposCsv): Double = {
    if (c.eloVacio) return Jugador.ELO_POR_DEFECTO // [TABLA: return]
    if (c.eloNulo) throw new ErrorDatos(TipoError.ELO_INVALIDO, "Linea " + numero + ": Elo nulo")
    if (!c.eloConFormato) throw new ErrorDatos(TipoError.LINEA_MALFORMADA, "Linea " + numero + ": Elo con formato invalido '" + c.eloTexto + "'")
    if (c.eloCentesimas <= 0.0) throw new ErrorDatos(TipoError.ELO_INVALIDO, "Linea " + numero + ": Elo negativo o cero (" + c.eloTexto + ")")
    c.eloCentesimas
  }

  // Recorre en orden de archivo guardando los ids vistos en un Set: la segunda aparicion es duplicada
  def deduplicar(pendientes: List[Analisis], vistos: Set[Int], unicos: List[Jugador],
                 errores: List[RegistroError]): (List[Jugador], List[RegistroError]) = pendientes match {
    case a :: resto =>
      if (vistos.contains(a.jugador.id))
        deduplicar(resto, vistos, unicos,
          RegistroError(TipoError.ID_DUPLICADO, "Linea " + a.linea + ": id duplicado " + a.jugador.id) :: errores)
      else deduplicar(resto, vistos + a.jugador.id, a.jugador :: unicos, errores)
    case Nil => (Listas.invertir(unicos), Listas.invertir(errores))
  }
}

// Campos ya troceados de una linea del CSV
case class CamposCsv(id: Int, nombre: String, eloTexto: String, eloVacio: Boolean, eloNulo: Boolean,
                     eloConFormato: Boolean, eloCentesimas: Double)
