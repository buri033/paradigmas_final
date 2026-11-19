# Guion de sustentación — reparto entre los 4 integrantes

> Duración sugerida: **≈ 24 minutos** (6 por persona) + preguntas. Si les dan menos tiempo, cada uno recorta los
> puntos marcados como *(opcional)*. Regla de oro del profesor: **si no se sabe sustentar el código, la nota es 0**,
> así que cada integrante debe poder abrir sus archivos y explicar cualquier línea.

## Mapa de la exposición

| # | Integrante | Tema | Archivos que debe dominar | Tiempo |
|---|---|---|---|---|
| 1 | Integrante 1 | Problema, diseño común, Elo, LCG, determinismo y hash | `datos/generador.py`, `*/GeneradorLCG.*`, `*/Elo.*`, `*/SalidaNormalizada.*`, `Infraestructura.scala` (parte de salida) | 6 min |
| 2 | Integrante 2 | Java con Streams y C# con LINQ | `java/src/main/java/torneos/**`, `csharp/TorneosCSharp/**` | 6 min |
| 3 | Integrante 3 | Scala solo con la tabla de clase + escáner de prohibidos | `scala/src/main/scala/torneos/**`, `orquestador/calidad.py` (escáner), `resultados/matriz_cobertura_scala.md` | 6 min |
| 4 | Integrante 4 | Metodología experimental, orquestador, ranking, conclusión y **demo** | `orquestador/*.py`, `orquestador/ui/index.html`, `resultados/*` | 6 min |

---

## Integrante 1 — El problema y el diseño común (6 min)

**Qué decir (en este orden):**

1. *Objetivo:* "No es solo programar tres veces: diseñamos un experimento que mide métricas reales y un orquestador que
   decide automáticamente cuál lenguaje es mejor para nuestro problema."
2. *El problema:* sistema de torneos de e-sports con matchmaking por Elo, eliminación directa, liga todos contra todos,
   estadísticas y manejo de errores. Mostrar el CSV (`datos/escenarios/pequeno.csv`): `id,nombre,elo`.
3. *Elo* (escribirlo en el tablero o mostrar la diapositiva): E = 1 / (1 + 10^((Rb − Ra)/400)), R' = R + 32·(S − E).
   Ejemplo: 1600 vs 1400 → E = 0,76; si gana el favorito sube 7,69; entre iguales ±16.
4. *Arquitectura común:* modelo → dominio (funciones puras) → infra (bordes con efectos). Misma CLI y mismo JSON.
5. *El reto de la equivalencia:* cada lenguaje tiene su propio `Random` con secuencias distintas. Solución:
   - **LCG propio** de 64 bits con las mismas constantes en los 3 lenguajes.
   - El aleatorio de cada partida es **función pura de (semilla, ronda, índice)** → no depende del orden de evaluación.
   - **Elo en centésimas enteras**: sumar enteros es exacto en cualquier orden; con `double` los tres darían resultados
     distintos en el último decimal.
6. *Verificación:* cada programa escribe un texto canónico y su **SHA-256**; los 7 escenarios dan el mismo hash en los 3.
7. *Datos:* generador con semilla fija: 64, 1.024, 16.384, 131.072 (+1 M opcional) y un escenario sucio con
   12 líneas malformadas, 10 Elo inválidos, 8 duplicados y una línea en blanco.

**Archivo para mostrar en pantalla:** `java/src/main/java/torneos/dominio/GeneradorLCG.java` (método `aleatorioPartida`).

## Integrante 2 — Java (Streams) y C# (LINQ) (6 min)

1. *Java:* records inmutables (`Jugador`, `Partida`), `sealed interface Emparejamiento` con `Pareja` y `Bye`.
2. *Eliminación directa sin mutación:* `Stream.iterate(inicial, e -> siguienteRonda(e, semilla)).dropWhile(e -> vivos > 1)`.
   Cada ronda es una transformación de la anterior: ordenar → emparejar → resolver → ganadores.
3. *Liga:* `IntStream.range(0, n).boxed().flatMap(...)` genera (0,1),(0,2)…; `Collectors.toMap(id, aporte, Aporte::sumar)`
   construye la tabla; desempate con `Comparator.comparingInt(puntos).reversed().thenComparing(...)`.
4. *Validación:* `Collectors.partitioningBy(esValida)`; errores agrupados con `groupingBy(tipo, counting())`.
5. *Cero bucles `for`/`while` en Java* (el orquestador lo verifica).
6. *C#:* lo mismo con LINQ: `Chunk(2)` para emparejar, `Chunk(64)` para grupos, `SelectMany` para todos contra todos,
   `GroupBy` + `ToDictionary` + `Aggregate` para la tabla, *switch expressions* con patrones `Pareja(var a, var b) =>`,
   records con `with`, `Zip` + `Skip` + `All` para comprobar que la tabla quedó ordenada.
