import Keycloak from 'keycloak-js';

const keycloak = new Keycloak({
  url: 'http://localhost:8180',
  realm: 'timeseries',
  clientId: 'timeseries-frontend',
});

export function getUserId(): string {
  return keycloak.subject ?? '';
}

export function getUsername(): string {
  return keycloak.tokenParsed?.preferred_username ?? '';
}

export default keycloak;
