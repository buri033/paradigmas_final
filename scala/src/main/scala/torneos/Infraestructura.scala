package torneos

import java.io.{BufferedReader, File, FileInputStream, FileNotFoundException, FileOutputStream, InputStreamReader, OutputStreamWriter, PrintWriter}
import java.lang.management.{ManagementFactory, MemoryType}
import java.security.MessageDigest
import scala.math._

// =====================================================================================
// INFRAESTRUCTURA: UNICO lugar del proyecto Scala donde se usa API de libreria fuera de la
// tabla de clase (java.io para leer/escribir archivos, System.nanoTime y MXBeans para medir,
// MessageDigest para el SHA-256 y metodos de String como split/trim para parsear el CSV).
// La logica del torneo (Matchmaking, EliminacionDirecta, Liga, Estadisticas) no la toca:
// solo recibe y devuelve datos inmutables.
// =====================================================================================
object Infraestructura {

  // ---------------------------------- ENTRADA (CSV) ----------------------------------

  // Lee todas las lineas con su numero. while + var: lectura secuencial de un archivo
  def leerLineas(ruta: String): List[(Int, String)] = {
    val archivo = new File(ruta)
    if (!archivo.isFile) throw new ErrorDatos(TipoError.ARCHIVO_INEXISTENTE, "No existe el archivo: " + ruta)
    val lector = new BufferedReader(new InputStreamReader(new FileInputStream(archivo), "UTF-8"))
    try {
      var acumuladas: List[(Int, String)] = Nil // [TABLA: var] lectura del CSV linea a linea
      var numero = 1
      var linea = lector.readLine()
      while (linea != null) { // [TABLA: while] fin de archivo desconocido de antemano
        acumuladas = (numero, linea) :: acumuladas
        numero = numero + 1
        linea = lector.readLine()
      }
      Listas.invertir(acumuladas)
    } catch {
      case e: FileNotFoundException => throw new ErrorDatos(TipoError.ARCHIVO_INEXISTENTE, "No existe el archivo: " + ruta)
    } finally { // [TABLA: finally] el archivo se cierra siempre
      lector.close()
    }
  }

  def esIgnorable(numero: Int, texto: String): Boolean =
    texto.trim == "" || (numero == 1 && texto.trim.toLowerCase.startsWith("id"))

  // Trocea una linea "id,nombre,elo" (split, trim, matches y toInt son metodos de String)
  def trocearLinea(numero: Int, texto: String): CamposCsv = {
    val campos: Array[String] = texto.split(",", -1) // [TABLA: Array]
    if (campos.length != 3)
      throw new ErrorDatos(TipoError.LINEA_MALFORMADA, "Linea " + numero + ": se esperaban 3 campos y hay " + campos.length)
    val id = campos(0).trim
    val nombre = campos(1).trim
    val elo = campos(2).trim
    if (!id.matches("[0-9]{1,9}") || id.toInt < 1)
      throw new ErrorDatos(TipoError.LINEA_MALFORMADA, "Linea " + numero + ": id invalido '" + id + "'")
    if (nombre == "")
      throw new ErrorDatos(TipoError.LINEA_MALFORMADA, "Linea " + numero + ": nombre vacio")
    val conFormato = elo.matches("-?[0-9]{1,6}(\\.[0-9]{1,2})?")
    CamposCsv(id.toInt, nombre, elo, elo == "", elo.equalsIgnoreCase("null"), conFormato,
      if (conFormato) centesimas(elo) else 0.0)
  }

  // "1534.5" -> 153450.0 ; "-0.5" -> -50.0  (sin pasar por la conversion binaria de decimales)
  def centesimas(t: String): Double = {
    val negativo = t.startsWith("-")
    val partes = (if (negativo) t.substring(1) else t).split("\\.")
    val decimales = if (partes.length > 1) partes(1).toLong * (if (partes(1).length == 1) 10 else 1) else 0L
    val valor = (partes(0).toLong * 100 + decimales).toDouble
    if (negativo) -valor else valor
  }

  // ---------------------------------- MEDICION ----------------------------------

