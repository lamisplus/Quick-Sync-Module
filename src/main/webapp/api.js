export const url =
    process.env.NODE_ENV === "development"
        ? "http://localhost:8789/api/v1/"
        : "/api/v1/";
export const token =
    process.env.NODE_ENV === "development"
        ? "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJndWVzdEBsYW1pc3BsdXMub3JnIiwiYXV0aCI6IlN1cGVyIEFkbWluIiwibmFtZSI6Ikd1ZXN0IEd1ZXN0IiwiZXhwIjoxNzMwOTA3MjgyfQ.7ztvfwtfkCsVb6-WQVY3jTx31ZHjEMYJwSr2_Ci_L6JpuIoYOlGBVIrv41An6mgJ4PbXtVGFooLqKpC1cMDDMw" : new URLSearchParams(window.location.search).get("jwt");
