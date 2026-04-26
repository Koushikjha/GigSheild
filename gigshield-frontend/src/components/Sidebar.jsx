import { NavLink } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

const navItems = [
  { to: '/', label: 'Dashboard', short: 'Live risk', icon: 'D' },
  { to: '/claims', label: 'My Claims', short: 'Auto payouts', icon: 'C' },
  { to: '/history', label: 'History', short: 'Payouts & policies', icon: 'H' },
  { to: '/plans', label: 'Plans', short: 'Choose weekly', icon: 'P' },
]

export default function Sidebar({ open, onClose, onNavigate }) {
  const { worker, currentPlan, logout } = useAuth()

  return (
    <aside className={`sidebar ${open ? 'sidebar--open' : ''}`} aria-hidden={!open}>
      <div className="sidebar__brand">
        <div className="sidebar__mark">G</div>
        <div className="sidebar__brand-copy">
          <strong>GigShield</strong>
        </div>
        <button className="sidebar__close" type="button" onClick={onClose} aria-label="Close navigation menu">
          ×
        </button>
      </div>

      <div className="sidebar__profile">
        <div>
          <p className="sidebar__profile-label">Worker profile</p>
          <div className="sidebar__profile-name">{worker.name}</div>
        </div>
        <div className="sidebar__profile-meta">
          <div>
            Zone: <strong>{worker.zone}</strong>
          </div>
          <div>
            Weekly income: <strong>₹{worker.weeklyIncome.toLocaleString('en-IN')}</strong>
          </div>
          <div>
            Weekly plan: <strong>{currentPlan}</strong>
          </div>
        </div>
      </div>

      <nav className="sidebar__nav" aria-label="Main navigation">
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            onClick={onNavigate}
            className={({ isActive }) => `sidebar__nav-link ${isActive ? 'sidebar__nav-link--active' : ''}`}
          >
            <span className="sidebar__nav-icon" aria-hidden="true">
              {item.icon}
            </span>
            <span className="sidebar__nav-copy">
              <strong>{item.label}</strong>
              <span>{item.short}</span>
            </span>
          </NavLink>
        ))}
      </nav>

      <button
        type="button"
        className="button button--secondary sidebar__logout"
        onClick={() => {
          onNavigate()
          logout()
        }}
      >
        Logout
      </button>

      <div className="sidebar__footer">
        <strong>Built for auto-payouts.</strong>
        <p className="muted" style={{ marginTop: 8 }}>
          No forms, no claim filing. Coverage tracks live risk and active deliveries.
        </p>
      </div>
    </aside>
  )
}