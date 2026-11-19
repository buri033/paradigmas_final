package torneos

// Punto de entrada (CLI comun a los 3 lenguajes):
//   java -jar torneos-scala.jar --input datos.csv --mode all --runs 10 --warmup 3 --out resultado.json
object Main {

  val LENGUAJE: String = "Scala"
  val FASES: List[String] = List("carga", "matchmaking", "eliminacion", "liga", "estadisticas", "total")

  def main(args: Array[String]): Unit = {
    val a = Infraestructura.argumentos(args)
    val entrada = a.getOrElse("input", "../datos/escenarios/pequeno.csv")
    val salida = a.getOrElse("out", "resultado_scala.json")
    val modo = a.getOrElse("mode", "all")
    val runs = Infraestructura.aEntero(a.getOrElse("runs", "10"))
    val warmup = Infraestructura.aEntero(a.getOrElse("warmup", "3"))
    val semilla = Infraestructura.aLargo(a.getOrElse("seed", "20261117"))
    val grupo = Infraestructura.aEntero(a.getOrElse("grupo", "64"))

    // Ciclo de repeticiones: primero el calentamiento (se descarta), luego las ejecuciones medidas
    var corrida = 0 // [TABLA: var] contador del ciclo de medicion
    var mediciones: List[Infraestructura.Cronometro] = Nil
    do { // [TABLA: do-while] siempre se ejecuta al menos una vez
      if (corrida == warmup) {
        Infraestructura.recolectarBasura()
        Infraestructura.reiniciarPicos()
      }
      val c = new Infraestructura.Cronometro()
      c.medir("total", () => Pipeline.ejecutar(entrada, semilla, grupo, c))
      if (corrida >= warmup) mediciones = c :: mediciones
      corrida = corrida + 1
    } while (corrida < warmup + runs)

    val r = Pipeline.ejecutar(entrada, semilla, grupo, new Infraestructura.Cronometro())
    val enOrden = Listas.invertir(mediciones)
    val tiempos = FASES.map(f => (f, enOrden.map(c => c.tiemposMs.getOrElse(f, 0.0))))
    val medianaTotalSeg = Medicion.resumir(enOrden.map(c => c.tiemposMs.getOrElse("total", 0.0))).mediana / 1000.0
    val partidas = r.eliminacion.partidas + r.liga.partidas
    val picoMuestras = enOrden.map(c => c.picoMuestreado).fold(0.0)((x, y) => if (x > y) x else y)
    val picoPools = Infraestructura.picoHeapPools()
    val picoHeap = if (picoPools > picoMuestras) picoPools else picoMuestras
    val texto = Infraestructura.salidaNormalizada(r)
    val hash = Infraestructura.sha256(texto)
    val porSeg = (cantidad: Double) => if (medianaTotalSeg > 0) cantidad / medianaTotalSeg else 0.0

    Infraestructura.escribir(salida, Infraestructura.construirJson(LENGUAJE, entrada, modo, semilla, warmup, runs, tiempos,
      picoHeap, Infraestructura.picoProceso(), porSeg(partidas), porSeg(r.carga.validos), Invariantes.cumple(r), r, texto, hash))

    Infraestructura.imprimir("[" + LENGUAJE + "] " + entrada + " | validos=" + r.carga.validos +
      " errores=" + Listas.contar(r.errores) + " | invariantes=" + (if (Invariantes.cumple(r)) "OK" else "FALLAN") + " | campeon=" + (if (r.eliminacion.hayCampeon) r.eliminacion.campeon.id else 0) +
      " | mediana total=" + Infraestructura.dosDecimales(medianaTotalSeg * 1000) + " ms | hash=" + hash.substring(0, 16) + "...")
    // Primeros errores del reporte (efecto de salida en el borde)
    Listas.primeros(r.errores, 5).foreach(e => Infraestructura.imprimir("   - " + e.tipo + ": " + e.mensaje)) // [TABLA: foreach]
  }
}
