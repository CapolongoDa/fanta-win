package it.capoldan.fantawin.exception.config;


import it.capoldan.fantawin.generated.openapi.server.v1.dto.Problem;

public interface Exception {

    /**
     * Ritorna il Problem da tornare al frontend a partire dalle informazioni presenti nell'exception
     * @return Problem contenente le info da usare nella risposta
     */
    Problem getProblem();

    /**
     * Ritorna il codice http da usare nella risposta in base all'exception
     * @return intero contenente il codice http della risposta
     */
    default int getStatus() {
        return getProblem().getStatus();
    }
}
