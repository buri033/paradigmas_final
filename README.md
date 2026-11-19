# Comparativo de lenguajes: Java vs Scala vs C#
### Sistema de matchmaking y gestión de torneos de e-sports — Final de Lenguajes y Paradigmas

El **mismo** sistema implementado en tres lenguajes, un proceso experimental que mide métricas reales y un
**orquestador con interfaz gráfica** que compila, ejecuta, mide, verifica la equivalencia (SHA-256), normaliza,
pondera y **clasifica automáticamente** los lenguajes.

| | Java | Scala | C# |
|---|---|---|---|
| Enfoque | Streams, records, lambdas, `Collectors` | **Solo** la tabla de construcciones de clase | LINQ, records, `switch` expressions |
| Build | Maven (`pom.xml`) | sbt (`build.sbt`, Scala 2.13) | `dotnet` (.NET 8) |
| Pruebas | JUnit 5 (23) | MUnit (23) | xUnit (23) |

---

## 1. Requisitos

| Herramienta | Versión | Para qué |
|---|---|---|
| JDK | 17 o superior (probado con 21) | Java y Scala |
| Maven | 3.9+ | compilar y probar Java (si no está, el orquestador usa `javac`) |
| sbt | 1.10+ | compilar y probar Scala (descarga Scala 2.13.14 solo) |
| .NET SDK | 8.0 | C# |
| Python | 3.10+ | generador de datos y orquestador |

```bash
pip install -r orquestador/requirements.txt      # psutil (memoria externa) y matplotlib (gráficos PNG)
```

> **¿Por qué Scala 2.13 y no Scala 3?** Scala 3 eliminó `do-while`, que es una construcción obligatoria de la tabla.

## 2. Forma rápida: la interfaz gráfica

```bash
python orquestador/app.py
```

Se abre el navegador en **http://localhost:8765**. Desde el *Panel de control*:

1. **🗂 Generar datos** (solo la primera vez; los CSV ya vienen en el repo).
2. Elegir escenarios, ejecuciones (10) y calentamiento (3).
3. **▶ Ejecutar todo**: compila, corre pruebas, mide los 3 lenguajes en todos los escenarios y consolida.
4. Revisar las pestañas **Ranking**, **Rendimiento**, **Equivalencia**, **Calidad y Scala**, **Torneo** y **Sensibilidad**.

El repositorio ya trae una ejecución de referencia en `resultados/`, así que el panel muestra datos desde el primer momento.

## 3. Paso a paso por consola (alternativa)

```bash
# 1) Datos (semilla fija => siempre los mismos archivos)
python datos/generador.py                 # 64, 1.024, 16.384, 131.072 + sucio + mínimo
python datos/generador.py --extremo       # opcional: 1.000.000 de jugadores

# 2) Java
cd java && mvn package && mvn test
java -jar target/torneos-java.jar --input ../datos/escenarios/mediano.csv --mode all --runs 10 --warmup 3 --out ../resultados/java.json

# 3) Scala
cd ../scala && sbt assembly && sbt test
java -jar target/scala-2.13/torneos-scala.jar --input ../datos/escenarios/mediano.csv --mode all --runs 10 --warmup 3 --out ../resultados/scala.json

# 4) C#
cd ../csharp && dotnet build TorneosCSharp/TorneosCSharp.csproj -c Release -o bin-release && dotnet test
dotnet bin-release/TorneosCSharp.dll --input ../datos/escenarios/mediano.csv --mode all --runs 10 --warmup 3 --out ../resultados/csharp.json

# 5) Todo junto + ranking automático
cd .. && python orquestador/ejecutar_todo.py --runs 10 --warmup 3
python orquestador/ejecutar_todo.py --solo-consolidar      # recalcula el ranking sin volver a medir
```

Los tres programas tienen **la misma interfaz de línea de comandos**:

| Argumento | Por defecto | Significado |
|---|---|---|
| `--input` | `../datos/escenarios/pequeno.csv` | CSV de jugadores |
| `--mode` | `all` | matchmaking + eliminación + liga + estadísticas |
| `--runs` | `10` | ejecuciones medidas |
| `--warmup` | `3` | ejecuciones de calentamiento descartadas (JIT) |
| `--out` | `resultado_<lenguaje>.json` | JSON de métricas y resultados |
| `--seed` | `20261117` | semilla del LCG |
| `--grupo` | `64` | tamaño de los grupos de la liga |

## 4. Estructura del repositorio

