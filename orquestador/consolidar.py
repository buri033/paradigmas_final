"""
Consolidación, comparación y CLASIFICACIÓN AUTOMÁTICA.

1. Lee los JSON de cada programa (resultados/crudos), la medición externa, las pruebas y el análisis estático.
2. Verifica la equivalencia funcional comparando el SHA-256 de la salida normalizada (referencia = mayoría).
3. Normaliza cada métrica a 0-100 (min-max por escenario, invertido cuando "menos es mejor").
4. Calcula el puntaje ponderado con los pesos del enunciado y determina el ganador.
5. Análisis de sensibilidad (pesos ±5 puntos) y exportación a JSON, CSV, Markdown y gráficos PNG.
"""
import csv
import math
import statistics
import time
from collections import Counter

from calidad import analizar, matriz_cobertura_md
from config import (DATOS, ESCENARIOS_CONFIABILIDAD, ESCENARIOS_RENDIMIENTO, GRAFICOS, LENGUAJES, NOMBRES_CRITERIOS,
                    PESOS, RESULTADOS, ruta_crudo)
from motor import guardar_json, leer_json

TIPOS_ERROR = ["LINEA_MALFORMADA", "ELO_INVALIDO", "ID_DUPLICADO", "ARCHIVO_INEXISTENTE", "TORNEO_INSUFICIENTE"]
UNIDADES = {"tiempo": "ms (media geométrica de medianas)", "memoria": "MB pico RSS (media geométrica)",
            "rendimiento": "partidas/s (media geométrica)", "calidad": "puntaje reglas 0-100",
            "confiabilidad": "puntaje reglas 0-100", "funcional": "puntaje reglas 0-100"}


def media_geometrica(valores):
    valores = [v for v in valores if v and v > 0]
    return math.exp(sum(math.log(v) for v in valores) / len(valores)) if valores else 0.0


def normalizar(valores: dict, menos_es_mejor: bool, metodo: str):
    """min-max: el mejor = 100, el peor = 0.  proporcional: 100 * mejor / valor (o valor / mejor)."""
    v = {k: x for k, x in valores.items() if x is not None}
    if not v:
        return {k: 0.0 for k in valores}
    mn, mx = min(v.values()), max(v.values())
    res = {}
    for k, x in valores.items():
        if x is None:
            res[k] = 0.0
        elif metodo == "proporcional":
            res[k] = 100 * (mn / x if menos_es_mejor else x / mx) if x and mx else 0.0
        elif mx == mn:
            res[k] = 100.0
        else:
            res[k] = 100 * ((mx - x) if menos_es_mejor else (x - mn)) / (mx - mn)
    return res


def cargar_crudos():
    escenarios = list(ESCENARIOS_RENDIMIENTO) + ESCENARIOS_CONFIABILIDAD
    return {e: {l: leer_json(ruta_crudo(l, e)) for l in LENGUAJES} for e in escenarios}


def resumen_escenario(crudo, externo):
    if not crudo:
        return None
    r = crudo["resumenMs"]
    memoria = (externo or {}).get("rssPicoMB") or crudo["memoria"].get("picoProcesoMB") or crudo["memoria"]["picoHeapMB"]
    if memoria is not None and memoria <= 0:
        memoria = crudo["memoria"]["picoHeapMB"]
    return {
        "tiempoMs": r["total"]["mediana"], "mediaMs": r["total"]["media"], "desviacionMs": r["total"]["desviacion"],
        "minMs": r["total"]["min"], "maxMs": r["total"]["max"],
        "fasesMs": {f: r[f]["mediana"] for f in ("carga", "matchmaking", "eliminacion", "liga", "estadisticas")},
        "memoriaMB": round(memoria, 2) if memoria else None,
        "heapMB": crudo["memoria"]["picoHeapMB"],
        "memoriaFuente": "externa (RSS)" if (externo or {}).get("rssPicoMB") else "interna",
        "partidasPorSeg": crudo["throughput"]["partidasPorSeg"],
        "jugadoresPorSeg": crudo["throughput"]["jugadoresPorSeg"],
        "partidas": crudo["conteos"]["partidasEliminacion"] + crudo["conteos"]["partidasLiga"],
        "validos": crudo["conteos"]["jugadoresValidos"],
        "runs": crudo["runs"], "warmup": crudo["warmup"],
        "hash": crudo["hashSalida"], "invariantesOk": crudo.get("invariantesOk"),
        "version": crudo.get("version"), "paredSeg": (externo or {}).get("paredSeg"),
    }


