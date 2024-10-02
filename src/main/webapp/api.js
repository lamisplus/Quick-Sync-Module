
// export const  token = (new URLSearchParams(window.location.search)).get("jwt")
// export const url = '/api/v1/'
/*
export const url =  'http://localhost:8383/api/v1/';
export const  token = 'eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJndWVzdEBsYW1pc3BsdXMub3JnIiwiYXV0aCI6IlN1cGVyIEFkbWluIiwibmFtZSI6Ikd1ZXN0IEd1ZXN0IiwiZXhwIjoxNjgxOTY3NDg1fQ.gUifadmIat8vjW2YJVQAmgLDKmo-DMJrpbEmaRk2ofjzouAxcPaVP-jMvZY8g0DFIpwhMQPHVzKtQ79qGJ8lAw'
*/

export const url =
    process.env.NODE_ENV === "development"
        ? "http://localhost:8383/api/v1/"
        : "/api/v1/";
export const token =
    process.env.NODE_ENV === "development"
        ? "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJndWVzdEBsYW1pc3BsdXMub3JnIiwiYXV0aCI6IlN1cGVyIEFkbWluIiwibmFtZSI6Ikd1ZXN0IEd1ZXN0IiwiZXhwIjoxNzI3OTAxMDc1fQ.mJuAe14hOhEy2l7qa8vkBhBm2ky4WVrkRfgcEmQx0_DHy0NvShW7VPFViphq4amT1g8ZWq37JZ5udIji6A_Xxg"
        : new URLSearchParams(window.location.search).get("jwt");
