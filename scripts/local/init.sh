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
#   - un endpoint DynamoDB locale in ascolto (default: LocalStack su http://localhost:4566,
#     es. `docker run -p 4566:4566 localstack/localstack`) - coerente con
#     aws.endpoint-url in config/application.properties
#
# Uso:
#   ./scripts/local/init.sh
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
  ddb put-item --table-name "$MATCH_STATS_TABLE" --item '{
    "playerId": {"S": "'"$player_id"'"},
    "matchday": {"N": "'"$matchday"'"},
    "season": {"S": "2026-27"},
    "opponentTeam": {"S": "'"$opponent"'"},
    "home": {"BOOL": '"$home"'},
    "voto": {"N": "'"$voto"'"},
    "fantavoto": {"N": "'"$voto"'"}
  }' >/dev/null
}

echo "=== 4) Seed $MATCH_STATS_TABLE ==="
# Giornata 4
put_stat barella      4 Inter    true  7.5
put_stat davis        4 Udinese  false 6.5
put_stat perri        4 Torino   true  5.5
put_stat comuzzo      4 Torino   true  5.5
put_stat vlasic       4 Torino   true  5.5
put_stat simeone      4 Torino   true  8.5
put_stat molina       4 Roma     false 6.0
put_stat kone         4 Roma     false 6.0
put_stat dilorenzo    4 Napoli   true  9.0
put_stat spinazzola   4 Napoli   true  6.5
put_stat pinamonti    4 Lazio    true  8.5
put_stat modric       4 Milan    false 9.5
put_stat ederson      4 Atalanta true  5.5
put_stat deketelaere  4 Atalanta true  5.0
put_stat lauriente    4 Sassuolo true  6.5
put_stat bremer       4 Juventus false 5.0
put_stat ostigard     4 Genoa    true  6.0
# Giornata 3
put_stat ndicka       3 Atalanta true  5.5
put_stat molina       3 Atalanta true  6.0
put_stat kone         3 Atalanta true  6.0
put_stat soule        3 Atalanta true  8.5
put_stat ederson      3 Roma     false 7.0
put_stat deketelaere  3 Roma     false 6.0
put_stat skorupski    3 Sassuolo true  7.5
# Giornata 2
put_stat okoye        2 Monza    false 7.0
put_stat ekkelenkamp  2 Monza    false 9.5
put_stat davis        2 Monza    false 6.5
echo "27 voti inseriti."

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
