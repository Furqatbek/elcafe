module.exports = {
  root: true,
  env: { browser: true, es2020: true, node: true },
  extends: [
    'eslint:recommended',
    'plugin:react/recommended',
    'plugin:react/jsx-runtime',
    'plugin:react-hooks/recommended',
  ],
  ignorePatterns: ['dist', '.eslintrc.cjs', 'public/sw.js'],
  parserOptions: { ecmaVersion: 'latest', sourceType: 'module' },
  settings: { react: { version: '18.3' } },
  plugins: ['react-refresh'],
  rules: {
    'react-refresh/only-export-components': 'off',
    'react/prop-types': 'off',
    'no-unused-vars': ['warn', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
    'react-hooks/exhaustive-deps': 'warn',
    // EH-4.1 (docs/ERROR_HANDLING_PLAN.md): the 245 alert() popups were replaced by the toast
    // pipeline (notifyError / notifySuccess / notifyWarning from lib/errors). Keep them gone.
    // Scoped to alert/prompt (not confirm — the delete-confirmation dialogs are a separate,
    // tracked cleanup); a new alert() fails lint.
    'no-restricted-globals': ['error',
      { name: 'alert', message: 'Use notifyError / notifySuccess / notifyWarning from lib/errors instead of alert().' },
      { name: 'prompt', message: 'Use a proper input dialog instead of prompt().' },
    ],
  },
}
