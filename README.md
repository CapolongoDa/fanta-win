# 🏆 fanta-win

> **Motore Reattivo Predittivo e Data Analysis per l'Ottimizzazione della Formazione di Fantacalcio**

`fanta-win` è un'applicazione microservice sviluppata in **Java 21** e **Spring Boot 3 (WebFlux)** progettata per agire come un motore decisionale algoritmico ("FantaClaude Engine"). Il suo obiettivo fondamentale è guidare il fantallenatore nella scelta della formazione ideale della Serie A, azzerando l'errore umano, l'istinto e le componenti emotive grazie alla data analysis e a modelli matematici pesati.

---

## 🎯 Architettura della Logica di Business

Il motore elabora i dati della rosa e della giornata applicando rigorosamente **5 Moduli Logici Predittivi**:

1. **Rating Predittivo Avanzato (FantaRating):**
   * **40%** Stato di forma recente (Media voto e bonus recenti).
   * **30%** Storico contro l'avversario specifico (Fantamedia e bonus/malus passati).
   * **30%** Difficoltà match ed Expected Goals/Assists ($xG$/$xA$).
2. **Controllo Infrasettimanali e Turnover:**
   * Calcolo e applicazione dinamica del malus stanchezza e rotazione (-1.5 per i Big, -2.5 per le riserve in caso di impegni europei o turni ravvicinati).
3. **Report Medici e Disponibilità Reale:**
   * Forza il rating a 0 per infortunati e squalificati (esclusione tassativa).
   * Applicazione di malus scalari per ballottaggi (55%-75% -> -0.8), subentranti (<50% -> -2.0) e giocatori in dubbio (-1.0).
4. **Confronto Dinamico dei Moduli & Modificatore:**
   * Simulazione in tempo reale delle combinazioni sui moduli `3-4-3`, `4-3-3`, `3-5-2` e `4-4-2`.
   * Calcolo della proiezione del **Modificatore di Difesa** (Voto puro atteso del Portiere + 3 migliori Difensori: $+1$ per media $\ge 6.0$, $+3$ per $\ge 6.5$, $+6$ per $\ge 7.0$).
   * Selezione automatica del modulo che massimizza il punteggio complessivo atteso.
5. **Blindatura della Panchina (Algoritmo Anti-Voto-Zero):**
   * Assegnazione automatica del secondo portiere della stessa squadra reale.
   * Ordinamento dinamico dei panchinari pesando la "Certezza del Voto" per garantire la copertina completa dei titolari a rischio.

---

## 🛠️ Stack Tecnologico

* **Java:** 21
* **Framework:** Spring Boot 3.3.4 (`spring-boot-starter-webflux`, `spring-boot-starter-validation`)
* **Cloud & Storage:** AWS SDK v2 (`dynamodb-enhanced`), Spring Cloud AWS 3.4.0
* **API Documentation & First:** OpenAPI 3.0 / SpringDoc OpenAPI 2.6.0 (`openapi-generator-maven-plugin`)
* **Build Tool:** Maven
* **Testing:** JUnit 5, Testcontainers (LocalStack), MockServer, JaCoCo

---

## 📦 Prerequisiti

* **JDK 21** o superiore
* **Apache Maven 3.8+**
* **Docker** (per l'esecuzione di LocalStack nei test d'integrazione)

---

## 🔧 Configurazione e Build

Per generare i sorgenti DTO/Interfaces dalla specifica OpenAPI ed eseguire la build:

```bash
# Clona il repository
git clone https://github.com/CapolongoDa/fanta-win.git
cd fanta-win

# Genera il codice da OpenAPI e compila
mvn clean compile
```

Per eseguire i test unitari e d'integrazione con report JaCoCo:

```bash
mvn clean test
```

---

## 📑 API Spec & Swagger UI

L'applicazione segue un approccio **OpenAPI First**. La specifica OpenAPI 3.0 è definita in:
`docs/openapi/fanta-win-api-internal.yaml`

A servizio avviato, la Swagger UI è disponibile all'indirizzo:
`http://localhost:8080/swagger-ui.html`

---

## 📝 Struttura del Progetto

```text
fanta-win/
├── docs/
│   └── openapi/
│       └── fanta-win-api-internal.yaml   # Specifica OpenAPI 3.0 della logica FantaClaude
├── src/
│   ├── main/
│   │   ├── java/                         # Controller, Service reattivi e DTO
│   │   └── resources/                    # application.yml, logback configuration
│   └── test/                             # Integration & Unit test (LocalStack, MockServer)
└── pom.xml                               # Configurazione Maven
```

---

## 📄 Licenza

Progetto ad uso personale. Tutti i diritti riservati.