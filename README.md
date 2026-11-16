# Système Bancaire Distribué : Event Sourcing & CQRS avec Spring Boot et Axon

Implémentation d'un microservice de gestion de comptes bancaires reposant sur les architectures **CQRS** (Command Query Responsibility Segregation) et **Event Sourcing**, propulsé par **Spring Boot**, **Axon Framework** et **Axon Server**.

---

## Vue d'ensemble et objectifs

Ce projet met en œuvre une séparation stricte entre les flux d'écriture (mutations d'état via commandes) et les flux de lecture (projections optimisées), en utilisant un journal d'événements immuable comme unique source de vérité.

* **Découplage Command / Query** : isolation des règles métier transactionnelles et des modèles de consultation.
* **Persistance événementielle** : stockage séquentiel des événements métier dans un Event Store (évolution progressive : H2 en mémoire $\rightarrow$ PostgreSQL $\rightarrow$ Axon Server).
* **Projections asynchrones** : synchronisation d'une base relationnelle de lecture à partir du flux d'événements.
* **Streaming temps réel** : diffusion continue des opérations bancaires vers les clients via Server-Sent Events (SSE).

### Périmètre fonctionnel

* Création d'un compte bancaire avec activation automatique immédiate.
* Exécution d'opérations de crédit et de débit soumises à validation métier.
* Gestion du cycle de vie et des états d'un compte (`CREATED`, `ACTIVATED`, `SUSPENDED`, `BLOCKED`).
* Consultation du catalogue global des comptes et génération de relevés détaillés (*account statements*).
* Souscription en temps réel au flux d'opérations d'un compte spécifique.

---

## Stack technique

| Composant | Technologie / Version |
| :--- | :--- |
| **Langage** | Java 21 |
| **Framework applicatif** | Spring Boot 3.4.3 |
| **Moteur CQRS / ES** | Axon Framework 4.10.3 |
| **Broker & Event Store** | Axon Server Community Edition (gRPC / HTTP) |
| **Bases de données** | H2 (prototypage), PostgreSQL (persistance relationnelle) |
| **Accès aux données & Réactivité** | Spring Data JPA, Project Reactor (`Flux`) |
| **Documentation & Outillage** | SpringDoc OpenAPI (Swagger UI), Lombok, Docker Compose |

---

## Architecture logicielle

### Flux de données bidirectionnel

```text
                       ┌─────────────────── COMMAND SIDE ───────────────────┐
Requête HTTP (POST/PUT)│  AccountCommandController                          │
──────────────────────►│         │                                          │
                       │         ▼                                          │
                       │   CommandGateway ──► AccountAggregate              │
                       │                       ├─ @CommandHandler (Règles)  │
                       │                       ├─ AggregateLifecycle.apply()│
                       │                       └─ @EventSourcingHandler     │
                       └─────────────────────────┬──────────────────────────┘
                                                 │ Publication d'événements
                                                 ▼
                                          [ EVENT STORE ]
                                      (H2 / PostgreSQL / Axon)
                                                 │ Diffusion asynchrone
                                                 ▼
                       ┌──────────────────── QUERY SIDE ────────────────────┐
                       │  AccountEventHandler (@EventHandler)               │
                       │         │                                          │
                       │         ▼                                          │
                       │  Base de lecture JPA (Account & AccountOperation)  │
                       │         ▲                                          │
Requête HTTP (GET)     │         │                                          │
──────────────────────►│  AccountQueryController ──► QueryGateway           │
(REST & SSE Flux)      │                              └─ AccountQueryHandler│
                       └────────────────────────────────────────────────────┘
```

### Organisation des packages

```text
src/main/java/.../
├── commands/
│   ├── aggregates/     # AccountAggregate (logique décisionnelle et reconstruction d'état)
│   ├── commands/       # AddAccountCommand, CreditAccountCommand, DebitAccountCommand, UpdateAccountStatusCommand
│   ├── controllers/    # AccountCommandController (endpoints REST d'écriture)
│   └── dto/            # Records de requêtes entrantes (*RequestDTO)
├── events/             # Événements métier immuables (AccountCreatedEvent, AccountActivatedEvent, etc.)
├── enums/              # AccountStatus, OperationType
└── query/
    ├── controllers/    # AccountQueryController (endpoints REST et flux SSE)
    ├── dto/            # AccountStatementResponseDTO
    ├── entities/       # Entités JPA de projection (Account, AccountOperation)
    ├── handlers/       # AccountEventHandler (projections), AccountQueryHandler (requêtes)
    ├── queries/        # GetAllAccountsQuery, GetAccountStatementQuery, WatchEventQuery
    └── repository/     # AccountRepository, OperationRepository
```