```
proyecto-final/
├── java/          pom.xml · src/main/java/torneos/{modelo,dominio,infra} · src/test (JUnit 5)
├── scala/         build.sbt · src/main/scala/torneos (solo tabla de clase) · src/test (MUnit)
├── csharp/        TorneosCSharp.sln · TorneosCSharp/{Modelo,Dominio,Infra} · TorneosCSharp.Tests (xUnit)
├── datos/         generador.py · escenarios/*.csv · escenarios/errores_esperados.json
├── orquestador/   app.py (interfaz) · ejecutar_todo.py · motor.py · calidad.py · consolidar.py · ui/index.html
├── resultados/    ranking.json/.csv · metricas_por_escenario.csv · reporte.md · graficos/*.png · crudos/*.json
│                  calidad.json · matriz_cobertura_scala.md · mediciones_externas.json · entorno.json · pruebas.json
└── informe/       informe_tecnico.md · Guia_Exposicion_e_Informe.pdf
```

## 5. Contrato común (idéntico en los 3 lenguajes)

**Entrada (CSV):** `id,nombre,elo` — Elo vacío ⇒ 1000.

| Regla | Error registrado |
|---|---|
| Línea 1 que empieza por `id` = encabezado; líneas en blanco se ignoran | — |
| No hay exactamente 3 campos; id no es 1–9 dígitos o es 0; nombre vacío; Elo con formato inválido | `LINEA_MALFORMADA` |
| Elo `null`, 0 o negativo | `ELO_INVALIDO` |
| id ya visto en una línea válida anterior | `ID_DUPLICADO` |
| El archivo no existe | `ARCHIVO_INEXISTENTE` |
| Menos de 2 jugadores para un torneo | `TORNEO_INSUFICIENTE` |

Ningún error detiene el programa: se registra y se continúa.

**Algoritmos:**

- **Elo:** `E = 1 / (1 + 10^((Rb − Ra)/400))`, `R' = R + 32·(S − E)`. El Elo se guarda en **centésimas enteras** y la
  variación se redondea con `floor(x + 0.5)`, así todas las sumas son exactas sin importar el orden.
- **Aleatorio propio (LCG 64 bits):** `s' = s·6364136223846793005 + 1442695040888963407 (mod 2^64)`,
  `u = (s' >>> 11) · 2^-53`. El aleatorio de cada partida es una **función pura** de `(semilla, ronda, índice)`.
- **Matchmaking:** ordenar por Elo desc. (desempate id asc.) y emparejar 1-2, 3-4…; si es impar, el último recibe BYE.
- **Eliminación directa:** cada ronda vuelve a ordenar a los vivos y aplica el matchmaking; `P(A gana) = E`.
  Con *n* jugadores hay siempre *n − 1* partidas.
- **Liga:** grupos de 64 consecutivos en el orden de seeding; todos contra todos; `u < 0.10` ⇒ empate,
  `u < 0.10 + 0.90·E` ⇒ gana A, si no gana B; 3/1/0 puntos; Elo por lotes (estilo FIDE); desempate puntos → Elo → id.
- **Estadísticas:** rangos BRONCE (<1200), PLATA (<1500), ORO (<1800), PLATINO; cantidad, Elo promedio, victorias de
  liga por rango; top 10; jugador con más victorias.

**Salida:** JSON de métricas con el mismo esquema + `salidaNormalizada` (texto canónico) + `hashSalida` (SHA-256).

## 6. Ranking automático

| Criterio | Peso | Métrica | Normalización |
|---|---|---|---|
| Tiempo de ejecución | 25 % | mediana del tiempo total | min-max por escenario, invertida |
| Consumo de memoria | 20 % | pico de memoria del proceso (medido desde afuera) | min-max por escenario, invertida |
| Rendimiento | 20 % | partidas procesadas por segundo | min-max por escenario |
| Calidad de código | 15 % | 6 reglas explícitas (LOC, complejidad, anidamiento, % funcional, duplicación, efectos fuera de bordes) | 0–100 por regla |
| Confiabilidad | 10 % | 40 % pruebas + 30 % errores bien manejados + 30 % hash coincidente | 0–100 |
| Uso funcional | 10 % | 50 % ratio funcional + 50 % construcciones exigidas (Scala: cobertura de la tabla) | 0–100 |

Además: escáner automático de términos **PROHIBIDOS** en Scala (cada violación descuenta 10 puntos en calidad y en
uso funcional) y análisis de sensibilidad de pesos ±5 puntos.

## 7. Notas de la ejecución de referencia incluida

La carpeta `resultados/` se generó en un servidor Linux de 2 núcleos sin acceso a Maven Central ni NuGet; por eso Java
se compiló con `javac`, Scala con `scalac` (2.11.12, el disponible allí) y las 23 pruebas de cada lenguaje se corrieron
con un ejecutor mínimo compatible. **Para la entrega final se recomienda volver a ejecutar `▶ Ejecutar todo` en el
computador del grupo**: el orquestador usará Maven, sbt (Scala 2.13) y `dotnet test`, y regenerará todos los
resultados, tablas y gráficos.