  // Mide cada fase con System.nanoTime y muestrea la memoria al terminarla.
  class Cronometro {
    var tiemposMs: Map[String, Double] = Map[String, Double]() // [TABLA: var] estado de la medicion
    var picoMuestreado: Double = 0.0

    def medir[T](fase: String, bloque: () => T): T = {
      val t0 = System.nanoTime()
      try {
        bloque()
      } finally {
        tiemposMs = tiemposMs + (fase -> (System.nanoTime() - t0) / 1e6)
        val usado = heapUsado()
        if (usado > picoMuestreado) picoMuestreado = usado
      }
    }
  }

  def heapUsado(): Double = {
    val r = Runtime.getRuntime
    (r.totalMemory() - r.freeMemory()).toDouble
  }

  def recolectarBasura(): Unit = System.gc()

  def reiniciarPicos(): Unit = {
    val it = ManagementFactory.getMemoryPoolMXBeans.iterator()
    while (it.hasNext) it.next().resetPeakUsage()
  }

  def picoHeapPools(): Double = {
    val it = ManagementFactory.getMemoryPoolMXBeans.iterator()
    var total = 0.0
    while (it.hasNext) {
      val p = it.next()
      if (p.getType == MemoryType.HEAP) total = total + p.getPeakUsage.getUsed
    }
    total
  }

  // Pico de memoria residente (VmHWM) en Linux; en otros sistemas -1 (lo mide el orquestador)
  def picoProceso(): Double =
    try {
      val lineas = leerLineas("/proc/self/status")
      val hwm = lineas.filter(l => l._2.startsWith("VmHWM:")).map(l => l._2.replaceAll("[^0-9]", "").toDouble * 1024)
      hwm match {
        case x :: _ => x
        case Nil => -1.0
      }
    } catch {
      case e: Exception => -1.0
    }

  // ---------------------------------- SALIDA ----------------------------------

  def entero(d: Double): String = d.toLong.toString

  def textoElo(c: Double): String = {
    val v = c.toLong
    val abs = if (v < 0) -v else v
    (if (v < 0) "-" else "") + (abs / 100) + "." + (if (abs % 100 < 10) "0" else "") + (abs % 100)
  }

  def dosDecimales(d: Double): String = java.lang.String.format(java.util.Locale.ROOT, "%.2f", java.lang.Double.valueOf(d))

  def numero(d: Double): String = java.lang.String.format(java.util.Locale.ROOT, "%.4f", java.lang.Double.valueOf(d))

  def unir(elementos: List[String], separador: String): String = {
    val sb = new java.lang.StringBuilder()
    var primero = true
    for (e <- elementos) { // [TABLA: for] construccion de texto con efectos (StringBuilder)
      if (!primero) sb.append(separador)
      sb.append(e)
      primero = false
    }
    sb.toString
  }

