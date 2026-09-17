# 🏆 fanta-win

> **Motore Reattivo Predittivo e Data Analysis per l'Ottimizzazione della Formazione di Fantacalcio**

`fanta-win` è un'applicazione backend in **Java 21** e **Spring Boot 3 (WebFlux)** che calcola la formazione ideale di Fantacalcio (Serie A) per una rosa, incrociando forma recente, storico contro l'avversario, difficoltà del match, disponibilità reale (infortuni/squalifiche/ballottaggi) e turnover per impegni ravvicinati/coppe europee. Espone tutto tramite API REST reattive documentate con OpenAPI 3.0, persiste i dati su DynamoDB e sincronizza calendario/formazioni/statistiche reali da due provider esterni (Football-Data.org e SportMonks).

Progetto personale, ma pensato per essere chiaro da riprendere anche a distanza di tempo: questo README copre sia la logica di dominio sia com'è organizzato il codice, così da poter orientarsi rapidamente in qualunque punto della codebase.

---

## Indice

- [Architettura della Logica di Business](#-architettura-della-logica-di-business)
- [Come è organizzato il codice](#-come-è-organizzato-il-codice)
- [Modello dati (DynamoDB)](#-modello-dati-dynamodb)
- [API](#-api)
- [Integrazioni esterne](#-integrazioni-esterne)
- [Job schedulati](#-job-schedulati)
- [Gestione degli errori](#-gestione-degli-errori)
- [Autenticazione](#-autenticazione)
- [Stack Tecnologico](#️-stack-tecnologico)
- [Installazione e Setup](#-installazione-e-setup)
- [Configurazione](#-configurazione)
- [Build e Test](#-build-e-test)
- [Struttura del progetto](#-struttura-del-progetto)
- [Note e limitazioni note](#-note-e-limitazioni-note)

---

## 🎯 Architettura della Logica di Business

Il motore elabora i dati della rosa e della giornata applicando rigorosamente **5 Moduli Logici Predittivi** (implementati principalmente in `utils/PlayerRatingCalculator.java` e `utils/FormationSelector.java`):

1. **Rating Predittivo Avanzato (FantaRating):**
   * **40%** Stato di forma recente (media fantavoto/voto puro delle ultime 5 partite, pesata via `fantawin.pure-vote-weight`).
   * **30%** Storico contro l'avversario specifico (fantamedia nelle partite passate contro la stessa squadra).
   * **30%** Difficoltà match ed Expected Goals/Assists (xG/xA).
2. **Controllo Infrasettimanali e Turnover:**
   * Calcolo e applicazione dinamica del malus stanchezza e rotazione: **-1.5** per i big (fantamedia recente ≥ `fantawin.big-player-threshold`), **-2.5** per le riserve, se la squadra reale ha impegni europei o infrasettimanali nella finestra di 3 giorni prima/dopo.
3. **Report Medici e Disponibilità Reale:**
   * Rating forzato a **0** per infortunati e squalificati (esclusione tassativa da titolari e panchina).
   * Malus scalari per ballottaggio (55%-75% → **-0.8**), probabile subentrante (<50% → **-2.0**) e giocatori "in dubbio" (**-1.0**).
4. **Confronto Dinamico dei Moduli & Modificatore di Difesa:**
   * Simulazione delle combinazioni sui moduli `3-4-3`, `4-3-3`, `3-5-2` e `4-4-2` (configurabili, vedi `fantawin.formations.*`).
   * **Modificatore di Difesa** (solo su moduli a 4 difensori): portiere + 4 difensori titolari, si scarta il fantavoto più basso del gruppo di 5 e si media il fantavoto dei 4 rimanenti, poi si applica una griglia a step di 0.25 (0 punti sotto 6.0, fino a un massimo di +6 punti a 7.25+).
   * Selezione automatica del modulo che massimizza il punteggio complessivo atteso.
5. **Blindatura della Panchina (Algoritmo Anti-Voto-Zero):**
   * Portiere di riserva: il secondo portiere della stessa squadra reale del titolare, se presente in rosa.
   * Giocatori di movimento ordinati per "certezza del voto" (probabilità di titolarità), poi per FantaRating, per coprire in sicurezza le scommesse rischiose tra i titolari.

---

## 🧭 Come è organizzato il codice

Il progetto segue un approccio **OpenAPI First**: la specifica in `docs/openapi/fanta-win-api-internal.yaml` è la fonte di verità dei contratti REST (request/response, campi obbligatori, vincoli come `maxItems`/`nullable`) e genera automaticamente, in fase di build, le interfacce dei controller e i DTO in `it.capoldan.fantawin.generated.openapi.server.v1.*` (cartella `target/generated-sources`, mai committata). I controller scritti a mano implementano quelle interfacce; non aggiungere mai un DTO a mano per il livello REST, va sempre dichiarato nello YAML.

Pacchetti principali sotto `src/main/java/it/capoldan/fantawin/`:

| Pacchetto | Responsabilità |
|---|---|
| `controller` | Endpoint REST reattivi (`RosterController`, `PredictionController`, `PlayerCatalogController`, `LineupSyncController`, `MatchStatsController`). Nessuna logica di business: validano l'input (via `@Valid`/OpenAPI), delegano al `service` corrispondente, loggano ricevuta/completata/fallita per ogni richiesta. |
| `service` | Logica applicativa: `RosterService` (CRUD rosa, ownership), `LineupPredictionService` (orchestrazione dei 5 moduli), `PlayerCatalogImportService`/`PlayerCatalogResolverService` (anagrafica giocatori), `PlayerFormAggregationService` (aggregazione statistiche storiche), `OfficialLineupSyncService`/`RealMatchStatsSyncService`/`RealMatchStatsScheduler` (sync da SportMonks), `FootballDataSyncJob`/`FootballDataTeamIdResolver`/`MatchFixtureLookupService` (sync da Football-Data.org), `PlayerMatchStatImportService`/`MatchStatsCsvExporter` (import/export CSV voti). |
| `utils` | Algoritmi puri, senza I/O: `PlayerRatingCalculator` (FantaRating, moduli 1-3), `FormationSelector` (modulo 4, scelta del modulo migliore e panchina), `FantaVotoCalculator` (formula fantavoto da voto+bonus/malus), `RoleParser` (alias ruoli P/D/C/A, POR/DIF/CEN/ATT, CC/DC), `LineupFormationLogger` (stampa la formazione nei log). |
| `middleware/dao/dynamo` | Accesso a DynamoDB via Enhanced Client asincrono, un DAO per tabella (`PlayerDao`, `RosterDao`, `AvailabilityReportDao`, `FixtureDao`, `PlayerMatchStatDao`, `PlayerCatalogDao`) più `BaseDao` con le operazioni CRUD comuni, e i rispettivi `*EntityMapper` per la conversione DTO ↔ item DynamoDB. |
| `middleware/externalclient` | Client verso le API esterne (`FootballDataClient`, `SportMonksClient`), con retry/backoff su 429/5xx e mappatura degli errori HTTP. |
| `mapper` | Conversioni tra DTO interni e DTO OpenAPI generati (`RosterAggregationMapper`, `RosterRequestMapper`). |
| `exception` | Gerarchia di eccezioni di dominio (`NotFoundException`, `ValidationException`, `ForbiddenException`, `DataIntegrityException`, `IncompleteRosterException`, `ConcurrentUpdateException`, `ExternalServiceException`, `ServiceUnavailableException`, ecc.) e i loro mapper verso il formato `Problem`/`ProblemError` (RFC 7807-like) restituito dalle API. |
| `security` | `AuthenticatedUserProvider`: astrae "chi sta chiamando" leggendo il claim `sub` del JWT quando l'autenticazione Cognito è attiva, altrimenti restituisce sempre `Optional.empty()` (vedi [Autenticazione](#-autenticazione)). |
| `config` | Configurazione Spring: `FantaWinConfigs` (proprietà applicative), `SecurityConfig` (Cognito/JWT o `permitAll()`), `AwsServicesClientsConfig` (client DynamoDB), `RestTemplateFactory`/`RestTemplateRetryable` (client HTTP con retry verso i provider esterni), `SchedulingConfig` (pool dedicato ai job `@Scheduled`), `FootballDataTeamsConfig`/`SportMonksTeamsConfig` (override manuali teamId). |
| `dto` | Modello interno (non generato), usato tra service/DAO: `PlayerDto`, `RosterDto`, `RosterPlayerDto`, `AvailabilityReportDto`, `AvailabilityStatus`, `FixtureDto`, `PlayerMatchStatDto`, `PlayerCatalogDto`, `Role`, `PlayerCalculation`. |

**Come leggere il flusso di una richiesta tipica** (es. `POST /fanta-private/predict/lineup/{rosterId}`): `PredictionController` valida il body → `LineupPredictionService` carica la rosa da `RosterDao`, per ogni giocatore aggrega disponibilità (`AvailabilityReportDao`), fixture (`FixtureDao`) e storico voti (`PlayerFormAggregationService`) → `PlayerRatingCalculator` calcola il FantaRating di ognuno → `FormationSelector` sceglie il modulo migliore e costruisce titolari+panchina → la risposta passa per `RosterAggregationMapper` verso il DTO OpenAPI e viene loggata in formato "ad albero" da `LineupFormationLogger`.

---

## 🗄️ Modello dati (DynamoDB)

Sei tabelle, tutte con billing `PAY_PER_REQUEST` (vedi `src/test/resources/testcontainers/init.sh` per la creazione in locale):

| Tabella | Chiave | Contenuto |
|---|---|---|
| `Players` | PK `playerId` (S) | Anagrafica dei giocatori effettivamente in una rosa: nome, squadra reale, ruolo. |
| `Roster` | PK `rosterId` (S) | Una rosa fantacalcistica: nome squadra, elenco `playerId`+ruolo fantacalcistico, `ownerId` (opzionale, vedi [Autenticazione](#-autenticazione)). |
| `AvailabilityReports` | PK `playerId` (S) | Stato di disponibilità reale: `status` (OK/INFORTUNATO/SQUALIFICATO/IN_DUBBIO/BALLOTTAGGIO), `startingProbability`. |
| `Fixtures` | PK `matchday` (N), SK `realTeam` (S) | Prossimo avversario reale per squadra e giornata, difficoltà match, flag coppe europee/turni infrasettimanali prima/dopo. Popolata solo da `FootballDataSyncJob`. |
| `PlayerMatchStats` | PK `playerId` (S), SK `matchDay` (N) | Storico voti/bonus/malus per giornata: voto, fantavoto (sempre ricalcolato da `FantaVotoCalculator`), gol, assist, ammonizioni, espulsioni, gol subiti, xG/xA. |
| `PlayerCatalog` | PK `catalogId` (S) | Anagrafica completa Serie A (tutte le quotazioni fantacalcio.it, non solo i giocatori in rosa), usata per risolvere nome+ruolo → id univoco quando si aggiunge un giocatore alla rosa. |

I nomi tabella sono configurabili (`fantawin.dao.*`, vedi [Configurazione](#-configurazione)) ma per default coincidono con quelli sopra.

---

## 📡 API

Tutti gli endpoint sono sotto `/fanta-private/*` (nessun prefisso versione, vedi [Autenticazione](#-autenticazione) per lo stato attuale dei controlli d'accesso). Contratti completi, esempi e codici di errore nello spec OpenAPI e nella collection Postman (`scripts/postman/FantaWin.postman_collection.json`).

**Rosa (`RosterController`)**

| Metodo | Path | Descrizione |
|---|---|---|
| GET | `/fanta-private/rosters` | Elenca le rose dell'utente chiamante. |
| GET | `/fanta-private/getRoster/{rosterId}?matchDay=` | Rosa completa con FantaRating calcolato per ogni giocatore, per la giornata indicata. |
| POST | `/fanta-private/addPlayer/{rosterId}?teamName=` | Aggiunge/aggiorna un giocatore nella rosa (body minimale: id, name, realTeam, position — vedi `AddOrUpdatePlayerRequest`). Crea la rosa se non esiste ancora. |
| POST | `/fanta-private/addPlayers` | Bulk-add: nome+ruolo risolti contro `PlayerCatalog` (max 25 elementi per richiesta). |
| DELETE | `/fanta-private/{playerId}/{rosterId}` | Rimuove un giocatore dalla rosa (non tocca l'anagrafica `Players`). |
| DELETE | `/fanta-private/rosters/{rosterId}` | Elimina l'intera rosa. |

**Previsione formazione (`PredictionController`)**

| Metodo | Path | Descrizione |
|---|---|---|
| POST | `/fanta-private/predict/lineup/{rosterId}` | Calcola la formazione ottimale applicando i 5 moduli logici (vedi sopra). |

**Anagrafica giocatori (`PlayerCatalogController`)**

| Metodo | Path | Descrizione |
|---|---|---|
| POST | `/fanta-private/playercatalog/import` | Import massivo da CSV (`multipart/form-data`, campo `file`) delle quotazioni Serie A. |
| POST | `/fanta-private/playercatalog` | Aggiunge una singola voce (nome, ruolo, squadra) con id univoco generato automaticamente. |

**Sync formazioni ufficiali (`LineupSyncController`)**

| Metodo | Path | Descrizione |
|---|---|---|
| POST | `/fanta-private/lineups/sync-official/{rosterId}` | Legge da SportMonks la formazione ufficiale appena comunicata e aggiorna `startingProbability` dei giocatori in rosa. |

**Statistiche di giornata (`MatchStatsController`)**

| Metodo | Path | Descrizione |
|---|---|---|
| POST | `/fanta-private/matchstats/import` | Import da CSV (`multipart/form-data`) di voti/bonus/malus di giornata. |
| GET | `/fanta-private/matchstats/fixture-lookup/{realTeam}/{matchday}` | Aiuto per compilare il CSV: recupera vero avversario/casa-trasferta/data da Football-Data.org. |
| POST | `/fanta-private/matchstats/sync-real/{rosterId}/{matchday}` | Sincronizza da SportMonks gli eventi oggettivi (gol/assist/cartellini/gol subiti) di una giornata già disputata. |

A servizio avviato, la Swagger UI è disponibile su `http://localhost:<server.port>/swagger-ui.html` (porta di default in locale: `8085`, vedi `config/application.properties`).

---

## 🌍 Integrazioni esterne

| Provider | Uso | Client / Resolver |
|---|---|---|
| **[Football-Data.org](https://www.football-data.org/)** v4 | Calendario reale (prossimo avversario, casa/trasferta, date), usato per popolare `Fixtures` e per il modulo "difficoltà match". Piano free: 10 richieste/minuto, per questo `FootballDataSyncJob` serializza le chiamate con una pausa di sicurezza (~6.5s) tra una e l'altra. | `FootballDataClient` + `FootballDataTeamIdResolver` (risolve i teamId per nome via `GET /competitions/SA/teams`, con cache in-memory e override manuali in `football-data.team-ids.*`). |
| **[SportMonks](https://www.sportmonks.com/)** v3 Football | Formazioni ufficiali pre-partita (`OfficialLineupSyncService`) e statistiche oggettive post-partita — gol/assist/cartellini/gol subiti (`RealMatchStatsSyncService`). | `SportMonksClient` + `SportMonksTeamIdResolver` (risolve i teamId via `GET /teams/search/{nome}`, con cache e override in `sportmonks.team-ids.*`). |

Entrambi i client (`config/springbootcfg/RestTemplateFactory`, `RestTemplateRetryable`) applicano retry con backoff esponenziale sui codici 429/5xx e sugli errori di rete/timeout.

Le API key **non vanno mai committate**: si passano come variabili d'ambiente (`FOOTBALL_DATA_API_KEY`, `SPORTMONKS_API_KEY`) referenziate da `config/application.properties`, oppure risolte da AWS Secrets Manager in ambienti dove è configurato (`spring.config.import=optional:aws-secretsmanager:fantawin/api-keys`).

---

## ⏱️ Job schedulati

| Job | Default cron | Cosa fa |
|---|---|---|
| `FootballDataSyncJob.syncAllTeams` | ogni giorno alle 6:00 (`fantawin.football-data.sync-cron`) | Sincronizza `Fixtures` per tutte le squadre reali derivate dall'anagrafica `Players` (non serve una whitelist). |
| `RealMatchStatsScheduler.syncLatestFinishedMatchdayForAllRosters` | ogni lunedì alle 6:00 (`fantawin.match-stats.sync-cron`) | Sincronizza automaticamente da SportMonks l'ultima giornata segnata come "finished", per tutte le rose esistenti. |

Entrambi girano su un `TaskScheduler` dedicato con pool di 3 thread (`fantawin.scheduling.pool-size`) invece del singolo thread condiviso di default di Spring, per evitare che due cron coincidenti si mettano in coda.

---

## ⚠️ Gestione degli errori

Tutte le risposte di errore seguono lo schema `Problem`/`ProblemError` (in stile RFC 7807) definito in `docs/openapi/problem-internal.yaml`. Il flusso è centralizzato in `exception/ExceptionHelper`, che smista ogni eccezione verso lo status HTTP e il codice giusti:

| Eccezione | Status | Quando |
|---|---|---|
| `NotFoundException` | 404 | Rosa/giocatore/risorsa non trovata. |
| `ValidationException` | 400 | Input non valido rilevato a livello di business logic (oltre a quanto già coperto dallo spec OpenAPI). |
| `ForbiddenException` | 403 | La rosa appartiene a un altro utente (ownership). |
| `IdConflictException` | 409 | Es. voce duplicata in `PlayerCatalog`. |
| `ConcurrentUpdateException` | 409 | Scrittura concorrente rifiutata dal controllo di versione ottimistico su DynamoDB. |
| `IncompleteRosterException` | — | Rosa insufficiente per comporre un modulo (es. nessun portiere disponibile). |
| `DataIntegrityException` | 500 | Incoerenza tra tabelle DynamoDB (es. un `playerId` in rosa ma assente da `Players`) — segnalata separatamente da un generico errore interno. |
| `ExternalServiceException` / `ServiceUnavailableException` | 502/503 | Football-Data.org o SportMonks non raggiungibili dopo i retry, o DynamoDB non disponibile. |
| `InvalidImportFileException` | 400 | File CSV mancante, vuoto o non leggibile. |

La validazione "di forma" (campi obbligatori, `maxItems`, tipi) è demandata il più possibile allo spec OpenAPI (bean validation generata automaticamente su `@Valid @RequestBody`); il codice a mano nei service si occupa solo di regole di business che l'OpenAPI non può esprimere (es. verificare che una rosa non superi gli slot per ruolo, o che un giocatore risolto da `PlayerCatalogResolverService` non sia ambiguo).

---

## 🔐 Autenticazione

Il modello di ownership è già implementato (`RosterDto.ownerId`, `RosterService.enforceOwnership`, un utente → 1..N rose) ma **l'autenticazione è disattivata di default**: `SecurityConfig` applica Cognito Hosted UI (JWT/OAuth2 resource server) solo se `spring.security.oauth2.resourceserver.jwt.issuer-uri` è valorizzato; altrimenti tutte le richieste passano (`permitAll()`) e `AuthenticatedUserProvider.currentUserId()` restituisce sempre `Optional.empty()` — le rose senza `ownerId` (come quelle seminate da `src/test/resources/testcontainers/init.sh`) restano quindi aperte a chiunque, comportamento corretto per lo sviluppo locale ma **da non portare mai così in produzione**.

Per attivare l'autenticazione reale, valorizzare `COGNITO_ISSUER_URI` con l'issuer del proprio User Pool Cognito (`https://cognito-idp.<region>.amazonaws.com/<userPoolId>`) e passare `Authorization: Bearer <JWT>` nelle richieste.

---

## 🛠️ Stack Tecnologico

* **Java:** 21
* **Framework:** Spring Boot 3.3.4 (`spring-boot-starter-webflux`, `spring-boot-starter-validation`, `spring-boot-starter-oauth2-resource-server`)
* **Cloud & Storage:** AWS SDK v2 (`dynamodb-enhanced`, client asincrono Netty), Spring Cloud AWS (credenziali/regione/Secrets Manager)
* **API Documentation & First:** OpenAPI 3.0 / SpringDoc OpenAPI (`springdoc-openapi-starter-webflux-ui`), generazione codice con `openapi-generator-maven-plugin` 7.4.0
* **Altro:** Lombok, ModelMapper, OpenCSV (import/export CSV), Jackson (incl. `jackson-databind-nullable` e supporto YAML), Logstash Logback Encoder (log strutturati)
* **Build Tool:** Maven
* **Testing:** JUnit 5, Testcontainers (LocalStack), MockServer, JaCoCo

---

## 🚀 Installazione e Setup

Guida completa per portare su l'ambiente di sviluppo locale da zero.

### 1. Cosa installare

| Strumento | Versione | Uso |
|---|---|---|
| **JDK** | 21+ | Compilazione ed esecuzione dell'app. |
| **Apache Maven** | 3.8+ | Build, generazione codice da OpenAPI, test. |
| **Docker** e **Docker Compose** | recenti | Avvio di LocalStack (DynamoDB locale) e di `dynamodb-admin`. |
| **AWS CLI v2** | qualunque | Creazione tabelle e seed dati via `src/test/resources/testcontainers/init.sh`. |
| **jq** | qualunque | Solo per il seed dell'anagrafica `PlayerCatalog` da CSV in `init.sh` (facoltativo: senza `jq` lo script salta quel passo e resta possibile importare via API). |
| Un client REST (Postman, Insomnia, `curl`) | — | Per chiamare le API: è pronta una collection in `scripts/postman/FantaWin.postman_collection.json`. |

Account/chiavi API esterne (facoltative per avviare l'app, necessarie per far funzionare le sync reali):

* **[Football-Data.org](https://www.football-data.org/client/register)** — piano free sufficiente (10 richieste/minuto), registrazione gratuita per ottenere il token.
* **[SportMonks](https://www.sportmonks.com/)** — richiede un piano che includa la Serie A e le fixture con `lineups`/`events` (verificare la copertura del proprio piano).

### 2. Clona il repository

```bash
git clone https://github.com/CapolongoDa/fanta-win.git
cd fanta-win
```

### 3. Avvia l'infrastruttura locale (DynamoDB via LocalStack)

```bash
docker-compose up -d
```

Questo avvia:
* **LocalStack** (`localhost:4566`) — solo DynamoDB (e Secrets Manager, opzionale) è l'unico servizio AWS usato da FantaWin.
* **dynamodb-admin** (`http://localhost:8001`) — interfaccia web per ispezionare le tabelle.

### 4. Configura `config/application.properties`

Il file `config/application.properties` è già presente nel repo (fuori da `src/main/resources` apposta, per restare fuori dal jar impacchettato) e punta già a LocalStack senza altre modifiche. Prima di avviare l'app, esporta le chiavi delle API esterne come variabili d'ambiente (se non le hai ancora, l'app parte comunque: le sole chiamate a Football-Data.org/SportMonks falliranno con 401/403):

```bash
export FOOTBALL_DATA_API_KEY=xxxxxxxx
export SPORTMONKS_API_KEY=xxxxxxxx
```

Non serve valorizzare `COGNITO_ISSUER_URI` in locale: lasciandolo vuoto l'autenticazione resta disattivata (vedi [Autenticazione](#-autenticazione)).

### 5. Crea le tabelle e semina i dati di sviluppo

```bash
./scripts/local/init.sh
```

Crea le sei tabelle DynamoDB e popola `Players`, `Roster` (la rosa "AS Junior."), `PlayerMatchStats` (voti recenti), `AvailabilityReports` (infortuni/ballottaggi) e, se `jq` è installato, l'intero `PlayerCatalog` da CSV. La tabella `Fixtures` resta volutamente vuota: la popola solo il vero `FootballDataSyncJob` con dati reali (altrimenti diventerebbe subito vecchia/fuorviante).

### 6. Compila e avvia l'app

```bash
mvn clean compile   # genera i DTO/interfacce da OpenAPI e compila
mvn spring-boot:run
```

L'app parte sulla porta configurata in `config/application.properties` (default `8085`). Swagger UI: `http://localhost:8085/swagger-ui.html`.

### 7. Prova le API

Importa `scripts/postman/FantaWin.postman_collection.json` in Postman (o equivalente) e prova ad esempio `GET /fanta-private/rosters` o `GET /fanta-private/getRoster/as-junior?matchDay=4`.

### Spegnere l'ambiente

```bash
docker-compose down       # ferma LocalStack e dynamodb-admin
docker-compose down -v    # come sopra, ma azzera anche i dati persistiti (volume fantawin-localstack-data)
```

## Test di integrazione (Testcontainers + LocalStack)

Richiedono un Docker engine attivo e raggiungibile sul socket standard.

- **macOS (Docker Desktop):** Settings → Advanced → abilita
  "Allow the default Docker socket to be used". Poi `docker info` deve rispondere.
- **Windows (Docker Desktop):** usa il backend WSL2 (Settings → General) e avvia
  Docker Desktop. Nessuna variabile d'ambiente necessaria.
- **Linux / WSL2:** Docker in esecuzione; `/var/run/docker.sock` disponibile.

NON impostare `DOCKER_HOST` a un path fisso: Testcontainers rileva da solo
il socket (unix su Mac/Linux, named pipe su Windows). Un valore hardcoded
funzionerebbe solo su un OS.

---

## ⚙️ Configurazione

Le proprietà applicative principali (`fantawin.*`, in `config/application.properties`, prefisso mappato su `FantaWinConfigs`):

| Proprietà | Default locale | Descrizione |
|---|---|---|
| `fantawin.default-match-difficulty` | `5.0` | Difficoltà match neutra usata quando manca il dato reale di classifica. |
| `fantawin.big-player-threshold` | `6.5` | Soglia di fantamedia recente sopra la quale un giocatore è considerato "big" (malus turnover -1.5 invece di -2.5). |
| `fantawin.pure-vote-weight` | `0.8` | Peso del voto puro (0.0-1.0) rispetto al fantavoto nella componente forma recente. |
| `fantawin.dao.*` | `Players`, `PlayerMatchStats`, `Fixtures`, `AvailabilityReports`, `Roster`, `PlayerCatalog` | Nomi delle tabelle DynamoDB. |
| `fantawin.football-data-base-url` / `fantawin.football-data-api-key` | `https://api.football-data.org/v4` / `${FOOTBALL_DATA_API_KEY:}` | Base URL e token Football-Data.org. |
| `football-data.team-ids.<Nome>` | — | Override manuale del teamId Football-Data.org per una squadra, se la risoluzione automatica per nome fallisce. |
| `fantawin.sportmonks-base-url` / `fantawin.sportmonks-api-key` | `https://api.sportmonks.com/v3/football` / `${SPORTMONKS_API_KEY:}` | Base URL e token SportMonks. |
| `fantawin.sportmonks-serie-a-league-id` / `fantawin.sportmonks-serie-a-season-id` | — | Id numerici lega/stagione su SportMonks, da verificare sul proprio account (dashboard o endpoint `/leagues`, `/seasons`). |
| `sportmonks.team-ids.<Nome>` | — | Override manuale del teamId SportMonks per una squadra. |
| `fantawin.match-stats-export-dir` | `./data/exports` | Cartella dove viene scritto l'export CSV per-rosterId dopo ogni sync statistiche reali. |
| `fantawin.rest-template.*` | retry 3, connect 5000ms, read 10000ms | Timeout e retry per i client HTTP verso i provider esterni. |
| `fantawin.scheduling.pool-size` | `3` | Thread dedicati ai job `@Scheduled`. |
| `fantawin.formations.<modulo>` | `3-4-3`, `4-3-3`, `3-5-2`, `4-4-2` | Moduli candidati valutati da `FormationSelector` (formato: difensori,centrocampisti,attaccanti). |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | vuoto | Issuer Cognito; vuoto = autenticazione disattivata. |
| `aws.endpoint-url` / `aws.region-code` / `aws.access-key-id` / `aws.secret-access-key` | LocalStack (`http://localhost:4566`, `us-east-1`, `test`/`test`) | Configurazione del client DynamoDB. |

---

## 🧪 Build e Test

```bash
# Genera il codice da OpenAPI e compila
mvn clean compile

# Esegue i test unitari e d'integrazione (Testcontainers avvia LocalStack, MockServer simula i provider esterni), con report JaCoCo
mvn clean test
```

---

## 📝 Struttura del progetto

```text
fanta-win/
├── docker-compose.yml                     # LocalStack (DynamoDB) + dynamodb-admin per lo sviluppo locale
├── config/
│   ├── application.properties             # Configurazione locale (fuori dal jar, vedi Installazione)
│   └── logback-local.xml
├── docs/
│   └── openapi/
│       ├── fanta-win-api-internal.yaml    # Spec OpenAPI 3.0: fonte di verità dei contratti REST
│       └── problem-internal.yaml          # Schema degli errori (Problem/ProblemError)
├── scripts/
│   ├── local/
│   │   ├── init.sh                        # Crea le tabelle DynamoDB e semina i dati di sviluppo
│   │   └── data/                          # CSV di seed (anagrafica Serie A, voti di giornata)
│   ├── postman/
│   │   └── FantaWin.postman_collection.json
│   └── aws/cfn/                           # Template CloudFormation per il deploy (storage, microservizio)
├── src/
│   ├── main/
│   │   ├── java/it/capoldan/fantawin/
│   │   │   ├── controller/                # Endpoint REST (vedi tabella API sopra)
│   │   │   ├── service/                   # Logica applicativa e orchestrazione
│   │   │   ├── utils/                     # Algoritmi puri (FantaRating, scelta modulo, ecc.)
│   │   │   ├── middleware/
│   │   │   │   ├── dao/dynamo/            # DAO ed entity mapper per DynamoDB
│   │   │   │   └── externalclient/        # Client verso Football-Data.org e SportMonks
│   │   │   ├── mapper/                    # DTO interni <-> DTO OpenAPI generati
│   │   │   ├── exception/                 # Eccezioni di dominio e mapper verso Problem/ProblemError
│   │   │   ├── security/                  # AuthenticatedUserProvider
│   │   │   ├── config/                    # Configurazione Spring (AWS, sicurezza, scheduling, client HTTP)
│   │   │   └── dto/                       # Modello interno (non generato)
│   │   └── resources/                     # application.properties (bootstrap), logback-base.xml
│   └── test/                              # Test unitari e di integrazione (LocalStack, MockServer)
└── pom.xml                                # Configurazione Maven (build, plugin openapi-generator, ecc.)
```

---

## 📌 Note e limitazioni note

* **Autenticazione disattivata di default** — vedi [Autenticazione](#-autenticazione). Non esporre l'app pubblicamente senza prima configurare `COGNITO_ISSUER_URI`.
* **`Fixtures` non va seminata a mano** — è responsabilità esclusiva di `FootballDataSyncJob` (dati reali), seminarla manualmente la renderebbe subito vecchia/fuorviante.
* **Match Difficulty Index è un placeholder** — il vero indice richiederebbe i dati di classifica (endpoint `standings`, non ancora integrato): oggi si usa `fantawin.default-match-difficulty` come valore neutro fisso.
* **Rate limit dei provider esterni** — piano free di Football-Data.org: 10 richieste/minuto (già gestito da `FootballDataSyncJob` serializzando le chiamate); verificare la copertura del proprio piano SportMonks per fixture con `lineups`/`events` inclusi.
* **CSV come fonte di verità per i voti** — nessun provider gratuito fornisce voti/pagelle italiane: `PlayerMatchStatImportService` si aspetta un CSV compilato a mano (o esportato da fantacalcio.it), con il fantavoto sempre ricalcolato server-side da `FantaVotoCalculator` a partire da voto+bonus/malus (la colonna "fantavoto" nel CSV è solo un controllo di coerenza, non la fonte usata).

---

## 📄 Licenza

Progetto ad uso personale. Tutti i diritti riservati.
