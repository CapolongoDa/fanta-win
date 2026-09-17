#!/usr/bin/env bash
#
# Crea le tabelle DynamoDB dell'app e le popola con dati di sviluppo locale: la rosa reale
# "AS Junior." di Daniele (25 giocatori), i suoi voti recenti (giornate 2-4 stagione 2026/27,
# fonte fantacalcio.it) e lo stato disponibilita' (infortuni/ballottaggi) discusso in chat.
# Gli stessi identificativi/dati usati in src/test/java/.../LineupPredictionIntegrationTest.java,
# cosi' i due si tengono allineati.
#
# NON semina la tabella Fixtures: quella la scrive solo il vero FootballDataSyncJob chiamando
# football-data.org (prossimo avversario reale) - seminarla a mano qui la renderebbe
# immediatamente vecchia e fuorviante.
#
# Prerequisiti:
#   - AWS CLI v2 installato
#   - un endpoint DynamoDB locale in ascolto (default: LocalStack su http://localhost:4566) -
#     coerente con aws.endpoint-url in config/application.properties. Il modo piu' semplice per
#     avviarlo e' `docker-compose up -d` (vedi docker-compose.yml nella root del progetto), che
#     porta su anche dynamo-admin (http://localhost:8001) per ispezionare le tabelle.
#
# Uso:
#   docker-compose up -d && ./scripts/local/init.sh
#   FANTAWIN_DYNAMO_ENDPOINT=http://localhost:4566 ./scripts/local/init.sh   # esplicito
#
set -euo pipefail

ENDPOINT_URL="${FANTAWIN_DYNAMO_ENDPOINT:-http://localhost:4566}"
REGION="${FANTAWIN_AWS_REGION:-us-east-1}"
export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-test}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-test}"

# Nomi tabella: devono restare allineati a fantawin.dao.* in config/application.properties
PLAYERS_TABLE="Players"
MATCH_STATS_TABLE="PlayerMatchStats"
FIXTURES_TABLE="Fixtures"
AVAILABILITY_TABLE="AvailabilityReports"
ROSTER_TABLE="Roster"
PLAYER_CATALOG_TABLE="PlayerCatalog"

ddb() {
  aws dynamodb --endpoint-url "$ENDPOINT_URL" --region "$REGION" "$@"
}

table_exists() {
  ddb describe-table --table-name "$1" >/dev/null 2>&1
}

create_table() {
  local table_name="$1" pk_name="$2" pk_type="$3" sk_name="${4:-}" sk_type="${5:-}"

  if table_exists "$table_name"; then
    echo "[skip] tabella '$table_name' esiste gia'"
    return
  fi

  local attr_defs key_schema
  if [ -n "$sk_name" ]; then
    attr_defs="AttributeName=$pk_name,AttributeType=$pk_type AttributeName=$sk_name,AttributeType=$sk_type"
    key_schema="AttributeName=$pk_name,KeyType=HASH AttributeName=$sk_name,KeyType=RANGE"
  else
    attr_defs="AttributeName=$pk_name,AttributeType=$pk_type"
    key_schema="AttributeName=$pk_name,KeyType=HASH"
  fi

  echo "[create] tabella '$table_name' (PK=$pk_name${sk_name:+, SK=$sk_name})"
  ddb create-table \
    --table-name "$table_name" \
    --attribute-definitions $attr_defs \
    --key-schema $key_schema \
    --billing-mode PAY_PER_REQUEST >/dev/null
  ddb wait table-exists --table-name "$table_name"
}

echo "=== 1) Creazione tabelle su $ENDPOINT_URL ==="
create_table "$PLAYERS_TABLE" playerId S
create_table "$MATCH_STATS_TABLE" playerId S matchday N
create_table "$FIXTURES_TABLE" matchday N realTeam S
create_table "$AVAILABILITY_TABLE" playerId S
create_table "$ROSTER_TABLE" rosterId S
# PlayerCatalog resta vuota qui: 500+ giocatori, popolata via POST /fanta-private/playercatalog/import
# (vedi Postman "Player Catalog > Import Player Catalog (CSV)") col CSV esportato dalle quotazioni
# fantacalcio.it, non seminata a mano come le altre tabelle.
create_table "$PLAYER_CATALOG_TABLE" catalogId S

