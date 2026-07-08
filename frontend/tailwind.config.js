/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: { DEFAULT: '#111827', foreground: '#ffffff' },
        secondary: { DEFAULT: '#f3f4f6', foreground: '#111827' },
        destructive: { DEFAULT: '#ef4444', foreground: '#ffffff' },
        muted: { DEFAULT: '#f9fafb', foreground: '#6b7280' },
        border: '#e5e7eb',
        background: '#ffffff',
        foreground: '#111827',
      },
      borderRadius: { lg: '0.5rem', md: '0.375rem', sm: '0.25rem' },
    },
  },
  plugins: [],
}

