import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from './AuthContext'
export default function ProtectedRoute({ roles }) {
  const { user } = useAuth()
  if (!localStorage.getItem('stocksight_token') || !user) return <Navigate to="/login" replace />
  if (roles && !roles.includes(user.role)) return <Navigate to="/" replace />
  return <Outlet />
}