# ---------------------------------------------------------------------------------------------
# 2) Anagrafica giocatori (Players) - rosa reale "AS Junior."
# ---------------------------------------------------------------------------------------------

put_player() {
  local player_id="$1" name="$2" real_team="$3" role="$4"
  ddb put-item --table-name "$PLAYERS_TABLE" --item '{
    "playerId": {"S": "'"$player_id"'"},
    "name": {"S": "'"$name"'"},
    "realTeam": {"S": "'"$real_team"'"},
    "role": {"S": "'"$role"'"},
    "active": {"BOOL": true},
    "version": {"N": "1"}
  }' >/dev/null
}

echo "=== 2) Seed $PLAYERS_TABLE ==="
put_player okoye        "Okoye"        "Udinese"  POR
put_player perri         "Perri"        "Torino"   POR
put_player skorupski     "Skorupski"    "Bologna"  POR
put_player bremer        "Bremer"       "Juventus" DIF
put_player comuzzo       "Comuzzo"      "Torino"   DIF
put_player dilorenzo     "Di Lorenzo"   "Napoli"   DIF
put_player doekhi        "Doekhi"       "Lazio"    DIF
put_player molina        "Molina"       "Roma"     DIF
put_player ndicka        "Ndicka"       "Roma"     DIF
put_player ostigard      "Ostigard"     "Genoa"    DIF
put_player spinazzola    "Spinazzola"   "Napoli"   DIF
put_player barella       "Barella"      "Inter"    CEN
put_player ederson       "Ederson"      "Atalanta" CEN
put_player ekkelenkamp   "Ekkelenkamp"  "Udinese"  CEN
put_player kone          "Kone"         "Roma"     CEN
put_player mctominay     "McTominay"    "Napoli"   CEN
put_player modric        "Modric"       "Milan"    CEN
put_player moreira       "Moreira"      "Milan"    CEN
put_player vlasic        "Vlasic"       "Torino"   CEN
put_player davis         "Davis"        "Udinese"  ATT
put_player deketelaere   "De Ketelaere" "Atalanta" ATT
put_player lauriente     "Laurientè"    "Sassuolo" ATT
put_player pinamonti     "Pinamonti"    "Lazio"    ATT
put_player simeone       "Simeone"      "Torino"   ATT
put_player soule         "Soulè"        "Roma"     ATT
echo "25 giocatori inseriti."

# ---------------------------------------------------------------------------------------------
# 3) Rosa (Roster) - un solo item con la lista completa dei 25 giocatori
# ---------------------------------------------------------------------------------------------

