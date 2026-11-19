#!/usr/bin/env python3
"""
Generador reproducible de escenarios de prueba (CSV) para el Sistema de Matchmaking
y Torneos de e-sports.

- Usa un LCG de 64 bits PROPIO (mismas constantes que Java, Scala y C#), con semilla
  fija, para que los archivos sean idénticos en cualquier máquina y sistema operativo.
- Formato del CSV:  id,nombre,elo   (elo vacío => 1000 por defecto)
- Escribe además sucio_esperado.json con el número EXACTO de errores que cada
  programa debe detectar (lo usa el orquestador para medir confiabilidad).

Uso:
    python datos/generador.py                # escenarios estándar
    python datos/generador.py --extremo      # añade el escenario de 1.000.000
"""
import argparse
import json
import os

MASK = (1 << 64) - 1
A = 6364136223846793005
C = 1442695040888963407
SEMILLA_DATOS = 20261116

ESCENARIOS = {
    "pequeno": 64,
    "mediano": 1024,
    "grande": 16384,
    "muy_grande": 131072,
}
EXTREMO = {"extremo": 1000000}

PREFIJOS = ["Shadow", "Neo", "Pixel", "Viper", "Nova", "Blaze", "Frost", "Echo",
            "Rogue", "Titan", "Zero", "Lynx", "Raven", "Storm", "Ghost", "Orion"]


class LCG:
    """LCG de 64 bits: estado' = estado * A + C (mod 2^64)."""

    def __init__(self, semilla):
        self.estado = semilla & MASK

    def siguiente(self):
        self.estado = (self.estado * A + C) & MASK
        return self.estado

    def uniforme(self):
        return (self.siguiente() >> 11) / float(1 << 53)

    def entero(self, n):
        return int(self.uniforme() * n)


def elo_aleatorio(g):
    # Suma de 4 uniformes ~ campana centrada en 1400 (rango aprox. 700 - 2300)
    s = g.uniforme() + g.uniforme() + g.uniforme() + g.uniforme()
    elo = int(round(1400 + (s - 2.0) * 450))
    return max(600, min(2700, elo))


def texto_elo(g):
    r = g.uniforme()
    if r < 0.01:
        return ""                      # sin Elo -> 1000 por defecto
    elo = elo_aleatorio(g)
    if r < 0.06:
        return f"{elo}.5"              # algunos con decimales
    return str(elo)


def permutacion(n, g):
    ids = list(range(1, n + 1))
    for i in range(n - 1, 0, -1):      # Fisher-Yates con el LCG propio
        j = g.entero(i + 1)
        ids[i], ids[j] = ids[j], ids[i]
    return ids


def lineas_limpias(n, semilla):
    g = LCG(semilla)
    ids = permutacion(n, g)
    return [f"{i},{PREFIJOS[i % len(PREFIJOS)]}_{i:07d},{texto_elo(g)}" for i in ids]


def escribir(ruta, lineas):
    with open(ruta, "w", encoding="utf-8", newline="\n") as f:
        f.write("id,nombre,elo\n")
        for l in lineas:
            f.write(l + "\n")


