export const url =
    process.env.NODE_ENV === "development"
        ? "http://localhost:8789/api/v1/"
        : "/api/v1/";
export const token =
    process.env.NODE_ENV === "development"
        ? "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJndWVzdEBsYW1pc3BsdXMub3JnIiwiYXV0aCI6IlN1cGVyIEFkbWluIiwibmFtZSI6Ikd1ZXN0IEd1ZXN0IiwiZXhwIjoxNzMxNTgzMTA5fQ._8EHjAJ8pLaCaCzsKYrvwjD33mIzGmpD2ABHJ_UvjNfrKxU0iSrDo1YqNWYpfTlMdpjkThZliEdcdkE6U1dgSQ" : new URLSearchParams(window.location.search).get("jwt");