def consolidar(metodo="minmax", pesos=None, log=print):
    pesos = pesos or PESOS
    crudos = cargar_crudos()
    externos = leer_json(RESULTADOS / "mediciones_externas.json", {})
    pruebas = leer_json(RESULTADOS / "pruebas.json", {})
    esperados = leer_json(DATOS / "errores_esperados.json", {})
    entorno = leer_json(RESULTADOS / "entorno.json", {})
    langs = list(LENGUAJES)

    # ---------------- equivalencia funcional (hash) en TODOS los escenarios con datos
    equivalencia = {}
    for esc, porl in crudos.items():
        hashes = {l: (c or {}).get("hashSalida") for l, c in porl.items()}
        if not any(hashes.values()):
            continue
        conteo = Counter(h for h in hashes.values() if h)
        referencia, votos = conteo.most_common(1)[0]
        referencia = referencia if votos >= 2 or len(conteo) == 1 else None
        equivalencia[esc] = {"hashes": hashes, "referencia": referencia,
                             "coinciden": {l: bool(referencia) and hashes[l] == referencia for l in langs}}
    for esc, eq in equivalencia.items():
        for l, ok in eq["coinciden"].items():
            if not ok:
                log(f"  ⚠ {l} NO coincide con el hash de referencia en '{esc}' -> se penaliza su confiabilidad")

    # ---------------- rendimiento por escenario
    perf = [e for e in ESCENARIOS_RENDIMIENTO if all(crudos[e][l] for l in langs)]
    por_escenario = {e: {l: resumen_escenario(crudos[e][l], externos.get(l, {}).get(e)) for l in langs} for e in perf}

    def criterio_cuantitativo(clave, menos_es_mejor):
        normal = {l: [] for l in langs}
        for e in perf:
            n = normalizar({l: por_escenario[e][l][clave] for l in langs}, menos_es_mejor, metodo)
            for l in langs:
                normal[l].append(n[l])
        crudo = {l: media_geometrica([por_escenario[e][l][clave] for e in perf]) for l in langs}
        return {l: {"crudo": round(crudo[l], 3), "normalizado": round(statistics.mean(normal[l]), 2) if perf else 0.0}
                for l in langs}

    criterios = {
        "tiempo": criterio_cuantitativo("tiempoMs", True),
        "memoria": criterio_cuantitativo("memoriaMB", True),
        "rendimiento": criterio_cuantitativo("partidasPorSeg", False),
    }

    # ---------------- calidad y uso funcional (análisis estático con reglas explícitas)
    analisis = analizar()
    guardar_json(RESULTADOS / "calidad.json", analisis)
    (RESULTADOS / "matriz_cobertura_scala.md").write_text(matriz_cobertura_md(analisis["escaneoScala"]), encoding="utf-8")
    met = analisis["metricas"]
    criterios["calidad"] = {l: {"crudo": met[l]["puntajeCalidad"], "normalizado": met[l]["puntajeCalidad"]} for l in langs}
    criterios["funcional"] = {l: {"crudo": met[l]["puntajeFuncional"], "normalizado": met[l]["puntajeFuncional"]} for l in langs}

    # ---------------- confiabilidad = 40 % pruebas + 30 % errores bien manejados + 30 % coincidencia de hash
    detalle_conf = {}
    for l in langs:
        p = pruebas.get(l)
        pct_pruebas = 100 * p["aprobadas"] / p["total"] if p and p.get("total") else 0.0
        aciertos = total = 0
        fallos = []
        for esc in ESCENARIOS_CONFIABILIDAD:
            esperado, crudo = esperados.get(esc), crudos[esc][l]
            if not esperado:
                continue
            chequeos = [("validos", esperado["validos"], (crudo or {}).get("conteos", {}).get("jugadoresValidos"))]
            chequeos += [(t, esperado["porTipo"][t], (crudo or {}).get("errores", {}).get("porTipo", {}).get(t)) for t in TIPOS_ERROR]
            for nombre, esp, obt in chequeos:
                total += 1
                if crudo and esp == obt:
                    aciertos += 1
                else:
                    fallos.append(f"{esc}.{nombre}: esperado {esp}, obtenido {obt}")
        pct_errores = 100 * aciertos / total if total else 0.0
        coinc = [eq["coinciden"][l] for eq in equivalencia.values()]
        pct_hash = 100 * sum(coinc) / len(coinc) if coinc else 0.0
        puntaje = 0.4 * pct_pruebas + 0.3 * pct_errores + 0.3 * pct_hash
        detalle_conf[l] = {"pruebas": p, "pctPruebas": round(pct_pruebas, 2), "pctErrores": round(pct_errores, 2),
                           "chequeosErrores": f"{aciertos}/{total}", "fallosErrores": fallos,
                           "pctHash": round(pct_hash, 2), "puntaje": round(puntaje, 2)}
    criterios["confiabilidad"] = {l: {"crudo": detalle_conf[l]["puntaje"], "normalizado": detalle_conf[l]["puntaje"]}
                                  for l in langs}

    # ---------------- puntaje ponderado y ranking
    def totales(pesos_usados, crit):
        suma = sum(pesos_usados.values())
        return {l: sum(crit[c][l]["normalizado"] * pesos_usados[c] / suma for c in pesos_usados) for l in langs}

    for c, w in pesos.items():
        for l in langs:
            criterios[c][l]["ponderado"] = round(criterios[c][l]["normalizado"] * w / sum(pesos.values()), 2)
    total = totales(pesos, criterios)
    orden = sorted(langs, key=lambda l: -total[l])
    ranking = [{"posicion": i + 1, "lenguaje": l, "total": round(total[l], 2),
                "ponderados": {c: criterios[c][l]["ponderado"] for c in pesos}} for i, l in enumerate(orden)]
    ganador = orden[0]

    # ---------------- análisis de sensibilidad: cada peso ±5 puntos (el resto se reparte proporcionalmente)
    variaciones = []
    for c in pesos:
        for delta in (+5, -5):
            nuevo = dict(pesos)
            nuevo[c] = max(0, pesos[c] + delta)
            resto = sum(w for k, w in pesos.items() if k != c)
            for k in pesos:
                if k != c:
                    nuevo[k] = pesos[k] - (nuevo[c] - pesos[c]) * pesos[k] / resto
            t = totales(nuevo, criterios)
            g = max(t, key=t.get)
            variaciones.append({"criterio": c, "delta": delta, "pesos": {k: round(v, 2) for k, v in nuevo.items()},
                                "totales": {l: round(v, 2) for l, v in t.items()}, "ganador": g, "cambia": g != ganador})
    otro_metodo = "proporcional" if metodo == "minmax" else "minmax"
    alterno = consolidar_rapido(por_escenario, perf, criterios, pesos, otro_metodo)
    sensibilidad = {"variaciones": variaciones, "ganadorCambia": any(v["cambia"] for v in variaciones),
                    "margen": round(total[orden[0]] - total[orden[1]], 2),
                    "metodoAlterno": otro_metodo, "ganadorMetodoAlterno": max(alterno, key=alterno.get),
                    "totalesMetodoAlterno": {l: round(v, 2) for l, v in alterno.items()}}

    resultado = {
        "generado": time.strftime("%Y-%m-%d %H:%M"), "entorno": entorno, "metodo": metodo, "pesos": pesos,
        "nombresCriterios": NOMBRES_CRITERIOS, "unidades": UNIDADES,
        "lenguajes": {l: {"color": d["color"], "colorOscuro": d["colorOscuro"]} for l, d in LENGUAJES.items()},
        "escenarios": [{"nombre": e, "jugadores": ESCENARIOS_RENDIMIENTO[e]} for e in perf],
        "porEscenario": por_escenario, "equivalencia": equivalencia, "criterios": criterios,
        "ranking": ranking, "ganador": ganador, "sensibilidad": sensibilidad, "confiabilidad": detalle_conf,
        "calidad": {l: {k: v for k, v in m.items()} for l, m in met.items()},
        "escaneoScala": analisis["escaneoScala"],
        "pruebas": pruebas,
    }
    guardar_json(RESULTADOS / "ranking.json", resultado)
    exportar_csv(resultado)
    (RESULTADOS / "reporte.md").write_text(reporte_md(resultado), encoding="utf-8")
    try:
        graficos(resultado)
    except ImportError:
        log("  (matplotlib no está instalado: se omiten los PNG; la interfaz web dibuja sus propios gráficos)")
    log(f"  🏆 Ganador automático: {ganador} ({round(total[ganador], 2)} pts) | método {metodo}")
    return resultado


