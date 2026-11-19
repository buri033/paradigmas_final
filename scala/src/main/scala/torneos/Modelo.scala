package torneos // [TABLA: package]

// =====================================================================================
// Modelo de dominio. Solo construcciones de la tabla de clase:
// case class, class, object, trait, abstract class, extends, with, override, new, val, def.
// El Elo se guarda en CENTESIMAS dentro de un Double con valor entero (1534.50 -> 153450.0):
// las sumas de enteros menores que 2^53 son exactas en cualquier orden.
// =====================================================================================

case class Jugador(id: Int, nombre: String, elo: Double, victorias: Int, derrotas: Int, empates: Int, puntos: Int) { // [TABLA: case class]
  // Devuelve un jugador NUEVO con el resultado aplicado (inmutabilidad: no se modifica el original)
  def conResultado(deltaElo: Double, v: Int, d: Int, e: Int, pts: Int): Jugador = // [TABLA: def]
    Jugador(id, nombre, elo + deltaElo, victorias + v, derrotas + d, empates + e, puntos + pts)
}

object Jugador { // [TABLA: object]
  val ELO_POR_DEFECTO: Double = 100000.0 // [TABLA: val] 1000.00 en centesimas
  val vacio: Jugador = Jugador(0, "", 0.0, 0, 0, 0, 0)
  def nuevo(id: Int, nombre: String, elo: Double): Jugador = Jugador(id, nombre, elo, 0, 0, 0, 0)
}

case class Partida(jugadorA: Int, jugadorB: Int, resultado: String, ronda: Int, deltaEloA: Double) {
  def ganador: Int = resultado match { // [TABLA: match]
    case "GANA_A" => jugadorA
    case "BYE" => jugadorA
    case "GANA_B" => jugadorB
    case _ => 0
  }
}

object Resultado {
  val GANA_A = "GANA_A"
  val GANA_B = "GANA_B"
  val EMPATE = "EMPATE"
  val BYE = "BYE"
}

object TipoError {
  val LINEA_MALFORMADA = "LINEA_MALFORMADA"
  val ELO_INVALIDO = "ELO_INVALIDO"
  val ID_DUPLICADO = "ID_DUPLICADO"
  val ARCHIVO_INEXISTENTE = "ARCHIVO_INEXISTENTE"
  val TORNEO_INSUFICIENTE = "TORNEO_INSUFICIENTE"
  val TODOS: List[String] = List(LINEA_MALFORMADA, ELO_INVALIDO, ID_DUPLICADO, ARCHIVO_INEXISTENTE, TORNEO_INSUFICIENTE) // [TABLA: List]
}

case class RegistroError(tipo: String, mensaje: String)

// Excepcion propia: se lanza con throw y se captura con try/catch para registrar el error
class ErrorDatos(val tipo: String, val detalle: String) extends Exception(detalle) { // [TABLA: class] [TABLA: extends]
  def comoRegistro: RegistroError = RegistroError(tipo, detalle)
}

// Resultado del matchmaking: una pareja que juega o un BYE
abstract class Emparejamiento // [TABLA: abstract class]
case class Pareja(a: Jugador, b: Jugador) extends Emparejamiento
case class Bye(jugador: Jugador) extends Emparejamiento

// Algo que reparte puntos por resultado (implementacion por defecto que se puede sobrescribir)
trait Puntuable { // [TABLA: trait]
  def puntosVictoria: Int = 3
  def puntosEmpate: Int = 1
  def puntosDerrota: Int = 0
  def puntos(resultadoPropio: String): Int = resultadoPropio match {
    case "VICTORIA" => puntosVictoria
    case "EMPATE" => puntosEmpate
    case _ => puntosDerrota
  }
}

// Plantilla comun de los torneos: validacion de participantes compartida
abstract class Torneo(val tipo: String) {
  def minimoJugadores: Int = 2
  def descripcion: String
  def validarParticipantes(cantidad: Int): Unit = {
    if (cantidad < minimoJugadores) { // [TABLA: if]
      throw new ErrorDatos(TipoError.TORNEO_INSUFICIENTE, // [TABLA: throw] [TABLA: new]
        descripcion + ": se requieren al menos " + minimoJugadores + " jugadores (hay " + cantidad + ")")
    }
  }
}

// Resultados inmutables de cada fase (sin Option: se usan banderas booleanas)
case class ResultadoCarga(jugadores: List[Jugador], errores: List[RegistroError], validos: Int)
case class ResultadoMatchmaking(emparejamientos: List[Emparejamiento], parejas: Int, byes: Int)
case class ResultadoEliminacion(hayCampeon: Boolean, campeon: Jugador, rondas: List[List[Partida]],
                                numRondas: Int, partidas: Int, finales: List[Jugador])
case class ResultadoLiga(tablas: List[List[Jugador]], grupos: Int, partidas: Int)
case class GrupoElo(rango: String, cantidad: Int, promedioElo: Double, victorias: Int)
case class ResultadoEstadisticas(grupos: List[GrupoElo], top: List[Jugador], hayMasVictorias: Boolean, masVictorias: Jugador)
case class ResultadoCompleto(carga: ResultadoCarga, matchmaking: ResultadoMatchmaking, eliminacion: ResultadoEliminacion,
                             liga: ResultadoLiga, estadisticas: ResultadoEstadisticas, errores: List[RegistroError])

object ResultadosVacios {
  val eliminacion: ResultadoEliminacion = ResultadoEliminacion(false, Jugador.vacio, Nil, 0, 0, Nil)
  val liga: ResultadoLiga = ResultadoLiga(Nil, 0, 0)
}
