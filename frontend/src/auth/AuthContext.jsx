import { createContext, useContext, useMemo, useState } from 'react'
import api from '../api'

const AuthContext = createContext(null)
export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    try { return JSON.parse(localStorage.getItem('stocksight_user') || 'null') } catch { return null }
  })
  async function login(username, password) {
    const { data } = await api.post('/auth/login', { username, password })
    localStorage.setItem('stocksight_token', data.token)
    const nextUser = { username: data.username, role: data.role }
    localStorage.setItem('stocksight_user', JSON.stringify(nextUser))
    setUser(nextUser)
  }
  async function register(username, password) {
    const { data } = await api.post('/auth/register', { username, password })
    localStorage.setItem('stocksight_token', data.token)
    const nextUser = { username: data.username, role: data.role }
    localStorage.setItem('stocksight_user', JSON.stringify(nextUser))
    setUser(nextUser)
  }
  function logout() {
    localStorage.removeItem('stocksight_token'); localStorage.removeItem('stocksight_user'); setUser(null)
  }
  const value = useMemo(() => ({ user, login, register, logout }), [user])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
export function useAuth() { return useContext(AuthContext) }