def consolidar_rapido(por_escenario, perf, criterios, pesos, metodo):
    """Recalcula los totales con otro método de normalización (para la sensibilidad)."""
    langs = list(LENGUAJES)
    crit = {c: {l: dict(v) for l, v in criterios[c].items()} for c in criterios}
    for clave, c, menos in (("tiempoMs", "tiempo", True), ("memoriaMB", "memoria", True), ("partidasPorSeg", "rendimiento", False)):
        acum = {l: [] for l in langs}
        for e in perf:
            n = normalizar({l: por_escenario[e][l][clave] for l in langs}, menos, metodo)
            for l in langs:
                acum[l].append(n[l])
        for l in langs:
            crit[c][l]["normalizado"] = statistics.mean(acum[l]) if perf else 0
    suma = sum(pesos.values())
    return {l: sum(crit[c][l]["normalizado"] * pesos[c] / suma for c in pesos) for l in langs}


# ------------------------------------------------------------------ exportación

def exportar_csv(r):
    with open(RESULTADOS / "ranking.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["posicion", "lenguaje", "criterio", "peso", "metrica_cruda", "unidad", "normalizado_0_100", "ponderado", "total"])
        for fila in r["ranking"]:
            l = fila["lenguaje"]
            for c, peso in r["pesos"].items():
                d = r["criterios"][c][l]
                w.writerow([fila["posicion"], l, c, peso, d["crudo"], UNIDADES[c], d["normalizado"], d["ponderado"], fila["total"]])
    with open(RESULTADOS / "metricas_por_escenario.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["escenario", "jugadores", "lenguaje", "mediana_ms", "media_ms", "desviacion_ms", "min_ms", "max_ms",
                    "memoria_pico_MB", "fuente_memoria", "heap_MB", "partidas_por_seg", "jugadores_por_seg", "partidas",
                    "carga_ms", "matchmaking_ms", "eliminacion_ms", "liga_ms", "estadisticas_ms", "hash"])
        for e in r["escenarios"]:
            for l, d in r["porEscenario"][e["nombre"]].items():
                w.writerow([e["nombre"], e["jugadores"], l, d["tiempoMs"], d["mediaMs"], d["desviacionMs"], d["minMs"], d["maxMs"],
                            d["memoriaMB"], d["memoriaFuente"], d["heapMB"], d["partidasPorSeg"], d["jugadoresPorSeg"],
                            d["partidas"], *d["fasesMs"].values(), d["hash"]])


