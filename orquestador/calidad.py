"""
Análisis estático del código fuente con reglas EXPLÍCITAS y VERIFICABLES (criterios cualitativos):

Calidad de código (0-100) = promedio de 6 sub-puntajes
  1. Líneas de código (LOC)         : 100 * LOC_mínimo / LOC_propio (menos líneas para lo mismo = más expresivo)
  2. Complejidad ciclomática prom.  : <= 2 -> 100, >= 10 -> 0, lineal en medio
  3. Profundidad máx. de anidamiento: <= 4 -> 100, >= 9 -> 0, lineal en medio (por sangría)
  4. % operaciones funcionales      : valor directo (funcionales / (funcionales + imperativas))
  5. % duplicación (ventanas de 4 líneas repetidas): 0 % -> 100, >= 15 % -> 0
  6. Efectos fuera de los bordes    : 100 - 20 por cada efecto (E/S, reloj, consola) fuera de los archivos borde
  Penalización: -10 por cada violación de la lista PROHIBIDO en Scala.

Uso funcional (0-100) = 50 % ratio funcional + 50 % cumplimiento de las construcciones exigidas
  (Scala: cobertura de la tabla; Java: streams exigidos; C#: LINQ exigido) - 10 por violación en Scala.
"""
import re
from collections import Counter
from pathlib import Path

from config import LENGUAJES, RAIZ

# ------------------------------------------------------------------ utilidades de texto

def quitar_comentarios_y_textos(codigo: str, conservar_textos: bool = False) -> str:
    """Reemplaza comentarios (y literales de texto, salvo conservar_textos) por espacios; conserva los saltos de línea."""
    resultado, i, n = [], 0, len(codigo)
    while i < n:
        c = codigo[i]
        if codigo.startswith("//", i):
            fin = codigo.find("\n", i)
            fin = n if fin == -1 else fin
            resultado.append(" " * (fin - i))
            i = fin
        elif codigo.startswith("/*", i):
            fin = codigo.find("*/", i + 2)
            fin = n if fin == -1 else fin + 2
            resultado.append(re.sub(r"[^\n]", " ", codigo[i:fin]))
            i = fin
        elif c == '"' or (c == "@" and i + 1 < n and codigo[i + 1] == '"') or (c == "$" and i + 1 < n and codigo[i + 1] == '"'):
            inicio = i
            if c in "@$":
                i += 1
            if codigo.startswith('"""', i):
                fin = codigo.find('"""', i + 3)
                fin = n if fin == -1 else fin + 3
            else:
                fin = i + 1
                while fin < n and codigo[fin] != '"' and codigo[fin] != "\n":
                    fin += 2 if codigo[fin] == "\\" else 1
                fin = min(fin + 1, n)
            resultado.append(codigo[inicio:fin] if conservar_textos else '"' + " " * max(0, fin - inicio - 2) + '"')
            i = fin
        elif c == "'" and i + 2 < n and (codigo[i + 2] == "'" or (codigo[i + 1] == "\\" and i + 3 < n and codigo[i + 3] == "'")):
            fin = i + (4 if codigo[i + 1] == "\\" else 3)
            resultado.append("' '" + " " * (fin - i - 3))
            i = fin
        else:
            resultado.append(c)
            i += 1
    return "".join(resultado)


def archivos(lenguaje, clave="fuentes"):
    datos = LENGUAJES[lenguaje]
    lista = []
    for carpeta in datos[clave]:
        lista += sorted(p for p in (RAIZ / carpeta).rglob("*" + datos["extension"])
                        if "bin" not in p.parts and "obj" not in p.parts and "target" not in p.parts)
    return lista


def es_borde(lenguaje, ruta: Path):
    texto = ruta.as_posix()
    return any(b in texto for b in LENGUAJES[lenguaje]["bordes"])


def rel(p: Path):
    return p.relative_to(RAIZ).as_posix()


# ------------------------------------------------------------------ patrones por lenguaje

