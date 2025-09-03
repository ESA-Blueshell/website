import axios from "axios";
// import OpenAPIClientAxios from 'openapi-client-axios';

const baseURL: string = "https://localhost/api"

// const baseURL: string = "https://esa-blueshell.nl/api/"
// const openApiClient = new OpenAPIClientAxios({ definition: baseURL + '/openapi.json' });

const api = axios.create({
  baseURL
})


export default api;
