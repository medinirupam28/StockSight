import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './AuthContext'
import ProtectedRoute from './ProtectedRoute'

function renderProtectedPath(path, roles) {
  return render(<MemoryRouter initialEntries={[path]}><AuthProvider><Routes>
    <Route path="/login" element={<h1>Sign in</h1>}/>
    <Route path="/" element={<h1>Overview</h1>}/>
    <Route element={<ProtectedRoute roles={roles}/> }>
      <Route path="/products" element={<h1>Products</h1>}/>
      <Route path="/admin" element={<h1>Admin tools</h1>}/>
    </Route>
  </Routes></AuthProvider></MemoryRouter>)
}

describe('ProtectedRoute access rules', () => {
  beforeEach(() => localStorage.clear())
  afterEach(() => {
    cleanup()
    localStorage.clear()
  })

  it('redirects a user without a token to login', () => {
    renderProtectedPath('/products')
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeTruthy()
  })

  it('allows an authenticated supplier to access shared products', () => {
    localStorage.setItem('stocksight_token', 'test-jwt')
    localStorage.setItem('stocksight_user', JSON.stringify({ username: 'supplier', role: 'SUPPLIER' }))
    renderProtectedPath('/products')
    expect(screen.getByRole('heading', { name: 'Products' })).toBeTruthy()
  })

  it('redirects a supplier away from an admin-only route', () => {
    localStorage.setItem('stocksight_token', 'test-jwt')
    localStorage.setItem('stocksight_user', JSON.stringify({ username: 'supplier', role: 'SUPPLIER' }))
    renderProtectedPath('/admin', ['ADMIN'])
    expect(screen.getByRole('heading', { name: 'Overview' })).toBeTruthy()
  })
})