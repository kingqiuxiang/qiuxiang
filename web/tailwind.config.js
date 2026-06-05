/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'PingFang SC', 'Microsoft YaHei', 'sans-serif'],
        mono: ['JetBrains Mono', 'ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
      },
      colors: {
        ink: {
          950: '#070a13',
          900: '#0b0f1a',
          850: '#0f1422',
          800: '#141a2b',
          700: '#1c2438',
          600: '#28324c',
        },
        brand: {
          400: '#7c9cff',
          500: '#5b7cfa',
          600: '#4361ee',
        },
        accent: {
          400: '#34d8c8',
          500: '#10b6a4',
        },
      },
      boxShadow: {
        glow: '0 0 0 1px rgba(123,156,255,0.15), 0 18px 50px -12px rgba(67,97,238,0.45)',
        card: '0 10px 40px -16px rgba(0,0,0,0.6)',
      },
      keyframes: {
        shimmer: {
          '100%': { transform: 'translateX(100%)' },
        },
        floaty: {
          '0%,100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-6px)' },
        },
      },
      animation: {
        shimmer: 'shimmer 1.6s infinite',
        floaty: 'floaty 6s ease-in-out infinite',
      },
    },
  },
  plugins: [],
};
