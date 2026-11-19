package torneos.infra;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import torneos.modelo.RegistroError;
import torneos.modelo.Resultados.LecturaCsv;
import torneos.modelo.Resultados.LineaCsv;
import torneos.modelo.TipoError;

/** BORDE de entrada: único lugar que toca el sistema de archivos para leer. */
public final class CargadorCsv {
    private CargadorCsv() { }

    public static LecturaCsv leer(String ruta) {
        try {
            List<String> lineas = Files.readAllLines(Path.of(ruta), StandardCharsets.UTF_8);
            return new LecturaCsv(IntStream.range(0, lineas.size())
                    .mapToObj(i -> new LineaCsv(i + 1, lineas.get(i)))
                    .toList(), List.of());
        } catch (NoSuchFileException e) {
            return new LecturaCsv(List.of(), List.of(new RegistroError(TipoError.ARCHIVO_INEXISTENTE,
                    "No existe el archivo: " + ruta)));
        } catch (IOException e) {
            return new LecturaCsv(List.of(), List.of(new RegistroError(TipoError.ARCHIVO_INEXISTENTE,
                    "No se pudo leer el archivo: " + ruta + " (" + e.getMessage() + ")")));
        }
    }
}
