package torneos

import scala.math._

// Estadistica descriptiva de las ejecuciones (funciones puras)
case class Resumen(mediana: Double, media: Double, desviacion: Double, minimo: Double, maximo: Double)

object Medicion {

  def resumir(valores: List[Double]): Resumen = {
    val ordenados = Listas.ordenar(valores, (a: Double, b: Double) => a < b)
    val n = Listas.contar(ordenados)
    if (n == 0) Resumen(0.0, 0.0, 0.0, 0.0, 0.0)
    else {
      val media = ordenados.fold(0.0)(_ + _) / n
      val mediana =
        if (n % 2 == 1) Listas.enPosicion(ordenados, n / 2, 0.0)
        else (Listas.enPosicion(ordenados, n / 2 - 1, 0.0) + Listas.enPosicion(ordenados, n / 2, 0.0)) / 2.0
      val desviacion =
        if (n < 2) 0.0
        else sqrt(ordenados.map(x => (x - media) * (x - media)).fold(0.0)(_ + _) / (n - 1))
      Resumen(mediana, media, desviacion, Listas.enPosicion(ordenados, 0, 0.0), Listas.enPosicion(ordenados, n - 1, 0.0))
    }
  }
}
