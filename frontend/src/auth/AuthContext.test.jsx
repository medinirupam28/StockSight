import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { AuthProvider, useAuth } from './AuthContext'
import api from '../api'

vi.mock('../api', () => ({ default: { post: vi.fn() } }))

function AuthProbe() {
  const { user, login, logout } = useAuth()
  return <div>
    <output>{user ? `${user.username}:${user.role}` : 'signed-out'}</output>
    <button onClick={() => login('qa-supplier', 'runtime-test-password')}>Sign in test user</button>
    <button onClick={logout}>Sign out</button>
  </div>
}

describe('AuthContext session lifecycle', () => {
  beforeEach(() => localStorage.clear())
  afterEach(() => {
    cleanup()
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('stores a successful login and clears it on logout', async () => {
    api.post.mockResolvedValue({ data: { token: 'test-jwt', username: 'qa-supplier', role: 'SUPPLIER' } })
    render(<AuthProvider><AuthProbe /></AuthProvider>)

    fireEvent.click(screen.getByRole('button', { name: 'Sign in test user' }))
    await waitFor(() => expect(screen.getByText('qa-supplier:SUPPLIER')).toBeTruthy())
    expect(localStorage.getItem('stocksight_token')).toBe('test-jwt')
    expect(JSON.parse(localStorage.getItem('stocksight_user'))).toEqual({ username: 'qa-supplier', role: 'SUPPLIER' })

    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }))
    expect(screen.getByText('signed-out')).toBeTruthy()
    expect(localStorage.getItem('stocksight_token')).toBeNull()
    expect(localStorage.getItem('stocksight_user')).toBeNull()
  })

  it('restores a stored session without exposing the token in user state', () => {
    localStorage.setItem('stocksight_token', 'stored-test-jwt')
    localStorage.setItem('stocksight_user', JSON.stringify({ username: 'returning', role: 'ADMIN' }))
    render(<AuthProvider><AuthProbe /></AuthProvider>)

    expect(screen.getByText('returning:ADMIN')).toBeTruthy()
    expect(screen.queryByText('stored-test-jwt')).toBeNull()
  })
})