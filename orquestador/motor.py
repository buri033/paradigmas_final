"""
Motor del orquestador: compila los tres proyectos, ejecuta las pruebas unitarias y corre cada
programa midiendo DESDE AFUERA el tiempo de pared y la memoria pico del proceso (RSS / Working Set).

Si la herramienta oficial no está instalada (Maven / sbt) intenta una alternativa:
  Java  -> javac + jar      Scala -> scalac (necesita scala-library.jar)
"""
import json
import os
import platform
import re
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

from config import (CRUDOS, JVM_FLAGS, LENGUAJES, RAIZ, RESULTADOS, SEMILLA, TAM_GRUPO_LIGA,
                    ruta_crudo, ruta_csv)

try:
    import psutil
except ImportError:  # el orquestador funciona sin psutil, pero la memoria externa queda sin medir
    psutil = None

ES_WINDOWS = os.name == "nt"


def herramienta(nombre):
    """Ruta completa de un ejecutable (en Windows encuentra mvn.cmd, sbt.bat, etc.)."""
    return shutil.which(nombre)


def correr(cmd, cwd, log, timeout=3600):
    """Ejecuta un comando, envía la salida al log y devuelve (código, salida)."""
    log(f"$ {' '.join(str(c) for c in cmd)}   (en {Path(cwd).relative_to(RAIZ) if Path(cwd).is_relative_to(RAIZ) else cwd})")
    try:
        p = subprocess.run([str(c) for c in cmd], cwd=cwd, capture_output=True, text=True,
                           timeout=timeout, encoding="utf-8", errors="replace")
        salida = (p.stdout or "") + (p.stderr or "")
        for linea in salida.strip().splitlines()[-15:]:
            log("    " + linea)
        return p.returncode, salida
    except FileNotFoundError as e:
        log(f"    ERROR: no se encontró el ejecutable ({e})")
        return 127, str(e)
    except subprocess.TimeoutExpired:
        log("    ERROR: tiempo límite agotado")
        return 124, "timeout"


# ------------------------------------------------------------------ COMPILACIÓN

def _scala_library():
    """Busca scala-library.jar para el modo alternativo (sin sbt)."""
    candidatos = []
    if os.environ.get("SCALA_HOME"):
        candidatos.append(Path(os.environ["SCALA_HOME"]) / "lib" / "scala-library.jar")
    scalac = herramienta("scalac")
    if scalac:
        real = Path(os.path.realpath(scalac))
        candidatos += [real.parent.parent / "lib" / "scala-library.jar"]
    candidatos += list(Path("/usr/share").glob("scala*/lib/scala-library.jar"))
    return next((c for c in candidatos if c.exists()), None)


