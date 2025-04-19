import axios from "axios";

const baseURL: string = "https://localhost/api"

// const baseURL: string = "https://esa-blueshell.nl/api/"

const api = axios.create({
  baseURL
})


export default api;