---

## Implémentation : Côté Command (Écriture)

### 1. Configuration initiale sans Axon Server

Au démarrage du développement, le connecteur Axon Server est exclu dans le `pom.xml` afin d'utiliser un bus de commandes local et un stockage d'événements basé sur JPA :

```xml
<exclusion>
    <groupId>org.axonframework</groupId>
    <artifactId>axon-server-connector</artifactId>
</exclusion>
```

Le contrôleur `AccountCommandController` expose les routes sous `/commands/account/...` via `CommandGateway` et intègre un `@ExceptionHandler` global pour renvoyer explicitement les violations de règles métier au client HTTP.

### 2. Modélisation de l'agrégat `AccountAggregate`

L'agrégat encapsule l'état courant d'un compte et garantit ses invariants métier :

* **Constructeur par défaut** : requis par Axon pour instancier l'agrégat lors de la relecture des événements.
* **Identifiants de routage** : liaison entre `@TargetAggregateIdentifier` (dans chaque objet commande) et `@AggregateIdentifier` (sur le champ `accountId` de l'agrégat).
* **Séparation décision / évolution** :
  * `@CommandHandler` : vérifie les préconditions (ex. solde initial strictement positif, compte actif, provision suffisante) et déclenche `AggregateLifecycle.apply(...)`.
  * `@EventSourcingHandler` : applique de manière déterministe chaque événement reçu pour faire évoluer les attributs internes (`balance`, `status`).

### 3. Cycle de vie et règles métier implémentées

* **Création et activation chaînée** : la réception d'un `AddAccountCommand` publie successivement `AccountCreatedEvent` puis `AccountActivatedEvent`. Chacun dispose de son propre `@EventSourcingHandler` afin que le compte passe immédiatement à l'état `ACTIVATED`.
* **Crédit (`CreditAccountCommand`)** : vérifie que le compte est à l'état `ACTIVATED` et que le montant est positif avant d'émettre `AccountCreditedEvent` (`balance += amount`).
* **Débit (`DebitAccountCommand`)** : contrôle l'état actif du compte ainsi que la couverture du solde (`balance >= amount`). En cas d'insuffisance, une exception est levée et aucun événement n'est persisté.
* **Changement de statut (`UpdateAccountStatusCommand`)** : empêche les transitions redondantes (ex. passer un compte déjà `SUSPENDED` vers `SUSPENDED`). Un compte suspendu ou bloqué rejette toute tentative de crédit ou de débit.

### 4. Persistance PostgreSQL et sérialisation JSON

Après validation sur H2 (via la table système `domain_event_entry`), l'infrastructure bascule sur PostgreSQL via Docker Compose. Par défaut, Axon sérialise les payloads au format XML (XStream). La configuration suivante bascule la sérialisation des événements en JSON via Jackson :

```properties
axon.serializer.events=jackson
axon.serializer.general=jackson
axon.serializer.messages=xstream
```

L'endpoint de diagnostic `GET /commands/account/events/{accountId}` interroge directement l'`EventStore` (`eventStore.readEvents(accountId)`) pour restituer l'historique complet des événements d'un agrégat.

---

## Implémentation : Côté Query (Lecture)

### 1. Modèle relationnel de projection

Deux entités JPA matérialisent la vue de consultation :

* **`Account`** : `id`, `balance`, `status` (`@Enumerated(EnumType.STRING)`), `currency`, `createdAt` (`Instant`).
* **`AccountOperation`** : `id` (auto-incrémenté), `timestamp`, `amount`, `type` (`CREDIT` / `DEBIT`), `currency`, et relation `@ManyToOne` vers `Account`.

### 2. Synchronisation par événements (`AccountEventHandler`)

Les méthodes annotées `@EventHandler` écoutent le bus d'événements et mettent à jour les tables `account` et `account_operation` :

| Événement consommé | Impact sur le modèle de lecture |
| :--- | :--- |
| `AccountCreatedEvent` | Insertion d'une nouvelle ligne dans `account` |
| `AccountActivatedEvent` | Passage du statut du compte à `ACTIVATED` |
| `AccountStatusUpdatedEvent` | Mise à jour du statut selon la nouvelle valeur |
| `AccountCreditedEvent` | Ajout d'une ligne `CREDIT` dans `account_operation` et incrémentation du solde |
| `AccountDebitedEvent` | Ajout d'une ligne `DEBIT` dans `account_operation` et décrémentation du solde |

> **Gestion des tokens de suivi (Tracking Tokens)** : Axon enregistre l'offset de lecture des projections dans la table `token_entry`. Pour déclencher un *replay* complet des événements depuis zéro, il convient de vider `token_entry` ainsi que les tables de projection, tout en maintenant `spring.jpa.hibernate.ddl-auto=update` afin de préserver les structures existantes.

### 3. Requêtes standards et streaming temps réel (SSE)

* **Consultation globale** : `GET /query/accounts/all` déclenche `GetAllAccountsQuery` via `QueryGateway`.
* **Relevé de compte** : `GET /query/accounts/accountStatement/{accountId}` agrège les informations du compte et l'historique de ses transactions dans un `AccountStatementResponseDTO`.
* **Observation temps réel (*Watch*)** : `GET /query/accounts/watch/{accountId}` retourne un `Flux<AccountOperation>` (`produces = MediaType.TEXT_EVENT_STREAM_VALUE`). Il s'appuie sur `queryGateway.subscriptionQuery(...)` couplé à `QueryUpdateEmitter` côté `AccountEventHandler` pour pousser instantanément chaque nouvelle opération vers les clients connectés.

---

## Migration vers Axon Server

Pour passer d'une architecture monolithique à un écosystème microservices distribué, l'Event Store relationnel est remplacé par **Axon Server** :

1. **Dépendance** : suppression de l'exclusion `axon-server-connector` dans le fichier `pom.xml`.
2. **Infrastructure** : ajout du conteneur `axon-server` dans `docker-compose.yml` (exposition du port HTTP `8024` pour l'interface d'administration et du port gRPC `8124` pour le routage des messages).
3. **Bascule** : au redémarrage, l'application négocie automatiquement une connexion gRPC bidirectionnelle avec Axon Server. Le routage des commandes, des requêtes et le stockage natif des événements sont désormais centralisés hors de PostgreSQL.

---

## Démarrage et référence des API

### Lancement de l'environnement

```bash
# Démarrer l'infrastructure (PostgreSQL, pgAdmin, Axon Server)
docker compose up -d

# Compiler et lancer le microservice Spring Boot
./mvnw spring-boot:run
```

### Catalogue des endpoints REST

| Méthode | Endpoint | Rôle |
| :--- | :--- | :--- |
| `POST` | `/commands/account/add` | Créer et activer un nouveau compte bancaire |
| `POST` | `/commands/account/credit` | Créditer un compte actif |
| `POST` | `/commands/account/debit` | Débiter un compte actif (avec contrôle de solde) |
| `PUT` | `/commands/account/updateStatus` | Modifier l'état d'un compte (`ACTIVATED`, `SUSPENDED`, `BLOCKED`) |
| `GET` | `/commands/account/events/{accountId}` | Inspecter le flux brut d'événements d'un agrégat |
| `GET` | `/query/accounts/all` | Récupérer la liste de tous les comptes projetés |
| `GET` | `/query/accounts/accountStatement/{accountId}` | Obtenir le relevé complet d'un compte et ses opérations |
| `GET` | `/query/accounts/watch/{accountId}` | Ouvrir un flux SSE pour suivre les opérations en direct |

---

## Bilan architectural

L'association de **CQRS** et de l'**Event Sourcing** garantit une traçabilité intégrale (auditabilité native par rejeu d'événements) et une scalabilité indépendante des charges de lecture et d'écriture. Bien que ce modèle introduise une verbosité structurelle initiale (multiplication des classes de commandes, événements, DTOs et handlers), il offre un cadre déterministe et résilient particulièrement adapté aux systèmes financiers distribués.