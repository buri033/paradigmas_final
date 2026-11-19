#!/usr/bin/env python3
"""
Pipeline completo y reproducible (lo usan la interfaz web y la línea de comandos):

  generar datos -> compilar -> pruebas unitarias -> benchmark (con medición externa) ->
  escenarios de confiabilidad -> consolidación, ranking, sensibilidad, CSV/JSON/MD y gráficos

Uso por consola (alternativa a la interfaz):
  python orquestador/ejecutar_todo.py --runs 10 --warmup 3
  python orquestador/ejecutar_todo.py --solo-consolidar          # recalcula el ranking con lo ya medido
"""
import argparse
import subprocess
import sys
import time

from config import DATOS, ESCENARIOS_CONFIABILIDAD, ESCENARIOS_RENDIMIENTO, LENGUAJES, RAIZ, RESULTADOS
from consolidar import consolidar
from motor import compilar, correr_escenario, describir_entorno, ejecutar_pruebas, guardar_json, leer_json


def generar_datos(extremo, log):
    cmd = [sys.executable, str(RAIZ / "datos" / "generador.py")] + (["--extremo"] if extremo else [])
    log("$ " + " ".join(cmd))
    p = subprocess.run(cmd, capture_output=True, text=True)
    for linea in (p.stdout + p.stderr).strip().splitlines():
        log("    " + linea)


def ejecutar_pipeline(runs=10, warmup=3, escenarios=None, pruebas=True, extremo=False, metodo="minmax",
                      log=print, progreso=lambda pct, etapa: None):
    inicio = time.time()
    escenarios = escenarios or [e for e in ESCENARIOS_RENDIMIENTO if e != "extremo"]
    if extremo and "extremo" not in escenarios:
        escenarios.append("extremo")
    faltan = [e for e in escenarios + ["sucio", "minimo"] if not (DATOS / f"{e}.csv").exists()]
    if faltan:
        progreso(2, "Generando datos")
        log(f"■ Generando escenarios faltantes: {', '.join(faltan)}")
        generar_datos(extremo or "extremo" in faltan, log)
    escenarios = [e for e in escenarios if (DATOS / f"{e}.csv").exists()]

    progreso(4, "Describiendo el entorno")
    entorno = describir_entorno()
    guardar_json(RESULTADOS / "entorno.json", entorno)
    log("■ Entorno: " + " | ".join(f"{k}: {v}" for k, v in entorno.items() if k in ("sistema", "cpu", "ramGB", "java", "dotnet")))

    comandos = {}
    for i, l in enumerate(LENGUAJES):
        progreso(5 + 5 * i, f"Compilando {l}")
        log(f"■ Compilando {l}")
        comandos[l] = compilar(l, log)
        log(f"  {l}: {'OK' if comandos[l] else 'FALLÓ (no se medirá)'}")

    if pruebas:
        resultados_pruebas = leer_json(RESULTADOS / "pruebas.json", {})
        for i, l in enumerate(LENGUAJES):
            progreso(20 + 5 * i, f"Pruebas unitarias {l}")
            log(f"■ Pruebas unitarias {l}")
            r = ejecutar_pruebas(l, log)
            if r:
                resultados_pruebas[l] = r
                log(f"  {l}: {r['aprobadas']}/{r['total']} aprobadas")
            elif l in resultados_pruebas:
                log(f"  {l}: se conserva el último resultado registrado ({resultados_pruebas[l].get('aprobadas')}/"
                    f"{resultados_pruebas[l].get('total')})")
        guardar_json(RESULTADOS / "pruebas.json", resultados_pruebas)

    externos = leer_json(RESULTADOS / "mediciones_externas.json", {})
    trabajos = [(e, l, runs, warmup) for e in escenarios for l in LENGUAJES] + \
               [(e, l, 1, 0) for e in ESCENARIOS_CONFIABILIDAD for l in LENGUAJES]
    for k, (e, l, r_, w_) in enumerate(trabajos):
        progreso(35 + 60 * k / len(trabajos), f"{l} · {e}")
        if not comandos.get(l):
            continue
        medicion = correr_escenario(l, comandos[l], e, r_, w_, log)
        externos.setdefault(l, {})[e] = medicion
        guardar_json(RESULTADOS / "mediciones_externas.json", externos)

    progreso(96, "Consolidando y clasificando")
    log("■ Consolidación, normalización, ranking y sensibilidad")
    resultado = consolidar(metodo, log=log)
    log(f"■ Terminado en {time.time() - inicio:.0f} s")
    progreso(100, "Listo")
    return resultado


def main():
    p = argparse.ArgumentParser(description="Orquestador del comparativo Java / Scala / C#")
    p.add_argument("--runs", type=int, default=10)
    p.add_argument("--warmup", type=int, default=3)
    p.add_argument("--escenarios", nargs="*", help="pequeno mediano grande muy_grande extremo")
    p.add_argument("--sin-pruebas", action="store_true")
    p.add_argument("--extremo", action="store_true", help="incluye 1.000.000 de jugadores")
    p.add_argument("--metodo", choices=["minmax", "proporcional"], default="minmax")
    p.add_argument("--solo-consolidar", action="store_true")
    a = p.parse_args()
    if a.solo_consolidar:
        consolidar(a.metodo)
        return
    r = ejecutar_pipeline(a.runs, a.warmup, a.escenarios, not a.sin_pruebas, a.extremo, a.metodo)
    print("\nRANKING")
    for f in r["ranking"]:
        print(f"  {f['posicion']}. {f['lenguaje']:<6} {f['total']:6.2f}")
    print(f"Reporte: {RESULTADOS / 'reporte.md'}")


if __name__ == "__main__":
    main()