FUNCIONALES = {
    "Java": r"\.(stream|map|filter|reduce|flatMap|collect|sorted|limit|mapToObj|mapToLong|mapToInt|mapToDouble|"
            r"anyMatch|allMatch|noneMatch|iterate|dropWhile|takeWhile|forEach|boxed|findFirst|average|sum)\(|"
            r"Collectors\.\w+|\w+::\w+",
    "Scala": r"\.(map|filter|reduce|fold|foreach)\(",
    "C#": r"\.(Select|Where|Aggregate|GroupBy|OrderBy|OrderByDescending|ThenBy|ThenByDescending|Take|Skip|Zip|"
          r"SelectMany|Any|All|Sum|Average|Count|LongCount|Min|Max|ToDictionary|Chunk|Concat|Append|Prepend|"
          r"Order|First|FirstOrDefault|ForEach)\(",
}
LAMBDAS = {
    "Java": r"(\b\w+|\([^()]*\))\s*->",
    "Scala": r"(\b\w+|\([^()]*\))\s*=>",
    "C#": r"(\b\w+|\([^()]*\))\s*=>",
}
IMPERATIVOS = {
    "Java": r"\bfor\s*\(|\bwhile\s*\(|\bdo\s*\{|\+\+|--(?!>)|\+=|-=",
    "Scala": r"\bvar\b|\bfor\s*\(|\bwhile\s*\(|\bdo\s*\{|\+=|-=",
    "C#": r"\bfor\s*\(|\bforeach\s*\(|\bwhile\s*\(|\bdo\s*\{|\+\+|--(?!>)|\+=|-=",
}
DECISIONES = r"\bif\b|\bwhile\b|\bfor\b|\bforeach\b|\bcase\b|\bcatch\b|&&|\|\||\s\?\s"
NO_FUNCION = ("if", "for", "while", "switch", "catch", "try", "else", "return", "new", "do", "throw", "using",
              "foreach", "lock", "case", "await", "yield", "var", "base", "this")


def contar_funciones(lenguaje, limpio):
    """Cuenta declaraciones de métodos/funciones (heurística por línea, sin expresiones con retroceso)."""
    if lenguaje == "Scala":
        return len(re.findall(r"\bdef\s+\w+", limpio))
    total = 0
    for linea in limpio.splitlines():
        s = linea.strip()
        if "(" not in s or s.startswith(("@", "[", ".", "+", ":", "?", "&", "|")):
            continue
        cabeza = s[:s.index("(")].split()
        if len(cabeza) < 2 or cabeza[0] in NO_FUNCION or "=" in s[:s.index("(")] or "->" in s[:s.index("(")]:
            continue
        if cabeza[-2] in ("class", "record", "interface", "enum", "struct", "new", "return", "throw"):
            continue
        if not re.fullmatch(r"\w+(<[\w, <>]+>)?", cabeza[-1]):
            continue
        cola = s.rstrip()
        if lenguaje == "Java" and (cola.endswith("{") or "throws" in cola or cola.endswith(")")):
            total += 1
        elif lenguaje == "C#" and (cola.endswith("{") or "=>" in cola or cola.endswith(")")):
            total += 1
    return total
EFECTOS = {
    "Java": r"System\.(out|err|nanoTime|gc)|Files\.|new File\b|Runtime\.getRuntime|ManagementFactory",
    "Scala": r"\bprintln\b|System\.|new File\b|java\.io|ManagementFactory|Runtime\.getRuntime",
    "C#": r"Console\.|File\.|Directory\.|Stopwatch|GC\.|Process\.",
}

