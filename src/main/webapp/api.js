export const url =
  process.env.NODE_ENV === "development"
    ? "http://localhost:8789/api/v1/"
    : "/api/v1/";
export const token =
  process.env.NODE_ENV === "development"
    ? "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJndWVzdEBsYW1pc3BsdXMub3JnIiwiYXV0aCI6IlN1cGVyIEFkbWluIiwibmFtZSI6Ikd1ZXN0IEd1ZXN0IiwiZXhwIjoxNzYwMDMxMDQzfQ.BzbFU7f3kgq8HXPG7TRIOahxcTWRWcQ0cRy38M8LW3HbXlWkbXW5_KBb002AgxrO4fkeR5KNsStPsQ-nixqmmA"
    : new URLSearchParams(window.location.search).get("jwt");