def _f(x, dec=2):
    return f"{x:,.{dec}f}".replace(",", "X").replace(".", ",").replace("X", ".") if isinstance(x, (int, float)) else str(x)


def reporte_md(r):
    L = list(r["lenguajes"])
    t = [f"# Reporte automático de comparación — {r['generado']}", "",
         f"**Ganador determinado automáticamente: {r['ganador']}** (método de normalización: `{r['metodo']}`).", ""]
    ent = r.get("entorno") or {}
    if ent:
        t += ["## Entorno", "", "| Elemento | Valor |", "|---|---|"] + [f"| {k} | {v} |" for k, v in ent.items()] + [""]
    t += ["## Ranking", "", "| Pos. | Lenguaje | " + " | ".join(f"{r['nombresCriterios'][c]} ({w} %)" for c, w in r["pesos"].items())
          + " | **Total** |", "|---|---|" + "---|" * len(r["pesos"]) + "---|"]
    for f in r["ranking"]:
        t.append(f"| {f['posicion']} | {f['lenguaje']} | " + " | ".join(_f(f["ponderados"][c]) for c in r["pesos"]) + f" | **{_f(f['total'])}** |")
    t += ["", "## Métrica cruda → normalizada (0-100) → ponderada", "", "| Criterio | Lenguaje | Métrica cruda | Unidad | Normalizado | Ponderado |",
          "|---|---|---|---|---|---|"]
    for c in r["pesos"]:
        for l in L:
            d = r["criterios"][c][l]
            t.append(f"| {r['nombresCriterios'][c]} | {l} | {_f(d['crudo'])} | {r['unidades'][c]} | {_f(d['normalizado'])} | {_f(d['ponderado'])} |")
    t += ["", "## Resultados por escenario (mediana de las ejecuciones medidas)", "",
          "| Escenario | Jugadores | Lenguaje | Mediana ms | Media ms | Desv. ms | Mín ms | Máx ms | Memoria pico MB | Partidas/s |",
          "|---|---|---|---|---|---|---|---|---|---|"]
    for e in r["escenarios"]:
        for l in L:
            d = r["porEscenario"][e["nombre"]][l]
            t.append(f"| {e['nombre']} | {_f(e['jugadores'], 0)} | {l} | {_f(d['tiempoMs'])} | {_f(d['mediaMs'])} | {_f(d['desviacionMs'])} | "
                     f"{_f(d['minMs'])} | {_f(d['maxMs'])} | {_f(d['memoriaMB'])} | {_f(d['partidasPorSeg'], 0)} |")
    t += ["", "## Equivalencia funcional (SHA-256 de la salida normalizada)", "", "| Escenario | " + " | ".join(L) + " | ¿Idénticos? |",
          "|---|" + "---|" * (len(L) + 1)]
    for esc, eq in r["equivalencia"].items():
        t.append(f"| {esc} | " + " | ".join(f"`{(eq['hashes'][l] or '—')[:12]}`" for l in L)
                 + f" | {'✅ sí' if all(eq['coinciden'].values()) else '❌ NO'} |")
    t += ["", "## Confiabilidad", "", "| Lenguaje | Pruebas | Errores manejados | Coincidencia hash | Puntaje |", "|---|---|---|---|---|"]
    for l, d in r["confiabilidad"].items():
        p = d["pruebas"] or {}
        t.append(f"| {l} | {p.get('aprobadas', '?')}/{p.get('total', '?')} ({_f(d['pctPruebas'])} %) | {d['chequeosErrores']} "
                 f"({_f(d['pctErrores'])} %) | {_f(d['pctHash'])} % | {_f(d['puntaje'])} |")
    t += ["", "## Calidad de código y uso funcional (reglas explícitas)", "",
          "| Lenguaje | LOC | Funciones | CC prom. | Anidamiento máx. | % funcional | Duplicación % | Efectos fuera de bordes | Cumplimiento | Calidad | Uso funcional |",
          "|---|---|---|---|---|---|---|---|---|---|---|"]
    for l, m in r["calidad"].items():
        t.append(f"| {l} | {m['loc']} | {m['funciones']} | {_f(m['complejidadPromedio'])} | {m['profundidadMaxima']} | {_f(m['ratioFuncional'])} | "
                 f"{_f(m['duplicacionPct'])} | {m['efectosFueraDeBordes']} | {_f(m['cumplimiento'])} % | {_f(m['puntajeCalidad'])} | {_f(m['puntajeFuncional'])} |")
    e = r["escaneoScala"]
    t += ["", f"**Escáner de Scala:** {e['totalViolaciones']} violaciones de la lista PROHIBIDO; cobertura de la tabla "
          f"{e['construccionesUsadas']}/{e['construccionesTabla']} ({_f(e['porcentajeCobertura'])} %). Ver `matriz_cobertura_scala.md`.", ""]
    s = r["sensibilidad"]
    t += ["## Análisis de sensibilidad (pesos ±5 puntos)", "",
          f"¿Cambia el ganador? **{'Sí' if s['ganadorCambia'] else 'No'}**. Margen entre 1.º y 2.º: {_f(s['margen'])} puntos. "
          f"Con normalización `{s['metodoAlterno']}` el ganador sería **{s['ganadorMetodoAlterno']}**.", "",
          "| Criterio | Variación | " + " | ".join(L) + " | Ganador |", "|---|---|" + "---|" * (len(L) + 1)]
    for v in s["variaciones"]:
        t.append(f"| {r['nombresCriterios'][v['criterio']]} | {v['delta']:+d} | " + " | ".join(_f(v["totales"][l]) for l in L)
                 + f" | {v['ganador']}{' ⚠' if v['cambia'] else ''} |")
    t += ["", "## Gráficos", ""] + [f"![{g}](graficos/{g}.png)" for g in
                                   ("ranking_total", "tiempos_por_escenario", "memoria_por_escenario", "escalabilidad",
                                    "throughput_por_escenario", "fases_escenario_mayor", "radar_criterios")]
    return "\n".join(t) + "\n"