# Construcciones exigidas por el enunciado
EXIGIDAS = {
    "Java": {
        "stream()": r"\.stream\(\)", "map": r"\.map\(", "filter": r"\.filter\(", "flatMap": r"\.flatMap\(",
        "reduce": r"\.reduce\(", "collect": r"\.collect\(", "Collectors.groupingBy": r"Collectors\.groupingBy",
        "Collectors.partitioningBy": r"Collectors\.partitioningBy", "Collectors.averaging*": r"Collectors\.averaging",
        "Collectors.counting": r"Collectors\.counting", "sorted(Comparator)": r"\.sorted\(", "limit": r"\.limit\(",
        "mapToInt/IntStream.range": r"mapToInt|IntStream\.range", "anyMatch/allMatch": r"anyMatch|allMatch",
        "Optional": r"\bOptional\b", "lambda": r"->", "referencia a método (::)": r"\w::\w",
        "Function/Predicate/BiFunction": r"\b(Function|Predicate|BiFunction|BinaryOperator)<",
        "Comparator": r"\bComparator\b", "record": r"\brecord\s+\w+",
    },
    "C#": {
        "Select": r"\.Select\(", "Where": r"\.Where\(", "Aggregate": r"\.Aggregate\(", "GroupBy": r"\.GroupBy\(",
        "OrderBy/ThenBy": r"\.(OrderBy|OrderByDescending)\(.*\.ThenBy", "Take": r"\.Take\(", "Skip": r"\.Skip\(",
        "Zip": r"\.Zip\(", "SelectMany": r"\.SelectMany\(", "Any/All": r"\.(Any|All)\(", "Sum/Average/Count":
        r"\.(Sum|Average|Count)\(", "ToDictionary": r"\.ToDictionary\(", "lambda": r"=>", "Func<>/Action<>/Predicate<>":
        r"\b(Func|Action|Predicate)<", "record": r"\brecord\s+\w+", "switch expression": r"\bswitch\s*$|\bswitch\s*\{",
        "tuplas": r"\(\w+:\s|\(var\s*\(|\bvar\s*\(", "pattern matching": r"\bis\s+(not\s+)?\w|\w+\(var\s+\w+",
    },
}

# Tabla de clase para Scala (construcción -> expresión regular sobre código sin comentarios)
TABLA_SCALA = {
    "val": r"\bval\b", "var": r"\bvar\b", "def": r"\bdef\b", "return": r"\breturn\b",
    "lambda (x: T) => ...": r"\(\w+:\s*[\w\[\]]+\)\s*=>", "if": r"\bif\b", "else if": r"\belse\s+if\b",
    "else": r"\belse\b(?!\s+if)", "match": r"\bmatch\b", "for": r"\bfor\s*\(", "while": r"^\s*while\s*\(",
    "do-while": r"\bdo\s*\{", "Array": r"\bArray\b", "List": r"\bList\b", "Set": r"\bSet\b", "Map": r"\bMap\b",
    "Tuple": r"\._[12]\b", "map": r"\.map\(", "filter": r"\.filter\(", "reduce": r"\.reduce\(", "fold": r"\.fold\(",
    "foreach": r"\.foreach\(", "class": r"^(?!.*\b(case|abstract)\s+class).*\bclass\b", "object": r"\bobject\b",
    "case class": r"\bcase\s+class\b", "trait": r"\btrait\b", "abstract class": r"\babstract\s+class\b",
    "try": r"\btry\b", "catch": r"\bcatch\b", "finally": r"\bfinally\b", "throw": r"\bthrow\b",
    "import": r"^\s*import\b", "package": r"^\s*package\b", "extends": r"\bextends\b", "with": r"\bwith\b",
    "override": r"\boverride\b", "new": r"\bnew\b",
}

PROHIBIDOS_SCALA = [
    "groupBy", "sortBy", "sortWith", "sorted", "flatMap", "flatten", "foldLeft", "foldRight", "zip", "zipWithIndex",
    "take", "drop", "takeWhile", "count", "sum", "max", "min", "maxBy", "minBy", "distinct", "partition", "collect",
    "exists", "forall", "find", "toMap", "mkString", "reverse", "scanLeft", "view", "Stream", "LazyList", "Option",
    "Some", "None", "yield",
]
# Otros métodos de colecciones fuera de la tabla (se buscan como llamada .metodo fuera de Infraestructura)
OTROS_METODOS_SCALA = [
    "head", "tail", "last", "init", "isEmpty", "nonEmpty", "toList", "toSet", "toArray", "toSeq", "toVector", "keys",
    "values", "keySet", "updated", "grouped", "sliding", "indexOf", "headOption", "lastOption", "filterNot", "span",
    "splitAt", "appended", "prepended", "diff", "intersect", "union", "withFilter", "tails", "unzip", "aggregate",
    "copy", "getOrElseUpdate", "sortInPlace",
]


