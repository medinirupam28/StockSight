import axios from 'axios'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || (import.meta.env.DEV ? 'http://localhost:8080/api' : '')
const api = axios.create({
  baseURL: apiBaseUrl || undefined,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000,
})

api.interceptors.request.use((config) => {
  if (!apiBaseUrl) throw new Error('VITE_API_BASE_URL must be configured for production builds.')
  const token = localStorage.getItem('stocksight_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('stocksight_token')
      localStorage.removeItem('stocksight_user')
      if (!window.location.pathname.startsWith('/login')) window.location.assign('/login')
    }
    return Promise.reject(error)
  },
)
export default api