# ------------------------------------------------------------------ gráficos (matplotlib)

def graficos(r):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    import numpy as np

    GRAFICOS.mkdir(parents=True, exist_ok=True)
    L = list(r["lenguajes"])
    col = {l: r["lenguajes"][l]["color"] for l in L}
    esc = [e["nombre"] for e in r["escenarios"]]
    etiquetas = [f"{e['nombre']}\n{e['jugadores']:,} jug.".replace(",", ".") for e in r["escenarios"]]
    plt.rcParams.update({"font.size": 10, "axes.spines.top": False, "axes.spines.right": False,
                         "axes.edgecolor": "#b5b4ad", "axes.labelcolor": "#3d3d3a", "xtick.color": "#5f5e5a",
                         "ytick.color": "#5f5e5a", "axes.titleweight": "bold", "axes.titlesize": 12,
                         "figure.facecolor": "#fcfcfb", "axes.facecolor": "#fcfcfb", "savefig.facecolor": "#fcfcfb"})

    def barras(clave, titulo, ylabel, archivo, log=False, fmt="{:.0f}"):
        fig, ax = plt.subplots(figsize=(9, 4.6))
        x = np.arange(len(esc))
        ancho = 0.26
        for i, l in enumerate(L):
            vals = [r["porEscenario"][e][l][clave] or 0 for e in esc]
            barras_ = ax.bar(x + (i - 1) * ancho, vals, ancho - 0.03, color=col[l], label=l, zorder=3)
            for b, v in zip(barras_, vals):
                ax.annotate(fmt.format(v), (b.get_x() + b.get_width() / 2, b.get_height()), ha="center", va="bottom",
                            fontsize=7.5, color="#3d3d3a", xytext=(0, 2), textcoords="offset points")
        ax.set_xticks(x, etiquetas)
        ax.set_ylabel(ylabel)
        ax.set_title(titulo, loc="left")
        if log:
            ax.set_yscale("log")
        ax.grid(axis="y", color="#e6e5e0", linewidth=0.8, zorder=0)
        ax.legend(frameon=False, ncol=3, loc="upper left")
        fig.tight_layout()
        fig.savefig(GRAFICOS / f"{archivo}.png", dpi=150)
        plt.close(fig)

    barras("tiempoMs", "Tiempo total por escenario (mediana, menos es mejor)", "milisegundos (escala log)", "tiempos_por_escenario", log=True)
    barras("memoriaMB", "Memoria pico del proceso por escenario (menos es mejor)", "MB", "memoria_por_escenario")
    barras("partidasPorSeg", "Rendimiento: partidas procesadas por segundo (más es mejor)", "partidas / s (escala log)",
           "throughput_por_escenario", log=True, fmt="{:,.0f}")

    # Escalabilidad
    fig, ax = plt.subplots(figsize=(8, 4.6))
    xs = [e["jugadores"] for e in r["escenarios"]]
    for l in L:
        ys = [r["porEscenario"][e][l]["tiempoMs"] for e in esc]
        ax.plot(xs, ys, color=col[l], linewidth=2, marker="o", markersize=7, markeredgecolor="#fcfcfb", label=l, zorder=3)
        ax.annotate(l, (xs[-1], ys[-1]), xytext=(6, 0), textcoords="offset points", va="center", fontsize=9, color="#3d3d3a")
    ax.set_xscale("log")
    ax.set_yscale("log")
    ax.set_xlabel("jugadores (escala log)")
    ax.set_ylabel("ms (escala log)")
    ax.set_title("Curva de escalabilidad: tiempo total vs. volumen", loc="left")
    ax.grid(color="#e6e5e0", linewidth=0.8, zorder=0)
    ax.legend(frameon=False)
    fig.tight_layout()
    fig.savefig(GRAFICOS / "escalabilidad.png", dpi=150)
    plt.close(fig)

    # Fases del escenario más grande
    mayor = esc[-1]
    fases = ["carga", "matchmaking", "eliminacion", "liga", "estadisticas"]
    fig, ax = plt.subplots(figsize=(9, 4.4))
    x = np.arange(len(fases))
    for i, l in enumerate(L):
        vals = [r["porEscenario"][mayor][l]["fasesMs"][f] for f in fases]
        ax.bar(x + (i - 1) * 0.26, vals, 0.23, color=col[l], label=l, zorder=3)
    ax.set_xticks(x, fases)
    ax.set_ylabel("ms (mediana)")
    ax.set_title(f"Tiempo por fase — escenario {mayor}", loc="left")
    ax.grid(axis="y", color="#e6e5e0", linewidth=0.8, zorder=0)
    ax.legend(frameon=False)
    fig.tight_layout()
    fig.savefig(GRAFICOS / "fases_escenario_mayor.png", dpi=150)
    plt.close(fig)

    # Ranking total
    fig, ax = plt.subplots(figsize=(8, 3.2))
    orden = [f["lenguaje"] for f in r["ranking"]][::-1]
    tot = {f["lenguaje"]: f["total"] for f in r["ranking"]}
    ax.barh(orden, [tot[l] for l in orden], color=[col[l] for l in orden], height=0.55, zorder=3)
    for i, l in enumerate(orden):
        ax.text(tot[l] + 1, i, f"{tot[l]:.1f}", va="center", fontsize=10, color="#3d3d3a")
    ax.set_xlim(0, 105)
    ax.set_xlabel("puntaje ponderado (0-100)")
    ax.set_title(f"Clasificación final — ganador: {r['ganador']}", loc="left")
    ax.grid(axis="x", color="#e6e5e0", linewidth=0.8, zorder=0)
    fig.tight_layout()
    fig.savefig(GRAFICOS / "ranking_total.png", dpi=150)
    plt.close(fig)

    # Radar
    crit = list(r["pesos"])
    ang = np.linspace(0, 2 * np.pi, len(crit), endpoint=False).tolist()
    fig, ax = plt.subplots(figsize=(6.4, 6), subplot_kw={"polar": True})
    for l in L:
        v = [r["criterios"][c][l]["normalizado"] for c in crit]
        ax.plot(ang + ang[:1], v + v[:1], color=col[l], linewidth=2, label=l)
        ax.fill(ang + ang[:1], v + v[:1], color=col[l], alpha=0.08)
    ax.set_xticks(ang, [r["nombresCriterios"][c].replace(" (", "\n(") for c in crit], fontsize=9)
    ax.set_ylim(0, 100)
    ax.set_yticks([25, 50, 75, 100], ["25", "50", "75", "100"], fontsize=7, color="#8a8984")
    ax.grid(color="#e6e5e0")
    ax.set_title("Puntaje normalizado por criterio (0-100)", pad=18, fontweight="bold")
    ax.legend(frameon=False, loc="upper right", bbox_to_anchor=(1.18, 1.08))
    fig.tight_layout()
    fig.savefig(GRAFICOS / "radar_criterios.png", dpi=150)
    plt.close(fig)


if __name__ == "__main__":
    import sys
    consolidar(sys.argv[1] if len(sys.argv) > 1 else "minmax")
