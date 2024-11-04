export const url =
    process.env.NODE_ENV === "development"
        ? "http://localhost:8383/api/v1/"
        : "/api/v1/";
export const token =
    process.env.NODE_ENV === "development"
        ? "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJndWVzdEBsYW1pc3BsdXMub3JnIiwiYXV0aCI6IlN1cGVyIEFkbWluIiwibmFtZSI6Ikd1ZXN0IEd1ZXN0IiwiZXhwIjoxNzMwNDUyMzgxfQ.PXdysrlP-nxVfw4dn3J5tdrLJ4y04m9JZGuqIVIf6VKMVbs_NhNVXDNKJu1WThOmIUecDM-7vuCH9HR_zKrh2A" : new URLSearchParams(window.location.search).get("jwt");
