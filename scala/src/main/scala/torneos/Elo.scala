package torneos

import scala.math._ // [TABLA: import] pow y floor

// Sistema Elo (funciones puras). Elo en centesimas: (Rb - Ra) / 400 = (rb - ra) / 40000.
object Elo {
  val K: Double = 32.0

  // E = 1 / (1 + 10^((Rb - Ra) / 400))
  def esperado(ra: Double, rb: Double): Double = 1.0 / (1.0 + pow(10.0, (rb - ra) / 40000.0))

  // Variacion de A en centesimas: K * (S - E), redondeada con floor(x + 0.5) igual que Java y C#
  def delta(ra: Double, rb: Double, s: Double): Double = floor(K * (s - esperado(ra, rb)) * 100.0 + 0.5)
}

// Contrato de una fuente aleatoria
trait Aleatorio {
  def siguienteDouble(): Double
}

// LCG de 64 bits PROPIO, mismo algoritmo y constantes que Java y C#:
// estado' = estado * 6364136223846793005 + 1442695040888963407 (mod 2^64)
class GeneradorLCG(semilla: Long) extends Aleatorio {
  var estado: Long = semilla // [TABLA: var] estado interno del LCG: es la naturaleza del generador

  def siguienteLong(): Long = {
    estado = estado * GeneradorLCG.A + GeneradorLCG.C // el desbordamiento de Long equivale a mod 2^64
    estado
  }

  override def siguienteDouble(): Double = (siguienteLong() >>> 11) * GeneradorLCG.ESCALA // [TABLA: override]
}

object GeneradorLCG {
  val A: Long = 6364136223846793005L
  val C: Long = 1442695040888963407L
  val DORADO: Long = -7046029254386353131L // 0x9E3779B97F4A7C15 con signo
  val ESCALA: Double = 1.0 / 9007199254740992.0 // 2^-53

  // Aleatorio de una partida: funcion PURA de (semilla, ronda, indice). El var queda encerrado
  // dentro del generador local, por eso desde afuera la funcion no tiene efectos.
  def aleatorioPartida(semilla: Long, ronda: Int, indice: Int): Double = {
    val clave = ronda * 1000003L + indice
    val g = new GeneradorLCG(semilla ^ (clave * DORADO))
    g.siguienteLong()
    g.siguienteDouble()
  }
}
