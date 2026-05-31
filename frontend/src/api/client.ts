import axios from 'axios'

const API_BASE = (import.meta.env.VITE_API_BASE_URL as string) || '/api'

const client = axios.create({
  baseURL: API_BASE,
  headers: {
    'Content-Type': 'application/json',
  },
})

client.interceptors.response.use(
  response => response,
  error => {
    // Basic error normalization
    return Promise.reject(error)
  }
)

export default client