def escenario_sucio(ruta_csv, ruta_esperado):
    """999 jugadores válidos (impar y no potencia de 2) + líneas defectuosas."""
    g = LCG(SEMILLA_DATOS + 99)
    validos = lineas_limpias(999, SEMILLA_DATOS + 7)
    malformadas = [
        "500001;Punto;Coma;1500",       # separador incorrecto -> 1 campo
        "500002,SoloDosCampos",         # 2 campos
        "500003,Cuatro,1500,extra",     # 4 campos
        "abc,IdNoNumerico,1500",        # id no numérico
        "500004,,1500",                 # nombre vacío
        "500005,EloTexto,abc",          # elo no numérico
        "-5,IdNegativo,1500",           # id negativo
        "0,IdCero,1500",                # id cero
        "500006,TresDecimales,15.555",  # elo con 3 decimales
        "500007,Notacion,1e3",          # notación científica
        ",,",                           # campos vacíos
        "9999999999,IdGigante,1500",    # id con más de 9 dígitos
    ]
    elo_invalidos = [
        "600001,EloNegativo,-1200", "600002,EloCero,0", "600003,EloNull,null",
        "600004,EloNegDec,-0.5", "600005,EloCeroDec,0.00", "600006,EloNULL,NULL",
        "600007,EloMenosUno,-1", "600008,EloNeg2,-850", "600009,EloCero2,0.0",
        "600010,EloNull2,Null",
    ]
    # duplicados: reutilizan ids válidos existentes con Elo válido
    duplicados, usados = [], set()
    while len(duplicados) < 8:
        id_dup = validos[g.entero(len(validos))].split(",")[0]
        if id_dup in usados:
            continue
        usados.add(id_dup)
        k = len(duplicados)
        duplicados.append(f"{id_dup},Duplicado_{k},{1100 + 37 * k}")
    defectuosas = malformadas + elo_invalidos + duplicados + ["   "]  # + línea en blanco (se ignora)
    lineas = list(validos)
    for d in defectuosas:
        pos = g.entero(len(lineas) + 1)
        lineas.insert(pos, d)
    escribir(ruta_csv, lineas)
    # Verificación: los ids duplicados deben ser distintos entre sí
    ids_dup = [d.split(",")[0] for d in duplicados]
    assert len(set(ids_dup)) == len(ids_dup), "regenerar: duplicado repetido"
    esperado = {
        "validos": 999,
        "porTipo": {"LINEA_MALFORMADA": len(malformadas), "ELO_INVALIDO": len(elo_invalidos),
                    "ID_DUPLICADO": len(duplicados), "ARCHIVO_INEXISTENTE": 0,
                    "TORNEO_INSUFICIENTE": 0},
    }
    esperado["total"] = sum(esperado["porTipo"].values())
    return esperado


def main():
    p = argparse.ArgumentParser(description="Generador de escenarios CSV")
    p.add_argument("--salida", default=os.path.join(os.path.dirname(os.path.abspath(__file__)), "escenarios"))
    p.add_argument("--extremo", action="store_true", help="incluye 1.000.000 de jugadores")
    a = p.parse_args()
    os.makedirs(a.salida, exist_ok=True)
    tam = dict(ESCENARIOS)
    if a.extremo:
        tam.update(EXTREMO)
    for k, (nombre, n) in enumerate(tam.items()):
        ruta = os.path.join(a.salida, f"{nombre}.csv")
        escribir(ruta, lineas_limpias(n, SEMILLA_DATOS + k))
        print(f"  {nombre:<11} {n:>9,} jugadores -> {ruta}")

    esperados = {}
    esperados["sucio"] = escenario_sucio(os.path.join(a.salida, "sucio.csv"), None)
    # mínimo: 1 jugador válido + 1 línea malformada => torneos imposibles
    escribir(os.path.join(a.salida, "minimo.csv"), ["1,Solitario_0000001,1500", "2;malformada"])
    esperados["minimo"] = {"validos": 1, "porTipo": {"LINEA_MALFORMADA": 1, "ELO_INVALIDO": 0,
                           "ID_DUPLICADO": 0, "ARCHIVO_INEXISTENTE": 0, "TORNEO_INSUFICIENTE": 2}, "total": 3}
    esperados["inexistente"] = {"validos": 0, "porTipo": {"LINEA_MALFORMADA": 0, "ELO_INVALIDO": 0,
                                "ID_DUPLICADO": 0, "ARCHIVO_INEXISTENTE": 1, "TORNEO_INSUFICIENTE": 2}, "total": 3}
    with open(os.path.join(a.salida, "errores_esperados.json"), "w", encoding="utf-8") as f:
        json.dump(esperados, f, indent=2, ensure_ascii=False)
    print("  sucio, minimo e inexistente -> errores_esperados.json")


if __name__ == "__main__":
    main()