7. *Detalle importante:* `InvariantGlobalization` en el `.csproj` para que Windows en español no escriba "1534,50" con coma.
8. *(opcional)* Pruebas: 23 por lenguaje (JUnit 5 / xUnit).

**Archivos para mostrar:** `EliminacionDirecta.java` y `Liga.cs`.

## Integrante 3 — Scala solo con la tabla de clase (6 min)

1. *La restricción:* solo val/var, def, lambdas, if/match, for/while/do-while, Array/List/Set/Map/Tuple,
   map/filter/reduce/fold/foreach, clases/objetos/traits, excepciones. Nada de `sortBy`, `groupBy`, `take`, `reverse`, `Option`…
2. *Ordenar sin `sortBy`:* merge sort propio (`Listas.scala`): `lista.map(x => List(x))` y luego mezclar por pares
   nivel a nivel → O(n log n). **Por qué no un `fold` lineal:** sería un ordenamiento por inserción O(n²): con 131.072
   jugadores, 8.600 millones de comparaciones.
3. *Sin `reverse`, `take`, `drop`:* `invertir`, `cortar`, `primeros`, `partir` con `match` sobre `::` y `Nil`.
4. *Recursión de cola:* la llamada recursiva es lo último → el compilador la vuelve un ciclo → no hay `StackOverflowError`
   con 131.072 jugadores.
5. *Agrupar sin `groupBy`:* cada jugador → `Map(rango -> Acumulado)` y `fold(Map())(combinar)`.
6. *Sin `Option`:* banderas `hayCampeon` / `hayMasVictorias` y objetos vacíos.
7. *POO de la tabla:* `abstract class Torneo` con `validarParticipantes`, `trait Puntuable` (3/1/0),
   `object Liga extends Torneo("LIGA") with Puntuable`, `override def descripcion`, `class GeneradorLCG extends Aleatorio`.
8. *Efectos aislados:* toda la API de librería está en `Infraestructura.scala` (está declarado en un comentario).
9. *`var`/`while`/`do-while`:* solo en el LCG, la lectura del CSV y el ciclo de mediciones, cada uno comentado.
10. *Scala 2.13 y no 3:* Scala 3 eliminó `do-while`.
11. *Escáner automático:* el orquestador quita comentarios y textos y busca cada término prohibido como token
    → **0 violaciones**; cobertura de la tabla **37/37** (matriz en `resultados/matriz_cobertura_scala.md`).

**Archivos para mostrar:** `Listas.scala` (mezclar/ordenar) y `Estadisticas.scala` (fold + combinar).

## Integrante 4 — Experimento, ranking, conclusión y demo (6 min)

1. *Instrumentos reales:* `System.nanoTime` y `Stopwatch` por fase; memoria interna con MXBeans / `GC.GetTotalMemory`;
   memoria del proceso medida **desde afuera** por el orquestador (la misma regla para los 3).
2. *Rigor:* 3 calentamientos descartados (JIT) + 10 mediciones; mediana, media, desviación, mínimo y máximo; misma máquina,
   mismos datos, misma semilla, misma JVM (`-Xmx3g`, G1) y .NET Release.
3. *Normalización:* min-max por escenario (invertida para tiempo y memoria) y promedio entre escenarios.
4. *Criterios cualitativos con reglas verificables:* calidad (6 reglas), confiabilidad (pruebas, errores, hash),
   uso funcional (ratio + construcciones exigidas) y penalización por prohibidos.
5. *Resultado:* **Java 75,61 · C# 67,65 · Scala 37,86**. Java gana por tiempo y throughput; C# arrasa en memoria;
   Scala paga las listas inmutables.
6. *Sensibilidad:* con pesos ±5 % el ganador no cambia; con normalización proporcional ganaría C# → la conclusión depende
   del criterio y lo decimos con honestidad.
7. *Conclusión:* Java para un servidor de torneos de alto volumen (problema dominado por CPU); C# si hay poca memoria.
8. **Demo en vivo** (ver abajo).

---

## Demo paso a paso (≈ 3 min, la hace el Integrante 4)

