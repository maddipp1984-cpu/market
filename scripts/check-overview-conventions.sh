#!/bin/bash
# PostToolUse Hook: Prueft Architektur-Regeln fuer Uebersichtsseiten
# Wird nach Edit/Write von Java-Dateien ausgefuehrt

FILE="$1"

# Nur Java-Dateien pruefen
if [[ ! "$FILE" =~ \.java$ ]]; then
  exit 0
fi

ERRORS=""

# Regel 1: Kein SQL in Service-Klassen
if [[ "$FILE" =~ Service\.java$ ]]; then
  if grep -qE '(PreparedStatement|ResultSet|DataSource|getConnection|\"SELECT |\"INSERT |\"UPDATE |\"DELETE )' "$FILE" 2>/dev/null; then
    ERRORS="${ERRORS}\n[ARCHITEKTUR] $FILE: SQL/JDBC-Code in Service gefunden! SQL gehoert in ein Repository, nicht in den Service."
  fi
fi

# Regel 2: Controller mit TableResponse muss ueber Service gehen, nicht direkt Repository
if [[ "$FILE" =~ Controller\.java$ ]]; then
  if grep -q 'TableResponse' "$FILE" 2>/dev/null; then
    if grep -qP '(PreparedStatement|ResultSet|DataSource|getConnection)' "$FILE" 2>/dev/null; then
      ERRORS="${ERRORS}\n[ARCHITEKTUR] $FILE: JDBC-Code in Controller gefunden! Controller -> Service -> Repository."
    fi
  fi
fi

# Regel 3: Neues Repository fuer Uebersicht muss Raw JDBC nutzen, nicht JPA
if [[ "$FILE" =~ (Overview|List)Repository\.java$ ]]; then
  if grep -q 'JpaRepository\|CrudRepository\|findAll()' "$FILE" 2>/dev/null; then
    ERRORS="${ERRORS}\n[ARCHITEKTUR] $FILE: Uebersichts-Repository nutzt JPA! Uebersichten muessen Raw JDBC (DataSource + PreparedStatement) verwenden."
  fi
fi

# Regel 4: Kein JPA findAll() in Services die TableResponse/Uebersichten bedienen
if [[ "$FILE" =~ Service\.java$ ]]; then
  if grep -q 'findAllAsRows\|TableResponse' "$FILE" 2>/dev/null; then
    if grep -q '\.findAll()' "$FILE" 2>/dev/null; then
      ERRORS="${ERRORS}\n[ARCHITEKTUR] $FILE: JPA findAll() fuer Uebersicht gefunden! Uebersichten muessen Raw JDBC nutzen (FilterQueryBuilder braucht dynamisches SQL)."
    fi
  fi
fi

if [ -n "$ERRORS" ]; then
  echo -e "$ERRORS"
  exit 1
fi

exit 0
