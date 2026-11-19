package torneos.modelo;

/** Resultado del matchmaking: una pareja que juega o un BYE (pase directo). */
public sealed interface Emparejamiento permits Emparejamiento.Pareja, Emparejamiento.Bye {

    record Pareja(Jugador a, Jugador b) implements Emparejamiento { }

    record Bye(Jugador jugador) implements Emparejamiento { }
}
