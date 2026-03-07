# Claude Code Automations — Zeitreihensystem

Eingerichtet am 2026-03-07. Dokumentation aller konfigurierten Automations.

---

## 1. Hooks (PostToolUse)

Konfiguriert in `.claude/settings.local.json`.

### TypeScript Type-Check nach Frontend-Edit
- **Trigger**: Jedes `Edit` oder `Write` einer Datei unter `frontend/src`
- **Aktion**: Fuehrt `tsc --noEmit` aus und zeigt die letzten 20 Zeilen
- **Nutzen**: Typfehler werden sofort nach dem Edit erkannt, nicht erst beim naechsten Build

### Gradle compileJava nach Backend-Edit
- **Trigger**: Jedes `Edit` oder `Write` einer Datei unter `src/main/java`
- **Aktion**: Fuehrt `./gradlew compileJava -q` aus und zeigt die letzten 20 Zeilen
- **Nutzen**: Java-Kompilierfehler sofort erkennen statt erst beim naechsten bootRun

**Hinweis**: Die Hooks laufen automatisch im Hintergrund. Bei grossen Aenderungen kann es
einige Sekunden dauern. Falls ein Hook stoert (z.B. bei vielen schnellen Edits), kann er
in `settings.local.json` temporaer auskommentiert werden.

---

## 2. Subagents

Abgelegt in `.claude/agents/`.

### security-reviewer.md
- **Zweck**: Prueft den Code auf Sicherheitsluecken (OWASP Top 10)
- **Fokus**: SQL Injection (Raw JDBC), Spring Security Config, Input Validation, CORS
- **Aufruf**: `@security-reviewer` in Claude Code oder per Agent-Tool
- **Wann nutzen**: Vor Releases, nach Security-relevanten Aenderungen, bei neuen Endpoints

### db-migration-reviewer.md
- **Zweck**: Prueft SQL-Migrationen gegen das bestehende Schema
- **Fokus**: TimescaleDB-Kompatibilitaet, Hypertable-Aenderungen, Rollback-Sicherheit, Performance
- **Aufruf**: `@db-migration-reviewer` in Claude Code oder per Agent-Tool
- **Wann nutzen**: Nach jeder neuen Migration in `sql/migrations/`

---

## 3. Skills

Abgelegt in `.claude/skills/`.

### create-migration (NEU)
- **Trigger**: `/create-migration` oder "neue Migration", "migration erstellen"
- **Was es tut**: Ermittelt die naechste Migrationsnummer, erstellt die Datei mit Template,
  implementiert die SQL-Statements, aktualisiert schema.sql, testet gegen die DB
- **User-only**: Ja (`disable-model-invocation: true`)

### overview-new (bestehend)
- **Trigger**: `/overview-new` oder "neue Uebersicht"
- **Was es tut**: Fuehrt durch alle Ebenen einer neuen Uebersichtsseite (Backend DTO, Controller, Frontend Page)

### restart-web (bestehend)
- **Trigger**: `/restart-web` oder "restart", "neu starten"
- **Was es tut**: Stoppt Backend+Frontend, baut neu, startet wieder mit Healthcheck

---

## 4. Permissions (aufgeraeumt)

Die `settings.local.json` wurde von ~70 Einzel-Eintraegen auf ~20 generische Patterns reduziert.

Abgedeckt:
- `./gradlew` — alle Gradle-Befehle
- `git` — alle Git-Operationen
- `docker` — alle Docker-Befehle (DB-Zugriff etc.)
- `C:/tools/nodejs/*` und `/c/tools/nodejs/*` — Node.js Tools (npm, npx, node)
- `curl`, `mkdir`, `rm`, `mv`, `ls` — Standard-Shell
- `java`, `powershell`, `code`, `claude` — Entwicklungstools
- `WebSearch` + `WebFetch` fuer relevante Doku-Seiten

---

## 5. MCP Server — context7 (manuell einrichten)

**Was**: Live-Dokumentation fuer Libraries (TanStack, Keycloak, Spring Security etc.)
**Warum**: Liefert aktuelle API-Doku direkt in den Claude-Kontext

**Installation** (einmalig in der Kommandozeile):
```bash
claude mcp add context7 -- npx -y @upstash/context7-mcp
```

**Voraussetzung**: Node.js muss im PATH sein (`C:\tools\nodejs`).
Nach der Installation steht `context7` als Tool in Claude Code zur Verfuegung.

---

## Uebersicht: Dateien

| Datei | Typ | Beschreibung |
|-------|-----|--------------|
| `.claude/settings.local.json` | Config | Hooks + Permissions |
| `.claude/agents/security-reviewer.md` | Subagent | Security-Pruefung |
| `.claude/agents/db-migration-reviewer.md` | Subagent | Migrations-Pruefung |
| `.claude/skills/create-migration/SKILL.md` | Skill | Migration erstellen |
| `.claude/skills/overview-new/SKILL.md` | Skill | Uebersichtsseite (bestehend) |
| `.claude/skills/restart-web/skill.md` | Skill | System-Restart (bestehend) |
