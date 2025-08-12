export const url =
  process.env.NODE_ENV === "development"
    ? "http://localhost:8789/api/v1/"
    : "/api/v1/";
export const token =
  process.env.NODE_ENV === "development"
    ? "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJndWVzdEBsYW1pc3BsdXMub3JnIiwiYXV0aCI6IlN1cGVyIEFkbWluIiwibmFtZSI6Ikd1ZXN0IEd1ZXN0IiwiZXhwIjoxNzU0MDczMzA2fQ.DQNptWl6lh-nToWYEHXtrJX9n1XWK6lVLwRq2II_VCpfpQTTIUzvJ2m1JTB8AMWfN6TymdKfnujU7kgr2EkqGA"
    : new URLSearchParams(window.location.search).get("jwt");