echo "=== 3) Seed $ROSTER_TABLE (rosterId=as-junior) ==="
ROSTER_PLAYERS='[
  {"M":{"playerId":{"S":"okoye"},"fantasyRole":{"S":"POR"}}},
  {"M":{"playerId":{"S":"perri"},"fantasyRole":{"S":"POR"}}},
  {"M":{"playerId":{"S":"skorupski"},"fantasyRole":{"S":"POR"}}},
  {"M":{"playerId":{"S":"bremer"},"fantasyRole":{"S":"DIF"}}},
  {"M":{"playerId":{"S":"comuzzo"},"fantasyRole":{"S":"DIF"}}},
  {"M":{"playerId":{"S":"dilorenzo"},"fantasyRole":{"S":"DIF"}}},
  {"M":{"playerId":{"S":"doekhi"},"fantasyRole":{"S":"DIF"}}},
  {"M":{"playerId":{"S":"molina"},"fantasyRole":{"S":"DIF"}}},
  {"M":{"playerId":{"S":"ndicka"},"fantasyRole":{"S":"DIF"}}},
  {"M":{"playerId":{"S":"ostigard"},"fantasyRole":{"S":"DIF"}}},
  {"M":{"playerId":{"S":"spinazzola"},"fantasyRole":{"S":"DIF"}}},
  {"M":{"playerId":{"S":"barella"},"fantasyRole":{"S":"CEN"}}},
  {"M":{"playerId":{"S":"ederson"},"fantasyRole":{"S":"CEN"}}},
  {"M":{"playerId":{"S":"ekkelenkamp"},"fantasyRole":{"S":"CEN"}}},
  {"M":{"playerId":{"S":"kone"},"fantasyRole":{"S":"CEN"}}},
  {"M":{"playerId":{"S":"mctominay"},"fantasyRole":{"S":"CEN"}}},
  {"M":{"playerId":{"S":"modric"},"fantasyRole":{"S":"CEN"}}},
  {"M":{"playerId":{"S":"moreira"},"fantasyRole":{"S":"CEN"}}},
  {"M":{"playerId":{"S":"vlasic"},"fantasyRole":{"S":"CEN"}}},
  {"M":{"playerId":{"S":"davis"},"fantasyRole":{"S":"ATT"}}},
  {"M":{"playerId":{"S":"deketelaere"},"fantasyRole":{"S":"ATT"}}},
  {"M":{"playerId":{"S":"lauriente"},"fantasyRole":{"S":"ATT"}}},
  {"M":{"playerId":{"S":"pinamonti"},"fantasyRole":{"S":"ATT"}}},
  {"M":{"playerId":{"S":"simeone"},"fantasyRole":{"S":"ATT"}}},
  {"M":{"playerId":{"S":"soule"},"fantasyRole":{"S":"ATT"}}}
]'
# ownerId volutamente omesso: RosterService tratta una rosa senza ownerId come "non reclamata" -
# nessun controllo di ownership finche' Cognito non e' configurato (vedi SecurityConfig), quindi
# in locale resta apribile/modificabile come oggi indipendentemente dal JWT (se presente).
ddb put-item --table-name "$ROSTER_TABLE" --item '{
  "rosterId": {"S": "as-junior"},
  "teamName": {"S": "AS Junior."},
  "players": {"L": '"$ROSTER_PLAYERS"'}
}' >/dev/null
echo "Roster 'as-junior' inserito."

# ---------------------------------------------------------------------------------------------
# 4) Voti recenti (PlayerMatchStats) - giornate 2-4, stagione 2026-27, fonte fantacalcio.it
#    Copertura parziale: solo i dati che sono stati effettivamente trovati, nessun valore inventato.
# ---------------------------------------------------------------------------------------------

put_stat() {
  local player_id="$1" matchday="$2" opponent="$3" home="$4" voto="$5"
  local gol="${6:-0}" assist="${7:-0}" ammonizioni="${8:-0}" espulsioni="${9:-0}" golsubiti="${10:-0}"
  local fantavoto
  fantavoto=$(echo "scale=2; $voto + ($gol * 3) + ($assist * 1) - ($golsubiti * 1) - ($ammonizioni * 0.5) - ($espulsioni * 1)" | bc)
  ddb put-item --table-name "$MATCH_STATS_TABLE" --item '{
    "playerId": {"S": "'"$player_id"'"},
    "matchday": {"N": "'"$matchday"'"},
    "season": {"S": "2026-27"},
    "opponentTeam": {"S": "'"$opponent"'"},
    "home": {"BOOL": '"$home"'},
    "voto": {"N": "'"$voto"'"},
    "fantavoto": {"N": "'"$fantavoto"'"},
    "gol": {"N": "'"$gol"'"},
    "assist": {"N": "'"$assist"'"},
    "ammonizioni": {"N": "'"$ammonizioni"'"},
    "espulsioni": {"N": "'"$espulsioni"'"},
    "golSubiti": {"N": "'"$golsubiti"'"}
  }' >/dev/null
}