# ------------------------------------------------------------------ escáner de Scala

def escanear_scala():
    """Busca términos PROHIBIDOS (token exacto en código, sin comentarios ni textos) y construye la matriz de cobertura."""
    violaciones, cobertura = [], {c: [] for c in TABLA_SCALA}
    patron_prohibido = re.compile(r"\b(" + "|".join(PROHIBIDOS_SCALA) + r")\b")
    patron_otros = re.compile(r"\.(" + "|".join(OTROS_METODOS_SCALA) + r")\b")
    for ruta in archivos("Scala") + archivos("Scala", "pruebas"):
        original = ruta.read_text(encoding="utf-8").splitlines()
        limpio = quitar_comentarios_y_textos(ruta.read_text(encoding="utf-8")).splitlines()
        infra = ruta.name == "Infraestructura.scala"
        for num, linea in enumerate(limpio, start=1):
            for m in patron_prohibido.finditer(linea):
                violaciones.append({"archivo": rel(ruta), "linea": num, "termino": m.group(1),
                                    "codigo": original[num - 1].strip()[:140]})
            if not infra:
                for m in patron_otros.finditer(linea):
                    violaciones.append({"archivo": rel(ruta), "linea": num, "termino": m.group(1) + " (fuera de la tabla)",
                                        "codigo": original[num - 1].strip()[:140]})
            if "/test/" not in ruta.as_posix():
                for construccion, patron in TABLA_SCALA.items():
                    if re.search(patron, linea):
                        cobertura[construccion].append(f"{rel(ruta)}:{num}")
    usadas = [c for c, usos in cobertura.items() if usos]
    marcadores = Counter()
    for ruta in archivos("Scala"):
        for m in re.finditer(r"\[TABLA:\s*([^\]]+)\]", ruta.read_text(encoding="utf-8")):
            marcadores[m.group(1).strip()] += 1
    return {
        "violaciones": violaciones,
        "totalViolaciones": len(violaciones),
        "cobertura": {c: {"usos": len(u), "ubicaciones": u[:4]} for c, u in cobertura.items()},
        "porcentajeCobertura": round(100 * len(usadas) / len(TABLA_SCALA), 2),
        "construccionesUsadas": len(usadas),
        "construccionesTabla": len(TABLA_SCALA),
        "faltantes": [c for c in TABLA_SCALA if c not in usadas],
        "marcadoresTabla": dict(marcadores),
    }


def matriz_cobertura_md(escaneo):
    filas = ["| # | Construcción de la tabla | Usos | Archivo:línea (ejemplos) |", "|---|---|---|---|"]
    for i, (c, datos) in enumerate(escaneo["cobertura"].items(), start=1):
        filas.append(f"| {i} | `{c}` | {datos['usos']} | {', '.join(datos['ubicaciones']) or '**NO USADA**'} |")
    viol = escaneo["violaciones"]
    texto = [
        "# Matriz de cobertura de la tabla de clase (Scala)",
        "",
        f"Generada automáticamente por `orquestador/calidad.py`. Cobertura: **{escaneo['construccionesUsadas']}/"
        f"{escaneo['construccionesTabla']} ({escaneo['porcentajeCobertura']} %)**. "
        f"Violaciones de la lista PROHIBIDO: **{escaneo['totalViolaciones']}**.",
        "",
        *filas,
        "",
        "## Violaciones detectadas",
        "",
    ]
    if viol:
        texto += ["| Archivo | Línea | Término | Código |", "|---|---|---|---|"]
        texto += [f"| {v['archivo']} | {v['linea']} | `{v['termino']}` | `{v['codigo']}` |" for v in viol]
    else:
        texto.append("Ninguna: el código Scala (fuentes y pruebas) no usa ningún término prohibido.")
    return "\n".join(texto) + "\n"


