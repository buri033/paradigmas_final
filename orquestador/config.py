"""Configuración común del orquestador (rutas, lenguajes, escenarios y pesos del enunciado)."""
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
DATOS = RAIZ / "datos" / "escenarios"
RESULTADOS = RAIZ / "resultados"
CRUDOS = RESULTADOS / "crudos"
GRAFICOS = RESULTADOS / "graficos"

SEMILLA = 20261117
TAM_GRUPO_LIGA = 64

# Flags de la JVM (iguales para Java y Scala). Documentados en el informe.
JVM_FLAGS = ["-Xms256m", "-Xmx3g", "-XX:+UseG1GC"]

# Pesos del enunciado (suman 100)
PESOS = {
    "tiempo": 25,
    "memoria": 20,
    "rendimiento": 20,
    "calidad": 15,
    "confiabilidad": 10,
    "funcional": 10,
}

NOMBRES_CRITERIOS = {
    "tiempo": "Tiempo de ejecución",
    "memoria": "Consumo de memoria",
    "rendimiento": "Rendimiento (throughput)",
    "calidad": "Calidad de código",
    "confiabilidad": "Confiabilidad",
    "funcional": "Uso funcional",
}

# Escenarios de rendimiento (nombre -> jugadores) y de confiabilidad
ESCENARIOS_RENDIMIENTO = {
    "pequeno": 64,
    "mediano": 1024,
    "grande": 16384,
    "muy_grande": 131072,
    "extremo": 1000000,  # opcional: solo si existe el CSV (python datos/generador.py --extremo)
}
ESCENARIOS_CONFIABILIDAD = ["sucio", "minimo", "inexistente"]

LENGUAJES = {
    "Java": {
        "archivo": "Java",
        "color": "#eb6834", "colorOscuro": "#d95926",
        "carpeta": "java",
        "fuentes": ["java/src/main/java"],
        "pruebas": ["java/src/test/java"],
        "extension": ".java",
        # Bordes: únicos lugares donde se permiten efectos (E/S, medición)
        "bordes": ["/infra/", "Main.java"],
    },
    "Scala": {
        "archivo": "Scala",
        "color": "#2a78d6", "colorOscuro": "#3987e5",
        "carpeta": "scala",
        "fuentes": ["scala/src/main/scala"],
        "pruebas": ["scala/src/test/scala"],
        "extension": ".scala",
        "bordes": ["Infraestructura.scala", "Main.scala"],
    },
    "C#": {
        "archivo": "CSharp",
        "color": "#1baf7a", "colorOscuro": "#199e70",
        "carpeta": "csharp",
        "fuentes": ["csharp/TorneosCSharp"],
        "pruebas": ["csharp/TorneosCSharp.Tests"],
        "extension": ".cs",
        "bordes": ["/Infra/", "Program.cs"],
    },
}


def ruta_crudo(lenguaje: str, escenario: str) -> Path:
    return CRUDOS / f"{LENGUAJES[lenguaje]['archivo']}_{escenario}.json"


def ruta_csv(escenario: str) -> Path:
    if escenario == "inexistente":
        return DATOS / "no_existe_este_archivo.csv"
    return DATOS / f"{escenario}.csv"
