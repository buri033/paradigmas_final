package torneos

// =====================================================================================
// Utilidades de listas escritas SOLO con la tabla: match recursivo sobre ::, Nil y :::,
// map, filter y fold. La recursion es "de cola" (la llamada recursiva es lo ultimo que se
// hace), asi el compilador la convierte en un ciclo y no se desborda la pila con 1.000.000.
// =====================================================================================
object Listas {

  // Invierte una lista (equivale al metodo prohibido, implementado a mano)
  def invertir[T](lista: List[T]): List[T] = volcar(lista, Nil)

  // Pasa los elementos de "invertida" (al reves) delante de "resto"
  def volcar[T](invertida: List[T], resto: List[T]): List[T] = invertida match {
    case x :: xs => volcar(xs, x :: resto) // [TABLA: List] pattern matching sobre listas
    case Nil => resto
  }

  // Cantidad de elementos: cada elemento vale 1 y se suman con fold
  def contar[T](lista: List[T]): Int = lista.map((x: T) => 1).fold(0)(_ + _) // [TABLA: lambda] [TABLA: fold]

  // Mezcla dos listas ya ordenadas segun "antes" (merge)
  def mezclar[T](a: List[T], b: List[T], antes: (T, T) => Boolean, acumulado: List[T]): List[T] = (a, b) match { // [TABLA: Tuple]
    case (Nil, _) => volcar(acumulado, b)
    case (_, Nil) => volcar(acumulado, a)
    case (x :: xs, y :: ys) =>
      if (antes(y, x)) mezclar(a, ys, antes, y :: acumulado)
      else mezclar(xs, b, antes, x :: acumulado) // [TABLA: else]
  }

  // Merge sort ascendente por niveles: O(n log n).
  // (Combinar las listas unitarias con un fold lineal seria un insertion sort O(n^2): con
  //  131.072 jugadores son ~8.600 millones de comparaciones; por eso se mezclan por pares.)
  def ordenar[T](lista: List[T], antes: (T, T) => Boolean): List[T] =
    fusionarNiveles(lista.map((x: T) => List(x)), antes) // [TABLA: map]

  def fusionarNiveles[T](listas: List[List[T]], antes: (T, T) => Boolean): List[T] = listas match {
    case Nil => Nil
    case unica :: Nil => unica
    case _ => fusionarNiveles(fusionarPares(listas, antes, Nil), antes)
  }

  def fusionarPares[T](listas: List[List[T]], antes: (T, T) => Boolean, acumulado: List[List[T]]): List[List[T]] = listas match {
    case a :: b :: resto => fusionarPares(resto, antes, mezclar(a, b, antes, Nil) :: acumulado)
    case a :: Nil => volcar(acumulado, List(a))
    case Nil => volcar(acumulado, Nil)
  }

  // Primeros k elementos y el resto (reemplaza a los metodos prohibidos de cortar listas)
  def cortar[T](lista: List[T], k: Int, acumulado: List[T]): (List[T], List[T]) = lista match {
    case x :: xs => if (k > 0) cortar(xs, k - 1, x :: acumulado) else (invertir(acumulado), lista)
    case Nil => (invertir(acumulado), lista)
  }

  def primeros[T](lista: List[T], k: Int): List[T] = cortar(lista, k, Nil)._1

  // Parte una lista en bloques de tamano fijo (grupos de la liga)
  def partir[T](lista: List[T], tam: Int, acumulado: List[List[T]]): List[List[T]] = lista match {
    case Nil => invertir(acumulado)
    case _ =>
      val corte = cortar(lista, tam, Nil)
      partir(corte._2, tam, corte._1 :: acumulado)
  }

  // Elemento en la posicion k (0 = primero)
  def enPosicion[T](lista: List[T], k: Int, porDefecto: T): T = lista match {
    case x :: xs => if (k == 0) x else enPosicion(xs, k - 1, porDefecto)
    case Nil => porDefecto
  }

  // Concatena una lista de listas: fold con un operador asociativo (y ::: x copia solo la lista pequena)
  def aplanar[T](listas: List[List[T]]): List[T] = listas.fold(List[T]())((x, y) => y ::: x)
}