def compilar(lenguaje, log):
    """Compila y devuelve el comando base para ejecutar el programa (o None si falla)."""
    java = herramienta("java")
    sep = ";" if ES_WINDOWS else ":"
    if lenguaje == "Java":
        carpeta = RAIZ / "java"
        jar = carpeta / "target" / "torneos-java.jar"
        mvn = herramienta("mvn")
        if mvn:
            codigo, _ = correr([mvn, "-q", "-B", "-DskipTests", "package"], carpeta, log)
            if codigo == 0 and jar.exists():
                return [java, *JVM_FLAGS, "-jar", jar]
            log("  Maven falló; se intenta con javac directamente")
        javac = herramienta("javac")
        if not javac:
            log("  ERROR: no hay javac (instale un JDK 17+)")
            return None
        clases = carpeta / "target" / "clases-javac"
        shutil.rmtree(clases, ignore_errors=True)
        clases.mkdir(parents=True)
        fuentes = [str(p) for p in (carpeta / "src" / "main" / "java").rglob("*.java")]
        codigo, _ = correr([javac, "-encoding", "UTF-8", "-d", clases, *fuentes], carpeta, log)
        return [java, *JVM_FLAGS, "-cp", clases, "torneos.Main"] if codigo == 0 else None

    if lenguaje == "Scala":
        carpeta = RAIZ / "scala"
        sbt = herramienta("sbt")
        if sbt:
            codigo, _ = correr([sbt, "-batch", "assembly"], carpeta, log, timeout=1800)
            jar = next(iter((carpeta / "target").glob("scala-*/torneos-scala.jar")), None)
            if codigo == 0 and jar:
                return [java, *JVM_FLAGS, "-jar", jar]
            log("  sbt falló; se intenta con scalac directamente")
        scalac, libreria = herramienta("scalac"), _scala_library()
        if not scalac or not libreria:
            log("  ERROR: no hay sbt ni scalac + scala-library.jar (instale sbt)")
            return None
        clases = carpeta / "target" / "clases-scalac"
        shutil.rmtree(clases, ignore_errors=True)
        clases.mkdir(parents=True)
        fuentes = [str(p) for p in (carpeta / "src" / "main" / "scala").rglob("*.scala")]
        codigo, _ = correr([scalac, "-encoding", "UTF-8", "-d", clases, *fuentes], carpeta, log)
        return [java, *JVM_FLAGS, "-cp", f"{clases}{sep}{libreria}", "torneos.Main"] if codigo == 0 else None

    if lenguaje == "C#":
        carpeta = RAIZ / "csharp"
        dotnet = herramienta("dotnet")
        if not dotnet:
            log("  ERROR: no hay dotnet (instale el SDK de .NET 8)")
            return None
        salida = carpeta / "bin-release"
        extra = os.environ.get("TORNEOS_DOTNET_EXTRA", "").split()
        codigo, _ = correr([dotnet, "build", "TorneosCSharp/TorneosCSharp.csproj", "-c", "Release", "-o", salida,
                            "--nologo", "-v", "q", *extra], carpeta, log, timeout=1800)
        dll = salida / "TorneosCSharp.dll"
        return [dotnet, dll] if codigo == 0 and dll.exists() else None
    raise ValueError(lenguaje)


# ------------------------------------------------------------------ PRUEBAS UNITARIAS

PATRONES_PRUEBAS = {
    # Maven/Surefire:  Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
    "Java": re.compile(r"Tests run:\s*(\d+),\s*Failures:\s*(\d+),\s*Errors:\s*(\d+)"),
    # sbt + MUnit:     Passed: Total 23, Failed 0, Errors 0, Passed 23
    "Scala": re.compile(r"Total\s+(\d+),\s*Failed\s+(\d+),\s*Errors\s+(\d+)"),
    # dotnet test:     Failed:     0, Passed:    23, Skipped:     0, Total:    23
    "C#": re.compile(r"Failed:\s*(\d+),\s*Passed:\s*(\d+),\s*Skipped:\s*(\d+),\s*Total:\s*(\d+)"),
}


def ejecutar_pruebas(lenguaje, log):
    """Corre JUnit / MUnit / xUnit y devuelve {total, aprobadas} o None si no se pudo."""
    if lenguaje == "Java" and herramienta("mvn"):
        codigo, salida = correr([herramienta("mvn"), "-B", "test"], RAIZ / "java", log)
    elif lenguaje == "Scala" and herramienta("sbt"):
        codigo, salida = correr([herramienta("sbt"), "-batch", "test"], RAIZ / "scala", log, timeout=1800)
    elif lenguaje == "C#" and herramienta("dotnet"):
        extra = os.environ.get("TORNEOS_DOTNET_EXTRA", "").split()
        codigo, salida = correr([herramienta("dotnet"), "test", "TorneosCSharp.sln", "-c", "Release", *extra],
                                RAIZ / "csharp", log, timeout=1800)
    else:
        log(f"  {lenguaje}: herramienta de pruebas no disponible")
        return None
    coincidencias = PATRONES_PRUEBAS[lenguaje].findall(salida)
    if not coincidencias:
        log(f"  {lenguaje}: no se pudo leer el resumen de pruebas")
        return None
    if lenguaje == "C#":
        fallidas, aprobadas, _, total = map(int, coincidencias[-1])
    else:
        total, fallas, errores = map(int, coincidencias[-1])
        fallidas = fallas + errores
        aprobadas = total - fallidas
    return {"total": total, "aprobadas": aprobadas, "fuente": "framework", "codigoSalida": codigo}


# ------------------------------------------------------------------ EJECUCIÓN MEDIDA

def _vm_hwm(pid):
    """Pico de memoria residente exacto en Linux (VmHWM)."""
    try:
        with open(f"/proc/{pid}/status") as f:
            for linea in f:
                if linea.startswith("VmHWM:"):
                    return int(linea.split()[1]) * 1024
    except OSError:
        pass
    return 0