# ------------------------------------------------------------------ métricas por lenguaje

def _lineas_codigo(limpio):
    return [l for l in limpio.splitlines() if l.strip() and l.strip() not in ("{", "}", "};", "})", ")")]


def _duplicacion(lineas_por_archivo, ventana=4):
    ventanas = Counter()
    normalizadas = []
    for lineas in lineas_por_archivo:
        norm = [re.sub(r"\s+", " ", l.strip()) for l in lineas
                if len(l.strip()) > 3 and not re.match(r"^\s*(import|using|package|namespace)\b", l)]
        normalizadas.append(norm)
        for i in range(len(norm) - ventana + 1):
            ventanas[tuple(norm[i:i + ventana])] += 1
    duplicadas, total = set(), 0
    for k, norm in enumerate(normalizadas):
        total += len(norm)
        for i in range(len(norm) - ventana + 1):
            if ventanas[tuple(norm[i:i + ventana])] > 1:
                duplicadas.update((k, j) for j in range(i, i + ventana))
    return 100 * len(duplicadas) / total if total else 0.0


def _decisiones_cs_switch(limpio):
    """Brazos de las switch expressions de C# (cada brazo es una decisión)."""
    total, dentro, profundidad = 0, False, 0
    for linea in limpio.splitlines():
        if re.search(r"\bswitch\b", linea) and not re.search(r"\bswitch\s*\(", linea):
            dentro, profundidad = True, 0
        if dentro:
            profundidad += linea.count("{") - linea.count("}")
            total += len(re.findall(r"=>", linea)) if "switch" not in linea else 0
            if profundidad <= 0 and "}" in linea:
                dentro = False
    return total


def _profundidad(lenguaje, limpio):
    """Profundidad máxima de bloques { } (misma regla para los 3 lenguajes, sin comentarios ni textos)."""
    nivel = maxima = 0
    for c in limpio:
        if c == "{":
            nivel += 1
            maxima = max(maxima, nivel)
        elif c == "}":
            nivel = max(0, nivel - 1)
    return maxima


def _lineal(valor, bueno, malo):
    if valor <= bueno:
        return 100.0
    if valor >= malo:
        return 0.0
    return 100.0 * (malo - valor) / (malo - bueno)


