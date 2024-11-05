export const url =
    process.env.NODE_ENV === "development"
        ? "http://localhost:8789/api/v1/"
        : "/api/v1/";
export const token =
    process.env.NODE_ENV === "development"
        ? "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJndWVzdEBsYW1pc3BsdXMub3JnIiwiYXV0aCI6IlN1cGVyIEFkbWluIiwibmFtZSI6Ikd1ZXN0IEd1ZXN0IiwiZXhwIjoxNzMwNzU5OTA2fQ.rAv3nmBty2gFM7w37wllMIVFWKlWS94Q27QPuqCIfICI35Ep1uX9wJgd-KcluABGvwQ_cpZlsMRoK5_8zBP1LQ" : new URLSearchParams(window.location.search).get("jwt");
