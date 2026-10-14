import { useEffect, useState } from 'react'
import { Link, Navigate, Route, Routes, useNavigate, useParams } from 'react-router-dom'
import api from './api'
import { useAuth } from './auth/AuthContext'
import ProtectedRoute from './auth/ProtectedRoute'
import ReactMarkdown from 'react-markdown'

async function loadAllProducts() {
  const firstPage = await api.get('/products', { params: { page: 0, size: 100 } })
  const products = [...(firstPage.data.content || [])]
  for (let page = 1; page < (firstPage.data.totalPages || 1); page += 1) {
    const response = await api.get('/products', { params: { page, size: 100 } })
    products.push(...(response.data.content || []))
  }
  return products
}

function newestFirstMovements(items) {
  return [...items].sort((a, b) => b.billDate.localeCompare(a.billDate) || b.id - a.id)
}

function Shell({ children }) {
  const { user, logout } = useAuth()
  const nav = useNavigate()
  return <div className="app-shell"><aside className="sidebar">
    <div className="brand"><span className="brand-mark">S</span><div><b>StockSight</b><small>Inventory intelligence</small></div></div>
    <nav><Link to="/">Overview</Link><Link to="/products">Products</Link><Link to="/movements">Stock movements</Link><Link to="/assistant">AI assistant</Link></nav>
    <div className="profile"><div className="avatar">{user?.username?.[0]?.toUpperCase() || 'U'}</div><div className="profile-name"><b>{user?.username}</b><small>{user?.role}</small></div><button className="icon-btn" title="Log out" onClick={() => { logout(); nav('/login') }}>↗</button></div>
  </aside><main className="main">{children}</main></div>
}
function Login() {
  const { login, user } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [passwordFocused, setPasswordFocused] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const nav = useNavigate()

  if (user) return <Navigate to="/" replace />

  async function submit(e) {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      await login(username, password)
      nav('/')
    } catch (err) {
      setError(
        err.response?.data?.error ||
        err.response?.data?.message ||
        'Could not authenticate. Check the API and credentials.'
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="brand">
          <span className="brand-mark">S</span>
          <div>
            <b>StockSight</b>
            <small>Inventory intelligence</small>
          </div>
        </div>
        <h1>Welcome back</h1>
        <p className="muted">Know what you have. Predict what you need.</p>
        <form onSubmit={submit}>
          <label>
            Username
            <input
              value={username}
              onChange={e => setUsername(e.target.value)}
              required
              minLength={3}
              autoComplete="username"
            />
          </label>
          

<label>
  Password
  <div className="password-field">
    <input
      type={showPassword ? 'text' : 'password'}
      value={password}
      onChange={e => setPassword(e.target.value)}
      onFocus={() => setPasswordFocused(true)}
      onBlur={() => setPasswordFocused(false)}
      required
      minLength={8}
      autoComplete="current-password"
    />

    {passwordFocused && password.length > 0 && (
      <button
        type="button"
        className="password-toggle"
        onMouseDown={e => e.preventDefault()}
        onClick={() => setShowPassword(value => !value)}
        aria-label={showPassword ? 'Hide password' : 'Show password'}
        title={showPassword ? 'Hide password' : 'Show password'}
      >
        {showPassword ? (
          <svg viewBox="0 0 24 24" width="20" height="20"
            fill="none" stroke="currentColor" strokeWidth="1.8"
            strokeLinecap="round" strokeLinejoin="round">
            <path d="M3 3l18 18" />
            <path d="M10.6 10.6a2 2 0 002.8 2.8" />
            <path d="M9.9 5.2A10.8 10.8 0 0112 5c5 0 8.5 4.5 9.5 7
              a11 11 0 01-4 4.8" />
            <path d="M6.2 6.2A12 12 0 002.5 12c1 2.5 4.5 7 9.5 7
              a10 10 0 004-.8" />
          </svg>
        ) : (
          <svg viewBox="0 0 24 24" width="20" height="20"
            fill="none" stroke="currentColor" strokeWidth="1.8"
            strokeLinecap="round" strokeLinejoin="round">
            <path d="M2.5 12s3.5-7 9.5-7 9.5 7 9.5 7-3.5 7-9.5 7-9.5-7-9.5-7z" />
            <circle cx="12" cy="12" r="3" />
          </svg>
        )}
      </button>
    )}
  </div>
</label>


          {error && <div className="alert danger">{error}</div>}
          <button className="primary full" disabled={busy}>
            {busy ? 'Please wait…' : 'Sign in'}
          </button>
        </form>
      </div>
    </div>
  )
}
function Dashboard() {
 const [products,setProducts]=useState([]),[movements,setMovements]=useState([]),[error,setError]=useState(''),[loading,setLoading]=useState(true)
 async function load(){setLoading(true);try{const [allProducts,m]=await Promise.all([loadAllProducts(),api.get('/movements')]);setProducts(allProducts);setMovements(newestFirstMovements(m.data||[]));setError('')}catch(e){setError(e.response?.data?.error||'Could not load dashboard. Ensure backend is running and you are signed in.')}finally{setLoading(false)}}
 useEffect(()=>{load()},[])
 const low=products.filter(p=>p.lowStock)
 return <Shell><header className="page-head"><div><div className="eyebrow">INVENTORY OVERVIEW</div><h1>Good day. Here's your stock.</h1><p className="muted">A live snapshot of inventory health and recent activity.</p></div><button className="secondary" onClick={load} disabled={loading}>Refresh</button></header>{error&&<div className="alert danger">{error}</div>}
 <section className="stats"><div className="stat"><span>Total products</span><strong>{loading?'—':products.length}</strong><small>Tracked SKUs</small></div><div className="stat"><span>Units in stock</span><strong>{loading?'—':products.reduce((total,p)=>total+p.currentStock,0).toLocaleString()}</strong><small>Across all products</small></div><div className="stat"><span>Low stock</span><strong className={low.length?'warning-text':''}>{loading?'—':low.length}</strong><small>At or below safety threshold</small></div><div className="stat"><span>Movements</span><strong>{loading?'—':movements.length}</strong><small>Recorded transactions</small></div></section>
 <section className="panel"><div className="panel-head"><div><h2>Low-stock alerts</h2><p className="muted">Products at or below their configured safety threshold.</p></div><Link className="text-link" to="/products">Manage products</Link></div>{loading?<div className="empty">Loading inventory…</div>:low.length?<ProductTable products={low}/>:<div className="empty">No products are at or below their safety threshold.</div>}</section>
 <section className="panel"><div className="panel-head"><div><h2>Inventory health</h2><p className="muted">Items that need attention appear first.</p></div><Link className="text-link" to="/products">View all →</Link></div>{loading?<div className="empty">Loading inventory…</div>:<ProductTable products={[...products].sort((a,b)=>Number(b.lowStock)-Number(a.lowStock)).slice(0,8)}/>}</section>
 <section className="panel"><div className="panel-head"><div><h2>Recent movements</h2><p className="muted">Latest recorded stock activity.</p></div><Link className="text-link" to="/movements">View movements →</Link></div>{loading?<div className="empty">Loading movements…</div>:<MovementTable movements={movements.slice(0,6)}/>}</section>
 <DashboardIntelligence/>
 </Shell>
}
function DashboardIntelligence() {
 const [suggestions,setSuggestions]=useState([]),[anomalies,setAnomalies]=useState([]),[error,setError]=useState(''),[loading,setLoading]=useState(true)
 async function load(){setLoading(true);try{const [r,a]=await Promise.all([api.get('/products/reorder-suggestions'),api.get('/movements/anomalies')]);setSuggestions(r.data||[]);setAnomalies(a.data||[]);setError('')}catch(e){setError(e.response?.data?.error||'Could not load reorder and anomaly analysis.')}finally{setLoading(false)}}
 useEffect(()=>{load()},[])
 return <>{error&&<div className="alert danger">{error}</div>}<section className="panel"><div className="panel-head"><div><h2>Reorder suggestions</h2><p className="muted">Based on actual outward movements and each product’s safety threshold.</p></div></div>{loading?<div className="empty">Loading reorder analysis…</div>:suggestions.length?<div className="table-wrap"><table><thead><tr><th>Product</th><th>Current</th><th>Threshold</th><th>Outward</th><th>Suggested</th><th>History</th></tr></thead><tbody>{suggestions.map(item=><tr key={item.productId}><td><Link className="text-link" to={`/products/${item.productId}`}>{item.productName} <span className="code">{item.sku}</span></Link></td><td>{item.currentStock}</td><td>{item.safetyThreshold}</td><td>{item.totalOutwardQuantity} units / {item.periodDays} days</td><td><b>{item.suggestedReorderQuantity}</b></td><td>{item.sufficientHistory?'Sufficient':'Sparse'}</td></tr>)}</tbody></table></div>:<div className="empty">No reorder recommendations from the selected historical data.</div>}</section><section className="panel"><div className="panel-head"><div><h2>Movement anomalies</h2><p className="muted">Rule-based flags from recorded transactions in the recent analysis window.</p></div></div>{loading?<div className="empty">Loading anomaly checks…</div>:anomalies.length?<div className="table-wrap"><table><thead><tr><th>Severity</th><th>Product</th><th>Signal</th><th>Transaction</th><th>Stock after</th></tr></thead><tbody>{anomalies.slice(0,8).map((item,index)=><tr key={`${item.transactionId}-${item.type}-${index}`}><td><span className={`pill ${item.severity==='HIGH'?'pill-warn':'pill-neutral'}`}>{item.severity}</span></td><td><Link className="text-link" to={`/products/${item.productId}`}>{item.productName} <span className="code">{item.sku}</span></Link></td><td title={item.reason}>{item.type}: {item.reason}</td><td>#{item.transactionId} {item.movementType} {item.quantity} · {item.billDate}</td><td>{item.stockAfter}</td></tr>)}</tbody></table></div>:<div className="empty">No rule-based movement anomalies in the analysis window.</div>}</section></>
}
function ProductTable({products,onEdit,onDelete}) {
 if(!products.length)return <div className="empty">No products yet. An administrator can create the first product.</div>
 return <div className="table-wrap"><table><thead><tr><th>Product</th><th>SKU</th><th>Current stock</th><th>Safety threshold</th><th>Status</th>{onEdit&&<th>Actions</th>}</tr></thead><tbody>{products.map(p=><tr key={p.id}><td><Link className="text-link" to={`/products/${p.id}`}><b>{p.name}</b></Link></td><td className="code">{p.sku}</td><td>{p.currentStock.toLocaleString()}</td><td>{p.safetyThreshold}</td><td><span className={`pill ${p.lowStock?'pill-warn':'pill-ok'}`}>{p.lowStock?'Low stock':'Healthy'}</span></td>{onEdit&&<td><button className="secondary" type="button" onClick={()=>onEdit(p)}>Edit</button> <button className="secondary" type="button" onClick={()=>onDelete(p)}>Delete</button></td>}</tr>)}</tbody></table></div>
}
function ProductDetails() {
 const {id}=useParams(),[product,setProduct]=useState(null),[analytics,setAnalytics]=useState(null),[movements,setMovements]=useState([]),[error,setError]=useState(''),[loading,setLoading]=useState(true),[from,setFrom]=useState(()=>new Date(Date.now()-29*86400000).toISOString().slice(0,10)),[to,setTo]=useState(()=>new Date().toISOString().slice(0,10))
 async function load(){setLoading(true);setError('');try{const [p,a,m]=await Promise.all([api.get(`/products/${id}`),api.get(`/products/${id}/analytics`,{params:{from,to}}),api.get('/movements',{params:{productId:id}})]);setProduct(p.data);setAnalytics(a.data);setMovements(newestFirstMovements(m.data||[]))}catch(e){setError(e.response?.data?.error||'Could not load product details.')}finally{setLoading(false)}}
 useEffect(()=>{load()},[id,from,to])
 return <Shell><header className="page-head"><div><div className="eyebrow">PRODUCT DETAILS</div><h1>{product?.name||'Product details'}</h1><p className="muted">{product?`SKU ${product.sku}`:'Inventory, demand and movement history.'}</p></div><div><Link className="secondary" to="/products">Back to products</Link> <button className="secondary" onClick={load} disabled={loading}>Refresh</button></div></header>{error&&<div className="alert danger">{error}</div>}{loading&&!product&&<div className="panel empty">Loading product details…</div>}{product&&<><section className="panel"><div className="product-form"><label>Analysis from<input type="date" value={from} max={to} onChange={event=>setFrom(event.target.value)}/></label><label>Analysis to<input type="date" value={to} min={from} max={new Date().toISOString().slice(0,10)} onChange={event=>setTo(event.target.value)}/></label></div></section><section className="stats"><div className="stat"><span>Current stock</span><strong>{product.currentStock.toLocaleString()}</strong><small><span className={`pill ${product.lowStock?'pill-warn':'pill-ok'}`}>{product.lowStock?'Low stock':'Healthy'}</span></small></div><div className="stat"><span>Safety threshold</span><strong>{product.safetyThreshold.toLocaleString()}</strong><small>Configured minimum</small></div><div className="stat"><span>Outward consumption</span><strong>{analytics?.totalOutwardQuantity?.toLocaleString()??'—'}</strong><small>{analytics?.outwardMovementCount??'—'} transactions · selected period</small></div><div className="stat"><span>Average daily outward</span><strong>{analytics?.periodAverageDailyOutward?.toLocaleString()??'—'}</strong><small>{analytics?.periodDays??'—'} calendar days</small></div></section><section className="panel"><div className="panel-head"><div><h2>Reorder analysis</h2><p className="muted">{analytics?.method||'Inventory analytics from the API.'}</p></div><span className={`pill ${analytics?.reorderRecommended?'pill-warn':'pill-ok'}`}>{analytics?.reorderRecommended?'Reorder recommended':'No reorder recommended'}</span></div>{analytics&&!analytics.sufficientHistory&&product.currentStock>product.safetyThreshold&&<div className="alert danger">Insufficient outward history for a consumption-based prediction. Only the safety threshold is applied.</div>}<p>Suggested reorder quantity: <b>{analytics?.suggestedReorderQuantity?.toLocaleString()??'—'}</b></p><p className="muted">Estimated days to safety threshold: {analytics?.estimatedDaysUntilSafetyThreshold??'Unavailable'} · Lead time is not configured.</p></section><section className="panel"><div className="panel-head"><div><h2>Stock history</h2><p className="muted">Inward and outward transactions for this product.</p></div></div>{loading?<div className="empty">Refreshing history…</div>:<MovementTable movements={movements}/>}</section></>}</Shell>
}
function Products() {
 const {user}=useAuth(),[products,setProducts]=useState([]),[error,setError]=useState(''),[notice,setNotice]=useState(''),[form,setForm]=useState({name:'',sku:'',openingBalance:0,safetyThreshold:5}),[editingProduct,setEditingProduct]=useState(null),[busy,setBusy]=useState(false),[page,setPage]=useState(0),[totalPages,setTotalPages]=useState(0),[queryDraft,setQueryDraft]=useState(''),[query,setQuery]=useState('')
 async function load(){try{const r=await api.get('/products',{params:{page,size:10,...(query?{q:query}:{})}});setProducts(r.data.content||[]);setTotalPages(r.data.totalPages||0);setError('')}catch(e){setError(e.response?.data?.error||'Failed to load products.')}}
 useEffect(()=>{load()},[page,query])
 function startEdit(product){setEditingProduct(product);setForm({name:product.name,sku:product.sku,openingBalance:product.openingBalance,safetyThreshold:product.safetyThreshold});setNotice('')}
 function cancelEdit(){setEditingProduct(null);setForm({name:'',sku:'',openingBalance:0,safetyThreshold:5})}
 async function save(e){e.preventDefault();setBusy(true);setError('');setNotice('');const payload={...form,openingBalance:Number(form.openingBalance),safetyThreshold:Number(form.safetyThreshold)};try{if(editingProduct){await api.put(`/products/${editingProduct.id}`,payload);setNotice('Product updated.');cancelEdit()}else{await api.post('/products',payload);setForm({name:'',sku:'',openingBalance:0,safetyThreshold:5});setNotice('Product created.')}await load()}catch(e){setError(e.response?.data?.error||(editingProduct?'Could not update product.':'Could not create product. ADMIN role required.'))}finally{setBusy(false)}}
 async function remove(product){if(!window.confirm(`Delete ${product.name}? Products with stock movement history cannot be deleted.`))return;setError('');setNotice('');try{await api.delete(`/products/${product.id}`);if(editingProduct?.id===product.id)cancelEdit();setNotice('Product deleted.');await load()}catch(e){setError(e.response?.data?.error||'Could not delete product.')}}
 function searchProducts(e){e.preventDefault();setPage(0);setQuery(queryDraft.trim())}
 return <Shell><header className="page-head"><div><div className="eyebrow">CATALOG</div><h1>Products</h1><p className="muted">Manage SKUs and monitor stock thresholds.</p></div></header>{error&&<div className="alert danger">{error}</div>}{notice&&<div className="alert success">{notice}</div>}{user?.role==='ADMIN'&&<section className="panel"><h2>{editingProduct?'Edit product':'Add product'}</h2><form className="product-form" onSubmit={save}><label>Product name<input value={form.name} onChange={e=>setForm({...form,name:e.target.value})} required/></label><label>SKU<input value={form.sku} onChange={e=>setForm({...form,sku:e.target.value})} required/></label><label>Opening balance{editingProduct?' (unchanged)':''}<input type="number" min="0" value={form.openingBalance} onChange={e=>setForm({...form,openingBalance:e.target.value})} required disabled={Boolean(editingProduct)}/></label><label>Safety threshold<input type="number" min="0" value={form.safetyThreshold} onChange={e=>setForm({...form,safetyThreshold:e.target.value})} required/></label><button className="primary" disabled={busy}>{busy?'Saving…':editingProduct?'Save changes':'Add product'}</button>{editingProduct&&<button className="secondary" type="button" onClick={cancelEdit} disabled={busy}>Cancel</button>}</form></section>}<section className="panel"><div className="panel-head"><h2>All products</h2><button className="secondary" onClick={load}>Refresh</button></div><form className="product-form" onSubmit={searchProducts}><label>Search name or SKU<input value={queryDraft} onChange={e=>setQueryDraft(e.target.value)} placeholder="Enter a product name or SKU"/></label><button className="secondary" type="submit">Search</button>{query&&<button className="secondary" type="button" onClick={()=>{setQueryDraft('');setQuery('');setPage(0)}}>Clear</button>}</form><ProductTable products={products} onEdit={user?.role==='ADMIN'?startEdit:undefined} onDelete={user?.role==='ADMIN'?remove:undefined}/>{totalPages>1&&<div className="panel-head"><span className="muted">Page {page+1} of {totalPages}</span><div><button className="secondary" disabled={page===0} onClick={()=>setPage(page-1)}>Previous</button> <button className="secondary" disabled={page+1>=totalPages} onClick={()=>setPage(page+1)}>Next</button></div></div>}</section></Shell>
}
function Movements() {
 const [products,setProducts]=useState([]),[items,setItems]=useState([]),[error,setError]=useState(''),[notice,setNotice]=useState(null),[form,setForm]=useState({productId:'',movementType:'INWARD',quantity:1,billDate:new Date().toISOString().slice(0,10),deliveryDate:new Date().toISOString().slice(0,10)})
 async function load(){try{const [allProducts,m]=await Promise.all([loadAllProducts(),api.get('/movements')]);setProducts(allProducts);setItems(newestFirstMovements(m.data||[]));if(!form.productId&&allProducts.length)setForm(f=>({...f,productId:String(allProducts[0].id)}));setError('')}catch(e){setError(e.response?.data?.error||'Failed to load movements.')}}
 useEffect(()=>{load()},[])
 async function submit(e){e.preventDefault();setError('');setNotice(null);try{const {data}=await api.post('/movements',{...form,productId:Number(form.productId),quantity:Number(form.quantity)});setNotice(data.anomaly?{kind:'danger',text:`Anomaly flagged: ${data.anomalyReason} Current stock: ${data.currentStock}`}:{kind:'success',text:`Movement saved. Current stock: ${data.currentStock}`});await load()}catch(e){setError(e.response?.data?.error||'Could not save movement.')}}
 const selectedProduct=products.find(product=>product.id===Number(form.productId))
 return <Shell><header className="page-head"><div><div className="eyebrow">TRANSACTION LEDGER</div><h1>Stock movements</h1><p className="muted">Every inward and outward movement recalculates stock.</p></div></header>{error&&<div className="alert danger">{error}</div>}{notice&&<div className={`alert ${notice.kind}`}>{notice.text}</div>}<section className="panel"><h2>Record movement</h2>{!products.length?<p className="muted">Create a product first. ADMIN role required for product creation.</p>:<form className="movement-form" onSubmit={submit}><label>Product<select required value={form.productId} onChange={e=>setForm({...form,productId:e.target.value})}>{products.map(p=><option key={p.id} value={p.id}>{p.name} ({p.sku}) · {p.currentStock} units</option>)}</select></label><label>Movement type<select value={form.movementType} onChange={e=>setForm({...form,movementType:e.target.value})}><option value="INWARD">Inward</option><option value="OUTWARD">Outward</option></select></label><label>Quantity<input type="number" min="1" max={form.movementType==='OUTWARD'?selectedProduct?.currentStock:undefined} required value={form.quantity} onChange={e=>setForm({...form,quantity:e.target.value})}/></label><label>Bill date<input type="date" required value={form.billDate} onChange={e=>setForm({...form,billDate:e.target.value})}/></label><label>Delivery date<input type="date" min={form.billDate} required value={form.deliveryDate} onChange={e=>setForm({...form,deliveryDate:e.target.value})}/></label><button className="primary">Save movement</button></form>}</section><section className="panel"><h2>Movement history</h2><MovementTable movements={items}/></section></Shell>
}
function MovementTable({movements}) {
 if(!movements.length)return <div className="empty">No movements recorded.</div>
 return <div className="table-wrap"><table><thead><tr><th>Product</th><th>Type</th><th>Quantity</th><th>Bill date</th><th>Delivery date</th></tr></thead><tbody>{movements.map(m=><tr key={m.id}><td><Link className="text-link" to={`/products/${m.productId}`}>{m.productName}</Link></td><td><span className={`pill ${m.movementType==='INWARD'?'pill-ok':'pill-neutral'}`}>{m.movementType}</span></td><td>{m.quantity}</td><td>{m.billDate}</td><td>{m.deliveryDate}</td></tr>)}</tbody></table></div>
}

function Assistant() {
  const [question, setQuestion] = useState('Which products need reordering?');
  const [messages, setMessages] = useState([
    {
      role: 'assistant',
      content:
        'Hi! I can explain current stock, safety thresholds, recent outward movements and reorder suggestions using the inventory data available to StockSight.',
    },
  ]);
  const [busy, setBusy] = useState(false);

  const faqs = [
    'Which products need reordering?',
    'What is the current stock of each product?',
    'Which products are below their safety threshold?',
    'Which products have the highest consumption?',
    'Summarize recent stock movements.',
    'Are there any unusual stock movements?',
  ];

  async function send(e, selectedQuestion) {
    e?.preventDefault();

    const text = (selectedQuestion ?? question).trim();
    if (!text || busy) return;

    setMessages((prev) => [...prev, { role: 'user', content: text }]);
    setQuestion('');
    setBusy(true);

    try {
const { data } = await api.post('/ai/chat', { question: text });      setMessages((prev) => [
        ...prev,
        {
          role: 'assistant',
          content: data.answer ?? 'No answer was returned.',
          contextSummary: data.contextSummary,
        },
      ]);
    } catch (err) {
      setMessages((prev) => [
        ...prev,
        {
          role: 'assistant',
          content:
            err.response?.data?.message ??
            'Unable to get an answer. Please try again.',
        },
      ]);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Shell>
      <header className="page-head">
        <div>
          <div className="eyebrow">STOCKSIGHT ASSISTANT</div>
          <h1>Ask your inventory</h1>
          <p className="muted">
            Get inventory answers and reorder insights using your stock data.
          </p>
        </div>
      </header>

      <section className="chat-panel">
        <div className="chat-heading">
          <div className="assistant-icon">✳</div>
          <div>
            <b>Inventory assistant</b>
            <small>Live inventory retrieval · AI-powered answers</small>
          </div>
        </div>

        <div className="messages">
          {messages.map((m, i) => (
            <div key={i} className={`message ${m.role}`}>
              <div className="message-role">
                {m.role === 'assistant' ? 'StockSight' : 'You'}
              </div>
              <div className="message-content">
  <ReactMarkdown>{m.content}</ReactMarkdown>
</div>
              {m.contextSummary && (
                <small className="hint">{m.contextSummary}</small>
              )}
            </div>
          ))}

          {busy && (
            <div className="message assistant">
              <div className="message-role">StockSight</div>
              Analyzing inventory…
            </div>
          )}
        </div>

        <div className="faq-section">
          <b>Frequently asked questions</b>
          <div className="faq-list">
            {faqs.map((faq) => (
              <button
                key={faq}
                type="button"
                className="faq-button"
                disabled={busy}
                onClick={() => send(null, faq)}
              >
                {faq} ↗
              </button>
            ))}
          </div>
        </div>

        <form className="chat-input" onSubmit={send}>
          <input
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            placeholder="Ask about stock, consumption, reorders or anomalies…"
            maxLength={1000}
          />
          <button
            className="primary"
            disabled={busy || !question.trim()}
          >
            Send ↑
          </button>
        </form>

        <div className="hint">
          Recommendations use recorded movements and configured stock
          thresholds. Verify operational decisions against the inventory ledger.
        </div>
      </section>
    </Shell>
  );
}

export default function App() {
 return <Routes><Route path="/login" element={<Login/>}/><Route element={<ProtectedRoute/>}><Route path="/" element={<Dashboard/>}/><Route path="/products" element={<Products/>}/><Route path="/products/:id" element={<ProductDetails/>}/><Route path="/movements" element={<Movements/>}/><Route path="/assistant" element={<Assistant/>}/></Route><Route path="*" element={<Navigate to="/" replace/>}/></Routes>
}
