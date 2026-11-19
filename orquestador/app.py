#!/usr/bin/env python3
"""
INTERFAZ GRÁFICA (web local, sin dependencias extra): abre el panel en el navegador.

    python orquestador/app.py            ->  http://localhost:8765

Desde el panel se generan los datos, se compila, se ejecutan pruebas y benchmark, y se ven
el ranking, los gráficos, la equivalencia de hashes, la calidad del código y el torneo jugado.
"""
import json
import mimetypes
import sys
import threading
import time
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlparse

sys.path.insert(0, str(Path(__file__).resolve().parent))

from config import DATOS, ESCENARIOS_RENDIMIENTO, LENGUAJES, RAIZ, RESULTADOS  # noqa: E402
from consolidar import consolidar  # noqa: E402
from ejecutar_todo import ejecutar_pipeline, generar_datos  # noqa: E402
from motor import herramienta  # noqa: E402

PUERTO = 8765
UI = Path(__file__).resolve().parent / "ui" / "index.html"

estado = {"ejecutando": False, "progreso": 0, "etapa": "En espera", "log": [], "error": None}
candado = threading.Lock()


def log(linea):
    with candado:
        estado["log"].append(f"[{time.strftime('%H:%M:%S')}] {linea}")
        estado["log"] = estado["log"][-600:]
    print(linea, flush=True)


def progreso(pct, etapa):
    with candado:
        estado["progreso"], estado["etapa"] = round(pct, 1), etapa


def en_segundo_plano(funcion, *args, **kwargs):
    def tarea():
        try:
            funcion(*args, **kwargs)
        except Exception as e:  # se informa en la interfaz sin tumbar el servidor
            log(f"ERROR: {e!r}")
            estado["error"] = str(e)
        finally:
            estado["ejecutando"] = False
    if estado["ejecutando"]:
        return False
    estado.update(ejecutando=True, progreso=0, etapa="Iniciando", error=None)
    threading.Thread(target=tarea, daemon=True).start()
    return True


class Manejador(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def _enviar(self, codigo, cuerpo: bytes, tipo="application/json; charset=utf-8"):
        self.send_response(codigo)
        self.send_header("Content-Type", tipo)
        self.send_header("Content-Length", str(len(cuerpo)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(cuerpo)

    def _json(self, datos, codigo=200):
        self._enviar(codigo, json.dumps(datos, ensure_ascii=False).encode("utf-8"))

    def do_GET(self):
        ruta = unquote(urlparse(self.path).path)
        if ruta in ("/", "/index.html"):
            return self._enviar(200, UI.read_bytes(), "text/html; charset=utf-8")
        if ruta == "/api/estado":
            with candado:
                datos = dict(estado, log=estado["log"][-250:])
            datos["herramientas"] = {h: bool(herramienta(h)) for h in ("java", "javac", "mvn", "sbt", "scalac", "dotnet")}
            datos["escenarios"] = {e: (DATOS / f"{e}.csv").exists() for e in list(ESCENARIOS_RENDIMIENTO) + ["sucio", "minimo"]}
            datos["hayResultados"] = (RESULTADOS / "ranking.json").exists()
            return self._json(datos)
        if ruta.startswith("/resultados/"):
            archivo = (RESULTADOS / ruta[len("/resultados/"):]).resolve()
            if RESULTADOS.resolve() in archivo.parents and archivo.is_file():
                tipo = mimetypes.guess_type(archivo.name)[0] or "application/octet-stream"
                return self._enviar(200, archivo.read_bytes(), tipo)
        if ruta.startswith("/fuente/"):  # ver el código fuente desde la interfaz
            archivo = (RAIZ / ruta[len("/fuente/"):]).resolve()
            if RAIZ.resolve() in archivo.parents and archivo.suffix in (".java", ".scala", ".cs") and archivo.is_file():
                return self._enviar(200, archivo.read_bytes(), "text/plain; charset=utf-8")
        self._json({"error": "no encontrado"}, 404)

    def do_POST(self):
        ruta = urlparse(self.path).path
        largo = int(self.headers.get("Content-Length") or 0)
        cuerpo = json.loads(self.rfile.read(largo) or b"{}")
        if ruta == "/api/ejecutar":
            ok = en_segundo_plano(ejecutar_pipeline, runs=int(cuerpo.get("runs", 10)), warmup=int(cuerpo.get("warmup", 3)),
                                  escenarios=cuerpo.get("escenarios"), pruebas=bool(cuerpo.get("pruebas", True)),
                                  extremo=bool(cuerpo.get("extremo", False)), metodo=cuerpo.get("metodo", "minmax"),
                                  log=log, progreso=progreso)
            return self._json({"iniciado": ok})
        if ruta == "/api/generar":
            ok = en_segundo_plano(lambda: (progreso(10, "Generando datos"), generar_datos(bool(cuerpo.get("extremo")), log),
                                           progreso(100, "Datos generados")))
            return self._json({"iniciado": ok})
        if ruta == "/api/consolidar":
            ok = en_segundo_plano(lambda: (progreso(30, "Consolidando"), consolidar(cuerpo.get("metodo", "minmax"), log=log),
                                           progreso(100, "Listo")))
            return self._json({"iniciado": ok})
        self._json({"error": "ruta desconocida"}, 404)


def main():
    puerto = int(sys.argv[1]) if len(sys.argv) > 1 else PUERTO
    servidor = ThreadingHTTPServer(("127.0.0.1", puerto), Manejador)
    url = f"http://localhost:{puerto}"
    print(f"Panel del comparativo en {url}  (Ctrl+C para salir)")
    threading.Timer(1.0, lambda: webbrowser.open(url)).start()
    try:
        servidor.serve_forever()
    except KeyboardInterrupt:
        print("\nServidor detenido")


if __name__ == "__main__":
    main()
