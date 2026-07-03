import { Component } from 'react'
import type { ReactNode } from 'react'

interface Props { children: ReactNode }
interface State { error: Error | null }

export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  render() {
    if (this.state.error) {
      return (
        <div style={{ padding: 40, fontFamily: 'monospace', background: '#fff', minHeight: '100vh' }}>
          <h1 style={{ color: '#ef4444', fontSize: 20, marginBottom: 16 }}>⚠️ 렌더링 에러</h1>
          <pre style={{ background: '#fef2f2', padding: 16, borderRadius: 4, overflow: 'auto', fontSize: 13, color: '#991b1b' }}>
            {this.state.error.message}
            {'\n\n'}
            {this.state.error.stack}
          </pre>
          <button
            onClick={() => this.setState({ error: null })}
            style={{ marginTop: 16, padding: '8px 16px', background: '#111', color: '#fff', border: 'none', cursor: 'pointer' }}
          >
            재시도
          </button>
        </div>
      )
    }
    return this.props.children
  }
}