def ejecutar_medido(cmd, log, timeout=3600):
    """Ejecuta y mide desde afuera: tiempo de pared y memoria pico del proceso (sondeo cada 20 ms)."""
    with tempfile.TemporaryFile(mode="w+", encoding="utf-8", errors="replace") as salida:
        t0 = time.perf_counter()
        proceso = subprocess.Popen([str(c) for c in cmd], cwd=RAIZ, stdout=salida, stderr=subprocess.STDOUT)
        pico = 0
        vigilado = psutil.Process(proceso.pid) if psutil else None
        while proceso.poll() is None:
            if vigilado:
                try:
                    info = vigilado.memory_info()
                    pico = max(pico, info.rss, getattr(info, "peak_wset", 0) or 0, _vm_hwm(proceso.pid))
                except (psutil.Error, OSError):
                    pass
            if time.perf_counter() - t0 > timeout:
                proceso.kill()
                break
            time.sleep(0.02)
        pared = time.perf_counter() - t0
        salida.seek(0)
        texto = salida.read().strip()
    for linea in texto.splitlines()[:6]:
        log("    " + linea[:220])
    return {"codigoSalida": proceso.returncode, "paredSeg": round(pared, 4),
            "rssPicoMB": round(pico / 1048576, 2) if pico else None, "salida": texto[-2000:]}


def correr_escenario(lenguaje, comando, escenario, runs, warmup, log):
    destino = ruta_crudo(lenguaje, escenario)
    destino.parent.mkdir(parents=True, exist_ok=True)
    if destino.exists():
        destino.unlink()
    args = ["--input", ruta_csv(escenario), "--mode", "all", "--runs", runs, "--warmup", warmup,
            "--out", destino, "--seed", SEMILLA, "--grupo", TAM_GRUPO_LIGA]
    log(f"  ▶ {lenguaje:<5} | {escenario:<11} | runs={runs} warmup={warmup}")
    medicion = ejecutar_medido([*comando, *args], log)
    medicion["jsonGenerado"] = destino.exists()
    return medicion


# ------------------------------------------------------------------ ENTORNO

def _version(cmd):
    try:
        p = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
        texto = (p.stdout + p.stderr).strip().splitlines()
        return next((l for l in texto if l.strip() and "JAVA_TOOL_OPTIONS" not in l), "").strip()
    except (OSError, subprocess.TimeoutExpired):
        return "no disponible"


def describir_entorno():
    cpu = platform.processor() or ""
    try:
        with open("/proc/cpuinfo") as f:
            cpu = next((l.split(":", 1)[1].strip() for l in f if l.startswith("model name")), cpu)
    except OSError:
        pass
    ram = round(psutil.virtual_memory().total / 2**30, 1) if psutil else None
    return {
        "sistema": f"{platform.system()} {platform.release()}",
        "cpu": cpu or platform.machine(),
        "nucleosLogicos": os.cpu_count(),
        "ramGB": ram,
        "python": sys.version.split()[0],
        "java": _version([herramienta("java") or "java", "-version"]),
        "maven": _version([herramienta("mvn"), "-v"]) if herramienta("mvn") else "no disponible (se compiló con javac)",
        "sbt": "instalado" if herramienta("sbt") else "no disponible (se compiló con scalac)",
        "dotnet": _version([herramienta("dotnet") or "dotnet", "--version"]),
        "flagsJVM": " ".join(JVM_FLAGS),
        "modoDotnet": "Release (dotnet build -c Release), InvariantGlobalization, TieredPGO",
        "fecha": time.strftime("%Y-%m-%d %H:%M"),
    }


def guardar_json(ruta, datos):
    ruta = Path(ruta)
    ruta.parent.mkdir(parents=True, exist_ok=True)
    ruta.write_text(json.dumps(datos, indent=2, ensure_ascii=False, default=str), encoding="utf-8")


def leer_json(ruta, defecto=None):
    try:
        return json.loads(Path(ruta).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return defecto


__all__ = ["compilar", "ejecutar_pruebas", "correr_escenario", "describir_entorno", "guardar_json", "leer_json",
           "LENGUAJES", "RESULTADOS", "CRUDOS"]