1. Abrir una terminal en la carpeta del repo y ejecutar `python orquestador/app.py` → se abre el navegador.
2. **Panel de control:** mostrar las herramientas detectadas (java, mvn, sbt, dotnet) y los escenarios.
3. Para no esperar mucho en vivo: desmarcar `grande` y `muy_grande`, poner **runs = 10, warmup = 3** y pulsar
   **▶ Ejecutar todo**. Mientras corre, mostrar la consola: compila, ejecuta pruebas y mide cada lenguaje.
   *(Si el tiempo es muy corto, saltar este paso: el repo ya trae resultados.)*
4. **Ranking:** podio, ganador automático, tabla cruda → normalizada → ponderada. Mover un peso en el
   *Simulador de pesos* (por ejemplo memoria a 35) y mostrar cómo C# pasa adelante.
5. **Equivalencia:** los 7 escenarios con hash idéntico.
6. **Calidad y Scala:** 0 violaciones, 37/37 de la tabla, construcciones exigidas de Java y C#.
7. **Torneo:** las llaves de los 64 jugadores, el campeón y la tabla de la liga (idénticos en los tres lenguajes:
   cambiar el selector entre Java, Scala y C#).
8. Si piden ver un archivo exportado: `resultados/reporte.md`, `ranking.csv` o `graficos/`.

**Plan B si algo falla en vivo:** el repo ya contiene `resultados/` completos; el panel los muestra sin ejecutar nada.
También se puede correr un solo programa: `java -jar java/target/torneos-java.jar --input datos/escenarios/pequeno.csv --runs 3 --warmup 1 --out x.json`.

---

## Preguntas probables del profesor (y cómo responderlas)

**1. ¿Por qué Elo?** Es el estándar para medir habilidad relativa (ajedrez, e-sports); da una probabilidad de victoria que
usamos para simular partidas realistas y un matchmaking por cercanía de nivel. Es simple, con una fórmula cerrada y
fácil de verificar en pruebas.

**2. ¿Por qué un generador aleatorio propio?** `java.util.Random`, `scala.util.Random` y `System.Random` usan algoritmos
distintos: con la misma semilla dan números distintos y los torneos no serían comparables. El LCG de 64 bits es una
línea de código, idéntica en los tres, y el desbordamiento de `long`/`ulong` hace el módulo 2^64 gratis.

**3. ¿Por qué el aleatorio depende de (semilla, ronda, índice) y no de un generador global?** Para que sea una función
pura: el resultado de una partida no depende del orden en que se evalúe. Con un generador global, cualquier diferencia
de orden entre `Stream`, LINQ y `List.map` cambiaría todo el torneo.

**4. ¿Por qué el Elo en centésimas enteras?** La suma de `double` no es asociativa (`(a+b)+c ≠ a+(b+c)` en el último bit).
Java (`Collectors`), C# (`Sum` vectorizado) y Scala (`fold`) suman en órdenes distintos. Con enteros la suma es exacta y
los hashes coinciden.

**5. ¿Qué garantiza el hash?** Que la salida normalizada (campeón, Elo final, tabla del grupo 1, líderes de todos los grupos,
sumas de verificación de todos los Elo finales, estadísticas, top 10 y conteo de errores) es idéntica byte a byte.

**6. ¿Cómo manejan un número de jugadores que no es potencia de 2?** En cada ronda se empareja 1-2, 3-4…; si queda uno
impar recibe BYE y pasa directo. Siempre hay exactamente *n − 1* partidas (lo comprueban las pruebas y los invariantes).

**7. ¿Por qué grupos de 64 en la liga?** Todos contra todos es O(n²): con 131.072 jugadores serían 8.590 millones de
partidas. Con grupos de 64 son 2.016 por grupo y el total crece lineal (4,1 millones).

**8. ¿Qué es el Elo "por lotes" de la liga?** Cada partida usa el Elo con que el jugador empezó la liga y las variaciones se
suman al final (así lo hace la FIDE por período). Hace que las partidas sean independientes entre sí y se puedan
agregar con una reducción asociativa.

**9. ¿Dónde están los efectos secundarios?** Solo en los bordes: `infra/` (Java), `Infra/` (C#) e `Infraestructura.scala`,
más el `Main`. El orquestador cuenta los efectos fuera de esos archivos: 0 en los tres.

**10. ¿Qué es una función pura en su código? Muestren una.** `Elo.esperado`, `Elo.delta`, `Matchmaking.emparejar`,
`Liga.jugarPartida`, `Listas.mezclar`: mismo resultado para los mismos argumentos y no modifican nada externo.

**11. (Scala) ¿Cómo ordenan sin `sortBy`?** Merge sort propio de abajo hacia arriba en `Listas.scala` con `match` sobre
`::` y `Nil`; O(n log n). Explicar `mezclar` y `fusionarPares`.

**12. (Scala) ¿Por qué no usaron `fold` para ordenar como sugería el enunciado?** Plegar listas unitarias con un merge de
izquierda a derecha es un insertion sort O(n²); con 131.072 jugadores no terminaría en un tiempo
razonable (estimación: ≈ 8.600 millones de comparaciones). Lo explicamos como decisión justificada.

**13. (Scala) ¿No se desborda la pila con tanta recursión?** No, porque son recursiones de cola en `object`: el compilador
las transforma en ciclos. La única recursión no de cola tiene profundidad 64 (todos contra todos de un grupo) o log₂ n (rondas).

**14. (Scala) ¿Por qué usan `var` si es programación funcional?** La tabla lo permite y lo limitamos a tres casos
justificados: el estado interno del LCG (su naturaleza es tener estado), la lectura línea a línea del CSV y el ciclo de
repeticiones de la medición. Ninguno escapa de su función.

**15. ¿Cómo detectan los términos prohibidos?** `calidad.py` elimina comentarios y textos, separa el código en tokens y
busca cada palabra de la lista; además busca llamadas `.metodo` a colecciones fuera de la tabla. Reporta archivo y
línea, y descuenta 10 puntos por violación en calidad y uso funcional.

**16. ¿Por qué la mediana y no el promedio?** Porque el recolector de basura produce pausas aisladas que inflan el promedio;
la mediana es robusta. Igual reportamos media, desviación, mínimo y máximo.

**17. ¿Por qué el calentamiento?** La JVM y .NET compilan con JIT: las primeras ejecuciones son interpretadas o poco
optimizadas. Descartamos 3 para medir el estado estable.

**18. ¿Por qué C# usa tan poca memoria y Java tanta?** La JVM con G1 crece el heap hasta donde le permite `-Xmx3g` antes
de recolectar (prefiere velocidad); .NET recolecta más seguido con un heap más compacto. Es una amenaza a la validez que
documentamos: con un `-Xmx` menor Java usaría menos memoria.

**19. Si con normalización proporcional gana C#, ¿por qué dicen que el mejor es Java?** Porque el enunciado define
min-max y esos pesos; el mecanismo automático aplica esas reglas. Lo honesto es mostrar que la conclusión depende del
criterio: Java es mejor cuando importa la velocidad con volumen; C# cuando importa la memoria.

**20. ¿Por qué Scala quedó último?** Por la restricción de la tabla: listas enlazadas inmutables y `Map` persistentes,
sin `Vector`, arreglos mutables ni colecciones optimizadas. Es el costo medible del estilo funcional puro; aun así es
correcto (mismo hash) y cumple 37/37 construcciones con 0 violaciones.

**21. ¿Cómo garantizan condiciones equivalentes?** Misma máquina, mismos CSV, misma semilla, mismo número de ejecuciones,
ejecución intercalada por escenario, mismos flags de JVM para Java y Scala, .NET en Release y memoria medida desde afuera
con el mismo instrumento.

**22. ¿Qué pasa si un lenguaje da otro hash?** El orquestador toma como referencia el hash de la mayoría, reporta el
escenario y el lenguaje que no coinciden y le descuenta en confiabilidad (30 % del criterio).

**23. ¿Cuál es la complejidad?** Matchmaking O(n log n) por el ordenamiento; eliminación O(n log n) (se reordena cada ronda,
y las rondas se reducen a la mitad); liga O(n·64); estadísticas O(n log n) por el top 10.

---

## Decisiones de diseño (resumen para tener a mano)

| Decisión | Justificación |
|---|---|
| Elo con K = 32 | Estándar; da probabilidad de victoria para simular y emparejar por nivel. |
| LCG propio | Los Random nativos difieren entre lenguajes. |
| Aleatorio = f(semilla, ronda, índice) | Función pura, independiente del orden de evaluación. |
| Elo en centésimas | Sumas exactas e independientes del orden → hashes iguales. |
| Liga por grupos de 64 | Evita O(n²). |
| Elo por lotes en la liga | Partidas independientes; agregación asociativa. |
| Merge sort de abajo hacia arriba en Scala | `fold` lineal sería O(n²). |
| Recursión de cola en Scala | Sin desbordamiento de pila con volúmenes grandes. |
| Scala 2.13 | Scala 3 eliminó do-while (exigido por la tabla). |
| Mediana | Robusta frente a pausas del GC. |
| Min-max por escenario | Lo pide el enunciado; cada escenario pesa igual. |
| Memoria medida desde afuera | El mismo instrumento para JVM y .NET. |
| Interfaz web local | Bonita, sin consola, sin dependencias extra (solo Python estándar). |
