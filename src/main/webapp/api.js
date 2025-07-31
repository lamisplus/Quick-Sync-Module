export const url =
  process.env.NODE_ENV === "development"
    ? "http://localhost:8789/api/v1/"
    : "/api/v1/";
export const token =
  process.env.NODE_ENV === "development"
    ? "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJndWVzdEBsYW1pc3BsdXMub3JnIiwiYXV0aCI6IlN1cGVyIEFkbWluIiwibmFtZSI6Ikd1ZXN0IEd1ZXN0IiwiZXhwIjoxNzUzOTIwNDI3fQ.0t5PCUD64Xeaap26V01gm3v4BqdPvZWTLM8NZEXdBA6qrXopPR6aaARKdeOVkzQcw3uxDIjIh2AzRACCBO8oFw"
    : new URLSearchParams(window.location.search).get("jwt");