def metricas_lenguaje(lenguaje):
    fuentes = archivos(lenguaje)
    loc = funciones = decisiones = func_ops = lambdas = imperativos = efectos = 0
    profundidad = 0
    efectos_detalle, todas = [], []
    for ruta in fuentes:
        limpio = quitar_comentarios_y_textos(ruta.read_text(encoding="utf-8"))
        lineas = _lineas_codigo(limpio)
        todas.append(_lineas_codigo(quitar_comentarios_y_textos(ruta.read_text(encoding="utf-8"), conservar_textos=True)))
        loc += len(lineas)
        funciones += contar_funciones(lenguaje, limpio)
        decisiones += len(re.findall(DECISIONES, limpio))
        if lenguaje == "C#":
            decisiones += _decisiones_cs_switch(limpio)
        func_ops += len(re.findall(FUNCIONALES[lenguaje], limpio))
        lineas_lambda = [l for l in limpio.splitlines() if not re.match(r"^\s*case\b", l)]
        if lenguaje == "Scala":  # excluye tipos función  (T, T) => Boolean
            lineas_lambda = [re.sub(r":\s*\([^()]*\)\s*=>\s*\w+", "", l) for l in lineas_lambda]
        if lenguaje == "C#":  # excluye miembros con cuerpo de expresión y brazos de switch
            lineas_lambda = [l for l in lineas_lambda if not re.search(r"\b(public|private|internal|protected)\b.*\)\s*=>", l)
                             and not re.match(r"^\s*([<>\w.]+|_|\w+\s+or\s+[\w.]+|<\s*[\d_]+)\s*=>", l)]
        lambdas += sum(len(re.findall(LAMBDAS[lenguaje], l)) for l in lineas_lambda)
        imperativos += len(re.findall(IMPERATIVOS[lenguaje], limpio))
        profundidad = max(profundidad, _profundidad(lenguaje, limpio))
        if not es_borde(lenguaje, ruta):
            for num, linea in enumerate(limpio.splitlines(), start=1):
                if re.search(EFECTOS[lenguaje], linea):
                    efectos += 1
                    efectos_detalle.append(f"{rel(ruta)}:{num}")
    funcionales = func_ops + lambdas
    ratio = 100 * funcionales / (funcionales + imperativos) if funcionales + imperativos else 0
    resultado = {
        "archivos": len(fuentes),
        "loc": loc,
        "funciones": funciones,
        "complejidadPromedio": round(1 + decisiones / max(funciones, 1), 2),
        "profundidadMaxima": profundidad,
        "operacionesFuncionales": func_ops,
        "lambdas": lambdas,
        "construccionesImperativas": imperativos,
        "ratioFuncional": round(ratio, 2),
        "duplicacionPct": round(_duplicacion(todas), 2),
        "efectosFueraDeBordes": efectos,
        "efectosDetalle": efectos_detalle[:20],
    }
    if lenguaje in EXIGIDAS:
        codigo = "\n".join(quitar_comentarios_y_textos(r.read_text(encoding="utf-8")) for r in fuentes)
        presentes = {c: bool(re.search(p, codigo, flags=re.MULTILINE)) for c, p in EXIGIDAS[lenguaje].items()}
        resultado["exigidas"] = presentes
        resultado["cumplimiento"] = round(100 * sum(presentes.values()) / len(presentes), 2)
    return resultado


def analizar():
    """Devuelve métricas, sub-puntajes y puntajes de calidad / uso funcional para los 3 lenguajes."""
    escaneo = escanear_scala()
    metricas = {l: metricas_lenguaje(l) for l in LENGUAJES}
    metricas["Scala"]["cumplimiento"] = escaneo["porcentajeCobertura"]
    metricas["Scala"]["violacionesProhibido"] = escaneo["totalViolaciones"]
    loc_min = min(m["loc"] for m in metricas.values())
    for l, m in metricas.items():
        penal = 10 * m.get("violacionesProhibido", 0)
        sub = {
            "loc": round(100 * loc_min / m["loc"], 2),
            "complejidad": round(_lineal(m["complejidadPromedio"], 2, 10), 2),
            "anidamiento": round(_lineal(m["profundidadMaxima"], 4, 9), 2),
            "ratioFuncional": m["ratioFuncional"],
            "duplicacion": round(_lineal(m["duplicacionPct"], 0, 15), 2),
            "efectos": max(0.0, 100.0 - 20 * m["efectosFueraDeBordes"]),
        }
        m["subpuntajesCalidad"] = sub
        m["puntajeCalidad"] = round(max(0.0, sum(sub.values()) / len(sub) - penal), 2)
        m["puntajeFuncional"] = round(max(0.0, 0.5 * m["ratioFuncional"] + 0.5 * m["cumplimiento"] - penal), 2)
    return {"metricas": metricas, "escaneoScala": escaneo}


if __name__ == "__main__":
    import json
    r = analizar()
    print(json.dumps({l: {k: v for k, v in m.items() if k not in ("efectosDetalle",)} for l, m in r["metricas"].items()},
                     indent=1, ensure_ascii=False))
    e = r["escaneoScala"]
    print("Scala: cobertura", e["porcentajeCobertura"], "% | violaciones", e["totalViolaciones"], "| faltantes", e["faltantes"])
    for v in e["violaciones"][:30]:
        print("  ", v)
