import axios from "axios";

const baseURL: string = "http://localhost:1024/apiGateway"

// const baseURL: string = "https://esa-blueshell.nl/api/"

const apiGateway = axios.create({
  baseURL
})

export default apiGateway;