echo "=== 4) Seed $MATCH_STATS_TABLE ==="
# Giornata 4 (avversari corretti: lo script portava per errore la squadra del giocatore
# stesso al posto del vero avversario - bug non presente nelle giornate 2-3; voti verificati
# via pagelle reali 11-14/09/2026, vedi commit per fonti)
put_stat barella      4 Udinese  true  7.5
put_stat davis        4 Inter    false 6.5 0 1 1 0 0
put_stat perri        4 Roma     true  5.0 0 0 0 0 2
put_stat comuzzo      4 Roma     true  5.5 0 0 0 0 2
put_stat vlasic       4 Roma     true  5.0
put_stat simeone      4 Roma     true  5.0
put_stat molina       4 Torino   false 6.0
put_stat kone         4 Torino   false 6.5
put_stat ndicka       4 Torino   false 7.0
put_stat soule        4 Torino   false 6.0
put_stat dilorenzo    4 Bologna  true  6.0
put_stat spinazzola   4 Bologna  true  6.5
put_stat pinamonti    4 Milan    true  4.5
put_stat modric       4 Lazio    false 4.5
put_stat ederson      4 Cagliari true  5.0
put_stat deketelaere  4 Cagliari true  6.0
put_stat lauriente    4 Juventus true  6.5
put_stat bremer       4 Sassuolo false 5.0 0 0 0 0 3
put_stat ostigard     4 Frosinone true 6.0 0 0 0 0 1
# Skorupski NON gioca in giornata 4 (esce dai convocati per un ritardo alla riunione tecnica,
# gioca il vice Pessina): nessuna riga, "Senza Voto" e' corretto qui, non un dato mancante.
# Giornata 3
put_stat ndicka       3 Atalanta true  5.5
put_stat molina       3 Atalanta true  6.0
put_stat kone         3 Atalanta true  6.0
put_stat soule        3 Atalanta true  8.5 1 0 0 0 0
put_stat ederson      3 Roma     false 7.0 1 0 0 0 0
put_stat deketelaere  3 Roma     false 6.0
put_stat skorupski    3 Sassuolo true  7.5
put_stat davis        3 Lazio    true  6.5 0 1 0 0 0
# Giornata 2
put_stat okoye        2 Monza    false 7.0
put_stat ekkelenkamp  2 Monza    false 9.5
put_stat davis        2 Monza    false 6.5
echo "30 voti inseriti."

# ---------------------------------------------------------------------------------------------
# 5) Disponibilita' (AvailabilityReports) - come confermato in chat il 14/09/2026, con Ndicka
#    riclassificato IN_DUBBIO per la notizia reale trovata su fantacalcio.it/Sky Sport.
# ---------------------------------------------------------------------------------------------

echo "=== 5) Seed $AVAILABILITY_TABLE ==="
ddb put-item --table-name "$AVAILABILITY_TABLE" --item '{
  "playerId": {"S": "mctominay"}, "status": {"S": "INFORTUNATO"}, "startingProbability": {"N": "0"}
}' >/dev/null
ddb put-item --table-name "$AVAILABILITY_TABLE" --item '{
  "playerId": {"S": "doekhi"}, "status": {"S": "INFORTUNATO"}, "startingProbability": {"N": "0"}
}' >/dev/null
ddb put-item --table-name "$AVAILABILITY_TABLE" --item '{
  "playerId": {"S": "molina"}, "status": {"S": "BALLOTTAGGIO"}, "startingProbability": {"N": "65"}
}' >/dev/null
ddb put-item --table-name "$AVAILABILITY_TABLE" --item '{
  "playerId": {"S": "pinamonti"}, "status": {"S": "BALLOTTAGGIO"}, "startingProbability": {"N": "65"}
}' >/dev/null
ddb put-item --table-name "$AVAILABILITY_TABLE" --item '{
  "playerId": {"S": "ndicka"}, "status": {"S": "IN_DUBBIO"}
}' >/dev/null
echo "5 stati disponibilita' inseriti (gli altri 20 giocatori restano di default OK/100% - nessuna riga necessaria)."

echo
echo "=== Fatto. ==="
echo "Fixtures NON seminata: avvia l'app (con FOOTBALL_DATA_API_KEY valorizzata) e lascia che"
echo "FootballDataSyncJob la popoli con i dati reali di football-data.org, oppure chiamala a mano."
