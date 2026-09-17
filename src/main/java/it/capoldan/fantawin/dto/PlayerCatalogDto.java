package it.capoldan.fantawin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Anagrafica di riferimento di TUTTI i calciatori di Serie A (fonte: quotazioni fantacalcio.it,
 * importate via CSV in PlayerCatalogImportService), usata per risolvere nome+ruolo in un id univoco
 * quando si aggiunge un giocatore alla rosa (vedi RosterService#addPlayersBulk e
 * PlayerCatalogResolverService). NON e' la rosa dell'utente e NON va confusa con la tabella Players
 * (anagrafica dei soli giocatori effettivamente in una rosa).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerCatalogDto {

    /** Id numerico originale delle quotazioni fantacalcio.it, riusato come playerId in Players/Roster quando il giocatore viene aggiunto. */
    private String catalogId;

    private String nome;

    private Role ruolo;

    private String squadra;
}
