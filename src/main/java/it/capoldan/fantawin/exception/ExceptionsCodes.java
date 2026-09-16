package it.capoldan.fantawin.exception;

public class ExceptionsCodes {

    protected ExceptionsCodes(){}
    /**
     * codice di errore generico, quando non si sa cosa mettere. Dovrebbe essere usato il meno possibile
     */
    public static final String ERROR_CODE_GENERIC_ERROR = "GENERIC_ERROR";
    public static final String ERROR_CODE_WEB_GENERIC_ERROR = "WEB_GENERIC_ERROR";
    public static final String ERROR_CODE_HTTPRESPONSE_GENERIC_ERROR = "HTTPRESPONSE_GENERIC_ERROR";
    public static final String ERROR_CODE_TOO_MANY_REQUESTS = "GENERIC_TOO_MANY_REQUESTS";
    public static final String ERROR_CODE_GENERIC_INVALIDPARAMETER_DUPLICATED = "GENERIC_INVALIDPARAMETER_DUPLICATED";
    public static final String ERROR_CODE_GENERIC_INVALIDPARAMETER = "GENERIC_INVALIDPARAMETER";
    public static final String ERROR_CODE_NOT_FOUND = "GENERIC_NOT_FOUND";

    /** Rosa che referenzia un playerId assente dall'anagrafica giocatori: incoerenza tra tabelle. */
    public static final String ERROR_CODE_DATA_INTEGRITY = "DATA_INTEGRITY_ERROR";
    /** Rosa senza abbastanza giocatori (o senza portiere disponibile) per completare il calcolo richiesto. */
    public static final String ERROR_CODE_INCOMPLETE_ROSTER = "INCOMPLETE_ROSTER";
    /** Scrittura concorrente su DynamoDB rifiutata dal controllo di versione ottimistico. */
    public static final String ERROR_CODE_CONCURRENT_UPDATE = "CONCURRENT_UPDATE_CONFLICT";
    /** Errore nella comunicazione con un servizio esterno (es. Football-Data.org). */
    public static final String ERROR_CODE_EXTERNAL_SERVICE_ERROR = "EXTERNAL_SERVICE_ERROR";
    /** Dipendenza esterna (DynamoDB/LocalStack, servizio esterno a livello di trasporto) non raggiungibile. */
    public static final String ERROR_CODE_SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
}

