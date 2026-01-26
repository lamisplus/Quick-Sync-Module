export const url =
  process.env.NODE_ENV === "development"
    ? "http://localhost:8789/api/v1/"
    : "/api/v1/";
export const token =
  process.env.NODE_ENV === "development"
    ? "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJndWVzdEBsYW1pc3BsdXMub3JnIiwiYXV0aCI6IlN1cGVyIEFkbWluLFVzZXIsUkRFIiwibmFtZSI6Ikd1ZXN0IEd1ZXN0IiwiZXhwIjoxNzYxODQzMzM2fQ.2ZFVlHd3hM7I_iREaYgiTlvjpFTOepNLROaD5zdLdgyeROglpjkAVImlcmzRb5LB1lqd7tcPwCHBTzl0q6xt9Q"
    : new URLSearchParams(window.location.search).get("jwt");