  def texto(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""

  def checksumEliminacion(e: ResultadoEliminacion): Double =
    e.finales.map(j => j.elo * (j.id % 1000 + 1)).fold(0.0)(_ + _)

  def checksumLiga(l: ResultadoLiga): Double =
    Listas.aplanar(l.tablas).map(j => j.elo * (j.id % 1000 + 1) + j.puntos * (j.id % 97 + 1).toDouble).fold(0.0)(_ + _)

  def conteoPorTipo(errores: List[RegistroError], tipo: String): Int =
    errores.filter(e => e.tipo == tipo).map(e => 1).fold(0)(_ + _)

  // Texto canonico identico byte a byte en Java, Scala y C#
  def salidaNormalizada(r: ResultadoCompleto): String = {
    val e = r.eliminacion
    val l = r.liga
    val tablaG1 = l.tablas match {
      case t :: _ => unir(t.map(j => "" + j.id + ":" + j.puntos + ":" + j.victorias + ":" + j.empates + ":" + j.derrotas + ":" + textoElo(j.elo)), ",")
      case Nil => ""
    }
    val lideres = unir(l.tablas.map(t => t match {
      case lider :: _ => "" + lider.id
      case Nil => ""
    }), ",")
    "VALIDOS=" + r.carga.validos + "\n" +
      "ERRORES=" + Listas.contar(r.errores) + "\n" +
      "ERRORES_TIPO=" + unir(TipoError.TODOS.map(t => t + ":" + conteoPorTipo(r.errores, t)), ";") + "\n" +
      "PAREJAS=" + r.matchmaking.parejas + ";BYES=" + r.matchmaking.byes + "\n" +
      "ELIM=RONDAS:" + e.numRondas + ";PARTIDAS:" + e.partidas + ";CAMPEON:" + (if (e.hayCampeon) e.campeon.id else 0) +
      ";ELO:" + textoElo(if (e.hayCampeon) e.campeon.elo else 0.0) + "\n" +
      "LIGA=GRUPOS:" + l.grupos + ";PARTIDAS:" + l.partidas + "\n" +
      "TABLA_G1=" + tablaG1 + "\n" +
      "LIDERES=" + lideres + "\n" +
      "CHECK_ELIM=" + entero(checksumEliminacion(e)) + "\n" +
      "CHECK_LIGA=" + entero(checksumLiga(l)) + "\n" +
      "STATS=" + unir(r.estadisticas.grupos.map(g => g.rango + ":" + g.cantidad + ":" + textoElo(g.promedioElo) + ":" + g.victorias), ";") + "\n" +
      "TOP10=" + unir(r.estadisticas.top.map(j => "" + j.id + ":" + textoElo(j.elo)), ",") + "\n" +
      "MAS_VICTORIAS=" + (if (r.estadisticas.hayMasVictorias) "" + r.estadisticas.masVictorias.id + ":" + r.estadisticas.masVictorias.victorias else "0:0") + "\n"
  }

  def sha256(t: String): String = {
    val bytes = MessageDigest.getInstance("SHA-256").digest(t.getBytes("UTF-8"))
    unir(bytes.toList.map(b => java.lang.String.format("%02x", java.lang.Byte.valueOf(b))), "")
  }

  // ---------------------------------- JSON ----------------------------------

  def jsonJugador(j: Jugador): String =
    "{\"id\": " + j.id + ", \"nombre\": " + texto(j.nombre) + ", \"elo\": " + texto(textoElo(j.elo)) +
      ", \"puntos\": " + j.puntos + ", \"victorias\": " + j.victorias + ", \"empates\": " + j.empates +
      ", \"derrotas\": " + j.derrotas + "}"

  def jsonPartida(p: Partida): String =
    "{\"a\": " + p.jugadorA + ", \"b\": " + p.jugadorB + ", \"resultado\": " + texto(p.resultado) +
      ", \"ganador\": " + p.ganador + ", \"deltaEloA\": " + texto(textoElo(p.deltaEloA)) + "}"

  def jsonLista(elementos: List[String]): String = "[" + unir(elementos, ", ") + "]"

  def jsonResumen(r: Resumen): String =
    "{\"mediana\": " + numero(r.mediana) + ", \"media\": " + numero(r.media) + ", \"desviacion\": " + numero(r.desviacion) +
      ", \"min\": " + numero(r.minimo) + ", \"max\": " + numero(r.maximo) + "}"

  def construirJson(lenguaje: String, entrada: String, modo: String, semilla: Long, warmup: Int, runs: Int,
                    tiempos: List[(String, List[Double])], picoHeap: Double, picoProc: Double,
                    partidasSeg: Double, jugadoresSeg: Double, invariantes: Boolean, r: ResultadoCompleto,
                    salida: String, hash: String): String = {
    val detalle = r.carga.validos <= 128
    val tiemposJson = "{" + unir(tiempos.map(t => texto(t._1) + ": " + jsonLista(t._2.map(numero))), ", ") + "}"
    val resumenJson = "{" + unir(tiempos.map(t => texto(t._1) + ": " + jsonResumen(Medicion.resumir(t._2))), ", ") + "}"
    val tipos = "{" + unir(TipoError.TODOS.map(t => texto(t) + ": " + conteoPorTipo(r.errores, t)), ", ") + "}"
    val detalleErrores = jsonLista(Listas.primeros(r.errores, 100).map(e => "{\"tipo\": " + texto(e.tipo) + ", \"mensaje\": " + texto(e.mensaje) + "}"))
    val llaves = if (detalle) jsonLista(r.eliminacion.rondas.map(ronda => jsonLista(ronda.map(jsonPartida)))) else "[]"
    val participantes = if (detalle) jsonLista(r.carga.jugadores.map(jsonJugador)) else "[]"
    val tablaG1 = r.liga.tablas match {
      case t :: _ => jsonLista(t.map(jsonJugador))
      case Nil => "[]"
    }
    val stats = jsonLista(r.estadisticas.grupos.map(g => "{\"rango\": " + texto(g.rango) + ", \"cantidad\": " + g.cantidad +
      ", \"promedioElo\": " + texto(textoElo(g.promedioElo)) + ", \"victorias\": " + g.victorias + "}"))
    val campeon = if (r.eliminacion.hayCampeon) jsonJugador(r.eliminacion.campeon) else "null"
    val salidaRecortada = if (salida.length > 20000) salida.substring(0, 20000) else salida
    "{\n" +
      "  \"lenguaje\": " + texto(lenguaje) + ",\n" +
      "  \"version\": " + texto("Scala " + scala.util.Properties.versionNumberString + " (Java " + System.getProperty("java.version") + ")") + ",\n" +
      "  \"entrada\": " + texto(entrada) + ",\n" +
      "  \"modo\": " + texto(modo) + ",\n" +
      "  \"semilla\": " + semilla + ",\n" +
      "  \"warmup\": " + warmup + ",\n" +
      "  \"runs\": " + runs + ",\n" +
      "  \"tiemposMs\": " + tiemposJson + ",\n" +
      "  \"resumenMs\": " + resumenJson + ",\n" +
      "  \"memoria\": {\"picoHeapMB\": " + numero(picoHeap / 1048576.0) + ", \"picoProcesoMB\": " + numero(picoProc / 1048576.0) + "},\n" +
      "  \"throughput\": {\"partidasPorSeg\": " + numero(partidasSeg) + ", \"jugadoresPorSeg\": " + numero(jugadoresSeg) + "},\n" +
      "  \"conteos\": {\"jugadoresValidos\": " + r.carga.validos + ", \"partidasEliminacion\": " + r.eliminacion.partidas +
      ", \"partidasLiga\": " + r.liga.partidas + "},\n" +
      "  \"errores\": {\"total\": " + Listas.contar(r.errores) + ", \"porTipo\": " + tipos + ", \"detalle\": " + detalleErrores + "},\n" +
      "  \"resultado\": {\"campeon\": " + campeon + ", \"rondas\": " + r.eliminacion.numRondas + ", \"gruposLiga\": " + r.liga.grupos +
      ", \"tablaG1\": " + tablaG1 + ", \"top10\": " + jsonLista(r.estadisticas.top.map(jsonJugador)) + ", \"estadisticas\": " + stats +
      ", \"participantes\": " + participantes + ", \"llaves\": " + llaves + "},\n" +
      "  \"invariantesOk\": " + (if (invariantes) "true" else "false") + ",\n" +
      "  \"salidaNormalizada\": " + texto(salidaRecortada) + ",\n" +
      "  \"hashSalida\": " + texto(hash) + "\n" +
      "}\n"
  }

  def escribir(ruta: String, contenido: String): Unit = {
    val archivo = new File(ruta)
    val carpeta = archivo.getAbsoluteFile.getParentFile
    if (carpeta != null) carpeta.mkdirs()
    val escritor = new PrintWriter(new OutputStreamWriter(new FileOutputStream(archivo), "UTF-8"))
    try {
      escritor.write(contenido)
    } finally {
      escritor.close()
    }
  }

  // ---------------------------------- CLI ----------------------------------

  // --clave valor --clave valor ...  ->  Map
  def argumentos(args: Array[String]): Map[String, String] = {
    var resultado = Map[String, String]() // [TABLA: var] lectura de la linea de comandos
    for (i <- 0 until args.length) {
      if (args(i).startsWith("--") && i + 1 < args.length) resultado = resultado + (args(i).substring(2) -> args(i + 1))
    }
    resultado
  }

  def aEntero(t: String): Int = t.toInt

  def aLargo(t: String): Long = t.toLong

  def imprimir(t: String): Unit = println(t)
}
